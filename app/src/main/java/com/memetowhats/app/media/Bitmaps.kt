package com.memetowhats.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Zona recortada en coordenadas relativas (0..1) de la imagen original. */
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    fun toPixels(width: Int, height: Int): Rect {
        val l = (left * width).roundToInt().coerceIn(0, width - 1)
        val t = (top * height).roundToInt().coerceIn(0, height - 1)
        val r = (right * width).roundToInt().coerceIn(l + 1, width)
        val b = (bottom * height).roundToInt().coerceIn(t + 1, height)
        return Rect(l, t, r, b)
    }

    companion object {
        val Full = CropRect()
    }
}

object Bitmaps {
    /** Decodifica una imagen reduciéndola para que su lado mayor no pase de [maxSide]. */
    fun decode(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("No se pudo leer la imagen")
    }

    /**
     * Recorta [source] y la encaja centrada en un lienzo de [width]×[height] con [margin] píxeles
     * de borde. Lo que sobra queda transparente (o del color [background]).
     */
    fun fitInto(
        source: Bitmap,
        crop: CropRect,
        width: Int,
        height: Int,
        margin: Int = 0,
        background: Int = Color.TRANSPARENT,
    ): Bitmap {
        val src = crop.toPixels(source.width, source.height)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(background)
        val availW = (width - 2 * margin).toFloat()
        val availH = (height - 2 * margin).toFloat()
        val scale = min(availW / src.width(), availH / src.height())
        val w = src.width() * scale
        val h = src.height() * scale
        val dst = RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        canvas.drawBitmap(source, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Texto del meme (mayúsculas, grande), abajo o arriba. */
    fun drawCaption(bitmap: Bitmap, text: String, atTop: Boolean = false) =
        drawText(bitmap, text.uppercase(), atTop = atTop, sizeDivisor = 9f)

    /** Subtítulo: más pequeño, sin pasar a mayúsculas, siempre abajo. */
    fun drawSubtitle(bitmap: Bitmap, text: String) =
        drawText(bitmap, text, atTop = false, sizeDivisor = 13f)

    /** Escribe [text] en blanco con borde negro, al estilo meme clásico. */
    private fun drawText(bitmap: Bitmap, text: String, atTop: Boolean, sizeDivisor: Float) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val canvas = Canvas(bitmap)
        val maxWidth = (bitmap.width * 0.92f).roundToInt()
        var size = bitmap.width / sizeDivisor
        val upper = clean
        lateinit var layout: StaticLayout
        lateinit var paint: TextPaint
        // Bajamos el tamaño hasta que el texto quepa en 3 líneas como mucho.
        while (true) {
            paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            layout = StaticLayout.Builder.obtain(upper, 0, upper.length, paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            if (layout.lineCount <= 3 || size < bitmap.width / 22f) break
            size *= 0.85f
        }
        val x = (bitmap.width - maxWidth) / 2f
        val margin = bitmap.height * 0.04f
        val y = if (atTop) margin else bitmap.height - layout.height - margin
        canvas.save()
        canvas.translate(x, y)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size / 6f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.BLACK
        layout.draw(canvas)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        layout.draw(canvas)
        canvas.restore()
    }

    /** Comprime a WebP bajando la calidad hasta que ocupe como mucho [maxBytes]. */
    fun toWebp(bitmap: Bitmap, maxBytes: Int): ByteArray {
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        var quality = 90
        while (true) {
            val bytes = ByteArrayOutputStream().also { bitmap.compress(format, quality, it) }.toByteArray()
            if (bytes.size <= maxBytes || quality <= 10) return bytes
            quality -= 10
        }
    }

    fun toPng(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
