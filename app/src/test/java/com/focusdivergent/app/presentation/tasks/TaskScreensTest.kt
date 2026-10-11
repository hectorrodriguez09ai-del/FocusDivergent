package com.focusdivergent.app.presentation.tasks

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.tasks.AgendaItem
import com.focusdivergent.app.domain.tasks.TaskAgenda
import com.focusdivergent.app.domain.tasks.TaskDraft
import com.focusdivergent.app.domain.tasks.TaskFilter
import com.focusdivergent.app.domain.tasks.TaskProblem
import com.focusdivergent.app.domain.tasks.TaskSection
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
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

/** Las pantallas de tareas, dibujadas con estado fijo (sin ViewModel ni base de datos). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class TaskScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun editor(
        state: TaskEditorUiState,
        onType: (TaskType) -> Unit = {},
        onToggleDetails: () -> Unit = {},
        onSave: () -> Unit = {},
        onDelete: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            TaskEditorContent(
                uiState = state,
                onBack = {},
                onTitle = {},
                onType = onType,
                onEnergy = {},
                onDate = {},
                onTime = {},
                onDuration = {},
                onToggleDetails = onToggleDetails,
                onWeekly = {},
                onToggleWeekDay = {},
                onSave = onSave,
                onDelete = onDelete,
            )
        }
    }

    private fun editorState(draft: TaskDraft = TaskDraft(), problems: Set<TaskProblem> = emptySet(), details: Boolean = false) =
        TaskEditorUiState(draft = draft, today = TEST_TODAY, problems = problems, detailsExpanded = details, loading = false)

    @Test
    fun `una tarea nueva solo pide el titulo y esconde los detalles opcionales`() {
        editor(editorState())

        compose.onNodeWithText("Nueva actividad").assertExists()
        compose.onNodeWithText("¿Qué hay que hacer?").assertExists()
        compose.onNodeWithText("Añadir día, hora o duración").assertExists()
        compose.onNodeWithText("Cuándo").assertDoesNotExist()
        compose.onNodeWithText("Elegir hora").assertDoesNotExist()
        // Crear no ofrece borrar.
        compose.onNodeWithText("Borrar").assertDoesNotExist()
    }

    @Test
    fun `los tres tipos se explican y el elegido queda marcado`() {
        var chosen: TaskType? = null
        editor(editorState(TaskDraft(type = TaskType.ROUTINE)), onType = { chosen = it })

        compose.onNodeWithText("Rutina").assertIsSelected()
        compose.onNodeWithText("Algo con hora fija que no puedes perderte. Te avisará 10 minutos antes.").assertExists()

        compose.onNodeWithText("Obligación").performClick()
        assertEquals(TaskType.OBLIGATION, chosen)
    }

    @Test
    fun `una obligacion siempre muestra dia y hora, y el aviso si faltan al guardar`() {
        editor(editorState(TaskDraft(title = "Cita", type = TaskType.OBLIGATION)))
        compose.onNodeWithText("Cuándo").assertExists()
        compose.onNodeWithText("Elegir hora").assertExists()
        compose.onNodeWithText("Una Obligación necesita día y hora para poder avisarte.").assertDoesNotExist()
    }

    @Test
    fun `el aviso de la obligacion aparece junto a sus campos cuando falta la hora`() {
        editor(
            editorState(
                TaskDraft(title = "Cita", type = TaskType.OBLIGATION, date = TEST_TODAY),
                problems = setOf(TaskProblem.OBLIGATION_NEEDS_DATE_AND_TIME),
            ),
        )

        compose.onNodeWithText("Una Obligación necesita día y hora para poder avisarte.").assertExists()
        compose.onNodeWithText("Hoy").assertIsSelected()
    }

    @Test
    fun `sin titulo al guardar, el campo explica que hace falta`() {
        editor(editorState(problems = setOf(TaskProblem.TITLE_BLANK)))

        compose.onNodeWithText("Ponle un nombre, aunque sea corto.").assertExists()
    }

    @Test
    fun `una rutina ofrece repeticion y hora, no dia`() {
        editor(editorState(TaskDraft(type = TaskType.ROUTINE, time = LocalTime.of(9, 0))))

        compose.onNodeWithText("Se repite").assertExists()
        compose.onNodeWithText("Todos los días").assertIsSelected()
        compose.onNodeWithText("Mañana").assertDoesNotExist()
        compose.onNodeWithText("Quitar hora").assertExists()
    }

    @Test
    fun `al editar se puede borrar y guardar`() {
        var saved = 0
        var deleted = 0
        editor(
            editorState(TaskDraft.from(task(3, title = "Dentista"), java.time.ZoneOffset.UTC)),
            onSave = { saved++ },
            onDelete = { deleted++ },
        )

        compose.onNodeWithText("Editar actividad").assertExists()
        compose.onNodeWithText("Dentista").assertExists()
        compose.onNodeWithText("Borrar").performClick()
        compose.onNodeWithText("Guardar").performClick()

        assertEquals(1, deleted)
        assertEquals(1, saved)
    }

    // ---- Lista

    private fun listState(vararg tasks: com.focusdivergent.app.domain.model.Task, filter: TaskFilter = TaskFilter()): TasksUiState {
        val sections = TaskAgenda.overview(tasks.toList(), emptySet(), TEST_TODAY, java.time.ZoneOffset.UTC, filter)
            .map { (section, items) -> TaskSectionUi(section, items) }
        return TasksUiState(
            today = TEST_TODAY,
            selectedDay = TEST_TODAY,
            week = TaskAgenda.weekOf(TEST_TODAY),
            filter = filter,
            sections = sections,
            hasAnyTask = tasks.isNotEmpty(),
            loading = false,
        )
    }

    private fun list(
        state: TasksUiState,
        onNewTask: () -> Unit = {},
        onEditTask: (Long) -> Unit = {},
        onSetDone: (AgendaItem, Boolean) -> Unit = { _, _ -> },
        onClearFilters: () -> Unit = {},
        onSelectDay: (java.time.LocalDate) -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            TasksContent(
                uiState = state,
                onNewTask = onNewTask,
                onEditTask = onEditTask,
                onSelectDay = onSelectDay,
                onPreviousWeek = {},
                onNextWeek = {},
                onGoToToday = {},
                onToggleEnergy = {},
                onToggleType = {},
                onClearFilters = onClearFilters,
                onSetDone = onSetDone,
            )
        }
    }

    @Test
    fun `sin ninguna actividad invita a crear la primera`() {
        var created = 0
        list(listState(), onNewTask = { created++ })

        compose.onNodeWithText("Empieza por una").assertExists()
        compose.onNodeWithText("Crear la primera").performClick()
        assertEquals(1, created)
    }

    @Test
    fun `con un filtro sin resultados ofrece quitarlo, no crear`() {
        var cleared = 0
        list(listState(task(1, energy = EnergyLevel.LOW), filter = TaskFilter(energy = EnergyLevel.HIGH)), onClearFilters = { cleared++ })

        compose.onNodeWithText("Nada con este filtro").assertExists()
        compose.onNodeWithText("Empieza por una").assertDoesNotExist()
        compose.onNodeWithText("Quitar filtros").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun `cada actividad muestra tipo, hora, duracion y energia, y se agrupa en su bloque`() {
        list(
            listState(
                task(1, title = "Llamar a la gestoría", startsInMinutes = 150, estimatedMinutes = 15, energy = EnergyLevel.LOW),
                task(2, title = "Declaración", energy = EnergyLevel.HIGH),
            ),
        )

        compose.onNodeWithText("Llamar a la gestoría").assertExists()
        compose.onNodeWithText("Tarea · ${LocalTime.of(14, 30).formatShort()} · 15 min").assertExists()
        compose.onNodeWithText("Sin fecha").assertExists()
        compose.onNodeWithText("Declaración").assertExists()
        compose.onNodeWithContentDescription("Energía: Alta").assertExists()
    }

    @Test
    fun `el circulo marca la actividad como hecha y el resto de la tarjeta la abre`() {
        var doneCall: Pair<Long, Boolean>? = null
        var opened: Long? = null
        list(
            listState(task(7, title = "Tender la ropa", startsInMinutes = 60)),
            onSetDone = { item, done -> doneCall = item.task.id to done },
            onEditTask = { opened = it },
        )

        compose.onNodeWithContentDescription("Hecha: Tender la ropa").assertIsOff().performClick()
        assertEquals(7L to true, doneCall)
        assertEquals(null, opened)

        compose.onNodeWithText("Tender la ropa").performClick()
        assertEquals(7L, opened)
    }

    @Test
    fun `lo hecho hoy esta plegado y se despliega al tocarlo`() {
        val done = task(1, title = "Fregar", status = com.focusdivergent.app.domain.model.TaskStatus.DONE, completedAt = com.focusdivergent.app.domain.usecase.TEST_NOW)
        list(listState(done, task(2, title = "Planchar", startsInMinutes = 60)))

        compose.onNodeWithText("Fregar").assertDoesNotExist()
        compose.onNodeWithText("Ver hechas hoy (1)").performScrollTo().performClick()

        compose.onNodeWithText("Fregar").assertExists()
        compose.onNodeWithContentDescription("Hecha: Fregar").assertIsOn()
        compose.onNodeWithText("Ocultar hechas hoy (1)").assertExists()
    }

    @Test
    fun `la tira semanal anuncia cada dia con su fecha completa y deja elegirlo`() {
        var picked: java.time.LocalDate? = null
        list(listState(task(1)), onSelectDay = { picked = it })

        compose.onNodeWithContentDescription("hoy, ${TEST_TODAY.formatFull()}").assertIsSelected()
        compose.onNodeWithContentDescription(TEST_TODAY.minusDays(5).formatFull()).performClick()

        assertEquals(TEST_TODAY.minusDays(5), picked)
    }

    @Test
    fun `en Proximamente cada actividad muestra tambien su dia`() {
        list(listState(task(1, title = "Entrega", startsInMinutes = 2 * 24 * 60 + 60)))

        compose.onNodeWithText("Próximamente").assertExists()
        compose.onNodeWithText("Tarea · ${TEST_TODAY.plusDays(2).formatShort()} · ${LocalTime.of(13, 0).formatShort()}").assertExists()
    }

    @Test
    fun `los bloques solo aparecen si tienen algo`() {
        list(listState(task(1, startsInMinutes = 60)))

        compose.onNodeWithText("Próximamente").assertDoesNotExist()
        compose.onNodeWithText("Sin fecha").assertDoesNotExist()
        assertEquals(listOf(TaskSection.TODAY), listState(task(1, startsInMinutes = 60)).sections.map { it.section })
    }
}
