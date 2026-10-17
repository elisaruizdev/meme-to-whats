package com.memetowhats.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MediaType { IMAGE, GIF, VIDEO }

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/**
 * Un meme, GIF o vídeo de la biblioteca. El archivo vive copiado en el almacenamiento
 * privado de la app ([fileName] dentro de `files/media/`), así sigue disponible aunque
 * se borre el original de la galería o de WhatsApp.
 */
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId")],
)
data class LibraryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val mimeType: String,
    val type: MediaType,
    val title: String,
    /** Lo que se oye en el vídeo: frase, canción o sonido. Se usa en la búsqueda. */
    val audioText: String = "",
    /** Etiquetas separadas por comas. */
    val tags: String = "",
    val categoryId: Long? = null,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
