package com.memetowhats.app.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** Una frase de subtítulo, con tiempos absolutos dentro del vídeo. */
data class Subtitle(val startMs: Long, val endMs: Long, val text: String)

private data class Word(val text: String, val startMs: Long, val endMs: Long)

/**
 * Voz a texto en el móvil con Vosk (gratis, sin internet una vez descargado el modelo,
 * y el audio no sale del móvil). Vosk da el momento de cada palabra, así los subtítulos
 * van sincronizados.
 */
object Transcriber {
    /** Modelo pequeño de español (~40 MB, licencia Apache 2.0). */
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
    const val MODEL_SIZE_MB = 40
    private const val READY_MARKER = ".ready"

    private var model: Model? = null

    private fun modelDir(context: Context) = File(context.filesDir, "vosk/es")

    fun isModelReady(context: Context) = File(modelDir(context), READY_MARKER).exists()

    /** Descarga y descomprime el modelo. [onProgress] va de 0 a 1. */
    suspend fun downloadModel(context: Context, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val dir = modelDir(context)
        dir.deleteRecursively()
        dir.mkdirs()
        val zip = File(context.cacheDir, "vosk-es.zip")
        try {
            val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: (MODEL_SIZE_MB * 1_000_000L)
            connection.inputStream.use { input ->
                zip.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        onProgress((done.toFloat() / total * 0.9f).coerceAtMost(0.9f))
                    }
                }
            }
            // El zip trae una carpeta raíz (vosk-model-small-es-0.42/); la quitamos.
            val root = dir.canonicalPath
            ZipInputStream(zip.inputStream().buffered()).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    val relative = entry.name.substringAfter('/', "")
                    if (relative.isEmpty()) continue
                    val target = File(dir, relative)
                    require(target.canonicalPath.startsWith(root)) { "Ruta no válida en el modelo" }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zin.copyTo(it) }
                    }
                }
            }
            File(dir, READY_MARKER).writeText("ok")
            onProgress(1f)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        } finally {
            zip.delete()
        }
    }

    /** Subtítulos del tramo [startMs, endMs) del vídeo. Lista vacía si no se entiende nada. */
    suspend fun transcribe(context: Context, uri: Uri, startMs: Long, endMs: Long): List<Subtitle> =
        withContext(Dispatchers.Default) {
            val pcm = AudioExtractor.decodeMono16k(context, uri, startMs, endMs)
            if (pcm.isEmpty()) return@withContext emptyList()
            val words = mutableListOf<Word>()
            Recognizer(loadModel(context), AudioExtractor.SAMPLE_RATE.toFloat()).use { recognizer ->
                recognizer.setWords(true)
                val chunk = 4_000
                var offset = 0
                while (offset < pcm.size) {
                    ensureActive()
                    val len = minOf(chunk, pcm.size - offset)
                    val part = pcm.copyOfRange(offset, offset + len)
                    if (recognizer.acceptWaveForm(part, len)) words += parse(recognizer.result, startMs)
                    offset += len
                }
                words += parse(recognizer.finalResult, startMs)
            }
            group(words, endMs)
        }

    @Synchronized
    private fun loadModel(context: Context): Model {
        model?.let { return it }
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        return Model(modelDir(context).path).also { model = it }
    }

    private fun parse(json: String, offsetMs: Long): List<Word> {
        val result = JSONObject(json).optJSONArray("result") ?: return emptyList()
        return (0 until result.length()).map { i ->
            val w = result.getJSONObject(i)
            Word(
                text = w.getString("word"),
                startMs = offsetMs + (w.getDouble("start") * 1000).toLong(),
                endMs = offsetMs + (w.getDouble("end") * 1000).toLong(),
            )
        }
    }

    /** Junta palabras en frases cortas: se corta en las pausas, a las ~6 palabras o a los 2,5 s. */
    private fun group(words: List<Word>, clipEndMs: Long): List<Subtitle> {
        val cues = mutableListOf<MutableList<Word>>()
        for (word in words) {
            val current = cues.lastOrNull()
            val startNew = current == null ||
                word.startMs - current.last().endMs > 700 ||
                current.size >= 6 ||
                word.endMs - current.first().startMs > 2_500 ||
                current.sumOf { it.text.length + 1 } + word.text.length > 34
            if (startNew) cues += mutableListOf(word) else current!!.add(word)
        }
        return cues.mapIndexed { i, cue ->
            val nextStart = cues.getOrNull(i + 1)?.first()?.startMs ?: clipEndMs
            // Se deja un poco más en pantalla para que dé tiempo a leerlo.
            val end = minOf(cue.last().endMs + 400, nextStart, clipEndMs)
            Subtitle(cue.first().startMs, end, cue.joinToString(" ") { it.text }.replaceFirstChar { it.uppercase() })
        }
    }
}
