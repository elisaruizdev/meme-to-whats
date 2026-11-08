package com.memetowhats.app.ui.onboarding

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memetowhats.app.media.ScreenCaptures
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class OnboardingStep { WELCOME, PERMISSION, TAKE_SCREENSHOT, DONE }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    /** La captura detectada. Puede ser null si Android avisó de la captura pero no hay permiso para leerla. */
    val capture: Uri? = null,
    val showHelp: Boolean = false,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    private var waiting: Job? = null

    fun goTo(step: OnboardingStep) {
        _state.update { it.copy(step = step) }
        if (step == OnboardingStep.TAKE_SCREENSHOT) startWaiting()
    }

    fun toggleHelp() = _state.update { it.copy(showHelp = !it.showHelp) }

    /**
     * Espera a que aparezca en la galería una captura hecha a partir de ahora.
     * Se combinan dos avisos: el observador de MediaStore y una comprobación cada segundo y medio,
     * porque algunos móviles guardan la captura un instante después de avisar.
     */
    private fun startWaiting() {
        if (waiting?.isActive == true) return
        val since = System.currentTimeMillis() / 1000 - 1
        waiting = viewModelScope.launch {
            launch {
                ScreenCaptures.changes(getApplication()).collect { check(since) }
            }
            while (isActive && _state.value.step == OnboardingStep.TAKE_SCREENSHOT) {
                check(since)
                delay(1_500)
            }
        }
    }

    private suspend fun check(since: Long) {
        if (_state.value.step != OnboardingStep.TAKE_SCREENSHOT) return
        val latest = ScreenCaptures.load(getApplication(), limit = 5)
            .firstOrNull { !it.isVideo && it.dateAdded >= since }
            ?: return
        found(latest.uri)
    }

    /** Android 14+ avisa de la captura aunque no tengamos permiso para leerla. */
    fun onSystemScreenshotDetected() {
        viewModelScope.launch {
            // Damos un momento a que se guarde para poder enseñarla.
            delay(1_500)
            if (_state.value.step == OnboardingStep.TAKE_SCREENSHOT) found(null)
        }
    }

    fun found(uri: Uri?) {
        waiting?.cancel()
        _state.update { it.copy(step = OnboardingStep.DONE, capture = uri, showHelp = false) }
    }
}
