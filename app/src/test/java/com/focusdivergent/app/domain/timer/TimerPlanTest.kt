package com.focusdivergent.app.domain.timer

import com.focusdivergent.app.domain.usecase.TEST_NOW
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerPlanTest {

    private val pomodoro = TimerMode.Pomodoro(focusMinutes = 45, breakMinutes = 20, cycles = 3)

    private fun session(mode: TimerMode) = TimerSession(mode, taskId = null, taskTitle = null, startedAt = TEST_NOW)

    private fun at(minutes: Long, seconds: Long = 0) = TEST_NOW.plus(Duration.ofMinutes(minutes)).plusSeconds(seconds)

    // ---- Plan

    @Test
    fun `un temporizador suelto es una sola fase de enfoque`() {
        assertEquals(listOf(TimerPhase(TimerPhaseKind.FOCUS, 25)), TimerPlan.phases(TimerMode.Single(25)))
    }

    @Test
    fun `el Pomodoro pone un descanso entre bloques y ninguno al final`() {
        val kinds = TimerPlan.phases(pomodoro).map { it.kind to it.minutes }

        assertEquals(
            listOf(
                TimerPhaseKind.FOCUS to 45, TimerPhaseKind.BREAK to 20,
                TimerPhaseKind.FOCUS to 45, TimerPhaseKind.BREAK to 20,
                TimerPhaseKind.FOCUS to 45,
            ),
            kinds,
        )
        assertEquals(Duration.ofMinutes(175), TimerPlan.totalDuration(pomodoro))
    }

    @Test
    fun `un Pomodoro de un solo bloque no tiene descanso`() {
        assertEquals(1, TimerPlan.phases(TimerMode.Pomodoro(25, 5, 1)).size)
    }

    // ---- Estado según la hora

    @Test
    fun `al empezar queda toda la primera fase`() {
        val state = TimerPlan.stateAt(session(pomodoro), TEST_NOW) as TimerState.Running

        assertEquals(TimerPhaseKind.FOCUS, state.phase.kind)
        assertEquals(0, state.phaseIndex)
        assertEquals(1, state.focusNumber)
        assertEquals(3, state.focusTotal)
        assertEquals(Duration.ofMinutes(45), state.remaining)
        assertEquals(1f, state.fractionRemaining, 0.0001f)
        assertEquals(TEST_NOW, state.phaseStartedAt)
        assertEquals(at(45), state.phaseEndsAt)
    }

    @Test
    fun `a mitad de una fase la tarta va por la mitad`() {
        val state = TimerPlan.stateAt(session(TimerMode.Single(30)), at(15)) as TimerState.Running

        assertEquals(0.5f, state.fractionRemaining, 0.0001f)
        assertEquals(15 * 60L, state.remainingSeconds)
    }

    @Test
    fun `despues del primer bloque llega el descanso, que sigue contando como el bloque 1`() {
        val state = TimerPlan.stateAt(session(pomodoro), at(50)) as TimerState.Running

        assertEquals(TimerPhaseKind.BREAK, state.phase.kind)
        assertEquals(1, state.phaseIndex)
        assertEquals(1, state.focusNumber)
        assertEquals(Duration.ofMinutes(15), state.remaining)
    }

    @Test
    fun `justo en el limite entre dos fases ya es la siguiente`() {
        val state = TimerPlan.stateAt(session(pomodoro), at(65)) as TimerState.Running

        assertEquals(2, state.phaseIndex)
        assertEquals(TimerPhaseKind.FOCUS, state.phase.kind)
        assertEquals(2, state.focusNumber)
        assertEquals(Duration.ofMinutes(45), state.remaining)
    }

    @Test
    fun `al acabar la ultima fase la sesion esta terminada, y lo sigue estando`() {
        val end = TimerPlan.stateAt(session(pomodoro), at(175))
        val muchoDespues = TimerPlan.stateAt(session(pomodoro), at(175 + 600))

        assertEquals(TimerState.Finished(endedAt = at(175)), end)
        assertEquals(TimerState.Finished(endedAt = at(175)), muchoDespues)
    }

    @Test
    fun `los segundos se redondean hacia arriba, de modo que 00 00 solo se ve al acabar`() {
        val almostDone = TimerPlan.stateAt(session(TimerMode.Single(5)), at(4, 59)) as TimerState.Running
        assertEquals(1L, almostDone.remainingSeconds)

        val partial = TimerPlan.stateAt(session(TimerMode.Single(5)), TEST_NOW.plusMillis(400)) as TimerState.Running
        assertEquals(5 * 60L, partial.remainingSeconds)
    }

    @Test
    fun `una hora anterior al inicio cuenta como el inicio`() {
        val state = TimerPlan.stateAt(session(TimerMode.Single(25)), TEST_NOW.minusSeconds(30)) as TimerState.Running

        assertEquals(Duration.ofMinutes(25), state.remaining)
    }

    // ---- Límites

    @Test
    fun `la configuracion se ajusta a los limites`() {
        assertEquals(TimerMode.Single(TimerLimits.SINGLE_MAX), TimerLimits.normalize(TimerMode.Single(9999)))
        assertEquals(TimerMode.Single(TimerLimits.SINGLE_MIN), TimerLimits.normalize(TimerMode.Single(0)))
        assertEquals(
            TimerMode.Pomodoro(TimerLimits.FOCUS_MAX, TimerLimits.BREAK_MIN, TimerLimits.CYCLES_MAX),
            TimerLimits.normalize(TimerMode.Pomodoro(focusMinutes = 500, breakMinutes = 1, cycles = 50)),
        )
    }

    @Test
    fun `el Pomodoro por defecto es el del SRD, 45 de enfoque y 20 de descanso`() {
        assertEquals(45, TimerLimits.DEFAULT_POMODORO.focusMinutes)
        assertEquals(20, TimerLimits.DEFAULT_POMODORO.breakMinutes)
        assertTrue(TimerLimits.SINGLE_PRESETS.containsAll(listOf(5, 15, 25, 45)))
    }
}
