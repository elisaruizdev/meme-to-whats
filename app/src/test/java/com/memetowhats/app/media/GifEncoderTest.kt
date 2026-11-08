package com.memetowhats.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/**
 * Codifica con [GifEncoder] y vuelve a leer el resultado con un decodificador GIF mínimo
 * (los tests locales de Android no tienen javax.imageio).
 */
class GifEncoderTest {

    private class Decoded(val width: Int, val height: Int, val frames: List<IntArray>, val delays: List<Int>)

    private fun decode(bytes: ByteArray): Decoded {
        var pos = 0
        fun u8() = bytes[pos++].toInt() and 0xFF
        fun u16() = u8() or (u8() shl 8)
        assertEquals("GIF89a", String(bytes, 0, 6))
        pos = 6
        val width = u16()
        val height = u16()
        val packed = u8()
        u8(); u8()
        val paletteSize = 2 shl (packed and 7)
        val palette = IntArray(paletteSize) { (0xFF shl 24) or (u8() shl 16) or (u8() shl 8) or u8() }
        val frames = mutableListOf<IntArray>()
        val delays = mutableListOf<Int>()
        while (true) {
            when (u8()) {
                0x21 -> {
                    val label = u8()
                    if (label == 0xF9) {
                        u8(); u8(); delays += u16(); u8(); u8()
                    } else {
                        while (true) { val n = u8(); if (n == 0) break; pos += n }
                    }
                }
                0x2C -> {
                    u16(); u16()
                    assertEquals(width, u16())
                    assertEquals(height, u16())
                    u8()
                    val minCode = u8()
                    val data = ByteArrayOutputStream()
                    while (true) { val n = u8(); if (n == 0) break; data.write(bytes, pos, n); pos += n }
                    val indices = lzwDecode(data.toByteArray(), minCode, width * height)
                    frames += IntArray(width * height) { palette[indices[it]] }
                }
                0x3B -> return Decoded(width, height, frames, delays)
                else -> error("bloque inesperado en $pos")
            }
        }
    }

    private fun lzwDecode(data: ByteArray, minCode: Int, pixelCount: Int): IntArray {
        val clear = 1 shl minCode
        val eoi = clear + 1
        val prefix = IntArray(4096)
        val suffix = IntArray(4096)
        val out = IntArray(pixelCount)
        var outPos = 0
        var codeSize = minCode + 1
        var next = eoi + 1
        var prev = -1
        var bitPos = 0
        fun readCode(): Int {
            var code = 0
            for (i in 0 until codeSize) {
                val byte = data[(bitPos + i) / 8].toInt()
                if ((byte shr ((bitPos + i) % 8)) and 1 == 1) code = code or (1 shl i)
            }
            bitPos += codeSize
            return code
        }
        fun expand(code: Int): IntArray {
            val stack = ArrayList<Int>()
            var c = code
            while (c >= clear) { stack += suffix[c]; c = prefix[c] }
            stack += c
            return stack.reversed().toIntArray()
        }
        while (true) {
            val code = readCode()
            if (code == clear) { codeSize = minCode + 1; next = eoi + 1; prev = -1; continue }
            if (code == eoi) break
            val seq = when {
                prev == -1 -> expand(code)
                code < next -> expand(code).also { prefix[next] = prev; suffix[next] = it[0]; next++ }
                else -> expand(prev).let { p -> (p + p[0]).also { prefix[next] = prev; suffix[next] = p[0]; next++ } }
            }
            for (v in seq) out[outPos++] = v
            if (next == (1 shl codeSize) && codeSize < 12) codeSize++
            prev = code
        }
        assertEquals("número de píxeles", pixelCount, outPos)
        return out
    }

    private fun encode(name: String, w: Int, h: Int, frames: List<IntArray>): ByteArray {
        val encoder = GifEncoder(w, h)
        frames.forEach(encoder::addFrame)
        val bytes = ByteArrayOutputStream().also { encoder.write(it, delayCs = 10) }.toByteArray()
        // Con GIF_TEST_OUT se guarda una copia para revisarla con otro decodificador.
        System.getenv("GIF_TEST_OUT")?.let { dir -> File(dir).apply { mkdirs() }.resolve("$name.gif").writeBytes(bytes) }
        return bytes
    }

    private fun channelError(expected: Int, actual: Int): Int =
        listOf(16, 8, 0).maxOf { abs(((expected shr it) and 0xFF) - ((actual shr it) and 0xFF)) }

    @Test
    fun framesWithFewColorsRoundTrip() {
        val w = 40
        val h = 30
        val colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())
        val frames = (0 until 3).map { f -> IntArray(w * h) { i -> colors[(i / w + i % w + f) % colors.size] } }

        val decoded = decode(encode("pocos-colores", w, h, frames))
        assertEquals(3, decoded.frames.size)
        assertEquals(listOf(10, 10, 10), decoded.delays)
        for (f in frames.indices) for (i in 0 until w * h) {
            assertTrue("fotograma $f píxel $i", channelError(frames[f][i], decoded.frames[f][i]) <= 8)
        }
    }

    @Test
    fun noisyFrameFillsDictionaryAndStillDecodes() {
        // Ruido con muchos colores: obliga a llenar el diccionario LZW (4096 códigos) y reiniciarlo.
        val w = 320
        val h = 240
        val random = Random(42)
        val frame = IntArray(w * h) { (0xFF shl 24) or random.nextInt(0x1000000) }

        val decoded = decode(encode("ruido", w, h, listOf(frame)))
        val mean = (0 until w * h).sumOf { channelError(frame[it], decoded.frames[0][it]) } / (w * h).toDouble()
        assertTrue("error medio demasiado alto: $mean", mean < 40)
    }

    @Test
    fun gradientKeepsShape() {
        val w = 256
        val h = 64
        val frame = IntArray(w * h) { i -> val v = i % w; (0xFF shl 24) or (v shl 16) or ((255 - v) shl 8) or (v / 2) }
        val decoded = decode(encode("degradado", w, h, listOf(frame)))
        for (i in 0 until w * h) assertTrue("píxel $i", channelError(frame[i], decoded.frames[0][i]) <= 24)
    }
}
