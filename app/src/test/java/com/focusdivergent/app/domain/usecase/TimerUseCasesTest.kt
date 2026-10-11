package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerSession
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerUseCasesTest {

    private val clock = MutableClock()
    private val store = FakeTimerStore()
    private val settings = FakeTimerSettings()
    private val alarms = FakeTimerAlarms()
    private val notifier = FakeTimerNotifier()
    private val tasks = FakeTasks()
    private val completions = FakeRoutineCompletions()

    private val rewards = FakeRewards()
    private val ledger = testLedger(completions, rewards, clock = clock)

    private val sync = SyncTimerUseCase(store, notifier, alarms, clock)
    private val start = StartTimerUseCase(store, settings, StartTaskUseCase(tasks), sync, clock, ledger)
    private val stop = StopTimerUseCase(store, sync, ledger)
    private val finish = FinishTimerUseCase(store, tasks, SetTaskDoneUseCase(tasks, completions, clock, ledger), sync, ledger)

    private val pomodoro = TimerMode.Pomodoro(focusMinutes = 45, breakMinutes = 20, cycles = 2)

    private fun minutes(n: Long) = TEST_NOW.plus(Duration.ofMinutes(n))

    // ---- Empezar

    @Test
    fun `empezar guarda la sesion, programa la alarma del fin de la primera fase y enseña la notificacion`() = runTest {
        val session = start(pomodoro)

        assertEquals(session, store.session.value)
        assertEquals(TEST_NOW, session.startedAt)
        assertEquals(minutes(45), alarms.scheduled)
        assertEquals(listOf("running:0"), notifier.events)
    }

    @Test
    fun `empezar recuerda la configuracion, ajustada a los limites`() = runTest {
        start(TimerMode.Single(9999))
        start(TimerMode.Pomodoro(focusMinutes = 50, breakMinutes = 10, cycles = 4))

        assertEquals(180, settings.single.minutes)
        assertEquals(TimerMode.Pomodoro(50, 10, 4), settings.pomodoro)
    }

    @Test
    fun `empezar con una tarea la deja en marcha y la sesion recuerda su titulo`() = runTest {
        tasks.save(task(7, title = "Informe"))

        val session = start(TimerMode.Single(25), tasks.getById(7))

        assertEquals(7L, session.taskId)
        assertEquals("Informe", session.taskTitle)
        assertEquals(TaskStatus.IN_PROGRESS, tasks.getById(7)?.status)
    }

    @Test
    fun `empezar una sesion sustituye a la anterior`() = runTest {
        start(TimerMode.Single(25))
        clock.now = minutes(10)

        start(TimerMode.Single(5))

        assertEquals(minutes(15), alarms.scheduled)
        assertEquals(minutes(10), store.session.value?.startedAt)
    }

    // ---- Avisos

    @Test
    fun `al acabar un bloque la alarma avisa del descanso y programa el fin del descanso`() = runTest {
        start(pomodoro)
        notifier.events.clear()
        clock.now = minutes(45)

        sync.onBoundaryReached()

        assertEquals(listOf("running:1", "phase:1"), notifier.events)
        assertEquals(minutes(65), alarms.scheduled)
    }

    @Test
    fun `al acabar la ultima fase avisa del fin, retira la notificacion en curso y no deja alarma`() = runTest {
        start(TimerMode.Single(25))
        notifier.events.clear()
        clock.now = minutes(25)

        sync.onBoundaryReached()

        assertEquals(listOf("cancelRunning", "finished"), notifier.events)
        assertNull(alarms.scheduled)
        // La sesión sigue guardada: espera a que el usuario la cierre.
        assertTrue(store.session.value != null)
    }

    @Test
    fun `si la alarma llega tarde y se saltaron fases, avisa solo de la que toca ahora`() = runTest {
        start(pomodoro)
        notifier.events.clear()
        clock.now = minutes(70)

        sync.onBoundaryReached()

        assertEquals(listOf("running:2", "phase:2"), notifier.events)
        assertEquals(minutes(110), alarms.scheduled)
    }

    @Test
    fun `restaurar tras un reinicio repone la alarma y la notificacion sin avisar de nada`() = runTest {
        store.save(TimerSession(pomodoro, null, null, TEST_NOW))
        clock.now = minutes(50)

        sync.restore()

        assertEquals(listOf("running:1"), notifier.events)
        assertEquals(minutes(65), alarms.scheduled)
    }

    @Test
    fun `restaurar una sesion que ya acabo no avisa ni programa nada`() = runTest {
        store.save(TimerSession(TimerMode.Single(25), null, null, TEST_NOW))
        clock.now = minutes(90)

        sync.restore()

        assertEquals(listOf("cancelRunning"), notifier.events)
        assertNull(alarms.scheduled)
    }

    @Test
    fun `sin sesion, sincronizar limpia cualquier alarma o notificacion que quedara`() {
        alarms.scheduled = minutes(5)

        sync.restore()

        assertNull(alarms.scheduled)
        assertEquals(listOf("cancelAll"), notifier.events)
    }

    // ---- Parar y cerrar

    @Test
    fun `parar borra la sesion, la alarma y las notificaciones`() = runTest {
        start(pomodoro)
        notifier.events.clear()

        stop()

        assertNull(store.session.value)
        assertNull(alarms.scheduled)
        assertEquals(listOf("cancelAll"), notifier.events)
    }

    @Test
    fun `cerrar diciendo que la tarea esta hecha la marca como hecha`() = runTest {
        tasks.save(task(3, title = "Informe"))
        start(TimerMode.Single(25), tasks.getById(3))
        clock.now = minutes(30)

        finish(taskDone = true)

        assertEquals(TaskStatus.DONE, tasks.getById(3)?.status)
        assertNull(store.session.value)
        assertNull(alarms.scheduled)
    }

    @Test
    fun `cerrar diciendo que todavia no deja la tarea como estaba, sin reproche ni huella`() = runTest {
        tasks.save(task(3, title = "Informe"))
        start(TimerMode.Single(25), tasks.getById(3))

        finish(taskDone = false)

        assertEquals(TaskStatus.IN_PROGRESS, tasks.getById(3)?.status)
        assertNull(store.session.value)
    }

    @Test
    fun `cerrar una sesion suelta no toca ninguna tarea`() = runTest {
        tasks.save(task(3))
        start(TimerMode.Single(25))

        finish(taskDone = true)

        assertEquals(TaskStatus.PENDING, tasks.getById(3)?.status)
        assertNull(store.session.value)
    }

    @Test
    fun `cerrar si la tarea se borro mientras tanto no falla`() = runTest {
        tasks.save(task(3))
        start(TimerMode.Single(25), tasks.getById(3))
        tasks.delete(task(3))

        finish(taskDone = true)

        assertNull(store.session.value)
    }
}
