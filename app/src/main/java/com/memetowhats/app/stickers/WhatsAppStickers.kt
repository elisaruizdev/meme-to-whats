package com.memetowhats.app.stickers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object WhatsAppStickers {
    private const val ACTION_ENABLE = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    private val packages = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun installedPackage(context: Context): String? = packages.firstOrNull { isInstalled(context, it) }

    /** Intent que abre WhatsApp con el diálogo "Añadir pack de stickers", o null si no está instalado. */
    fun addPackIntent(context: Context, pack: StickerPack): Intent? {
        val pkg = installedPackage(context) ?: return null
        return Intent(ACTION_ENABLE)
            .setPackage(pkg)
            .putExtra("sticker_pack_id", pack.id)
            .putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
            .putExtra("sticker_pack_name", pack.name)
    }

    /** Intent para abrir WhatsApp tal cual (para ir a un chat y usar el sticker). */
    fun openIntent(context: Context): Intent? =
        installedPackage(context)?.let { context.packageManager.getLaunchIntentForPackage(it) }

    /**
     * ¿El pack ya está añadido en WhatsApp? WhatsApp expone un proveedor para consultarlo
     * (mismo método que el ejemplo oficial). Si falla la consulta, asumimos que no.
     */
    fun isPackAdded(context: Context, packId: String): Boolean = packages.any { pkg ->
        val uri = Uri.Builder()
            .scheme("content")
            .authority("$pkg.provider.sticker_whitelist_check")
            .appendPath("is_whitelisted")
            .appendQueryParameter("authority", StickerContentProvider.AUTHORITY)
            .appendQueryParameter("identifier", packId)
            .build()
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow("result")) == 1
            } ?: false
        }.getOrDefault(false)
    }

    private fun isInstalled(context: Context, pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
