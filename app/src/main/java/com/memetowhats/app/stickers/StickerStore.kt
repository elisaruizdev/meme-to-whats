package com.memetowhats.app.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.memetowhats.app.media.Bitmaps
import com.memetowhats.app.media.CropRect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class StickerPack(
    val id: String,
    val name: String,
    val stickers: List<File>,
    val version: Int,
) {
    val trayFile: String get() = StickerStore.TRAY_FILE
    /** Stickers creados por la persona (sin contar los de inicio). */
    val own: List<File> get() = stickers.filter { it.name.startsWith(StickerStore.OWN_PREFIX) }
    val readyForWhatsApp get() = stickers.size >= StickerStore.MIN_STICKERS
    val isFull get() = own.size >= StickerStore.MAX_STICKERS
}

/**
 * Packs de stickers guardados en `files/stickers/<id>/`. WhatsApp los lee a través de
 * [StickerContentProvider].
 *
 * Reglas de WhatsApp: de 3 a 30 stickers por pack, cada uno WebP de 512×512 y ≤100 KB,
 * y un icono de bandeja PNG de 96×96 y ≤50 KB.
 *
 * Para que el pack se pueda añadir desde el primer sticker, mientras haya menos de 3 propios
 * se completa con stickers "de inicio" generados por la app, que desaparecen solos después.
 */
object StickerStore {
    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30
    const val TRAY_FILE = "tray.png"
    const val PUBLISHER = "Meme to Whats"
    const val EMOJI = "😂"
    const val OWN_PREFIX = "s_"
    private const val STARTER_PREFIX = "starter_"
    private const val VERSION_FILE = "version"

    private val starters = listOf(
        "JAJAJA" to 0xFF25D366.toInt(),
        "OK 👌" to 0xFF2196F3.toInt(),
        "¿QUÉ?" to 0xFFFF9800.toInt(),
    )

    private val _packs = MutableStateFlow<List<StickerPack>>(emptyList())
    val packs: StateFlow<List<StickerPack>> = _packs

    private fun root(context: Context) = File(context.filesDir, "stickers").apply { mkdirs() }

    fun dir(context: Context, packId: String) = File(root(context), packId)

    @Synchronized
    fun load(context: Context): List<StickerPack> {
        val packs = root(context).listFiles { f -> f.isDirectory }.orEmpty()
            .sortedBy { it.name.removePrefix("pack").toIntOrNull() ?: 0 }
            .mapIndexed { i, dir ->
                StickerPack(
                    id = dir.name,
                    name = if (i == 0) PUBLISHER else "$PUBLISHER ${i + 1}",
                    stickers = orderedStickers(dir),
                    version = File(dir, VERSION_FILE).takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 1,
                )
            }
            .filter { it.own.isNotEmpty() }
        _packs.value = packs
        return packs
    }

    /** Los propios del más nuevo al más antiguo (así el último sale el primero en WhatsApp) y luego los de inicio. */
    private fun orderedStickers(dir: File): List<File> {
        val all = dir.listFiles { f -> f.extension == "webp" }.orEmpty()
        val own = all.filter { it.name.startsWith(OWN_PREFIX) }.sortedByDescending { it.lastModified() }
        return own + all.filterNot { it.name.startsWith(OWN_PREFIX) }.sortedBy { it.name }
    }

    /**
     * Añade el sticker [webp] al último pack con hueco (o a uno nuevo) y devuelve el pack.
     * [key] identifica el sticker (el nombre en la biblioteca): si ya está en un pack, no se duplica.
     */
    @Synchronized
    fun add(context: Context, webp: File, key: String): StickerPack {
        val fileName = "$OWN_PREFIX$key.webp"
        val packs = load(context)
        packs.firstOrNull { pack -> pack.stickers.any { it.name == fileName } }?.let { return it }

        val target = packs.lastOrNull()?.takeUnless { it.isFull }?.id
            ?: "pack${(packs.maxOfOrNull { it.id.removePrefix("pack").toIntOrNull() ?: 0 } ?: 0) + 1}"
        val dir = dir(context, target).apply { mkdirs() }
        webp.copyTo(File(dir, fileName), overwrite = true).setLastModified(System.currentTimeMillis())
        refresh(dir)
        return load(context).first { it.id == target }
    }

    @Synchronized
    fun remove(context: Context, sticker: File) {
        val dir = sticker.parentFile ?: return
        sticker.delete()
        refresh(dir)
        load(context)
    }

    fun removeByKey(context: Context, key: String) {
        val name = "$OWN_PREFIX$key.webp"
        load(context).flatMap { it.stickers }.firstOrNull { it.name == name }?.let { remove(context, it) }
    }

    /** Tras cualquier cambio: completa con stickers de inicio y comprueba el icono. */
    private fun refresh(dir: File) {
        val own = dir.listFiles { f -> f.name.startsWith(OWN_PREFIX) }.orEmpty().sortedByDescending { it.lastModified() }
        if (own.isEmpty()) {
            dir.deleteRecursively()
            return
        }
        val needed = (MIN_STICKERS - own.size).coerceAtLeast(0)
        starters.forEachIndexed { i, (text, color) ->
            val file = File(dir, "$STARTER_PREFIX$i.webp")
            if (i < needed) {
                if (!file.exists()) file.writeBytes(Bitmaps.toWebp(starterSticker(text, color), 100 * 1024))
            } else {
                file.delete()
            }
        }
        // Icono del pack: el sticker más antiguo, para que no cambie con cada sticker nuevo.
        val tray = File(dir, TRAY_FILE)
        if (!tray.exists() || own.none { it.name == trayFrom(dir) }) writeTray(dir, own.last())
        // La versión del pack (image_data_version) ya no se sube con cada cambio: si sube, WhatsApp
        // pide "actualizar el pack". Como el pack se sirve sin caché, WhatsApp ve los stickers nuevos igual.
        if (!File(dir, VERSION_FILE).exists()) File(dir, VERSION_FILE).writeText("1")
    }

    private const val TRAY_SOURCE_FILE = "tray_source"

    /** Nombre del sticker del que se sacó el icono. */
    private fun trayFrom(dir: File) = File(dir, TRAY_SOURCE_FILE).takeIf { it.exists() }?.readText()?.trim()

    private fun writeTray(dir: File, sticker: File) {
        val source = BitmapFactory.decodeFile(sticker.path) ?: return
        val tray: Bitmap = Bitmaps.fitInto(source, CropRect.Full, 96, 96)
        File(dir, TRAY_FILE).writeBytes(Bitmaps.toPng(tray))
        File(dir, TRAY_SOURCE_FILE).writeText(sticker.name)
    }

    /** Bocadillo de color con borde blanco y texto, al estilo sticker. */
    private fun starterSticker(text: String, color: Int): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(40f, 110f, size - 40f, size - 110f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 18f
        }
        canvas.save()
        canvas.rotate(-6f, size / 2f, size / 2f)
        canvas.drawRoundRect(rect, 70f, 70f, fill)
        canvas.drawRoundRect(rect, 70f, 70f, border)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = 120f
        }
        while (paint.measureText(text) > rect.width() - 60 && paint.textSize > 40f) paint.textSize -= 6f
        val y = rect.centerY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, rect.centerX(), y, paint)
        canvas.restore()
        return bitmap
    }
}
