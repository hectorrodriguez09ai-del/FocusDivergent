package com.focusdivergent.app.domain.model

/** Categorías de actividad del SRD §3.2. Cada una dispara un comportamiento distinto del sistema. */
enum class TaskType {
    /** Acción puntual. Bloquea si se supera el tiempo asignado. */
    TASK,

    /** Evento crítico. Lanza un overlay preventivo antes de su hora. */
    OBLIGATION,

    /** Acción recurrente. Genera recordatorios amables y suma rachas. */
    ROUTINE,
}
