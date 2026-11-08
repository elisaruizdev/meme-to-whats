package com.memetowhats.app.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.memetowhats.app.MemeApp
import com.memetowhats.app.data.MediaType
import com.memetowhats.app.media.Bitmaps
import com.memetowhats.app.media.CropRect
import com.memetowhats.app.media.GifMaker
import com.memetowhats.app.media.GifOptions
import com.memetowhats.app.media.Gallery
import com.memetowhats.app.media.Originals
import com.memetowhats.app.media.AudioExtractor
import com.memetowhats.app.media.Subtitle
import com.memetowhats.app.media.Transcriber
import com.memetowhats.app.util.AppPrefs
import com.memetowhats.app.stickers.StickerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Lo creado, ya guardado en la biblioteca. [key] es su nombre allí (sirve para el pack de stickers). */
data class Creation(val file: File, val mimeType: String, val isSticker: Boolean, val key: String)

data class EditorState(
    val isVideo: Boolean,
    val preview: Bitmap? = null,
    val loadError: Boolean = false,
    val crop: CropRect = CropRect.Full,
    val caption: String = "",
    // Solo vídeo
    val durationMs: Long = 0,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val gifWidth: Int = 320,
    // Subtítulos (solo vídeo con sonido)
    val hasAudio: Boolean = false,
    val subtitles: List<Subtitle> = emptyList(),
    /** Tramo con el que se generaron, para avisar si luego se cambia. */
    val subtitlesRange: Pair<Long, Long>? = null,
    val transcribing: Boolean = false,
    /** Progreso de la descarga del modelo de voz (null = no se está descargando). */
    val modelDownload: Float? = null,
    val askModelDownload: Boolean = false,
    // Proceso y resultado
    val working: Boolean = false,
    val progress: Float = 0f,
    val result: Creation? = null,
    // Captura original en la galería
    /** Se puede quitar de la galería (es una foto/vídeo de MediaStore). */
    val canReplaceOriginal: Boolean = false,
    /** Quitar la original al crear y quedarse solo con el sticker o GIF. */
    val replaceOriginal: Boolean = true,
    /** Ya se quitó: no se puede volver a editar porque la fuente ya no existe. */
    val originalRemoved: Boolean = false,
)

sealed interface EditorEvent {
    data class Message(val text: String) : EditorEvent
    /** Hay que enseñar el aviso de Android para quitar la captura original. */
    data class ConfirmRemoveOriginal(val request: IntentSender) : EditorEvent
}

class EditorViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {
    private val uri: Uri = Uri.parse(checkNotNull(handle.get<String>("uri")))
    private val app get() = getApplication<Application>()
    private val creationsDir = File(app.filesDir, "creations").apply { mkdirs() }
    private val repository get() = (app as MemeApp).repository
    /** La captura en la galería, si se conoce (puede no coincidir con [uri], p. ej. al venir del portapapeles). */
    private val original: Uri? = handle.get<String>("original")?.let(Uri::parse)?.takeIf { Originals.isDeletable(it) }

    private val _state = MutableStateFlow(
        EditorState(
            isVideo = handle.get<Boolean>("video") == true,
            canReplaceOriginal = original != null,
            replaceOriginal = AppPrefs.replaceOriginal(application),
        ),
    )
    val state: StateFlow<EditorState> = _state

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var job: Job? = null
    private var previewJob: Job? = null

