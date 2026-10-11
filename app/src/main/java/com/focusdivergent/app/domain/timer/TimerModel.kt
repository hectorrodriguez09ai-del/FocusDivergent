package com.focusdivergent.app.domain.timer

import java.time.Duration
import java.time.Instant

/** Cómo se organiza una sesión de temporizador (RF-4.1). */
sealed interface TimerMode {

    /** Una sola cuenta atrás de [minutes] minutos. */
    data class Single(val minutes: Int) : TimerMode

    /**
     * Pomodoro: [cycles] bloques de enfoque de [focusMinutes], con un descanso de [breakMinutes] entre uno y el
     * siguiente. No hay descanso tras el último bloque: la sesión acaba cuando acaba el trabajo.
     */
    data class Pomodoro(val focusMinutes: Int, val breakMinutes: Int, val cycles: Int) : TimerMode
}

enum class TimerPhaseKind { FOCUS, BREAK }

data class TimerPhase(val kind: TimerPhaseKind, val minutes: Int) {
    val duration: Duration get() = Duration.ofMinutes(minutes.toLong())
}

/**
 * Una sesión en curso. Solo guarda **cuándo empezó** y cómo estaba configurada: en qué fase va y cuánto queda se
 * calcula siempre a partir de la hora ([TimerPlan.stateAt]). Así no depende de ningún contador en memoria, y
 * sobrevive a que el sistema mate la app, a una alarma retrasada o a un reinicio.
 *
 * @property taskId la tarea a la que acompaña, o nulo si es un temporizador suelto.
 * @property taskTitle su título tal como se eligió (no depende de que la tarea siga existiendo).
 */
data class TimerSession(
    val mode: TimerMode,
    val taskId: Long?,
    val taskTitle: String?,
    val startedAt: Instant,
)

sealed interface TimerState {

    /**
     * @property focusNumber de qué bloque de enfoque se trata (1, 2…), o el que acaba de pasar durante un descanso.
     * @property focusTotal cuántos bloques de enfoque tiene la sesión.
     */
    data class Running(
        val phase: TimerPhase,
        val phaseIndex: Int,
        val focusNumber: Int,
        val focusTotal: Int,
        val phaseStartedAt: Instant,
        val phaseEndsAt: Instant,
        val remaining: Duration,
    ) : TimerState {

        /** Lo que queda de la fase, de 1 (acaba de empezar) a 0 (acabada): lo que dibuja la «tarta» que se vacía. */
        val fractionRemaining: Float
            get() = (remaining.toMillis().toFloat() / phase.duration.toMillis().toFloat()).coerceIn(0f, 1f)

        /** Segundos que se enseñan: se redondea hacia arriba para que «00:00» solo se vea cuando de verdad acaba. */
        val remainingSeconds: Long get() = (remaining.toMillis() + 999) / 1000
    }

    /** Se acabaron todas las fases. La sesión sigue guardada hasta que el usuario la cierre. */
    data class Finished(val endedAt: Instant) : TimerState
}

/** Límites de lo que se puede configurar; todo lo que entra se ajusta a ellos. */
object TimerLimits {
    const val SINGLE_MIN = 5
    const val SINGLE_MAX = 180
    const val FOCUS_MIN = 5
    const val FOCUS_MAX = 120
    const val BREAK_MIN = 5
    const val BREAK_MAX = 60
    const val CYCLES_MIN = 1
    const val CYCLES_MAX = 8

    val SINGLE_PRESETS = listOf(5, 15, 25, 45)

    val DEFAULT_SINGLE = TimerMode.Single(25)

    /** El ejemplo del SRD: 45 minutos de enfoque y 20 de descanso. */
    val DEFAULT_POMODORO = TimerMode.Pomodoro(focusMinutes = 45, breakMinutes = 20, cycles = 3)

    fun normalize(mode: TimerMode): TimerMode = when (mode) {
        is TimerMode.Single -> mode.copy(minutes = mode.minutes.coerceIn(SINGLE_MIN, SINGLE_MAX))
        is TimerMode.Pomodoro -> mode.copy(
            focusMinutes = mode.focusMinutes.coerceIn(FOCUS_MIN, FOCUS_MAX),
            breakMinutes = mode.breakMinutes.coerceIn(BREAK_MIN, BREAK_MAX),
            cycles = mode.cycles.coerceIn(CYCLES_MIN, CYCLES_MAX),
        )
    }
}

/** El plan de fases de una sesión y el cálculo de en qué punto está según la hora. Puro: se prueba con una hora fija. */
object TimerPlan {

    fun phases(mode: TimerMode): List<TimerPhase> = when (mode) {
        is TimerMode.Single -> listOf(TimerPhase(TimerPhaseKind.FOCUS, mode.minutes))
        is TimerMode.Pomodoro -> buildList {
            repeat(mode.cycles) { index ->
                add(TimerPhase(TimerPhaseKind.FOCUS, mode.focusMinutes))
                if (index < mode.cycles - 1) add(TimerPhase(TimerPhaseKind.BREAK, mode.breakMinutes))
            }
        }
    }

    fun totalDuration(mode: TimerMode): Duration =
        phases(mode).fold(Duration.ZERO) { sum, phase -> sum + phase.duration }

    /** En qué fase está [session] a la hora [now], cuánto queda, o si ya terminó. Una hora anterior al inicio cuenta como el inicio. */
    fun stateAt(session: TimerSession, now: Instant): TimerState {
        val phases = phases(session.mode)
        val focusTotal = phases.count { it.kind == TimerPhaseKind.FOCUS }
        val elapsed = Duration.between(session.startedAt, now).coerceAtLeast(Duration.ZERO)

        var phaseStart = Duration.ZERO
        var focusSoFar = 0
        phases.forEachIndexed { index, phase ->
            if (phase.kind == TimerPhaseKind.FOCUS) focusSoFar++
            val phaseEnd = phaseStart + phase.duration
            if (elapsed < phaseEnd) {
                return TimerState.Running(
                    phase = phase,
                    phaseIndex = index,
                    focusNumber = focusSoFar,
                    focusTotal = focusTotal,
                    phaseStartedAt = session.startedAt + phaseStart,
                    phaseEndsAt = session.startedAt + phaseEnd,
                    remaining = phaseEnd - elapsed,
                )
            }
            phaseStart = phaseEnd
        }
        return TimerState.Finished(endedAt = session.startedAt + phaseStart)
    }

    private fun Duration.coerceAtLeast(min: Duration) = if (this < min) min else this
}
