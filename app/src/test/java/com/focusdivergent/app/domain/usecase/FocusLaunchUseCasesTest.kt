package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.launch.FocusLaunchStore
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.BlockedApp
import com.focusdivergent.app.domain.model.FocusLaunch
import com.focusdivergent.app.domain.model.FocusLaunchPrompt
import com.focusdivergent.app.domain.model.FocusLaunchSession
import com.focusdivergent.app.domain.model.ObligationReminder
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.model.UsageSnapshot
import com.focusdivergent.app.domain.realitycheck.RealityCheckSilences
import com.focusdivergent.app.domain.reminder.ReminderSnoozes
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusLaunchUseCasesTest {

    private val minute = 60_000L
    private val social = "com.example.social"

    /** Almacén en memoria con las mismas reglas que el de preferencias. */
    private class FakeFocusLaunchStore(var session: FocusLaunchSession? = null) : FocusLaunchStore {
        override fun current() = session
        override fun save(session: FocusLaunchSession) {
            this.session = session
        }
        override fun clear() {
            session = null
        }
    }

    private fun clockAt(offsetSeconds: Long) = Clock.fixed(TEST_NOW.plusSeconds(offsetSeconds), ZoneOffset.UTC)

    private fun session(title: String? = "Informe", id: Long? = 5, startedAt: Instant = TEST_NOW) =
        FocusLaunchSession(id, title, startedAt, startedAt.plusSeconds(120))

    // ---- Empezar

    @Test
    fun `empezar guarda una sesion de exactamente 120 segundos con la tarea elegida`() {
        val store = FakeFocusLaunchStore()

        val started = StartFocusLaunchUseCase(store, TEST_CLOCK)(task(5, title = "Informe"))

        assertTrue(started)
        assertEquals(FocusLaunchSession(5, "Informe", TEST_NOW, TEST_NOW.plusSeconds(120)), store.session)
        assertEquals(120, FocusLaunchSession.DURATION_SECONDS)
    }

    @Test
    fun `se puede empezar sin una tarea concreta`() {
        val store = FakeFocusLaunchStore()

        assertTrue(StartFocusLaunchUseCase(store, TEST_CLOCK)(null))

        assertNull(store.session?.taskId)
        assertNull(store.session?.taskTitle)
    }

    @Test
    fun `no se puede empezar otra mientras hay una en curso o sin contestar`() {
        val running = session()
        val store = FakeFocusLaunchStore(running)

        assertFalse(StartFocusLaunchUseCase(store, clockAt(30))(task(9)))
        assertEquals(running, store.session)

        // Ya terminada pero sin contestar a «¿quieres seguir?»: tampoco.
        assertFalse(StartFocusLaunchUseCase(store, clockAt(500))(task(9)))
        assertEquals(running, store.session)
    }

    @Test
    fun `tras contestar se puede empezar otra`() {
        val store = FakeFocusLaunchStore(session())

        FinishFocusLaunchUseCase(store, FakeTasks(), testLedger()).stop()

        assertTrue(StartFocusLaunchUseCase(store, clockAt(500))(null))
    }

    // ---- Buscar

    @Test
    fun `sin sesion no hay nada que mostrar`() {
        assertNull(FindFocusLaunchUseCase(FakeFocusLaunchStore(), TEST_CLOCK)())
    }

    @Test
    fun `mientras dura se muestra el bloqueo, hasta el ultimo instante`() {
        val store = FakeFocusLaunchStore(session())

        for (offset in listOf(0L, 1L, 60L, 119L)) {
            val result = FindFocusLaunchUseCase(store, clockAt(offset))()
            assertEquals("a los $offset s", FocusLaunch("Informe", TEST_NOW, TEST_NOW.plusSeconds(120)), result)
        }
        // A 119,999 s sigue siendo bloqueo; a 120 s exactos ya es la pregunta.
        val almost = Clock.fixed(TEST_NOW.plusSeconds(120).minusMillis(1), ZoneOffset.UTC)
        assertTrue(FindFocusLaunchUseCase(store, almost)() is FocusLaunch)
    }

    @Test
    fun `al terminar pasa a la pregunta y se queda ahi hasta que se contesta, aunque pase mucho tiempo`() {
        val store = FakeFocusLaunchStore(session())

        for (offset in listOf(120L, 121L, 3_600L, 86_400L)) {
            val result = FindFocusLaunchUseCase(store, clockAt(offset))()
            assertEquals("a los $offset s", FocusLaunchPrompt(5, "Informe", TEST_NOW), result)
        }
    }

    @Test
    fun `cada sesion tiene su propia clave, para que el overlay se redibuje al pasar a la pregunta`() {
        val block = FocusLaunch("A", TEST_NOW, TEST_NOW.plusSeconds(120))
        val prompt = FocusLaunchPrompt(1, "A", TEST_NOW)
        val later = FocusLaunch("A", TEST_NOW.plusSeconds(500), TEST_NOW.plusSeconds(620))

        assertEquals(3, setOf(block.key, prompt.key, later.key).size)
    }

    // ---- Terminar

    @Test
    fun `seguir deja la tarea en marcha y cierra la sesion`() = runTest {
        val tasks = FakeTasks(listOf(task(5, title = "Informe")))
        val store = FakeFocusLaunchStore(session())

        FinishFocusLaunchUseCase(store, tasks, testLedger()).continueWork(5)

        assertEquals(TaskStatus.IN_PROGRESS, tasks.items.single().status)
        assertNull(store.session)
    }

    @Test
    fun `seguir sin tarea concreta solo cierra la sesion`() = runTest {
        val tasks = FakeTasks(listOf(task(5)))
        val store = FakeFocusLaunchStore(session(id = null, title = null))

        FinishFocusLaunchUseCase(store, tasks, testLedger()).continueWork(null)

        assertNull(store.session)
        assertEquals(TaskStatus.PENDING, tasks.items.single().status)
    }

    @Test
    fun `seguir con una tarea que ya no existe no falla y cierra la sesion`() = runTest {
        val store = FakeFocusLaunchStore(session(id = 404))

        FinishFocusLaunchUseCase(store, FakeTasks(), testLedger()).continueWork(404)

        assertNull(store.session)
    }

    @Test
    fun `seguir no toca una rutina, una obligacion ni algo ya hecho`() = runTest {
        val tasks = FakeTasks(
            listOf(
                task(1, type = TaskType.ROUTINE),
                task(2, type = TaskType.OBLIGATION, startsInMinutes = 60),
                task(3, status = TaskStatus.DONE, completedAt = TEST_NOW),
            ),
        )

        for (id in 1L..3L) FinishFocusLaunchUseCase(FakeFocusLaunchStore(session(id = id)), tasks, testLedger()).continueWork(id)

        assertEquals(listOf(TaskStatus.PENDING, TaskStatus.PENDING, TaskStatus.DONE), tasks.items.map { it.status })
        assertEquals(TEST_NOW, tasks.items[2].completedAt)
    }

    @Test
    fun `dejarlo por ahora cierra la sesion y no toca nada mas`() = runTest {
        val tasks = FakeTasks(listOf(task(5)))
        val store = FakeFocusLaunchStore(session())

        FinishFocusLaunchUseCase(store, tasks, testLedger()).stop()

        assertNull(store.session)
        assertEquals(TaskStatus.PENDING, tasks.items.single().status)
        assertNull(tasks.items.single().completedAt)
    }

    // ---- Prioridad en el punto unico de decision

    private class Setup(
        store: FakeFocusLaunchStore,
        snapshot: UsageSnapshot,
        device: FakeDeviceState = FakeDeviceState(),
        tasks: List<Task> = emptyList(),
        clock: Clock = TEST_CLOCK,
    ) {
        private val limits = FakeAppLimits(listOf(AppLimit("com.example.social", 10)))
        val useCase = EvaluateInterruptionUseCase(
            usage = FakeUsage(snapshot),
            device = device,
            findObligationReminder = FindObligationReminderUseCase(FakeTasks(tasks), ReminderSnoozes(), clock),
            findFocusLaunch = FindFocusLaunchUseCase(store, clock),
            findBlockedApp = FindBlockedAppUseCase(limits, FakeTimeExtensions(), FakeDayClosureSettings(), clock, "com.focusdivergent.app"),
            findRealityCheck = FindRealityCheckUseCase(limits, RealityCheckSilences(), FakeRealityCheckSettings(), clock, "com.focusdivergent.app"),
        )
    }

    // Una app agotada y con 25 minutos seguidos: sin lanzadera, saldría el bloqueo.
    private val exhausted = UsageSnapshot(social, mapOf(social to 12 * minute), foregroundSessionMillis = 25 * minute)

    @Test
    fun `la lanzadera cubre todo el movil y tiene prioridad sobre el bloqueo de una app y el Reality Check`() = runTest {
        val result = Setup(FakeFocusLaunchStore(session()), exhausted).useCase()

        assertTrue(result is FocusLaunch)
        assertTrue(Setup(FakeFocusLaunchStore(), exhausted).useCase() is BlockedApp)
    }

    @Test
    fun `el aviso de una obligacion tiene prioridad sobre la lanzadera`() = runTest {
        val result = Setup(FakeFocusLaunchStore(session()), exhausted, tasks = listOf(obligation(1, startsInMinutes = 6))).useCase()

        assertTrue(result is ObligationReminder)
    }

    @Test
    fun `con la pantalla apagada o en una llamada la lanzadera se aparta`() = runTest {
        val store = FakeFocusLaunchStore(session())

        assertNull(Setup(store, exhausted, FakeDeviceState(interactive = false)).useCase())
        assertNull(Setup(store, exhausted, FakeDeviceState(inCall = true)).useCase())
        assertNull(Setup(store, exhausted, FakeDeviceState(safe = setOf(social))).useCase())
    }

    @Test
    fun `si se apago la pantalla durante la cuenta, al volver ya es la pregunta`() = runTest {
        val store = FakeFocusLaunchStore(session())

        val whileOff = Setup(store, exhausted, FakeDeviceState(interactive = false), clock = clockAt(60)).useCase()
        val backOn = Setup(store, exhausted, clock = clockAt(300)).useCase()

        assertNull(whileOff)
        assertTrue(backOn is FocusLaunchPrompt)
    }

    @Test
    fun `contestada la pregunta, el motor vuelve a su comportamiento normal`() = runTest {
        val store = FakeFocusLaunchStore(session())
        FinishFocusLaunchUseCase(store, FakeTasks(), testLedger()).stop()

        assertTrue(Setup(store, exhausted, clock = clockAt(300)).useCase() is BlockedApp)
    }
}
