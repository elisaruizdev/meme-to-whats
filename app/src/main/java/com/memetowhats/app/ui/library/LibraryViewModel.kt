package com.memetowhats.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.memetowhats.app.MemeApp
import com.memetowhats.app.data.Category
import com.memetowhats.app.data.LibraryItem
import com.memetowhats.app.data.LibraryRepository
import com.memetowhats.app.data.MediaType
import com.memetowhats.app.util.matchesQuery
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class TypeFilter { ALL, IMAGES, VIDEOS }

data class LibraryEntry(
    val item: LibraryItem,
    val categoryName: String?,
    val file: File,
)

data class Filters(
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val type: TypeFilter = TypeFilter.ALL,
    val categoryId: Long? = null,
)

data class LibraryState(
    val filters: Filters = Filters(),
    val entries: List<LibraryEntry> = emptyList(),
    val categories: List<Category> = emptyList(),
    val totalCount: Int = 0,
    val loading: Boolean = true,
)

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {
    private val filters = MutableStateFlow(Filters())
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    val state: StateFlow<LibraryState> =
        combine(repository.items, repository.categories, filters) { items, categories, f ->
            val categoryNames = categories.associate { it.id to it.name }
            // Si la categoría filtrada se borró, dejamos de filtrar por ella.
            val activeFilters = if (f.categoryId != null && f.categoryId !in categoryNames) f.copy(categoryId = null) else f
            val entries = items
                .filter { activeFilters.accepts(it, categoryNames[it.categoryId]) }
                .map { LibraryEntry(it, categoryNames[it.categoryId], repository.fileFor(it)) }
            LibraryState(activeFilters, entries, categories, items.size, loading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setQuery(query: String) = filters.update { it.copy(query = query) }
    fun toggleFavoritesOnly() = filters.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    fun setType(type: TypeFilter) = filters.update { it.copy(type = if (it.type == type) TypeFilter.ALL else type) }
    fun selectCategory(id: Long) = filters.update { it.copy(categoryId = if (it.categoryId == id) null else id) }

    fun import(uris: List<Uri>) = viewModelScope.launch {
        val imported = repository.import(uris)
        val failed = uris.size - imported
        messageChannel.send(
            buildString {
                append(if (imported == 1) "1 archivo añadido" else "$imported archivos añadidos")
                if (failed > 0) append(" ($failed no se pudieron importar)")
            },
        )
    }

    fun addCategory(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repository.addCategory(name)
    }

    fun deleteCategory(category: Category) = viewModelScope.launch { repository.deleteCategory(category) }

    private fun Filters.accepts(item: LibraryItem, categoryName: String?): Boolean {
        if (favoritesOnly && !item.favorite) return false
        if (categoryId != null && item.categoryId != categoryId) return false
        when (type) {
            TypeFilter.ALL -> Unit
            TypeFilter.IMAGES -> if (item.type == MediaType.VIDEO) return false
            TypeFilter.VIDEOS -> if (item.type != MediaType.VIDEO) return false
        }
        return matchesQuery(query, item.title, item.audioText, item.tags, categoryName)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LibraryViewModel((this[APPLICATION_KEY] as MemeApp).repository) }
        }
    }
}
