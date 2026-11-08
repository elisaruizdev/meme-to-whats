package com.memetowhats.app.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Saca el audio de un vídeo como PCM de 16 kHz, mono y 16 bits, que es lo que necesita el
 * reconocedor de voz. Usa los decodificadores del propio móvil (MediaCodec), sin librerías.
 */
object AudioExtractor {
    const val SAMPLE_RATE = 16_000

    fun hasAudio(context: Context, uri: Uri): Boolean = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            audioTrack(extractor) != null
        } finally {
            extractor.release()
        }
    }.getOrDefault(false)

    private fun audioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }

    /** Audio del tramo [startMs, endMs) en 16 kHz mono. */
    fun decodeMono16k(context: Context, uri: Uri, startMs: Long, endMs: Long): ShortArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val track = audioTrack(extractor) ?: run {
            extractor.release()
            error("El vídeo no tiene sonido")
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val startUs = startMs * 1000
        val endUs = endMs * 1000
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val mono = FloatBuilder()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var idleRounds = 0

        try {
            while (!outputDone && idleRounds < 500) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > endUs) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    out >= 0 -> {
                        idleRounds = 0
                        val buffer = codec.getOutputBuffer(out)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.LITTLE_ENDIAN)
                        val bytesPerSample = if (floatPcm) 4 else 2
                        val frames = info.size / bytesPerSample / channels
                        val floats = if (floatPcm) buffer.asFloatBuffer() else null
                        val shorts = if (!floatPcm) buffer.asShortBuffer() else null
                        for (frame in 0 until frames) {
                            val t = info.presentationTimeUs + frame * 1_000_000L / sampleRate
                            if (t < startUs) continue
                            if (t >= endUs) break
                            var sum = 0f
                            for (c in 0 until channels) {
                                val i = frame * channels + c
                                sum += floats?.get(i) ?: (shorts!!.get(i) / 32768f)
                            }
                            mono.add(sum / channels)
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || info.presentationTimeUs > endUs) {
                            outputDone = true
                        }
                    }
                    else -> if (inputDone) idleRounds++
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        return resample(mono.toArray(), sampleRate)
    }

    /** Remuestreo lineal a 16 kHz y paso a enteros de 16 bits. */
    private fun resample(input: FloatArray, fromRate: Int): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        val outLength = (input.size.toLong() * SAMPLE_RATE / fromRate).toInt()
        val ratio = fromRate.toDouble() / SAMPLE_RATE
        return ShortArray(outLength) { i ->
            val pos = i * ratio
            val a = pos.toInt().coerceAtMost(input.size - 1)
            val b = (a + 1).coerceAtMost(input.size - 1)
            val frac = (pos - a).toFloat()
            val v = input[a] * (1 - frac) + input[b] * frac
            (v.coerceIn(-1f, 1f) * 32767).toInt().toShort()
        }
    }

    private class FloatBuilder {
        private var data = FloatArray(16_384)
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
        fun toArray() = data.copyOf(size)
    }
}
