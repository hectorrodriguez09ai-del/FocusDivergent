package com.focusdivergent.app.data.ai

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusdivergent.app.data.settings.SharedPreferencesLlmSettings
import com.focusdivergent.app.domain.ai.LlmConfig
import com.focusdivergent.app.domain.ai.LlmEndpoint
import com.focusdivergent.app.domain.ai.StepPrompt
import com.focusdivergent.app.domain.ai.SuggestResult
import com.focusdivergent.app.domain.usecase.FakeSecretCipher
import com.focusdivergent.app.domain.usecase.FakeStepSuggester
import com.focusdivergent.app.domain.usecase.SuggestStepsUseCase
import com.focusdivergent.app.domain.usecase.configuredLlm
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La función opcional de pasos con IA propia (RF-3.1): qué se envía, a quién, y qué pasa cuando algo falla. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StepSuggestionTest {

    private val config = LlmConfig(baseUrl = "https://api.example.com/v1/", model = "modelo-x", hasKey = true)

    private fun reply(content: String) =
        JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString()

    // ---- Dirección

    @Test
    fun `solo vale una direccion https y se saca el servidor`() {
        assertEquals("api.openai.com", LlmEndpoint.host("https://api.openai.com/v1"))
        assertEquals("localhost", LlmEndpoint.host("  HTTPS://Localhost:8443/v1  "))
        assertNull(LlmEndpoint.host("http://api.openai.com/v1"))
        assertNull(LlmEndpoint.host("api.openai.com"))
        assertNull(LlmEndpoint.host("https://"))
        assertNull(LlmEndpoint.host("https://user:pass@evil.com/v1"))
        assertEquals("https://api.example.com/v1/chat/completions", LlmEndpoint.chatCompletionsUrl("https://api.example.com/v1/"))
    }

    @Test
    fun `la IA solo esta lista con direccion, modelo y clave, y el permiso vale para un servidor`() {
        assertFalse(LlmConfig().isConfigured)
        assertFalse(config.copy(model = " ").isConfigured)
        assertFalse(config.copy(hasKey = false).isConfigured)
        assertTrue(config.isConfigured)
        assertFalse(config.isConsented)
        assertTrue(config.copy(consentedHost = "api.example.com").isConsented)
        assertFalse(config.copy(consentedHost = "otro.com").isConsented)
    }

    // ---- Lectura de la respuesta

    @Test
    fun `la respuesta se limpia de numeros, vinetas, enfasis y repetidos`() {
        val answer = """
            Claro, aquí tienes:
            1. **Abrir** el documento
            2) Escribir el título
            - Escribir el título
            • Guardar una copia
            Paso 4: Respirar un momento
            ```
        """.trimIndent()

        assertEquals(
            listOf("Abrir el documento", "Escribir el título", "Guardar una copia", "Respirar un momento"),
            StepPrompt.parse(answer),
        )
    }

    @Test
    fun `nunca salen mas pasos de los que caben en una tarea`() {
        assertEquals(20, StepPrompt.parse((1..50).joinToString("\n") { "Paso largo número $it" }).size)
        assertTrue(StepPrompt.parse("  \n \n").isEmpty())
    }

    // ---- Lo que se envia

    @Test
    fun `se envia solo el titulo, la clave en la cabecera y al servidor configurado`() = runTest {
        var url = ""
        var headers = emptyMap<String, String>()
        var body = ""
        val suggester = ChatCompletionsSuggester { u, h, b ->
            url = u; headers = h; body = b
            HttpReply(200, reply("Uno\nDos"))
        }

        val result = suggester.suggest(config, "clave-secreta", "Preparar el informe")

        assertEquals(SuggestResult.Steps(listOf("Uno", "Dos")), result)
        assertEquals("https://api.example.com/v1/chat/completions", url)
        assertEquals("Bearer clave-secreta", headers["Authorization"])

        val json = JSONObject(body)
        assertEquals("modelo-x", json.getString("model"))
        val messages = json.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals(StepPrompt.SYSTEM, messages.getJSONObject(0).getString("content"))
        assertEquals("Tarea: Preparar el informe", messages.getJSONObject(1).getString("content"))
        // La clave viaja en la cabecera y en ningún otro sitio.
        assertFalse(body.contains("clave-secreta"))
    }

    @Test
    fun `una direccion que no es https no se llama nunca`() = runTest {
        var calls = 0
        val suggester = ChatCompletionsSuggester { _, _, _ -> calls++; HttpReply(200, "") }

        val result = suggester.suggest(config.copy(baseUrl = "http://api.example.com"), "clave", "Algo")

        assertEquals(SuggestResult.NotReady, result)
        assertEquals(0, calls)
    }

    // ---- Cuando algo falla

    @Test
    fun `sin red o con un fallo de conexion es sin conexion, sin detalles tecnicos`() = runTest {
        val suggester = ChatCompletionsSuggester { _, _, _ -> throw IOException("timeout") }

        assertEquals(SuggestResult.NoConnection, suggester.suggest(config, "k", "Algo"))
    }

    @Test
    fun `si el servicio no acepta la peticion se dice, con su codigo`() = runTest {
        listOf(401, 403, 404, 429, 500).forEach { code ->
            val suggester = ChatCompletionsSuggester { _, _, _ -> HttpReply(code, """{"error":"x"}""") }

            assertEquals(SuggestResult.Rejected(code), suggester.suggest(config, "k", "Algo"))
        }
    }

    @Test
    fun `una respuesta que no es lo esperado no se pasa por buena`() = runTest {
        listOf("no es json", "{}", """{"choices":[]}""", reply("   \n  ")).forEach { body ->
            val suggester = ChatCompletionsSuggester { _, _, _ -> HttpReply(200, body) }

            assertEquals(body, SuggestResult.Unreadable, suggester.suggest(config, "k", "Algo"))
        }
    }

    // ---- Caso de uso

    @Test
    fun `sin configurar, sin permiso o sin titulo no se llama al servicio`() = runTest {
        val suggester = FakeStepSuggester()

        assertEquals(SuggestResult.NotReady, SuggestStepsUseCase(com.focusdivergent.app.domain.usecase.FakeLlmSettings(), suggester)("Algo"))
        assertEquals(SuggestResult.NotReady, SuggestStepsUseCase(configuredLlm(consented = false), suggester)("Algo"))
        assertEquals(SuggestResult.NotReady, SuggestStepsUseCase(configuredLlm(), suggester)("   "))
        assertTrue(suggester.calls.isEmpty())
    }

    @Test
    fun `con todo en orden se envia el titulo limpio y la clave guardada`() = runTest {
        val suggester = FakeStepSuggester()

        val result = SuggestStepsUseCase(configuredLlm(), suggester)("  Escribir informe ")

        assertTrue(result is SuggestResult.Steps)
        assertEquals("Escribir informe", suggester.calls.single().taskTitle)
        assertEquals("clave-secreta", suggester.calls.single().apiKey)
    }

    // ---- Ajustes guardados

    private fun settings() = SharedPreferencesLlmSettings(ApplicationProvider.getApplicationContext<Context>(), FakeSecretCipher())

    private fun rawPrefs(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.applicationInfo.dataDir, "shared_prefs/llm.xml")
        return if (file.exists()) file.readText() else ""
    }

    @Test
    fun `la clave se guarda cifrada, nunca en claro, y se puede recuperar`() {
        val settings = settings()
        settings.clearKey()

        settings.saveKey("  sk-secreta-123  ")

        assertTrue(settings.config.value.hasKey)
        assertEquals("sk-secreta-123", settings.apiKey())
        val raw = rawPrefs()
        assertTrue(raw.contains("secret"))
        assertFalse("la clave no debe estar en claro: $raw", raw.contains("sk-secreta-123"))
    }

    @Test
    fun `quitar la clave quita tambien el permiso y la deja apagada`() {
        val settings = settings()
        settings.saveEndpoint("https://api.example.com/v1", "modelo-x")
        settings.saveKey("clave")
        settings.consent("api.example.com")
        assertTrue(settings.config.value.isConfigured && settings.config.value.isConsented)

        settings.clearKey()

        assertFalse(settings.config.value.hasKey)
        assertFalse(settings.config.value.isConfigured)
        assertNull(settings.config.value.consentedHost)
        assertNull(settings.apiKey())
        assertFalse(rawPrefs().contains("secret"))
    }

    @Test
    fun `una clave en blanco equivale a quitarla`() {
        val settings = settings()
        settings.saveKey("clave")

        settings.saveKey("   ")

        assertFalse(settings.config.value.hasKey)
    }

    @Test
    fun `cambiar de servidor pide de nuevo el permiso, y cambiar solo el modelo no`() {
        val settings = settings()
        settings.clearKey()
        settings.saveEndpoint("https://api.example.com/v1", "modelo-x")
        settings.consent("api.example.com")

        settings.saveEndpoint("https://api.example.com/v2", "modelo-y")
        assertTrue(settings.config.value.isConsented)

        settings.saveEndpoint("https://otro-servicio.com/v1", "modelo-y")
        assertFalse(settings.config.value.isConsented)
        assertNull(settings.config.value.consentedHost)
    }
}
