package com.memetowhats.app.media

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** Una captura de pantalla (imagen) o una grabación de pantalla (vídeo) de la galería. */
data class ScreenCapture(
    val uri: Uri,
    val isVideo: Boolean,
    val dateAdded: Long,
    /** Duración en milisegundos (solo vídeos). */
    val durationMs: Long = 0,
)

/**
 * Lee de MediaStore las capturas y grabaciones de pantalla. No hay una etiqueta oficial
 * para "esto es una captura", así que se filtra por el **nombre de la carpeta** (no por la ruta
 * entera, para no colar fotos del álbum): cada fabricante la llama distinto
 * (Screenshots, Capturas de pantalla, Screen recordings, ScreenRecorder…).
 */
object ScreenCaptures {
    // LIKE de SQLite no distingue mayúsculas en ASCII; "_" vale por cualquier carácter
    // (así "screen_shot" cubre "Screen shot" y "Screen_shot").
    private val imageFolders = listOf("screenshot%", "screen_shot%", "captura%")
    private val videoFolders = listOf("screen_record%", "screenrecord%", "screencast%", "grabaci%pantalla%", "grabaciones%")

    suspend fun load(context: Context, limit: Int = 120): List<ScreenCapture> = withContext(Dispatchers.IO) {
        val images = query(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageFolders, isVideo = false)
        val videos = query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoFolders, isVideo = true)
        (images + videos).sortedByDescending { it.dateAdded }.take(limit)
    }

    private fun query(context: Context, collection: Uri, folders: List<String>, isVideo: Boolean): List<ScreenCapture> {
        // Nombre de la carpeta (p. ej. "Screenshots"). La constante es de API 29, pero la columna existe desde siempre.
        val folderColumn = "bucket_display_name"
        val selection = folders.joinToString(" OR ", prefix = "(", postfix = ")") { "$folderColumn LIKE ?" }
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DATE_ADDED)
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()

        val result = mutableListOf<ScreenCapture>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                folders.toTypedArray(),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    result += ScreenCapture(
                        uri = ContentUris.withAppendedId(collection, id),
                        isVideo = isVideo,
                        dateAdded = cursor.getLong(1),
                        durationMs = if (isVideo) cursor.getLong(2) else 0,
                    )
                }
            }
        }
        return result
    }

    /** Emite cada vez que cambia algo en la galería (por ejemplo, al hacer una captura nueva). */
    fun changes(context: Context): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
}
