package com.focusdivergent.app.presentation.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.usecase.CaptureIdeaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** La captura terminó. [saved] es `true` si había texto y quedó guardado en la bandeja. */
data class CaptureFinished(val saved: Boolean)

/**
 * Pantalla de captura rápida (RF-5.1). Una idea nunca se pierde: se guarda al pulsar «Hecho», «Guardar», al tocar
 * fuera, al volver atrás y al dejar la pantalla de cualquier otra forma (ver `CaptureActivity.onStop`).
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val capture: CaptureIdeaUseCase,
) : ViewModel() {

    /** El texto sobrevive a un giro de pantalla y a que el sistema recree el proceso. */
    val text: StateFlow<String> = savedState.getStateFlow(KEY_TEXT, "")

    private val _finished = Channel<CaptureFinished>(Channel.BUFFERED)
    val finished = _finished.receiveAsFlow()

    // Varias salidas pueden coincidir (Hecho y justo después onStop): la idea se guarda una sola vez.
    private var submitted = false

    fun setText(value: String) {
        savedState[KEY_TEXT] = value
    }

    fun submit() {
        if (submitted) return
        submitted = true
        val value = text.value
        viewModelScope.launch {
            // La pantalla solo se cierra cuando la idea ya está en la base de datos.
            _finished.send(CaptureFinished(saved = capture(value)))
        }
    }

    private companion object {
        const val KEY_TEXT = "text"
    }
}
