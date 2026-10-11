package com.focusdivergent.app.domain.model

/** Estado de una actividad. No existe un estado "atrasada" o "fallida" a propósito (RNF-3). */
enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
}