    init {
        cleanOldCreations()
        viewModelScope.launch {
            try {
                if (_state.value.isVideo) {
                    val (duration, frame) = GifMaker.probe(app, uri)
                    val hasAudio = withContext(Dispatchers.IO) { AudioExtractor.hasAudio(app, uri) }
                    _state.update {
                        it.copy(
                            hasAudio = hasAudio,
                            preview = frame,
                            loadError = frame == null,
                            durationMs = duration,
                            endMs = minOf(duration, 4_000L),
                        )
                    }
                } else {
                    val bitmap = withContext(Dispatchers.IO) { Bitmaps.decode(app, uri) }
                    _state.update { it.copy(preview = bitmap) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo abrir $uri", e)
                _state.update { it.copy(loadError = true) }
            }
        }
    }

    fun setCrop(crop: CropRect) = _state.update { it.copy(crop = crop) }
    fun resetCrop() = _state.update { it.copy(crop = CropRect.Full) }
    fun setCaption(text: String) = _state.update { it.copy(caption = text.take(80)) }
    fun setGifWidth(width: Int) = _state.update { it.copy(gifWidth = width) }

    // ---- Subtítulos ----

    fun generateSubtitles() {
        if (_state.value.transcribing) return
        if (!Transcriber.isModelReady(app)) {
            _state.update { it.copy(askModelDownload = true) }
            return
        }
        transcribe()
    }

    fun dismissModelDownload() = _state.update { it.copy(askModelDownload = false) }

    fun confirmModelDownload() {
        _state.update { it.copy(askModelDownload = false, modelDownload = 0f) }
        viewModelScope.launch {
            try {
                Transcriber.downloadModel(app) { p -> _state.update { it.copy(modelDownload = p) } }
                _state.update { it.copy(modelDownload = null) }
                transcribe()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo descargar el modelo", e)
                _state.update { it.copy(modelDownload = null) }
                _events.send(EditorEvent.Message("No se pudo descargar el reconocimiento de voz. ¿Tienes internet?"))
            }
        }
    }

    private fun transcribe() {
        val s = _state.value
        _state.update { it.copy(transcribing = true) }
        viewModelScope.launch {
            try {
                val subtitles = Transcriber.transcribe(app, uri, s.startMs, s.endMs)
                _state.update { it.copy(subtitles = subtitles, subtitlesRange = s.startMs to s.endMs) }
                if (subtitles.isEmpty()) _events.send(EditorEvent.Message("No he entendido ninguna frase en este tramo"))
            } catch (e: Exception) {
                Log.w(TAG, "Error al transcribir", e)
                _events.send(EditorEvent.Message("No se pudieron crear los subtítulos: ${e.message ?: "error"}"))
            } finally {
                _state.update { it.copy(transcribing = false) }
            }
        }
    }

    fun editSubtitle(index: Int, text: String) = _state.update { st ->
        st.copy(subtitles = st.subtitles.mapIndexed { i, sub -> if (i == index) sub.copy(text = text.take(80)) else sub })
    }

    fun removeSubtitle(index: Int) = _state.update { st ->
        st.copy(subtitles = st.subtitles.filterIndexed { i, _ -> i != index })
    }

    fun clearSubtitles() = _state.update { it.copy(subtitles = emptyList(), subtitlesRange = null) }

    fun setReplaceOriginal(value: Boolean) {
        AppPrefs.setReplaceOriginal(app, value)
        _state.update { it.copy(replaceOriginal = value) }
    }

    /** Recorta el vídeo; como mucho [GifMaker.MAX_DURATION_MS] para que el GIF no pese demasiado. */
    fun setRange(startMs: Long, endMs: Long) {
        val current = _state.value
        var start = startMs
        var end = endMs
        if (end - start > GifMaker.MAX_DURATION_MS) {
            if (start != current.startMs) end = start + GifMaker.MAX_DURATION_MS else start = end - GifMaker.MAX_DURATION_MS
        }
        _state.update { it.copy(startMs = start, endMs = end) }
    }

    /** Actualiza la vista previa con el fotograma de inicio cuando se suelta el control. */
    fun refreshPreviewFrame() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            GifMaker.frameAt(app, uri, _state.value.startMs)?.let { frame -> _state.update { it.copy(preview = frame) } }
        }
    }

