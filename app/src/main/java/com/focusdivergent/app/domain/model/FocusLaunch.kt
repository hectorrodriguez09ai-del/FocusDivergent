package com.focusdivergent.app.domain.model

import java.time.Instant

/**
 * Una Lanzadera de Enfoque en curso (RF-3.2): [DURATION_SECONDS] segundos con el móvil bloqueado para romper la
 * inercia y empezar una tarea.
 *
 * @property taskId la tarea que se va a empezar, o nulo si el usuario solo quiere empezar «lo que sea».
 * @property taskTitle su título tal como se eligió (se guarda para no depender de que la tarea siga existiendo).
 * @property startedAt cuándo empezó; identifica la sesión.
 * @property endsAt cuándo termina el bloqueo y llega la pregunta de si se sigue.
 */
data class FocusLaunchSession(
    val taskId: Long?,
    val taskTitle: String?,
    val startedAt: Instant,
    val endsAt: Instant,
) {
    companion object {
        /** Duración de la regla de los 2 minutos. */
        const val DURATION_SECONDS = 120L
    }
}

/**
 * El bloqueo de la Lanzadera: pantalla completa con la tarea y la cuenta atrás, sin ningún botón para cerrarla.
 * Solo se retira al terminar el tiempo o, por seguridad, durante una llamada o con la pantalla apagada.
 */
data class FocusLaunch(
    val taskTitle: String?,
    val startedAt: Instant,
    val endsAt: Instant,
) : Interruption {

    override val key: String get() = "launch:$startedAt"
}

/**
 * La pregunta con la que acaba la Lanzadera: seguir con la tarea o dejarlo, sin culpa. No se retira sola; espera
 * a que el usuario conteste.
 */
data class FocusLaunchPrompt(
    val taskId: Long?,
    val taskTitle: String?,
    val startedAt: Instant,
) : Interruption {

    override val key: String get() = "launch-end:$startedAt"
}
