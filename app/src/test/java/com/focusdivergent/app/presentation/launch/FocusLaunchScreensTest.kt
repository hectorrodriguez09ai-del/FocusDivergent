package com.focusdivergent.app.presentation.launch

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.focusdivergent.app.core.permissions.PermissionManager
import com.focusdivergent.app.data.settings.SharedPreferencesFocusLaunchStore
import com.focusdivergent.app.domain.model.FocusLaunchSession
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.usecase.FakeRoutineCompletions
import com.focusdivergent.app.domain.usecase.FakeTasks
import com.focusdivergent.app.domain.usecase.StartFocusLaunchUseCase
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.usecase.TEST_NOW
import com.focusdivergent.app.domain.usecase.task
import com.focusdivergent.app.presentation.MainDispatcherRule
import com.focusdivergent.app.presentation.block.FocusLaunchContent
import com.focusdivergent.app.presentation.block.FocusLaunchPromptContent
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import com.focusdivergent.app.service.BlockCheckTrigger
import com.focusdivergent.app.service.BlockerStatus
import java.time.Instant
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class FocusLaunchScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val tasks = FakeTasks()
    private val completions = FakeRoutineCompletions()
    private val status = BlockerStatus()
    private val trigger = BlockCheckTrigger()
    private val store = SharedPreferencesFocusLaunchStore(context)

    @Before
    fun clean() {
        store.clear()
        ShadowSettings.setCanDrawOverlays(true)
    }

    private fun viewModel() = FocusLaunchViewModel(
        tasks = tasks,
        completions = completions,
        blockerStatus = status,
        permissions = PermissionManager(context),
        startFocusLaunch = StartFocusLaunchUseCase(store, TEST_CLOCK),
        trigger = trigger,
        clock = TEST_CLOCK,
    )

    private fun kotlinx.coroutines.test.TestScope.observe(vm: FocusLaunchViewModel) {
        backgroundScope.launch(mainDispatcher.dispatcher) { vm.uiState.collect() }
    }

    // ---- ViewModel

    @Test
    fun `se sugiere por defecto lo que ya esta en marcha y luego lo de hoy`() = runTest {
        tasks.save(task(1, title = "De hoy", startsInMinutes = 60))
        tasks.save(task(2, title = "Ya empezada", status = TaskStatus.IN_PROGRESS, startsInMinutes = 60))
        tasks.save(task(3, title = "Cita", type = TaskType.OBLIGATION, startsInMinutes = 90))
        status.setRunning(true)
        val vm = viewModel().also { observe(it) }

        val state = vm.uiState.value

        assertEquals(listOf("Ya empezada", "De hoy"), state.candidates.map { it.title })
        assertEquals(2L, state.selectedId)
        assertTrue(state.ready)
    }

    @Test
    fun `se puede elegir otra actividad o lo que sea`() = runTest {
        tasks.save(task(1))
        tasks.save(task(2))
        status.setRunning(true)
        val vm = viewModel().also { observe(it) }

        vm.select(2)
        assertEquals(2L, vm.uiState.value.selectedId)

        vm.select(null)
        assertNull(vm.uiState.value.selectedId)
    }

    @Test
    fun `como mucho se ofrecen cuatro actividades`() = runTest {
        repeat(9) { tasks.save(task(it + 1L)) }
        val vm = viewModel().also { observe(it) }

        assertEquals(4, vm.uiState.value.candidates.size)
    }

    @Test
    fun `sin la proteccion activa o sin permiso de superposicion no esta lista`() = runTest {
        val vm = viewModel().also { observe(it) }
        assertFalse("protección pausada", vm.uiState.value.ready)

        status.setRunning(true)
        assertTrue(vm.uiState.value.ready)

        ShadowSettings.setCanDrawOverlays(false)
        vm.refreshPermissions()
        assertFalse("sin permiso", vm.uiState.value.ready)
    }

    @Test
    fun `empezar guarda la sesion con la tarea elegida, avisa al servicio y vuelve a Hoy`() = runTest {
        tasks.save(task(7, title = "Informe"))
        status.setRunning(true)
        val vm = viewModel().also { observe(it) }

        vm.start()

        val session = store.current()!!
        assertEquals(7L, session.taskId)
        assertEquals("Informe", session.taskTitle)
        assertEquals(TEST_NOW.plusSeconds(120), session.endsAt)
        assertEquals(FocusLaunchEvent.Started, withTimeoutOrNull(100) { vm.events.first() })
        // El servicio recibe el aviso para evaluar al instante.
        assertNotNull(withTimeoutOrNull(100) { trigger.events.first() })
    }

    @Test
    fun `empezar sin lista no hace nada, ni guarda ni avisa`() = runTest {
        val vm = viewModel().also { observe(it) }

        vm.start()

        assertNull(store.current())
        assertNull(withTimeoutOrNull(100) { vm.events.first() })
    }

    @Test
    fun `no se empieza una segunda lanzadera encima de otra`() = runTest {
        tasks.save(task(1))
        status.setRunning(true)
        val first = FocusLaunchSession(1, "Ya en curso", TEST_NOW, TEST_NOW.plusSeconds(120))
        store.save(first)
        val vm = viewModel().also { observe(it) }

        vm.start()

        assertEquals(first, store.current())
        assertNull(withTimeoutOrNull(100) { vm.events.first() })
    }

    // ---- Almacén

    @Test
    fun `el almacen guarda la sesion y la recupera tal cual, tambien al reabrir la app`() {
        val session = FocusLaunchSession(5, "Informe", TEST_NOW, TEST_NOW.plusSeconds(120))

        store.save(session)

        assertEquals(session, store.current())
        assertEquals(session, SharedPreferencesFocusLaunchStore(context).current())
    }

    @Test
    fun `una sesion sin tarea concreta se guarda con la tarea nula`() {
        val session = FocusLaunchSession(null, null, TEST_NOW, TEST_NOW.plusSeconds(120))

        store.save(session)

        assertEquals(session, store.current())
        assertNull(store.current()?.taskId)
    }

    @Test
    fun `guardar sustituye la sesion anterior sin dejar restos de la tarea`() {
        store.save(FocusLaunchSession(5, "Informe", TEST_NOW, TEST_NOW.plusSeconds(120)))

        store.save(FocusLaunchSession(null, null, TEST_NOW.plusSeconds(500), TEST_NOW.plusSeconds(620)))

        assertNull(store.current()?.taskId)
        assertNull(store.current()?.taskTitle)
    }

    @Test
    fun `sin nada guardado no hay sesion, y borrar la quita`() {
        assertNull(store.current())

        store.save(FocusLaunchSession(1, "A", TEST_NOW, TEST_NOW.plusSeconds(120)))
        store.clear()

        assertNull(store.current())
    }

    // ---- Pantalla de preparar

    private fun setup(
        state: FocusLaunchUiState,
        onSelect: (Long?) -> Unit = {},
        onStart: () -> Unit = {},
        onOpenPermissions: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            FocusLaunchSetup(state, onBack = {}, onSelect = onSelect, onStart = onStart, onOpenPermissions = onOpenPermissions)
        }
    }

    @Test
    fun `lista para lanzar explica lo que va a pasar y deja elegir con la sugerida marcada`() {
        var started = 0
        var selected: Long? = -1
        setup(
            FocusLaunchUiState(
                candidates = listOf(task(1, title = "Informe"), task(2, title = "Fregar")),
                selectedId = 1,
                protectionRunning = true,
                overlayGranted = true,
                loading = false,
            ),
            onSelect = { selected = it },
            onStart = { started++ },
        )

        compose.onNodeWithText("Durante 2 minutos el móvil se bloquea para que empieces sin distracciones. Cuando acabe, decides si sigues.").assertExists()
        compose.onNodeWithText("Informe").assertIsSelected()
        compose.onNodeWithText("Fregar").performClick()
        assertEquals(2L, selected)
        compose.onNodeWithText("Lo que sea, solo empezar").performClick()
        assertNull(selected)

        compose.onNodeWithText("No se puede cerrar antes de tiempo. Si suena una llamada, se aparta sola.").performScrollTo().assertExists()
        compose.onNodeWithText("Empezar 2 minutos").performScrollTo().performClick()
        assertEquals(1, started)
    }

    @Test
    fun `sin proteccion no ofrece lanzar, lo explica y lleva a revisarla`() {
        var permissions = 0
        setup(FocusLaunchUiState(protectionRunning = false, overlayGranted = true, loading = false), onOpenPermissions = { permissions++ })

        compose.onNodeWithText("Necesita la protección activa").assertExists()
        compose.onNodeWithText("Empezar 2 minutos").assertDoesNotExist()
        compose.onNodeWithText("Revisar la protección").performClick()
        assertEquals(1, permissions)
    }

    // ---- Overlay: bloqueo y pregunta

    @Test
    fun `el bloqueo muestra la tarea y la cuenta atras`() {
        compose.setContent {
            FocusDivergentTheme {
                FocusLaunchContent(taskTitle = "Informe", endsAt = Instant.now().plusSeconds(100), onFinished = {})
            }
        }

        compose.onNodeWithText("Empieza ya").assertExists()
        compose.onNodeWithText("Informe").assertExists()
        compose.onNodeWithText("Nada más que esto durante dos minutos. Después decides.").assertExists()
        // El lector de pantalla oye «Quedan 1:40», no cuatro caracteres sueltos.
        compose.onNodeWithContentDescription("Quedan", substring = true).assertExists()
    }

    @Test
    fun `sin tarea concreta el bloqueo solo muestra la cuenta atras`() {
        compose.setContent {
            FocusDivergentTheme { FocusLaunchContent(taskTitle = null, endsAt = Instant.now().plusSeconds(100), onFinished = {}) }
        }

        compose.onNodeWithText("Empieza ya").assertExists()
        compose.onNodeWithContentDescription("Quedan", substring = true).assertExists()
    }

    @Test
    fun `al llegar la cuenta a cero avisa una sola vez`() {
        var finished = 0
        compose.setContent {
            FocusDivergentTheme {
                FocusLaunchContent(taskTitle = "Informe", endsAt = Instant.now().minusSeconds(1), onFinished = { finished++ })
            }
        }

        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)

        assertEquals(1, finished)
    }

    @Test
    fun `la pregunta final ofrece seguir o dejarlo, con las dos salidas a la vista y sin reproches`() {
        var continued = 0
        var stopped = 0
        compose.setContent {
            FocusDivergentTheme { FocusLaunchPromptContent("Informe", onContinue = { continued++ }, onStop = { stopped++ }) }
        }

        compose.onNodeWithText("Ya pasaron los 2 minutos").assertExists()
        compose.onNodeWithText("¿Quieres seguir con «Informe»?").assertExists()
        compose.onNodeWithText("Haber empezado ya cuenta.").assertExists()

        compose.onNodeWithText("Seguir").performClick()
        compose.onNodeWithText("Lo dejo por ahora").performClick()

        assertEquals(1, continued)
        assertEquals(1, stopped)
    }

    @Test
    fun `sin tarea concreta la pregunta final es generica`() {
        compose.setContent {
            FocusDivergentTheme { FocusLaunchPromptContent(null, onContinue = {}, onStop = {}) }
        }

        compose.onNodeWithText("¿Quieres seguir con lo que empezaste?").assertExists()
    }
}
