package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.data.ai.SecretCipher
import com.focusdivergent.app.domain.ai.LlmConfig
import com.focusdivergent.app.domain.ai.LlmSettings
import com.focusdivergent.app.domain.ai.StepSuggester
import com.focusdivergent.app.domain.ai.SuggestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ajustes de la IA en memoria. */
class FakeLlmSettings(initial: LlmConfig = LlmConfig(), private var key: String? = null) : LlmSettings {
    private val state = MutableStateFlow(initial)
    override val config: StateFlow<LlmConfig> = state

    override fun saveEndpoint(baseUrl: String, model: String) {
        state.value = state.value.copy(baseUrl = baseUrl, model = model)
    }

    override fun saveKey(key: String) {
        this.key = key
        state.value = state.value.copy(hasKey = true)
    }

    override fun clearKey() {
        key = null
        state.value = state.value.copy(hasKey = false, consentedHost = null)
    }

    override fun consent(host: String) {
        state.value = state.value.copy(consentedHost = host)
    }

    override fun apiKey(): String? = key
}

/** Una IA lista para usar en los tests: con servicio, modelo y clave, y con o sin el permiso ya dado. */
fun configuredLlm(consented: Boolean = true) = FakeLlmSettings(
    LlmConfig(
        baseUrl = "https://api.example.com/v1",
        model = "modelo-x",
        hasKey = true,
        consentedHost = if (consented) "api.example.com" else null,
    ),
    key = "clave-secreta",
)

/** Un servicio de sugerencias de mentira: contesta lo que se le diga y apunta lo que recibe. */
class FakeStepSuggester(var result: SuggestResult = SuggestResult.Steps(listOf("Uno", "Dos"))) : StepSuggester {
    data class Call(val config: LlmConfig, val apiKey: String, val taskTitle: String)

    val calls = mutableListOf<Call>()

    override suspend fun suggest(config: LlmConfig, apiKey: String, taskTitle: String): SuggestResult {
        calls += Call(config, apiKey, taskTitle)
        return result
    }
}

/** «Cifrado» reversible y reconocible, para comprobar que lo guardado no es la clave en claro. */
class FakeSecretCipher : SecretCipher {
    override fun encrypt(plain: String): String = "enc:" + plain.reversed()
    override fun decrypt(blob: String): String? = blob.removePrefix("enc:").takeIf { blob.startsWith("enc:") }?.reversed()
}
