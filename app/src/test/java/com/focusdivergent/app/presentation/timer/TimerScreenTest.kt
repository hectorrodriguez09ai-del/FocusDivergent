package com.focusdivergent.app.presentation.timer

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.focusdivergent.app.domain.timer.TimerLimits
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerPlan
import com.focusdivergent.app.domain.timer.TimerSession
import com.focusdivergent.app.domain.usecase.TEST_NOW
import com.focusdivergent.app.domain.usecase.task
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class TimerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setup(kind: TimerKind = TimerKind.SINGLE, taskTitle: String? = null) = TimerUiState(
        kind = kind,
        single = TimerLimits.DEFAULT_SINGLE,
        pomodoro = TimerLimits.DEFAULT_POMODORO,
        task = taskTitle?.let { task(1, title = it) },
    )

    private fun running(mode: TimerMode, elapsedMinutes: Long, taskId: Long? = null, taskTitle: String? = null): TimerUiState {
        val session = TimerSession(mode, taskId, taskTitle, TEST_NOW)
        return TimerUiState(
            single = TimerLimits.DEFAULT_SINGLE,
            pomodoro = TimerLimits.DEFAULT_POMODORO,
            session = session,
            state = TimerPlan.stateAt(session, TEST_NOW.plus(Duration.ofMinutes(elapsedMinutes))),
        )
    }

    private fun show(
        state: TimerUiState,
        onKind: (TimerKind) -> Unit = {},
        onSingle: (Int) -> Unit = {},
        onFocus: (Int) -> Unit = {},
        onCycles: (Int) -> Unit = {},
        onStart: () -> Unit = {},
        onStop: () -> Unit = {},
        onFinish: (Boolean) -> Unit = {},
        onBack: () -> Unit = {},
        onAudio: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            TimerContent(
                uiState = state,
                onBack = onBack,
                onKind = onKind,
                onSingleMinutes = onSingle,
                onFocusMinutes = onFocus,
                onBreakMinutes = {},
                onCycles = onCycles,
                onStart = onStart,
                onStop = onStop,
                onFinish = onFinish,
                onAudio = onAudio,
            )
        }
    }

    // ---- Elegir

    @Test
    fun `elegir enseña los dos modos, la duracion y el boton de empezar`() {
        var started = 0
        show(setup(), onStart = { started++ })

        compose.onNodeWithText("Un temporizador").assertExists()
        compose.onNodeWithText("Pomodoro").assertExists()
        compose.onNodeWithText("¿Cuánto tiempo?").assertExists()
        compose.onNodeWithText("Sonará un aviso al acabar cada fase.").assertExists()
        compose.onNodeWithText("Empezar").performScrollTo().performClick()

        assertEquals(1, started)
    }

    @Test
    fun `se puede cambiar de modo y ajustar los minutos`() {
        val kinds = mutableListOf<TimerKind>()
        val minutes = mutableListOf<Int>()
        show(setup(), onKind = { kinds += it }, onSingle = { minutes += it })

        compose.onNodeWithText("Pomodoro").performClick()
        compose.onNodeWithContentDescription("Añadir 5 minutos").performClick()
        compose.onNodeWithText("45 min").performClick()

        assertEquals(listOf(TimerKind.POMODORO), kinds)
        assertEquals(listOf(30, 45), minutes)
    }

    @Test
    fun `el Pomodoro resume los bloques y el tiempo total, y los bloques se ajustan con mas y menos`() {
        val cycles = mutableListOf<Int>()
        show(setup(TimerKind.POMODORO), onCycles = { cycles += it })

        compose.onNodeWithText("Bloques de enfoque").assertExists()
        compose.onNodeWithText("3 bloques de 45 min con descansos de 20 min. En total, 2 h 55 min.").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Un bloque más").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Un bloque menos").performScrollTo().performClick()

        assertEquals(listOf(4, 2), cycles)
    }

    @Test
    fun `desde una tarea se dice para cual es`() {
        show(setup(taskTitle = "Informe de octubre"))
        compose.onNodeWithText("Para: «Informe de octubre»").assertExists()
    }

    // ---- En marcha

    @Test
    fun `en marcha enseña la fase, las cifras de la cuenta atras, lo que viene despues y parar`() {
        var stopped = 0
        show(running(TimerLimits.DEFAULT_POMODORO, elapsedMinutes = 0), onStop = { stopped++ })

        compose.onNodeWithText("Enfoque 1 de 3").assertExists()
        compose.onNodeWithText("45:00").assertExists()
        compose.onNodeWithContentDescription("Quedan 45 minutos").assertExists()
        compose.onNodeWithText("Después: descanso de 20 min").assertExists()
        compose.onNodeWithText("Parar").performScrollTo().performClick()

        assertEquals(1, stopped)
    }

    @Test
    fun `durante el descanso se dice descanso y lo que viene es el siguiente bloque`() {
        show(running(TimerLimits.DEFAULT_POMODORO, elapsedMinutes = 50))

        compose.onNodeWithText("Descanso").assertExists()
        compose.onNodeWithText("15:00").assertExists()
        compose.onNodeWithText("Después: enfoque de 45 min").assertExists()
    }

    @Test
    fun `un temporizador suelto no numera los bloques y avisa de que es el ultimo tramo`() {
        show(running(TimerMode.Single(25), elapsedMinutes = 10, taskTitle = "Informe", taskId = 1))

        compose.onNodeWithText("Enfoque").assertExists()
        compose.onNodeWithText("Informe").assertExists()
        compose.onNodeWithText("15:00").assertExists()
        compose.onNodeWithText("Es el último tramo.").assertExists()
    }

    @Test
    fun `las horas se enseñan cuando la cuenta pasa de los 60 minutos`() {
        show(running(TimerMode.Single(90), elapsedMinutes = 0))
        compose.onNodeWithText("1:30:00").assertExists()
    }

    @Test
    fun `desde el temporizador se llega al sonido ambiente, eligiendo y en marcha`() {
        var opened = 0
        show(setup(), onAudio = { opened++ })
        compose.onNodeWithText("Poner sonido ambiente").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `en marcha tambien hay acceso al sonido ambiente`() {
        var opened = 0
        show(running(TimerMode.Single(25), elapsedMinutes = 5), onAudio = { opened++ })
        compose.onNodeWithText("Poner sonido ambiente").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    // ---- Terminada

    @Test
    fun `al terminar una sesion con tarea se pregunta con calma y las dos respuestas pesan igual`() {
        val answers = mutableListOf<Boolean>()
        show(running(TimerMode.Single(25), elapsedMinutes = 30, taskId = 1, taskTitle = "Informe"), onFinish = { answers += it })

        compose.onNodeWithText("Sesión terminada").assertExists()
        compose.onNodeWithText("Haber dedicado este tiempo ya cuenta.").assertExists()
        compose.onNodeWithText("¿Terminaste «Informe»?").assertExists()
        compose.onNodeWithText("Sí, está hecha").performScrollTo().performClick()
        compose.onNodeWithText("Todavía no").performScrollTo().performClick()

        assertEquals(listOf(true, false), answers)
    }

    @Test
    fun `al terminar una sesion suelta solo se cierra`() {
        val answers = mutableListOf<Boolean>()
        show(running(TimerMode.Single(25), elapsedMinutes = 30), onFinish = { answers += it })

        compose.onNodeWithText("¿Terminaste", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Cerrar").performScrollTo().performClick()

        assertEquals(listOf(false), answers)
    }

    // ---- Formato

    @Test
    fun `las cifras tienen siempre dos digitos y las horas solo aparecen si hacen falta`() {
        assertEquals("00:00", formatClock(0))
        assertEquals("00:09", formatClock(9))
        assertEquals("12:34", formatClock(12 * 60 + 34))
        assertEquals("59:59", formatClock(3599))
        assertEquals("1:00:00", formatClock(3600))
        assertEquals("2:05:09", formatClock(2 * 3600 + 5 * 60 + 9))
        assertEquals("00:00", formatClock(-5))
    }

    @Test
    fun `los minutos dichos en voz alta se redondean hacia arriba`() {
        assertEquals(1, spokenMinutes(1))
        assertEquals(1, spokenMinutes(60))
        assertEquals(13, spokenMinutes(12 * 60 + 5))
        assertEquals(0, spokenMinutes(0))
    }
}
