package com.focusdivergent.app.domain.ai

import com.focusdivergent.app.domain.steps.StepRules
import kotlinx.coroutines.flow.StateFlow

/**
 * Lo que el usuario ha configurado para pedir pasos a su propio LLM (RF-3.1). Es lo único de la app que sale a
 * internet, y solo cuando el usuario lo pide.
 *
 * @property baseUrl dirección base de un servicio compatible con «chat completions» de OpenAI (https).
 * @property model nombre del modelo.
 * @property hasKey hay una clave guardada (cifrada); la clave en sí nunca sale de [LlmSettings.apiKey].
 * @property consentedHost el servidor al que el usuario ya dijo que sí; si cambia la dirección, hay que volver a decirlo.
 */
data class LlmConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = "",
    val hasKey: Boolean = false,
    val consentedHost: String? = null,
) {
    /** Hay todo lo necesario para pedir pasos: dirección válida, modelo y clave. */
    val isConfigured: Boolean get() = hasKey && model.isNotBlank() && LlmEndpoint.host(baseUrl) != null

    val host: String? get() = LlmEndpoint.host(baseUrl)

    /** El usuario ya aceptó enviar la consulta a este servidor. */
    val isConsented: Boolean get() = host != null && host == consentedHost

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    }
}

/** Reglas de la dirección del servicio. Solo https: la clave y el título no viajan sin cifrar. */
object LlmEndpoint {

    /** El nombre del servidor de [baseUrl], o nulo si no es una dirección https válida. */
    fun host(baseUrl: String): String? {
        val trimmed = baseUrl.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        val authority = trimmed.substring("https://".length).substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty() || authority.contains('@') || authority.contains(' ')) return null
        return authority.substringBefore(':').lowercase().takeIf { it.isNotEmpty() }
    }

    /** La dirección completa de «chat completions» a partir de la base. */
    fun chatCompletionsUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/') + "/chat/completions"
}

/** Dónde se guardan la configuración y la clave. La clave se guarda cifrada. */
interface LlmSettings {

    val config: StateFlow<LlmConfig>

    fun saveEndpoint(baseUrl: String, model: String)

    /** Guarda la clave (cifrada). Una clave en blanco equivale a quitarla. */
    fun saveKey(key: String)

    /** Borra la clave y el permiso dado: la función queda como si nunca se hubiera configurado. */
    fun clearKey()

    /** Recuerda que el usuario aceptó enviar la consulta al servidor de [host]. */
    fun consent(host: String)

    /** La clave descifrada, solo para ponerla en la petición. Nulo si no hay o no se puede leer. */
    fun apiKey(): String?
}

sealed interface SuggestResult {
    data class Steps(val steps: List<String>) : SuggestResult

    /** Falta la clave, el modelo o la dirección, o el usuario aún no aceptó. */
    data object NotReady : SuggestResult

    /** No se pudo llegar al servicio (sin red, tiempo agotado, dirección que no responde). */
    data object NoConnection : SuggestResult

    /** El servicio contestó pero no aceptó la petición: clave, modelo o permisos. */
    data class Rejected(val httpCode: Int) : SuggestResult

    /** El servicio contestó algo que no se pudo leer como una lista de pasos. */
    data object Unreadable : SuggestResult
}

/** Pide pasos a un servicio externo. Solo recibe el título de la tarea: nada más sale del móvil. */
interface StepSuggester {
    suspend fun suggest(config: LlmConfig, apiKey: String, taskTitle: String): SuggestResult
}

/** Lo que se le pide al modelo y cómo se lee su respuesta. Funciones puras. */
object StepPrompt {

    const val SYSTEM = "Eres un asistente que ayuda a personas con TDAH a empezar tareas. Divide la tarea que te den " +
        "en entre 3 y 8 micro-pasos concretos, de unos 5 minutos cada uno, empezando por uno muy fácil. Cada paso " +
        "es una frase corta en imperativo, en el mismo idioma que la tarea. Responde solo con los pasos, uno por " +
        "línea, sin numerar, sin explicaciones y sin juicios."

    fun user(taskTitle: String): String = "Tarea: ${taskTitle.trim()}"

    private val listMark = Regex("""^\s*(?:[-*•–]+|\d{1,2}[.)]|paso\s*\d{1,2}\s*[:.)-])\s*""", RegexOption.IGNORE_CASE)
    private val emphasis = Regex("""[*_`]""")

    /** Pasos limpios a partir del texto del modelo: sin viñetas ni números, sin vacíos ni repetidos, con tope. */
    fun parse(answer: String): List<String> = answer.lineSequence()
        .map { listMark.replace(it, "").replace(emphasis, "") }
        .filterNot { it.trim().startsWith("```") }
        // Una frase de introducción («Claro, aquí tienes:») no es un paso.
        .filterNot { it.trimEnd().endsWith(':') }
        .mapNotNull(StepRules::cleanStep)
        .distinct()
        .take(StepRules.MAX_STEPS_PER_TASK)
        .toList()
}
