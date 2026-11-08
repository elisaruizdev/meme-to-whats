package com.memetowhats.app.stickers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.memetowhats.app.BuildConfig
import java.io.File

/**
 * Proveedor que WhatsApp consulta para leer nuestros packs de stickers.
 * Sigue el contrato del ejemplo oficial de WhatsApp (github.com/WhatsApp/stickers):
 *
 * - `metadata`                 → todos los packs
 * - `metadata/<pack>`          → un pack
 * - `stickers/<pack>`          → stickers del pack
 * - `stickers_asset/<pack>/<archivo>` → el archivo (WebP o el icono PNG)
 *
 * Solo WhatsApp puede leerlo: el manifest exige el permiso `com.whatsapp.sticker.READ`.
 */
class StickerContentProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, METADATA, ALL_PACKS)
        addURI(AUTHORITY, "$METADATA/*", ONE_PACK)
        addURI(AUTHORITY, "$STICKERS/*", PACK_STICKERS)
        addURI(AUTHORITY, "$STICKERS_ASSET/*/*", ASSET)
    }

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val context = requireNotNull(context)
        val packs = StickerStore.load(context).filter { it.readyForWhatsApp }
        return when (matcher.match(uri)) {
            ALL_PACKS -> packsCursor(packs, uri)
            ONE_PACK -> packsCursor(packs.filter { it.id == uri.lastPathSegment }, uri)
            PACK_STICKERS -> {
                val cursor = MatrixCursor(arrayOf(FILE_NAME, EMOJI, ACCESSIBILITY_TEXT))
                packs.firstOrNull { it.id == uri.lastPathSegment }?.stickers?.forEach {
                    cursor.addRow(arrayOf(it.name, StickerStore.EMOJI, "Sticker de meme"))
                }
                cursor.apply { setNotificationUri(context.contentResolver, uri) }
            }
            else -> throw IllegalArgumentException("URI desconocida: $uri")
        }
    }

    private fun packsCursor(packs: List<StickerPack>, uri: Uri): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                PACK_ID, PACK_NAME, PACK_PUBLISHER, PACK_ICON, ANDROID_LINK, IOS_LINK,
                PUBLISHER_EMAIL, PUBLISHER_WEBSITE, PRIVACY_POLICY, LICENSE,
                IMAGE_DATA_VERSION, AVOID_CACHE, ANIMATED_PACK,
            ),
        )
        for (pack in packs) {
            cursor.addRow(
                arrayOf<Any>(
                    pack.id, pack.name, StickerStore.PUBLISHER, pack.trayFile, "", "",
                    "", "", "", "",
                    // AVOID_CACHE = 1: el pack cambia cada vez que creas un sticker, así que WhatsApp
                    // los lee de aquí en vez de guardarse una copia (y no pide "actualizar el pack").
                    pack.version.toString(), 1, 0,
                ),
            )
        }
        cursor.setNotificationUri(requireNotNull(context).contentResolver, uri)
        return cursor
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != ASSET) return null
        val segments = uri.pathSegments
        val packId = segments[segments.size - 2]
        val fileName = segments.last()
        // Nada de rutas raras: solo nombres de archivo dentro de la carpeta del pack.
        require(!packId.contains("..") && !fileName.contains("..") && !fileName.contains('/'))
        val file = File(StickerStore.dir(requireNotNull(context), packId), fileName)
        if (!file.exists()) return null
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        ALL_PACKS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$METADATA"
        ONE_PACK -> "vnd.android.cursor.item/vnd.$AUTHORITY.$METADATA"
        PACK_STICKERS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$STICKERS"
        ASSET -> if (uri.lastPathSegment == StickerStore.TRAY_FILE) "image/png" else "image/webp"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
        throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) =
        throw UnsupportedOperationException()

    companion object {
        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.stickercontentprovider"

        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"

        private const val ALL_PACKS = 1
        private const val ONE_PACK = 2
        private const val PACK_STICKERS = 3
        private const val ASSET = 4

        private const val PACK_ID = "sticker_pack_identifier"
        private const val PACK_NAME = "sticker_pack_name"
        private const val PACK_PUBLISHER = "sticker_pack_publisher"
        private const val PACK_ICON = "sticker_pack_icon"
        private const val ANDROID_LINK = "android_play_store_link"
        private const val IOS_LINK = "ios_app_download_link"
        private const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        private const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        private const val PRIVACY_POLICY = "sticker_pack_privacy_policy_website"
        private const val LICENSE = "sticker_pack_license_agreement_website"
        private const val IMAGE_DATA_VERSION = "image_data_version"
        private const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
        private const val ANIMATED_PACK = "animated_sticker_pack"

        private const val FILE_NAME = "sticker_file_name"
        private const val EMOJI = "sticker_emoji"
        private const val ACCESSIBILITY_TEXT = "sticker_accessibility_text"
    }
}
