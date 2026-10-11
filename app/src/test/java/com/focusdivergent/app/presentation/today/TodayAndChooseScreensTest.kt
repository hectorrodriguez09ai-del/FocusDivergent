package com.focusdivergent.app.presentation.today

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.tasks.AgendaItem
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
import com.focusdivergent.app.presentation.chooseforme.ChooseForMeContent
import com.focusdivergent.app.presentation.chooseforme.ChooseForMeUiState
import com.focusdivergent.app.presentation.chooseforme.ChooseStep
import com.focusdivergent.app.presentation.common.formatFull
import com.focusdivergent.app.presentation.common.formatShort
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Hoy» y «Elige por mí», dibujadas con estado fijo (sin ViewModel ni base de datos). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class TodayAndChooseScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun item(id: Long, title: String = "Actividad $id", done: Boolean = false) =
        AgendaItem(task(id, title = title), isDone = done, date = TEST_TODAY, time = null)

    // ---- Hoy

    private fun today(
        state: TodayUiState,
        onChooseForMe: () -> Unit = {},
        onLaunch: () -> Unit = {},
        onEnergy: (EnergyLevel) -> Unit = {},
        onNewTask: () -> Unit = {},
        onSeeAllTasks: () -> Unit = {},
        onTimer: () -> Unit = {},
        onAudio: () -> Unit = {},
        onLeaveForLater: (AgendaItem) -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            TodayContent(
                uiState = state,
                onChooseForMe = onChooseForMe,
                onLaunch = onLaunch,
                onEnergy = onEnergy,
                onEditTask = {},
                onNewTask = onNewTask,
                onSeeAllTasks = onSeeAllTasks,
                onTimer = onTimer,
                onAudio = onAudio,
                onSetDone = { _, _ -> },
                onLeaveForLater = onLeaveForLater,
            )
        }
    }

    private fun todayState(
        energy: EnergyLevel? = null,
        now: List<AgendaItem> = emptyList(),
        obligation: AgendaItem? = null,
        items: List<AgendaItem> = emptyList(),
        pending: Int = items.size,
        done: Int = 0,
        hasAny: Boolean = true,
    ) = TodayUiState(
        today = TEST_TODAY,
        energy = energy,
        nowItems = now,
        nextObligation = obligation,
        todayItems = items,
        todayPending = pending,
        doneToday = done,
        hasAnyTask = hasAny,
        loading = false,
    )

    @Test
    fun `Hoy muestra la fecha, la energia y el boton grande de Elige por mi`() {
        var chosen = 0
        today(todayState(energy = EnergyLevel.MEDIUM, items = listOf(item(1))), onChooseForMe = { chosen++ })

        compose.onNodeWithText(TEST_TODAY.formatFull().replaceFirstChar { it.titlecase() }).assertExists()
        compose.onNodeWithText("¿Con cuánta energía estás hoy?").assertExists()
        compose.onNodeWithText("Media").assertIsSelected()
        compose.onNodeWithText("Te propongo una sola cosa para empezar.").assertExists()

        compose.onNodeWithText("Elige por mí").performClick()
        assertEquals(1, chosen)
    }

    @Test
    fun `Hoy tiene una tarjeta de Enfoque que lleva al temporizador`() {
        var opened = 0
        today(todayState(items = listOf(item(1))), onTimer = { opened++ })

        compose.onNodeWithText("Enfoque").assertExists()
        compose.onNodeWithText("Una cuenta atrás visual, o Pomodoro.").assertExists()
        compose.onNodeWithText("Temporizador").performScrollTo().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `la tarjeta de Enfoque tambien lleva al sonido ambiente`() {
        var opened = 0
        today(todayState(items = listOf(item(1))), onAudio = { opened++ })

        compose.onNodeWithText("Ruido blanco, marrón, binaural o lluvia, sin conexión.").assertExists()
        compose.onNodeWithText("Sonido ambiente").performScrollTo().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `la tarjeta del sonido dice si suena o esta en pausa`() {
        today(todayState(items = listOf(item(1))).copy(audioActive = true, audioPlaying = true))
        compose.onNodeWithText("Sonando. Toca para verlo.").assertExists()
    }

    @Test
    fun `la tarjeta del sonido en pausa lo dice`() {
        today(todayState(items = listOf(item(1))).copy(audioActive = true, audioPlaying = false))
        compose.onNodeWithText("En pausa. Toca para verlo.").assertExists()
    }

    @Test
    fun `con una sesion en curso la tarjeta lo dice`() {
        today(todayState(items = listOf(item(1))).copy(timerActive = true))

        compose.onNodeWithText("En marcha. Toca para verlo.").assertExists()
        compose.onNodeWithText("Una cuenta atrás visual, o Pomodoro.").assertDoesNotExist()
    }

    @Test
    fun `Hoy ofrece la lanzadera de 2 minutos con su explicacion`() {
        var launched = 0
        today(todayState(items = listOf(item(1))), onLaunch = { launched++ })

        compose.onNodeWithText("Si te cuesta arrancar: 2 minutos sin móvil y empiezas.").assertExists()
        compose.onNodeWithText("Lanzadera de 2 minutos").performClick()

        assertEquals(1, launched)
    }

    @Test
    fun `la lanzadera no le quita protagonismo a Elige por mi, que sigue siendo el boton grande`() {
        var chosen = 0
        var launched = 0
        today(todayState(), onChooseForMe = { chosen++ }, onLaunch = { launched++ })

        compose.onNodeWithText("Elige por mí").performClick()

        assertEquals(1, chosen)
        assertEquals(0, launched)
    }

    @Test
    fun `elegir una energia avisa`() {
        var energy: EnergyLevel? = null
        today(todayState(), onEnergy = { energy = it })

        compose.onNodeWithText("Alta").performClick()

        assertEquals(EnergyLevel.HIGH, energy)
    }

    @Test
    fun `sin ninguna actividad invita a crear la primera`() {
        var created = 0
        today(todayState(hasAny = false), onNewTask = { created++ })

        compose.onNodeWithText("Tu día empieza en blanco").performScrollTo().assertExists()
        compose.onNodeWithText("Crear la primera").performScrollTo().performClick()
        assertEquals(1, created)
    }

    @Test
    fun `con actividades pero ninguna para hoy lo dice con calma`() {
        today(todayState(hasAny = true))

        compose.onNodeWithText("Nada pendiente para hoy").performScrollTo().assertExists()
        compose.onNodeWithText("Tu día empieza en blanco").assertDoesNotExist()
    }

    @Test
    fun `la lista de hoy muestra las actividades y Ver todas solo si hay mas`() {
        var seeAll = 0
        today(todayState(items = listOf(item(1, "Tender"), item(2, "Planchar"), item(3, "Fregar")), pending = 5), onSeeAllTasks = { seeAll++ })

        compose.onNodeWithText("Para hoy").assertExists()
        compose.onNodeWithText("Tender").assertExists()
        compose.onNodeWithText("Fregar").assertExists()
        compose.onNodeWithText("Ver todas en Tareas").performScrollTo().performClick()
        assertEquals(1, seeAll)
    }

    @Test
    fun `sin mas actividades no aparece Ver todas`() {
        today(todayState(items = listOf(item(1)), pending = 1))

        compose.onNodeWithText("Ver todas en Tareas").assertDoesNotExist()
    }

    @Test
    fun `la proxima obligacion se muestra con su hora`() {
        val obligation = AgendaItem(
            task(9, title = "Dentista", type = TaskType.OBLIGATION, startsInMinutes = 90),
            isDone = false,
            date = TEST_TODAY,
            time = LocalTime.of(13, 30),
        )
        today(todayState(obligation = obligation))

        compose.onNodeWithText("Próxima obligación").assertExists()
        compose.onNodeWithText("Dentista").assertExists()
        compose.onNodeWithText("a las ${LocalTime.of(13, 30).formatShort()}").assertExists()
    }

    @Test
    fun `lo que esta en marcha va en Ahora y se puede dejar para luego`() {
        var left: AgendaItem? = null
        val running = item(4, "Informe")
        today(todayState(now = listOf(running)), onLeaveForLater = { left = it })

        compose.onNodeWithText("Ahora").assertExists()
        compose.onNodeWithText("Informe").assertExists()
        compose.onNodeWithText("Dejar para luego").performClick()

        assertEquals(running, left)
    }

    @Test
    fun `lo hecho hoy se reconoce con un numero y sin porcentajes`() {
        today(todayState(items = listOf(item(1)), done = 3))

        compose.onNodeWithText("Hoy llevas 3 hechas. Bien.").performScrollTo().assertExists()
    }

    @Test
    fun `sin nada hecho no hay contador`() {
        today(todayState(items = listOf(item(1)), done = 0))

        compose.onNodeWithText("Hoy llevas", substring = true).assertDoesNotExist()
    }

    // ---- Elige por mí

    private fun choose(
        state: ChooseForMeUiState,
        onEnergy: (EnergyLevel) -> Unit = {},
        onTime: (Int?) -> Unit = {},
        onStart: () -> Unit = {},
        onAnother: () -> Unit = {},
        onSeeList: () -> Unit = {},
        onCreateTask: () -> Unit = {},
        onChangeEnergy: () -> Unit = {},
        onBackToTime: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            ChooseForMeContent(
                uiState = state,
                onBack = {},
                onEnergy = onEnergy,
                onChangeEnergy = onChangeEnergy,
                onTime = onTime,
                onBackToTime = onBackToTime,
                onStart = onStart,
                onAnother = onAnother,
                onSeeList = onSeeList,
                onCreateTask = onCreateTask,
            )
        }
    }

    @Test
    fun `la primera pregunta es la energia y tocar un nivel contesta`() {
        var energy: EnergyLevel? = null
        choose(ChooseForMeUiState(step = ChooseStep.ENERGY), onEnergy = { energy = it })

        compose.onNodeWithText("¿Con cuánta energía estás ahora?").assertExists()
        compose.onNodeWithText("Baja").performClick()

        assertEquals(EnergyLevel.LOW, energy)
    }

    @Test
    fun `la pregunta del tiempo ofrece cinco opciones, la ultima sin prisa`() {
        val chosen = mutableListOf<Int?>()
        choose(ChooseForMeUiState(step = ChooseStep.TIME, energy = EnergyLevel.MEDIUM), onTime = { chosen += it })

        compose.onNodeWithText("¿De cuánto tiempo dispones?").assertExists()
        compose.onNodeWithText("5 min").performClick()
        compose.onNodeWithText("15 min").performClick()
        compose.onNodeWithText("30 min").performClick()
        compose.onNodeWithText("1 hora").performClick()
        compose.onNodeWithText("Sin prisa").performClick()

        assertEquals(listOf(5, 15, 30, 60, null), chosen)
    }

    @Test
    fun `en la pregunta del tiempo se ve la energia de hoy y se puede cambiar`() {
        var changed = 0
        choose(ChooseForMeUiState(step = ChooseStep.TIME, energy = EnergyLevel.HIGH), onChangeEnergy = { changed++ })

        compose.onNodeWithText("Energía de hoy: Alta · cambiar").performScrollTo().performClick()

        assertEquals(1, changed)
    }

    @Test
    fun `la propuesta es una sola actividad con Empezar y Otra`() {
        var started = 0
        var another = 0
        val pick = task(3, title = "Llamar a la gestoría", energy = EnergyLevel.LOW, estimatedMinutes = 15)
        choose(
            ChooseForMeUiState(step = ChooseStep.RESULT, energy = EnergyLevel.MEDIUM, pick = pick, canAskAnother = true),
            onStart = { started++ },
            onAnother = { another++ },
        )

        compose.onNodeWithText("Empieza por esto").assertExists()
        compose.onNodeWithText("Llamar a la gestoría").assertExists()
        compose.onNodeWithText("Tarea · 15 min").assertExists()
        compose.onNodeWithContentDescription("Energía: Baja").assertExists()

        compose.onNodeWithText("Empezar").performClick()
        compose.onNodeWithText("Otra").performClick()

        assertEquals(1, started)
        assertEquals(1, another)
        compose.onNodeWithText("Ver mi lista").assertDoesNotExist()
    }

    @Test
    fun `agotados los cambios Otra se convierte en Ver mi lista`() {
        var seeList = 0
        choose(
            ChooseForMeUiState(step = ChooseStep.RESULT, energy = EnergyLevel.MEDIUM, pick = task(1), canAskAnother = false),
            onSeeList = { seeList++ },
        )

        compose.onNodeWithText("Otra").assertDoesNotExist()
        compose.onNodeWithText("Ver mi lista").performClick()

        assertEquals(1, seeList)
    }

    @Test
    fun `si nada encaja ofrece crear una tarea, probar otro tiempo o cambiar la energia`() {
        var created = 0
        var retried = 0
        var changed = 0
        choose(
            ChooseForMeUiState(step = ChooseStep.NONE_FOUND, energy = EnergyLevel.LOW),
            onCreateTask = { created++ },
            onBackToTime = { retried++ },
            onChangeEnergy = { changed++ },
        )

        compose.onNodeWithText("Ahora mismo nada encaja").assertExists()
        compose.onNodeWithText("Crear una tarea rápida").performClick()
        compose.onNodeWithText("Probar con otro tiempo").performClick()
        compose.onNodeWithText("Cambiar la energía").performClick()

        assertEquals(listOf(1, 1, 1), listOf(created, retried, changed))
    }
}
