package com.focusdivergent.app.presentation.ai

import androidx.lifecycle.ViewModel
import com.focusdivergent.app.domain.ai.LlmConfig
import com.focusdivergent.app.domain.ai.LlmEndpoint
import com.focusdivergent.app.domain.ai.LlmSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

enum class AiSaveResult { SAVED, INVALID_URL }

/** Ajustes de «Pasos con tu propia IA». La clave nunca vuelve a la pantalla: solo se sabe si hay una guardada. */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settings: LlmSettings,
) : ViewModel() {

    val config: StateFlow<LlmConfig> = settings.config

    /** Guarda dirección y modelo, y la clave si se escribió una nueva (en blanco = se deja la que hay). */
    fun save(baseUrl: String, model: String, newKey: String): AiSaveResult {
        if (LlmEndpoint.host(baseUrl) == null) return AiSaveResult.INVALID_URL
        settings.saveEndpoint(baseUrl, model)
        if (newKey.isNotBlank()) settings.saveKey(newKey)
        return AiSaveResult.SAVED
    }

    fun removeKey() = settings.clearKey()
}
