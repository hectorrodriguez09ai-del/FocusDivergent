package com.focusdivergent.app.domain.steps

/**
 * Un micro-paso de una tarea (RF-3.1): algo de unos 5 minutos que se puede empezar sin pensar.
 *
 * [position] solo sirve para ordenar (no tiene por qué ser consecutivo). Que estén hechos todos los pasos no da la
 * tarea por hecha: eso lo decide el usuario.
 */
data class TaskStep(
    val id: Long = 0,
    val taskId: Long,
    val position: Int,
    val text: String,
    val done: Boolean = false,
)

/**
 * Una lista de pasos con nombre que se puede aplicar a otra tarea (p. ej. «Hacer la compra»).
 *
 * @property id 0 para las plantillas de partida, que no se guardan en la base de datos.
 */
data class StepTemplate(
    val id: Long = 0,
    val name: String,
    val steps: List<String>,
    val builtIn: Boolean = false,
)

/** Cuánto lleva una tarea: cuántos pasos tiene, cuántos están hechos y cuál toca ahora. */
data class StepProgress(
    val done: Int,
    val total: Int,
    val next: TaskStep?,
) {
    val hasSteps: Boolean get() = total > 0
    val allDone: Boolean get() = total > 0 && done == total

    companion object {
        val NONE = StepProgress(done = 0, total = 0, next = null)

        fun of(steps: List<TaskStep>): StepProgress {
            if (steps.isEmpty()) return NONE
            val ordered = steps.sortedWith(compareBy({ it.position }, { it.id }))
            return StepProgress(done = ordered.count { it.done }, total = ordered.size, next = ordered.firstOrNull { !it.done })
        }
    }
}

/** Límites y limpieza del texto de los pasos y de las plantillas. */
object StepRules {
    const val MAX_STEPS_PER_TASK = 20
    const val MAX_STEP_LENGTH = 120
    const val MAX_TEMPLATE_NAME = 40
    const val MAX_USER_TEMPLATES = 30

    private val whitespace = Regex("\\s+")

    /** Texto del paso sin saltos de línea ni espacios sobrantes y recortado al máximo; nulo si queda vacío. */
    fun cleanStep(text: String): String? =
        text.replace(whitespace, " ").trim().take(MAX_STEP_LENGTH).trim().takeIf { it.isNotEmpty() }

    fun cleanName(name: String): String? =
        name.replace(whitespace, " ").trim().take(MAX_TEMPLATE_NAME).trim().takeIf { it.isNotEmpty() }
}

/**
 * Qué paso toca ahora en la pantalla de uno en uno.
 *
 * «Ahora no» no marca nada ni cambia el orden guardado: solo aparta el paso durante esta sesión y pasa al siguiente.
 * Cuando ya no queda ninguno sin apartar, la sesión se acaba con calma y los pasos esperan.
 */
object StepQueue {

    fun current(steps: List<TaskStep>, setAside: Set<Long>): TaskStep? =
        steps.sortedWith(compareBy({ it.position }, { it.id })).firstOrNull { !it.done && it.id !in setAside }
}
