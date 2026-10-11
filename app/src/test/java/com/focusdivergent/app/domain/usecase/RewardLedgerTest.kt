package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.launch.FocusLaunchStore
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.FocusLaunchSession
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.reward.RewardCause
import com.focusdivergent.app.domain.reward.RewardEvent
import com.focusdivergent.app.domain.reward.RewardRules
import com.focusdivergent.app.domain.tasks.TaskTrash
import com.focusdivergent.app.domain.timer.TimerMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que se anota en el libro de XP cuando el usuario hace algo, y que deshacerlo lo retira. */
class RewardLedgerTest {

    private val clock = MutableClock()
    private val tasks = FakeTasks()
    private val completions = FakeRoutineCompletions()
    private val rewards = FakeRewards()
    private val stats = FakeUserStats()
    private val ledger = testLedger(completions, rewards, stats, clock)
    private val setDone = SetTaskDoneUseCase(tasks, completions, clock, ledger)

    private val store = FakeTimerStore()
    private val sync = SyncTimerUseCase(store, FakeTimerNotifier(), FakeTimerAlarms(), clock)
    private val startTimer = StartTimerUseCase(store, FakeTimerSettings(), StartTaskUseCase(tasks), sync, clock, ledger)
    private val stopTimer = StopTimerUseCase(store, sync, ledger)
    private val finishTimer = FinishTimerUseCase(store, tasks, setDone, sync, ledger)

    private fun day(n: Int) = LocalDate.of(2026, 10, n)

    // ---- Tareas

    @Test
    fun `terminar una tarea suma su XP y su moneda en el dia de hoy`() = runTest {
        val t = task(1, energy = EnergyLevel.MEDIUM).also { tasks.save(it) }

        setDone(t, done = true)

        assertEquals(listOf(RewardEvent(RewardCause.TASK_DONE, 1, TEST_TODAY, xp = 10, coins = 1)), rewards.events)
    }

    @Test
    fun `una tarea de energia alta suma mas`() = runTest {
        val t = task(1, energy = EnergyLevel.HIGH).also { tasks.save(it) }

        setDone(t, done = true)

        assertEquals(15, rewards.totalXp())
    }

    @Test
    fun `una Obligacion terminada cuenta como una tarea`() = runTest {
        val o = obligation(2, startsInMinutes = -30).also { tasks.save(it) }

        setDone(o, done = true)

        assertEquals(10, rewards.totalXp())
    }

    @Test
    fun `terminar dos veces la misma tarea no suma dos veces`() = runTest {
        val t = task(1).also { tasks.save(it) }

        setDone(t, done = true)
        setDone(t, done = true)

        assertEquals(10, rewards.totalXp())
    }

    @Test
    fun `desmarcar una tarea retira su XP`() = runTest {
        val t = task(1).also { tasks.save(it) }
        setDone(t, done = true)

        setDone(t, done = false)

        assertTrue(rewards.events.isEmpty())
        assertEquals(TaskStatus.PENDING, tasks.getById(1)?.status)
    }

    @Test
    fun `marcar y desmarcar sin parar no infla el XP`() = runTest {
        val t = task(1).also { tasks.save(it) }

        repeat(10) {
            setDone(t, done = true)
            setDone(t, done = false)
        }
        setDone(t, done = true)

        assertEquals(10, rewards.totalXp())
    }

    @Test
    fun `desmarcar retira el XP aunque se hubiera marcado otro dia`() = runTest {
        val t = task(1, status = TaskStatus.DONE, completedAt = TEST_NOW.minus(Duration.ofDays(2))).also { tasks.save(it) }
        rewards.record(RewardEvent(RewardCause.TASK_DONE, 1, day(8), xp = 10, coins = 1))

        setDone(t, done = false)

        assertTrue(rewards.events.isEmpty())
    }

    @Test
    fun `desmarcar una tarea retira solo la suya`() = runTest {
        val a = task(1).also { tasks.save(it) }
        val b = task(2).also { tasks.save(it) }
        setDone(a, done = true)
        setDone(b, done = true)

        setDone(a, done = false)

        assertEquals(listOf(2L), rewards.events.map { it.refId })
    }

    // ---- Rutinas

    @Test
    fun `una rutina hecha hoy suma 8 XP`() = runTest {
        val r = task(3, type = TaskType.ROUTINE).also { tasks.save(it) }

        setDone(r, done = true)

        assertEquals(listOf(RewardEvent(RewardCause.ROUTINE_DONE, 3, TEST_TODAY, xp = 8, coins = 1)), rewards.events)
    }

    @Test
    fun `una rutina marcada en un dia pasado suma en ese dia, y desmarcarla solo retira ese dia`() = runTest {
        val r = task(3, type = TaskType.ROUTINE).also { tasks.save(it) }
        setDone(r, done = true, day = day(8))
        setDone(r, done = true, day = day(9))

        setDone(r, done = false, day = day(8))

        assertEquals(listOf(day(9)), rewards.events.map { it.day })
    }

    @Test
    fun `una rutina de un dia futuro no hace nada ni suma`() = runTest {
        val r = task(3, type = TaskType.ROUTINE).also { tasks.save(it) }

        assertFalse(setDone(r, done = true, day = day(11)))

        assertTrue(rewards.events.isEmpty())
        assertTrue(completions.items.isEmpty())
    }

