package com.memetowhats.app.media

import java.io.OutputStream

/**
 * Codificador de GIF animado sin dependencias externas (Android no trae uno).
 *
 * Cómo funciona:
 * 1. Cada fotograma se reduce a color de 15 bits (5 bits por canal) → [ShortArray].
 * 2. Con el histograma de todos los fotogramas se calcula una paleta común de 256 colores
 *    por *median cut* (se parte el cubo de colores por la mediana del eje más largo).
 * 3. Cada píxel se sustituye por el índice del color más parecido y se comprime con LZW,
 *    que es la compresión que exige el formato GIF.
 */
class GifEncoder(private val width: Int, private val height: Int) {
    private val frames = mutableListOf<ShortArray>()
    private val histogram = IntArray(1 shl 15)

    /** [argb] tiene que medir width × height. */
    fun addFrame(argb: IntArray) {
        require(argb.size == width * height)
        val packed = ShortArray(argb.size)
        for (i in argb.indices) {
            val c = argb[i]
            val key = ((c shr 9) and 0x7C00) or ((c shr 6) and 0x03E0) or ((c shr 3) and 0x001F)
            packed[i] = key.toShort()
            histogram[key]++
        }
        frames += packed
    }

    val frameCount get() = frames.size

    /** Escribe el GIF completo. [delayCs] es la pausa entre fotogramas en centésimas de segundo. */
    fun write(out: OutputStream, delayCs: Int) {
        require(frames.isNotEmpty())
        val palette = medianCut(histogram, 256)
        val lookup = buildLookup(palette)

        out.write("GIF89a".toByteArray())
        writeShort(out, width)
        writeShort(out, height)
        out.write(0xF7) // tabla de color global de 256 entradas, 8 bits por color
        out.write(0) // color de fondo
        out.write(0) // proporción de píxel
        for (i in 0 until 256) {
            val c = palette.getOrElse(i) { 0 }
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }
        // Extensión NETSCAPE: repetir en bucle para siempre.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray())
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))

        val indices = ByteArray(width * height)
        for (frame in frames) {
            // Control gráfico: retardo del fotograma.
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x04))
            writeShort(out, delayCs)
            out.write(0)
            out.write(0)
            // Descriptor de imagen a pantalla completa, sin tabla local.
            out.write(0x2C)
            writeShort(out, 0)
            writeShort(out, 0)
            writeShort(out, width)
            writeShort(out, height)
            out.write(0)
            for (i in frame.indices) indices[i] = lookup[frame[i].toInt() and 0x7FFF]
            lzw(out, indices)
        }
        out.write(0x3B)
        out.flush()
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    // ---- Paleta ----

    private class Box(val keys: IntArray) {
        fun channel(key: Int, axis: Int) = (key shr (10 - axis * 5)) and 0x1F
        fun range(axis: Int): Int {
            var lo = 31
            var hi = 0
            for (k in keys) {
                val v = channel(k, axis)
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            return hi - lo
        }
    }

    private fun medianCut(hist: IntArray, maxColors: Int): IntArray {
        val used = hist.indices.filter { hist[it] > 0 }.toIntArray()
        if (used.isEmpty()) return intArrayOf(0)
        val boxes = mutableListOf(Box(used))
        while (boxes.size < maxColors) {
            // Partimos la caja con más píxeles que todavía se pueda dividir.
            val box = boxes.filter { it.keys.size > 1 }.maxByOrNull { b -> b.keys.sumOf { hist[it].toLong() } } ?: break
            val axis = (0..2).maxBy { box.range(it) }
            val sorted = box.keys.sortedBy { box.channel(it, axis) }
            val total = sorted.sumOf { hist[it].toLong() }
            var acc = 0L
            var cut = 1
            for ((i, k) in sorted.withIndex()) {
                acc += hist[k]
                if (acc * 2 >= total) {
                    cut = (i + 1).coerceIn(1, sorted.size - 1)
                    break
                }
            }
            boxes.remove(box)
            boxes += Box(sorted.subList(0, cut).toIntArray())
            boxes += Box(sorted.subList(cut, sorted.size).toIntArray())
        }
        // Color de cada caja = media ponderada por número de píxeles.
        return boxes.map { box ->
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0L
            for (k in box.keys) {
                val w = hist[k].toLong()
                r += box.channel(k, 0) * w
                g += box.channel(k, 1) * w
                b += box.channel(k, 2) * w
                n += w
            }
            val to8 = { v: Long -> ((v / n) * 255 / 31).toInt() }
            (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
        }.toIntArray()
    }

    /** Para cada color de 15 bits que aparece, el índice de la paleta más cercano. */
    private fun buildLookup(palette: IntArray): ByteArray {
        val lookup = ByteArray(1 shl 15)
        for (key in histogram.indices) {
            if (histogram[key] == 0) continue
            val r = ((key shr 10) and 0x1F) * 255 / 31
            val g = ((key shr 5) and 0x1F) * 255 / 31
            val b = (key and 0x1F) * 255 / 31
            var best = 0
            var bestDist = Int.MAX_VALUE
            for (i in palette.indices) {
                val c = palette[i]
                val dr = ((c shr 16) and 0xFF) - r
                val dg = ((c shr 8) and 0xFF) - g
                val db = (c and 0xFF) - b
                val d = dr * dr * 3 + dg * dg * 4 + db * db * 2 // el ojo nota más el verde
                if (d < bestDist) {
                    bestDist = d
                    best = i
                }
            }
            lookup[key] = best.toByte()
        }
        return lookup
    }

    // ---- LZW ----

    // Diccionario como tabla directa: clave = (prefijo << 8) | píxel. "stamp" evita vaciarla en cada CLEAR.
    private val codes = IntArray(1 shl 20)
    private val stamps = IntArray(1 shl 20)
    private var generation = 0

    private fun lzw(out: OutputStream, pixels: ByteArray) {
        val minCodeSize = 8
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1
        out.write(minCodeSize)

        val writer = BitWriter(out)
        var codeSize = minCodeSize + 1
        var next = eoiCode + 1
        generation++
        writer.write(clearCode, codeSize)

        var prefix = pixels[0].toInt() and 0xFF
        for (i in 1 until pixels.size) {
            val c = pixels[i].toInt() and 0xFF
            val key = (prefix shl 8) or c
            if (stamps[key] == generation) {
                prefix = codes[key]
                continue
            }
            writer.write(prefix, codeSize)
            if (next == 4096) {
                // Diccionario lleno: se reinicia.
                writer.write(clearCode, codeSize)
                generation++
                next = eoiCode + 1
                codeSize = minCodeSize + 1
            } else {
                if (next >= (1 shl codeSize)) codeSize++
                codes[key] = next++
                stamps[key] = generation
            }
            prefix = c
        }
        writer.write(prefix, codeSize)
        writer.write(eoiCode, codeSize)
        writer.finish()
        out.write(0) // fin de los datos de la imagen
    }

    /** Empaqueta códigos de longitud variable (bit menos significativo primero) en bloques de 255 bytes. */
    private class BitWriter(private val out: OutputStream) {
        private val block = ByteArray(255)
        private var blockSize = 0
        private var bits = 0
        private var bitCount = 0

        fun write(code: Int, size: Int) {
            bits = bits or (code shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                put(bits and 0xFF)
                bits = bits ushr 8
                bitCount -= 8
            }
        }

        fun finish() {
            if (bitCount > 0) put(bits and 0xFF)
            bits = 0
            bitCount = 0
            flushBlock()
        }

        private fun put(byte: Int) {
            block[blockSize++] = byte.toByte()
            if (blockSize == 255) flushBlock()
        }

        private fun flushBlock() {
            if (blockSize == 0) return
            out.write(blockSize)
            out.write(block, 0, blockSize)
            blockSize = 0
        }
    }
}
