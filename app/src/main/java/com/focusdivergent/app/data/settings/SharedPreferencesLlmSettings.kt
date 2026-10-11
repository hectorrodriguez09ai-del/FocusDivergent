package com.focusdivergent.app.data.settings

import android.content.Context
import androidx.core.content.edit
import com.focusdivergent.app.data.ai.SecretCipher
import com.focusdivergent.app.domain.ai.LlmConfig
import com.focusdivergent.app.domain.ai.LlmSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Configuración del LLM propio. La clave se guarda cifrada con [SecretCipher]; nunca en claro. */
@Singleton
class SharedPreferencesLlmSettings @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: SecretCipher,
) : LlmSettings {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override val config: StateFlow<LlmConfig> = state.asStateFlow()

    override fun saveEndpoint(baseUrl: String, model: String) {
        val updated = state.value.copy(baseUrl = baseUrl.trim(), model = model.trim())
        // Si el servidor cambia, el permiso dado al anterior no vale para el nuevo.
        val keepConsent = updated.host != null && updated.host == state.value.consentedHost
        prefs.edit {
            putString(KEY_URL, updated.baseUrl)
            putString(KEY_MODEL, updated.model)
            if (!keepConsent) remove(KEY_CONSENT)
        }
        state.value = if (keepConsent) updated else updated.copy(consentedHost = null)
    }

    override fun saveKey(key: String) {
        val clean = key.trim()
        if (clean.isEmpty()) return clearKey()
        val blob = cipher.encrypt(clean) ?: return
        prefs.edit { putString(KEY_SECRET, blob) }
        state.value = state.value.copy(hasKey = true)
    }

    override fun clearKey() {
        prefs.edit {
            remove(KEY_SECRET)
            remove(KEY_CONSENT)
        }
        state.value = state.value.copy(hasKey = false, consentedHost = null)
    }

    override fun consent(host: String) {
        prefs.edit { putString(KEY_CONSENT, host) }
        state.value = state.value.copy(consentedHost = host)
    }

    override fun apiKey(): String? = prefs.getString(KEY_SECRET, null)?.let(cipher::decrypt)

    private fun read() = LlmConfig(
        baseUrl = prefs.getString(KEY_URL, null) ?: LlmConfig.DEFAULT_BASE_URL,
        model = prefs.getString(KEY_MODEL, null).orEmpty(),
        hasKey = prefs.getString(KEY_SECRET, null) != null,
        consentedHost = prefs.getString(KEY_CONSENT, null),
    )

    private companion object {
        const val FILE = "llm"
        const val KEY_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_SECRET = "secret"
        const val KEY_CONSENT = "consented_host"
    }
}
