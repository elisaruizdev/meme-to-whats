package com.memetowhats.app.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Guarda lo creado en la galería del móvil, en Imágenes/Meme to Whats. */
object Gallery {
    private const val FOLDER = "Meme to Whats"
    private const val PREFS = "gallery"
    private const val SAVED_KEYS = "saved_keys"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSaved(context: Context, key: String) = key in prefs(context).getStringSet(SAVED_KEYS, emptySet()).orEmpty()

    /**
     * Guarda en la galería solo si no se guardó antes ([key] = nombre en la biblioteca).
     * Así, pulsar «Guardar» y además enviarlo no crea copias repetidas.
     * Devuelve true si lo ha guardado ahora, false si ya estaba.
     */
    @Synchronized
    fun saveOnce(context: Context, file: File, mimeType: String, key: String): Boolean {
        if (isSaved(context, key)) return false
        saveBlocking(context, file, mimeType)
        val keys = prefs(context).getStringSet(SAVED_KEYS, emptySet()).orEmpty() + key
        prefs(context).edit().putStringSet(SAVED_KEYS, keys).apply()
        return true
    }

    suspend fun save(context: Context, file: File, mimeType: String) = withContext(Dispatchers.IO) {
        saveBlocking(context, file, mimeType)
    }

    private fun saveBlocking(context: Context, file: File, mimeType: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("No se pudo crear el archivo en la galería")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: error("No se pudo escribir en la galería")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            // Android 8 y 9: se escribe directamente (necesita WRITE_EXTERNAL_STORAGE).
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
            dir.mkdirs()
            val target = File(dir, file.name)
            file.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mimeType), null)
        }
    }
}