    fun create() {
        if (job?.isActive == true) return
        _state.update { it.copy(working = true, progress = 0f) }
        job = viewModelScope.launch {
            try {
                val creation = if (_state.value.isVideo) makeGif() else makeSticker()
                _state.update { it.copy(result = creation) }
                val s = _state.value
                if (s.replaceOriginal && s.canReplaceOriginal) {
                    replaceOriginal(creation)
                } else {
                    _events.send(
                        EditorEvent.Message(
                            if (creation.isSticker) "Guardado en tu biblioteca y en tu pack de stickers" else "Guardado en tu biblioteca",
                        ),
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error al crear", e)
                _events.send(EditorEvent.Message("No se pudo crear: ${e.message ?: "error desconocido"}"))
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }

    /**
     * Deja en la galería lo creado en lugar de la captura: guarda el sticker/GIF y pide quitar la original.
     */
    private suspend fun replaceOriginal(creation: Creation) {
        val source = original ?: return
        runCatching { withContext(Dispatchers.IO) { Gallery.saveOnce(app, creation.file, creation.mimeType, creation.key) } }
        try {
            val request = if (Build.VERSION.SDK_INT >= 30) {
                Originals.trashRequest(app, source)
            } else {
                withContext(Dispatchers.IO) { Originals.deleteLegacy(app, source) }
            }
            if (request != null) {
                _events.send(EditorEvent.ConfirmRemoveOriginal(request))
            } else {
                onOriginalRemoved()
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo quitar la original", e)
            _events.send(EditorEvent.Message("Guardado en tu galería, pero no se pudo quitar la captura original"))
        }
    }

    /** Respuesta al aviso de Android para quitar la captura original. */
    fun onRemoveOriginalResult(accepted: Boolean) {
        viewModelScope.launch {
            val source = original ?: return@launch
            if (!accepted) {
                _events.send(EditorEvent.Message("Guardado en tu galería. Has conservado también la captura original"))
                return@launch
            }
            // En Android 10 el aviso solo da permiso: falta borrar de verdad.
            if (Build.VERSION.SDK_INT == 29) {
                runCatching { withContext(Dispatchers.IO) { app.contentResolver.delete(source, null, null) } }
            }
            onOriginalRemoved()
        }
    }

    private suspend fun onOriginalRemoved() {
        _state.update { it.copy(originalRemoved = true) }
        val kind = if (_state.value.isVideo) "el GIF" else "el sticker"
        val where = if (Build.VERSION.SDK_INT >= 30) "movida a la papelera" else "borrada"
        _events.send(EditorEvent.Message("En tu galería queda $kind; la captura original se ha $where"))
    }

    private suspend fun makeSticker(): Creation = withContext(Dispatchers.Default) {
        val s = _state.value
        // Se vuelve a leer a más resolución para que el recorte salga nítido.
        val source = Bitmaps.decode(app, uri, maxSide = 4096)
        // WhatsApp: 512×512, con margen transparente, WebP de 100 KB como máximo.
        val sticker = Bitmaps.fitInto(source, s.crop, 512, 512, margin = 16)
        Bitmaps.drawCaption(sticker, s.caption)
        val file = File(creationsDir, "sticker_${System.currentTimeMillis()}.webp")
        file.writeBytes(Bitmaps.toWebp(sticker, maxBytes = 100 * 1024))
        val (item, stored) = repository.addCreation(file, "image/webp", MediaType.STICKER, titleFor("Sticker", s.caption))
        val key = item.fileName.substringBeforeLast('.')
        // También al pack: si ya está añadido en WhatsApp, el sticker aparece allí solo.
        StickerStore.add(app, stored, key)
        Creation(stored, "image/webp", isSticker = true, key = key)
    }

    private fun titleFor(kind: String, caption: String): String =
        caption.trim().ifBlank { null }
            ?: "$kind ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())}"

    private suspend fun makeGif(): Creation {
        val s = _state.value
        val file = File(creationsDir, "gif_${System.currentTimeMillis()}.gif")
        GifMaker.make(
            context = app,
            uri = uri,
            options = GifOptions(
                s.startMs,
                s.endMs,
                s.crop,
                s.gifWidth,
                caption = s.caption,
                subtitles = s.subtitles.filter { it.text.isNotBlank() },
            ),
            target = file,
        ) { p -> _state.update { it.copy(progress = p) } }
        val (item, stored) = repository.addCreation(file, "image/gif", MediaType.GIF, titleFor("GIF", s.caption))
        return Creation(stored, "image/gif", isSticker = false, key = item.fileName.substringBeforeLast('.'))
    }

    fun cancel() {
        job?.cancel()
    }

    fun editAgain() = _state.update { it.copy(result = null) }

    fun saveToGallery() {
        val result = _state.value.result ?: return
        viewModelScope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { Gallery.saveOnce(app, result.file, result.mimeType, result.key) } }
                .fold(
                    { savedNow -> if (savedNow) "Guardado en la galería (Imágenes › Meme to Whats)" else "Ya estaba en la galería" },
                    { "No se pudo guardar: ${it.message}" },
                )
            _events.send(EditorEvent.Message(text))
        }
    }

    // Restos temporales (por ejemplo, si se cerró la app a medias); se limpian pasado un día.
    private fun cleanOldCreations() {
        val limit = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        creationsDir.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "EditorViewModel"
    }
}
