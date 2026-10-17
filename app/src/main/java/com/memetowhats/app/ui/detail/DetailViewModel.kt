package com.memetowhats.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.memetowhats.app.MemeApp
import com.memetowhats.app.data.Category
import com.memetowhats.app.data.LibraryItem
import com.memetowhats.app.data.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class DetailState(
    val item: LibraryItem? = null,
    val file: File? = null,
    val categories: List<Category> = emptyList(),
)

class DetailViewModel(
    private val repository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id: Long = checkNotNull(savedStateHandle["id"])

    val state: StateFlow<DetailState> =
        combine(repository.observeItem(id), repository.categories) { item, categories ->
            DetailState(item, item?.let(repository::fileFor), categories)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    fun save(title: String, audioText: String, tags: String, categoryId: Long?) = viewModelScope.launch {
        repository.updateDetails(id, title, audioText, tags, categoryId)
    }

    fun toggleFavorite() = viewModelScope.launch {
        state.value.item?.let { repository.setFavorite(id, !it.favorite) }
    }

    fun addCategory(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        if (name.isNotBlank()) onCreated(repository.addCategory(name))
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        state.value.item?.let { repository.delete(it) }
        onDeleted()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DetailViewModel((this[APPLICATION_KEY] as MemeApp).repository, createSavedStateHandle())
            }
        }
    }
}
