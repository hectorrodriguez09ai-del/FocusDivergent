package com.focusdivergent.app.domain.reward

import java.time.LocalDate

/**
 * Qué hizo ganar XP (RF-6.1). Cada causa admite como mucho un movimiento por actividad y día, de modo que repetir
 * una acción no suma dos veces.
 */
enum class RewardCause {
    /** Una tarea u Obligación terminada. [RewardEvent.refId] es el id de la tarea. */
    TASK_DONE,

    /** Una Rutina hecha en un día. [RewardEvent.refId] es el id de la rutina. */
    ROUTINE_DONE,

    /** Los bloques de enfoque completados de una sesión del temporizador. [RewardEvent.refId] es cuándo empezó. */
    FOCUS_SESSION,

    /** Seguir con la tarea al acabar la Lanzadera. [RewardEvent.refId] es cuándo empezó. */
    LAUNCH_CONTINUED,

    /** Respetar el límite de una app durante el día, medido al Cierre de Jornada. [RewardEvent.refId] identifica la app. */
    LIMIT_RESPECTED,

    /** Un micro-paso de una tarea hecho. [RewardEvent.refId] es el id del paso. */
    STEP_DONE,
}

/**
 * Un movimiento del libro de XP y monedas. Nunca es negativo: lo único que lo quita es deshacer la acción que lo
 * causó (desmarcar una tarea), y entonces se borra el movimiento entero.
 */
data class RewardEvent(
    val cause: RewardCause,
    val refId: Long,
    val day: LocalDate,
    val xp: Int,
    val coins: Int,
    /** Minutos de enfoque que cuenta el movimiento: solo los de una sesión del temporizador; 0 en los demás. */
    val minutes: Int = 0,
)

/** Lo ganado un día, sumando todos sus movimientos y antes de aplicar ningún tope. */
data class DayReward(
    val day: LocalDate,
    val xp: Int,
    val coins: Int,
    val minutes: Int = 0,
)
