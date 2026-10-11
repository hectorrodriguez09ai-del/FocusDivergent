package com.focusdivergent.app.domain.reward

import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.FocusLaunchSession
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerSession
import com.focusdivergent.app.domain.usecase.TEST_NOW
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RewardRulesTest {

    private fun session(mode: TimerMode, startedAt: Instant = TEST_NOW) =
        TimerSession(mode, taskId = null, taskTitle = null, startedAt = startedAt)

    private fun focus(mode: TimerMode, minutesIn: Long, startedAt: Instant = TEST_NOW) =
        RewardRules.focusSession(session(mode, startedAt), startedAt + Duration.ofMinutes(minutesIn), ZoneOffset.UTC)

    // ---- Tareas y rutinas

    @Test
    fun `una tarea vale 10 XP y una moneda, y 15 XP si pedia energia alta`() {
        val normal = RewardRules.taskDone(task(1, energy = EnergyLevel.MEDIUM), TEST_TODAY)
        val low = RewardRules.taskDone(task(2, energy = EnergyLevel.LOW), TEST_TODAY)
        val high = RewardRules.taskDone(task(3, energy = EnergyLevel.HIGH), TEST_TODAY)

        assertEquals(RewardEvent(RewardCause.TASK_DONE, 1, TEST_TODAY, xp = 10, coins = 1), normal)
        assertEquals(10, low.xp)
        assertEquals(15, high.xp)
        assertEquals(1, high.coins)
    }

    @Test
    fun `una rutina hecha vale 8 XP y una moneda, anotada en el dia que se hizo`() {
        val past = LocalDate.of(2026, 10, 8)

        assertEquals(RewardEvent(RewardCause.ROUTINE_DONE, 4, past, xp = 8, coins = 1), RewardRules.routineDone(task(4), past))
    }

    @Test
    fun `seguir tras la Lanzadera vale 5 XP y ninguna moneda`() {
        val launch = FocusLaunchSession(taskId = 3, taskTitle = "Informe", startedAt = TEST_NOW, endsAt = TEST_NOW.plusSeconds(120))

        assertEquals(
            RewardEvent(RewardCause.LAUNCH_CONTINUED, TEST_NOW.toEpochMilli(), TEST_TODAY, xp = 5, coins = 0),
            RewardRules.launchContinued(launch, TEST_TODAY),
        )
    }

    @Test
    fun `un paso hecho vale 2 XP y ninguna moneda`() {
        assertEquals(
            RewardEvent(RewardCause.STEP_DONE, 7, TEST_TODAY, xp = 2, coins = 0),
            RewardRules.stepDone(7, TEST_TODAY),
        )
    }

    // ---- Sesiones de enfoque

    @Test
    fun `un bloque a medias no cuenta`() {
        assertNull(focus(TimerMode.Single(25), minutesIn = 24))
    }

    @Test
    fun `un bloque terminado de 25 minutos vale 5 XP y una moneda`() {
        assertEquals(
            RewardEvent(RewardCause.FOCUS_SESSION, TEST_NOW.toEpochMilli(), TEST_TODAY, xp = 5, coins = 1, minutes = 25),
            focus(TimerMode.Single(25), minutesIn = 25),
        )
    }

    @Test
    fun `la sesion anota los minutos de enfoque de los bloques terminados, no los del descanso ni los del bloque a medias`() {
        val pomodoro = TimerMode.Pomodoro(focusMinutes = 45, breakMinutes = 20, cycles = 3)

        assertEquals(45, focus(pomodoro, minutesIn = 45)?.minutes)
        assertEquals(45, focus(pomodoro, minutesIn = 100)?.minutes)
        assertEquals(90, focus(pomodoro, minutesIn = 110)?.minutes)
        assertEquals(135, focus(pomodoro, minutesIn = 175)?.minutes)
    }

    @Test
    fun `respetar el limite de una app vale 5 XP, sin monedas, y se identifica por la app`() {
        val a = RewardRules.limitRespected("com.example.social", TEST_TODAY)
        val b = RewardRules.limitRespected("com.example.video", TEST_TODAY)

        assertEquals(RewardCause.LIMIT_RESPECTED, a.cause)
        assertEquals(5, a.xp)
        assertEquals(0, a.coins)
        assertEquals(TEST_TODAY, a.day)
        assertEquals(a.refId, RewardRules.limitRespected("com.example.social", TEST_TODAY).refId)
        assertEquals(false, a.refId == b.refId)
    }

    @Test
    fun `los minutos de enfoque del dia no tienen tope y las monedas si`() {
        val days = listOf(DayReward(TEST_TODAY, xp = 400, coins = 40, minutes = 400))

        assertEquals(400, RewardTotals.minutesOn(days, TEST_TODAY))
        assertEquals(RewardRules.DAILY_COIN_CAP, RewardTotals.coinsOn(days, TEST_TODAY))
        assertEquals(0, RewardTotals.minutesOn(days, TEST_TODAY.minusDays(1)))
    }

    @Test
    fun `se pagan 5 XP por cada tramo completo de 15 minutos del bloque`() {
        assertEquals(10, focus(TimerMode.Single(30), minutesIn = 30)?.xp)
        assertEquals(15, focus(TimerMode.Single(45), minutesIn = 45)?.xp)
        assertEquals(40, focus(TimerMode.Single(120), minutesIn = 120)?.xp)
    }

    @Test
    fun `un bloque de 10 minutos o mas cuenta, uno mas corto no`() {
        assertEquals(5, focus(TimerMode.Single(10), minutesIn = 10)?.xp)
        assertNull(focus(TimerMode.Single(5), minutesIn = 5))
        assertNull(focus(TimerMode.Pomodoro(focusMinutes = 5, breakMinutes = 5, cycles = 3), minutesIn = 60))
    }

    @Test
    fun `en un Pomodoro solo cuentan los bloques de enfoque ya terminados, no los descansos`() {
        val pomodoro = TimerMode.Pomodoro(focusMinutes = 45, breakMinutes = 20, cycles = 3)

        // Durante el primer bloque: nada. Justo al terminarlo: uno. En medio del segundo: sigue siendo uno.
        assertNull(focus(pomodoro, minutesIn = 44))
        assertEquals(15 to 1, focus(pomodoro, minutesIn = 45)?.let { it.xp to it.coins })
        assertEquals(15 to 1, focus(pomodoro, minutesIn = 90)?.let { it.xp to it.coins })
        // Dos bloques (45 + 20 + 45 = 110) y los tres (45·3 + 20·2 = 175).
        assertEquals(30 to 2, focus(pomodoro, minutesIn = 110)?.let { it.xp to it.coins })
        assertEquals(45 to 3, focus(pomodoro, minutesIn = 175)?.let { it.xp to it.coins })
        assertEquals(45 to 3, focus(pomodoro, minutesIn = 1_000)?.let { it.xp to it.coins })
    }

    @Test
    fun `la sesion se identifica por cuando empezo, asi cerrarla dos veces no suma dos veces`() {
        val first = focus(TimerMode.Single(25), 25)
        val again = focus(TimerMode.Single(25), 500)

        assertEquals(first?.refId, again?.refId)
        assertEquals(TEST_NOW.toEpochMilli(), first?.refId)
    }

    @Test
    fun `una sesion cerrada pasada la medianoche suma al dia del ultimo bloque`() {
        val lateStart = Instant.parse("2026-10-10T23:30:00Z")

        // El bloque de 45 minutos termina a las 00:15 del día siguiente.
        val event = focus(TimerMode.Single(45), minutesIn = 600, startedAt = lateStart)

        assertEquals(LocalDate.of(2026, 10, 11), event?.day)
    }

    // ---- Topes

    @Test
    fun `el XP de un dia se corta en el tope diario`() {
        val days = listOf(
            DayReward(LocalDate.of(2026, 10, 9), xp = 250, coins = 40),
            DayReward(LocalDate.of(2026, 10, 10), xp = 30, coins = 3),
        )

        assertEquals(RewardRules.DAILY_XP_CAP + 30, RewardTotals.xp(days))
        assertEquals(RewardRules.DAILY_COIN_CAP + 3, RewardTotals.coins(days))
        assertEquals(RewardRules.DAILY_XP_CAP, RewardTotals.xpOn(days, LocalDate.of(2026, 10, 9)))
        assertEquals(30, RewardTotals.xpOn(days, LocalDate.of(2026, 10, 10)))
        assertEquals(0, RewardTotals.xpOn(days, LocalDate.of(2026, 10, 11)))
    }

    @Test
    fun `un total negativo no resta`() {
        val days = listOf(DayReward(LocalDate.of(2026, 10, 9), xp = -5, coins = -1))

        assertEquals(0, RewardTotals.xp(days))
        assertEquals(0, RewardTotals.coins(days))
    }
}
