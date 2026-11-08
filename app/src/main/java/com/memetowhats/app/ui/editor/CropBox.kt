package com.memetowhats.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.memetowhats.app.media.CropRect
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE, NONE }

private const val MIN_SIZE = 0.08f

/** Dónde se dibuja la imagen dentro del lienzo (ajustada y centrada). */
private fun imageRect(canvas: Size, image: ImageBitmap): Rect {
    val scale = min(canvas.width / image.width, canvas.height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    val left = (canvas.width - w) / 2
    val top = (canvas.height - h) / 2
    return Rect(left, top, left + w, top + h)
}

private fun CropRect.inside(img: Rect) = Rect(
    img.left + left * img.width,
    img.top + top * img.height,
    img.left + right * img.width,
    img.top + bottom * img.height,
)

/**
 * Muestra [image] con un rectángulo de recorte encima. Se arrastran las esquinas para
 * cambiar el tamaño y el interior para moverlo.
 */
@Composable
fun CropBox(image: ImageBitmap, crop: CropRect, onCropChange: (CropRect) -> Unit, modifier: Modifier = Modifier) {
    val currentCrop by rememberUpdatedState(crop)
    val currentOnChange by rememberUpdatedState(onCropChange)

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(image) {
                val touch = 32.dp.toPx()
                var handle = Handle.NONE
                var working = CropRect.Full
                detectDragGestures(
                    onDragStart = { pos ->
                        working = currentCrop
                        val img = imageRect(Size(size.width.toFloat(), size.height.toFloat()), image)
                        val r = working.inside(img)
                        fun near(p: Offset) = abs(pos.x - p.x) < touch && abs(pos.y - p.y) < touch
                        handle = when {
                            near(r.topLeft) -> Handle.TOP_LEFT
                            near(r.topRight) -> Handle.TOP_RIGHT
                            near(r.bottomLeft) -> Handle.BOTTOM_LEFT
                            near(r.bottomRight) -> Handle.BOTTOM_RIGHT
                            r.contains(pos) -> Handle.MOVE
                            else -> Handle.NONE
                        }
                    },
                    onDrag = { change, drag ->
                        if (handle == Handle.NONE) return@detectDragGestures
                        change.consume()
                        val img = imageRect(Size(size.width.toFloat(), size.height.toFloat()), image)
                        val dx = drag.x / img.width
                        val dy = drag.y / img.height
                        val c = working
                        working = when (handle) {
                            Handle.TOP_LEFT -> c.copy(
                                left = (c.left + dx).coerceIn(0f, c.right - MIN_SIZE),
                                top = (c.top + dy).coerceIn(0f, c.bottom - MIN_SIZE),
                            )
                            Handle.TOP_RIGHT -> c.copy(
                                right = (c.right + dx).coerceIn(c.left + MIN_SIZE, 1f),
                                top = (c.top + dy).coerceIn(0f, c.bottom - MIN_SIZE),
                            )
                            Handle.BOTTOM_LEFT -> c.copy(
                                left = (c.left + dx).coerceIn(0f, c.right - MIN_SIZE),
                                bottom = (c.bottom + dy).coerceIn(c.top + MIN_SIZE, 1f),
                            )
                            Handle.BOTTOM_RIGHT -> c.copy(
                                right = (c.right + dx).coerceIn(c.left + MIN_SIZE, 1f),
                                bottom = (c.bottom + dy).coerceIn(c.top + MIN_SIZE, 1f),
                            )
                            Handle.MOVE -> {
                                val w = c.right - c.left
                                val h = c.bottom - c.top
                                val l = (c.left + dx).coerceIn(0f, 1f - w)
                                val t = (c.top + dy).coerceIn(0f, 1f - h)
                                CropRect(l, t, l + w, t + h)
                            }
                            Handle.NONE -> c
                        }
                        currentOnChange(working)
                    },
                )
            },
    ) {
        val img = imageRect(size, image)
        drawImage(
            image,
            dstOffset = IntOffset(img.left.roundToInt(), img.top.roundToInt()),
            dstSize = IntSize(img.width.roundToInt(), img.height.roundToInt()),
        )
        val r = crop.inside(img)
        val shade = Color.Black.copy(alpha = 0.55f)
        // Oscurecemos lo que queda fuera del recorte.
        drawRect(shade, img.topLeft, Size(img.width, r.top - img.top))
        drawRect(shade, Offset(img.left, r.bottom), Size(img.width, img.bottom - r.bottom))
        drawRect(shade, Offset(img.left, r.top), Size(r.left - img.left, r.height))
        drawRect(shade, Offset(r.right, r.top), Size(img.right - r.right, r.height))

        val stroke = 2.dp.toPx()
        drawRect(Color.White, r.topLeft, r.size, style = Stroke(stroke))
        // Rejilla de tercios para ayudar a encuadrar.
        val thin = Color.White.copy(alpha = 0.4f)
        for (i in 1..2) {
            val x = r.left + r.width * i / 3
            val y = r.top + r.height * i / 3
            drawLine(thin, Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
            drawLine(thin, Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
        }
        val dot = 7.dp.toPx()
        for (corner in listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight)) {
            drawCircle(Color.White, dot, corner)
        }
    }
}
