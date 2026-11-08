package com.memetowhats.app.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

data class GifOptions(
    val startMs: Long,
    val endMs: Long,
    val crop: CropRect,
    /** Ancho del GIF en píxeles; el alto sale de la proporción del recorte. */
    val width: Int,
    val fps: Int = 10,
    val caption: String = "",
    /** Subtítulos con tiempos absolutos del vídeo; se dibujan abajo y el texto fijo pasa arriba. */
    val subtitles: List<Subtitle> = emptyList(),
)

object GifMaker {
    const val MAX_DURATION_MS = 8_000L

    /** Duración del vídeo y un fotograma para la vista previa del recorte. */
    suspend fun probe(context: Context, uri: Uri): Pair<Long, Bitmap?> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frame = scaledFrame(retriever, 0, 1080, 1920)
            duration to frame
        } finally {
            retriever.release()
        }
    }

    suspend fun frameAt(context: Context, uri: Uri, timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            scaledFrame(retriever, timeMs * 1000, 1080, 1920)
        } finally {
            retriever.release()
        }
    }

    /** Genera el GIF en [target]. [onProgress] recibe de 0 a 1. */
    suspend fun make(
        context: Context,
        uri: Uri,
        options: GifOptions,
        target: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val first = scaledFrame(retriever, options.startMs * 1000, 1080, 1920)
                ?: error("No se pudo leer el vídeo")
            val cropW = (options.crop.right - options.crop.left) * first.width
            val cropH = (options.crop.bottom - options.crop.top) * first.height
            val width = options.width
            // GIF de alto par y como mucho el doble de ancho, para que no pese demasiado.
            val height = ((width * cropH / cropW).roundToInt().coerceIn(16, width * 2)) and 1.inv()
            // Pedimos el fotograma justo lo bastante grande para que el recorte salga nítido.
            val requestW = (width / (options.crop.right - options.crop.left)).roundToInt()
            val requestH = (height / (options.crop.bottom - options.crop.top)).roundToInt()

            val encoder = GifEncoder(width, height)
            val stepMs = 1000L / options.fps
            val end = minOf(options.endMs, options.startMs + MAX_DURATION_MS)
            val times = generateSequence(options.startMs) { it + stepMs }.takeWhile { it < end }.toList()
            val pixels = IntArray(width * height)
            times.forEachIndexed { i, t ->
                coroutineContext.ensureActive()
                val frame = scaledFrame(retriever, t * 1000, requestW, requestH) ?: return@forEachIndexed
                val out = Bitmaps.fitInto(frame, options.crop, width, height, background = android.graphics.Color.BLACK)
                Bitmaps.drawCaption(out, options.caption, atTop = options.subtitles.isNotEmpty())
                options.subtitles.firstOrNull { t >= it.startMs && t < it.endMs }?.let { Bitmaps.drawSubtitle(out, it.text) }
                out.getPixels(pixels, 0, width, 0, 0, width, height)
                encoder.addFrame(pixels)
                frame.recycle()
                out.recycle()
                onProgress((i + 1f) / times.size * 0.85f)
            }
            if (encoder.frameCount == 0) error("No se pudo extraer ningún fotograma")
            onProgress(0.9f)
            target.outputStream().buffered().use { encoder.write(it, delayCs = 100 / options.fps) }
            onProgress(1f)
        } finally {
            retriever.release()
        }
    }

    private fun scaledFrame(retriever: MediaMetadataRetriever, timeUs: Long, maxW: Int, maxH: Int): Bitmap? {
        val option = MediaMetadataRetriever.OPTION_CLOSEST
        return if (Build.VERSION.SDK_INT >= 27) {
            retriever.getScaledFrameAtTime(timeUs, option, maxW.coerceAtLeast(16), maxH.coerceAtLeast(16))
        } else {
            retriever.getFrameAtTime(timeUs, option)
        }
    }
}
