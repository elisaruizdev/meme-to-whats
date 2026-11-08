package com.memetowhats.app.media

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlin.math.abs

/**
 * Quitar de la galería la captura original una vez convertida en sticker o GIF.
 *
 * Una app no puede borrar fotos que no ha creado sin que la persona lo confirme:
 * - Android 11+: se pide moverla a la papelera (se puede recuperar unos 30 días).
 * - Android 10: se intenta borrar y Android pide permiso con un aviso.
 * - Android 8–9: se borra directamente (con el permiso de almacenamiento).
 */
object Originals {
    /** Solo se pueden quitar las fotos de la galería (MediaStore), no las del selector de fotos ni de otras apps. */
    fun isDeletable(uri: Uri?): Boolean =
        uri != null && uri.authority == MediaStore.AUTHORITY && "picker" !in uri.pathSegments

    /** Android 11+: aviso del sistema para mover a la papelera. */
    fun trashRequest(context: Context, uri: Uri): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30) {
            MediaStore.createTrashRequest(context.contentResolver, listOf(uri), true).intentSender
        } else {
            null
        }

    /**
     * Android 10 o anterior: intenta borrar. Devuelve null si se ha borrado, o el aviso que hay que
     * enseñar para pedir permiso (Android 10).
     */
    fun deleteLegacy(context: Context, uri: Uri): IntentSender? = try {
        context.contentResolver.delete(uri, null, null)
        null
    } catch (e: SecurityException) {
        if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) e.userAction.actionIntent.intentSender else throw e
    }

    /**
     * Al copiar una captura, el portapapeles no siempre apunta a la foto de la galería. En ese caso
     * buscamos la captura guardada a la misma hora (±2 minutos).
     */
    suspend fun matchScreenshot(context: Context, timestampMs: Long): Uri? {
        if (timestampMs <= 0) return null
        return ScreenCaptures.load(context, limit = 10)
            .filterNot { it.isVideo }
            .minByOrNull { abs(it.dateAdded * 1000 - timestampMs) }
            ?.takeIf { abs(it.dateAdded * 1000 - timestampMs) < 120_000 }
            ?.uri
    }
}
