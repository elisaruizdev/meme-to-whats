package com.memetowhats.app.ui.captures

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memetowhats.app.media.ScreenCapture
import com.memetowhats.app.media.ScreenCaptures
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** null = todavía cargando. */
class CapturesViewModel(application: Application) : AndroidViewModel(application) {
    private val _captures = MutableStateFlow<List<ScreenCapture>?>(null)
    val captures: StateFlow<List<ScreenCapture>?> = _captures

    private var watching: Job? = null

    fun refresh() {
        viewModelScope.launch { _captures.value = ScreenCaptures.load(getApplication()) }
    }

    /** Recarga sola cuando aparece una captura nueva mientras la pantalla está abierta. */
    @OptIn(FlowPreview::class)
    fun startWatching() {
        if (watching != null) return
        watching = viewModelScope.launch {
            ScreenCaptures.changes(getApplication()).debounce(500).collect { refresh() }
        }
    }
}