    @Test
    fun `las rutinas hechas suben la mejor racha y desmarcar no la baja`() = runTest {
        val r = task(3, type = TaskType.ROUTINE).also { tasks.save(it) }
        setDone(r, done = true, day = day(8))
        setDone(r, done = true, day = day(9))
        setDone(r, done = true, day = day(10))
        assertEquals(3, stats.bestStreak)

        setDone(r, done = false, day = day(10))
        setDone(r, done = false, day = day(9))

        assertEquals(3, stats.bestStreak)
    }

    @Test
    fun `borrar una rutina guarda antes la mejor racha, que no debe bajar`() = runTest {
        val r = task(3, type = TaskType.ROUTINE).also { tasks.save(it) }
        // Marcas hechas sin pasar por el libro (de antes de esta versión).
        for (d in 7..10) completions.markCompleted(3, day(d), TEST_NOW)

        DeleteTaskUseCase(tasks, TaskTrash(), ledger)(r)

        assertEquals(4, stats.bestStreak)
    }

    @Test
    fun `borrar una tarea que no es rutina no toca la racha`() = runTest {
        val t = task(4).also { tasks.save(it) }
        for (d in 7..10) completions.markCompleted(99, day(d), TEST_NOW)

        DeleteTaskUseCase(tasks, TaskTrash(), ledger)(t)

        assertEquals(0, stats.bestStreak)
    }

    @Test
    fun `borrar una actividad no retira el XP ya ganado`() = runTest {
        val t = task(1).also { tasks.save(it) }
        setDone(t, done = true)

        DeleteTaskUseCase(tasks, TaskTrash(), ledger)(t)

        assertEquals(10, rewards.totalXp())
    }

    // ---- Temporizador

    @Test
    fun `terminar una sesion suma sus bloques de enfoque una sola vez`() = runTest {
        startTimer(TimerMode.Single(25))
        clock.now = TEST_NOW + Duration.ofMinutes(26)

        finishTimer(taskDone = false)
        finishTimer(taskDone = false)

        assertEquals(
            listOf(RewardEvent(RewardCause.FOCUS_SESSION, TEST_NOW.toEpochMilli(), TEST_TODAY, xp = 5, coins = 1, minutes = 25)),
            rewards.events,
        )
    }

    @Test
    fun `terminar la sesion y marcar la tarea suma las dos cosas`() = runTest {
        tasks.save(task(7))
        startTimer(TimerMode.Single(25), tasks.getById(7))
        clock.now = TEST_NOW + Duration.ofMinutes(25)

        finishTimer(taskDone = true)

        assertEquals(setOf(RewardCause.FOCUS_SESSION, RewardCause.TASK_DONE), rewards.events.map { it.cause }.toSet())
    }

    @Test
    fun `parar a mitad de un bloque no suma ni resta`() = runTest {
        startTimer(TimerMode.Single(25))
        clock.now = TEST_NOW + Duration.ofMinutes(10)

        stopTimer()

        assertTrue(rewards.events.isEmpty())
        assertEquals(null, store.session.value)
    }

    @Test
    fun `parar tras el primer bloque de un Pomodoro suma ese bloque`() = runTest {
        startTimer(TimerMode.Pomodoro(focusMinutes = 25, breakMinutes = 5, cycles = 3))
        clock.now = TEST_NOW + Duration.ofMinutes(40)

        stopTimer()

        assertEquals(5 to 1, rewards.events.single().let { it.xp to it.coins })
    }

    @Test
    fun `empezar otra sesion suma los bloques que se habian terminado de la anterior`() = runTest {
        startTimer(TimerMode.Single(25))
        clock.now = TEST_NOW + Duration.ofMinutes(30)

        startTimer(TimerMode.Single(25))

        assertEquals(1, rewards.events.size)
        assertEquals(TEST_NOW.toEpochMilli(), rewards.events.single().refId)
    }

    @Test
    fun `un bloque largo paga 5 XP por cada tramo de 15 minutos`() = runTest {
        startTimer(TimerMode.Single(180))
        clock.now = TEST_NOW + Duration.ofMinutes(180)
        finishTimer(taskDone = false)

        assertEquals(12 * RewardRules.FOCUS_XP_PER_CHUNK, rewards.totalXp())
    }

    // ---- Lanzadera

    private class FakeLaunchStore(var session: FocusLaunchSession?) : FocusLaunchStore {
        override fun current() = session
        override fun save(session: FocusLaunchSession) {
            this.session = session
        }

        override fun clear() {
            session = null
        }
    }

    private fun launch(startedAt: Instant = TEST_NOW) = FocusLaunchSession(5, "Informe", startedAt, startedAt.plusSeconds(120))

    @Test
    fun `seguir tras la Lanzadera suma 5 XP`() = runTest {
        tasks.save(task(5))

        FinishFocusLaunchUseCase(FakeLaunchStore(launch()), tasks, ledger).continueWork(5)

        assertEquals(
            listOf(RewardEvent(RewardCause.LAUNCH_CONTINUED, TEST_NOW.toEpochMilli(), TEST_TODAY, xp = 5, coins = 0)),
            rewards.events,
        )
    }

    @Test
    fun `dejarlo por ahora tras la Lanzadera no suma ni resta nada`() = runTest {
        FinishFocusLaunchUseCase(FakeLaunchStore(launch()), tasks, ledger).stop()

        assertTrue(rewards.events.isEmpty())
    }

    @Test
    fun `seguir sin que quede sesion guardada no suma nada`() = runTest {
        FinishFocusLaunchUseCase(FakeLaunchStore(null), tasks, ledger).continueWork(5)

        assertTrue(rewards.events.isEmpty())
    }
}
