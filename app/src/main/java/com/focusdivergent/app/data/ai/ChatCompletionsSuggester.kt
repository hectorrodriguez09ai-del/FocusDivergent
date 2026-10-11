package com.focusdivergent.app.data.ai

import com.focusdivergent.app.domain.ai.LlmConfig
import com.focusdivergent.app.domain.ai.LlmEndpoint
import com.focusdivergent.app.domain.ai.StepPrompt
import com.focusdivergent.app.domain.ai.StepSuggester
import com.focusdivergent.app.domain.ai.SuggestResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Respuesta cruda de una petición: el código HTTP y el cuerpo. */
data class HttpReply(val code: Int, val body: String)

/** Envía un POST con JSON. Separado para poder probar el cliente sin red. Lanza [IOException] si no hay conexión. */
fun interface HttpPoster {
    fun post(url: String, headers: Map<String, String>, body: String): HttpReply
}

/** El único código de la app que abre una conexión a internet. Solo https, sin seguir redirecciones. */
@Singleton
class UrlConnectionPoster @Inject constructor() : HttpPoster {

    override fun post(url: String, headers: Map<String, String>, body: String): HttpReply {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // Una redirección podría llevar la clave a otro servidor: no se sigue.
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().take(MAX_BYTES).toByteArray().toString(Charsets.UTF_8) }.orEmpty()
            return HttpReply(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 25_000
        const val MAX_BYTES = 256 * 1024
    }
}

/** Habla con cualquier servicio compatible con «chat completions» de OpenAI. */
@Singleton
class ChatCompletionsSuggester @Inject constructor(
    private val http: HttpPoster,
) : StepSuggester {

    override suspend fun suggest(config: LlmConfig, apiKey: String, taskTitle: String): SuggestResult {
        if (LlmEndpoint.host(config.baseUrl) == null) return SuggestResult.NotReady
        val body = JSONObject()
            .put("model", config.model)
            .put("temperature", 0.4)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", StepPrompt.SYSTEM))
                    .put(JSONObject().put("role", "user").put("content", StepPrompt.user(taskTitle))),
            )
            .toString()
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json",
            "Accept" to "application/json",
        )
        val reply = try {
            withContext(Dispatchers.IO) { http.post(LlmEndpoint.chatCompletionsUrl(config.baseUrl), headers, body) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin red, tiempo agotado, dirección que no existe, certificado: para el usuario es lo mismo.
            return SuggestResult.NoConnection
        }
        // Cualquier código que no sea 2xx: el servicio contestó pero no aceptó (o no entendió) la petición.
        return if (reply.code in 200..299) parse(reply.body) else SuggestResult.Rejected(reply.code)
    }

    private fun parse(body: String): SuggestResult {
        val content = try {
            JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } catch (e: Exception) {
            return SuggestResult.Unreadable
        }
        val steps = StepPrompt.parse(content)
        return if (steps.isEmpty()) SuggestResult.Unreadable else SuggestResult.Steps(steps)
    }
}
