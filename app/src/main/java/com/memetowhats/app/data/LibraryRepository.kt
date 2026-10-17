package com.memetowhats.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LibraryRepository(
    private val context: Context,
    db: AppDatabase,
) {
    private val itemDao = db.itemDao()
    private val categoryDao = db.categoryDao()
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }

    val items = itemDao.observeAll()
    val categories = categoryDao.observeAll()

    fun observeItem(id: Long) = itemDao.observe(id)

    fun fileFor(item: LibraryItem) = File(mediaDir, item.fileName)

    /** Copia los archivos elegidos a la biblioteca. Devuelve cuántos se importaron. */
    suspend fun import(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        uris.count { uri ->
            runCatching { importOne(uri) }
                .onFailure { Log.w(TAG, "No se pudo importar $uri", it) }
                .isSuccess
        }
    }

    private suspend fun importOne(uri: Uri) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri)
        val mime = resolver.getType(uri)
            ?: displayName?.substringAfterLast('.', "")
                ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
            ?: error("Tipo de archivo desconocido")
        val type = when {
            mime == "image/gif" -> MediaType.GIF
            mime.startsWith("image/") -> MediaType.IMAGE
            mime.startsWith("video/") -> MediaType.VIDEO
            else -> error("Tipo no soportado: $mime")
        }
        // La extensión importa: Coil la usa para saber cómo decodificar la miniatura.
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: displayName?.substringAfterLast('.', "")?.ifBlank { null }
            ?: "bin"
        val target = File(mediaDir, "${UUID.randomUUID()}.$extension")

        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: error("No se pudo abrir el archivo")

        try {
            itemDao.insert(
                LibraryItem(
                    fileName = target.name,
                    mimeType = mime,
                    type = type,
                    title = titleFrom(displayName),
                ),
            )
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun titleFrom(displayName: String?): String =
        displayName
            ?.substringBeforeLast('.')
            ?.replace(Regex("[_\\-]+"), " ")
            ?.trim()
            ?.ifBlank { null }
            ?: "Sin título"

    suspend fun updateDetails(id: Long, title: String, audioText: String, tags: String, categoryId: Long?) =
        itemDao.updateDetails(
            id = id,
            title = title.trim().ifBlank { "Sin título" },
            audioText = audioText.trim(),
            tags = normalizeTags(tags),
            categoryId = categoryId,
        )

    suspend fun setFavorite(id: Long, favorite: Boolean) = itemDao.setFavorite(id, favorite)

    suspend fun delete(item: LibraryItem) = withContext(Dispatchers.IO) {
        itemDao.delete(item)
        fileFor(item).delete()
    }

    /** Crea la categoría si no existe (sin distinguir mayúsculas) y devuelve su id. */
    suspend fun addCategory(name: String): Long {
        val clean = name.trim()
        require(clean.isNotEmpty())
        categoryDao.findByName(clean)?.let { return it.id }
        return categoryDao.insert(Category(name = clean))
    }

    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    private fun normalizeTags(raw: String): String =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(", ")

    companion object {
        private const val TAG = "LibraryRepository"
    }
}
