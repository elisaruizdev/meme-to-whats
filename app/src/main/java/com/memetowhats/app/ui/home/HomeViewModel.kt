package com.memetowhats.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memetowhats.app.MemeApp
import com.memetowhats.app.data.LibraryItem
import com.memetowhats.app.data.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File

/** Qué creaciones se ven: todas, solo stickers o solo GIF. */
enum class CreationFilter { ALL, STICKERS, GIFS }

data class CreationEntry(val item: LibraryItem, val file: File) {
    val isSticker get() = item.type == MediaType.STICKER
}

data class HomeState(
    /** Stickers y GIF creados con la app. */
    val creations: List<CreationEntry> = emptyList(),
    val filter: CreationFilter = CreationFilter.ALL,
) {
    val visibleCreations: List<CreationEntry>
        get() = when (filter) {
            CreationFilter.ALL -> creations
            CreationFilter.STICKERS -> creations.filter { it.isSticker }
            CreationFilter.GIFS -> creations.filterNot { it.isSticker }
        }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MemeApp).repository
    private val filter = MutableStateFlow(CreationFilter.ALL)

    val state: StateFlow<HomeState> = combine(repository.items, filter) { items, f ->
        HomeState(
            creations = items
                .filter { it.type == MediaType.STICKER || it.type == MediaType.GIF }
                .map { CreationEntry(it, repository.fileFor(it)) },
            filter = f,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setFilter(value: CreationFilter) = filter.update { value }
}
