package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.ai.LlmSettings
import com.focusdivergent.app.domain.ai.StepSuggester
import com.focusdivergent.app.domain.ai.SuggestResult
import javax.inject.Inject

/**
 * Pide pasos al LLM propio del usuario para el [taskTitle] dado. Solo sale a internet si está todo configurado y el
 * usuario aceptó enviar la consulta a ese servidor; y lo único que se envía es el título.
 */
class SuggestStepsUseCase @Inject constructor(
    private val settings: LlmSettings,
    private val suggester: StepSuggester,
) {

    suspend operator fun invoke(taskTitle: String): SuggestResult {
        val config = settings.config.value
        val title = taskTitle.trim()
        if (title.isEmpty() || !config.isConfigured || !config.isConsented) return SuggestResult.NotReady
        val key = settings.apiKey() ?: return SuggestResult.NotReady
        return suggester.suggest(config, key, title)
    }
}
