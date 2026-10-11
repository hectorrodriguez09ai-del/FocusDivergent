package com.focusdivergent.app.presentation.steps

import android.app.Application
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.ai.LlmConfig
import com.focusdivergent.app.domain.steps.StepProgress
import com.focusdivergent.app.presentation.ai.AiSaveResult
import com.focusdivergent.app.presentation.ai.AiSettingsContent
import com.focusdivergent.app.domain.steps.StepTemplate
import com.focusdivergent.app.domain.steps.TaskStep
import com.focusdivergent.app.domain.tasks.AgendaItem
import com.focusdivergent.app.domain.tasks.TaskDraft
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
import com.focusdivergent.app.presentation.tasks.TaskEditorContent
import com.focusdivergent.app.presentation.tasks.TaskEditorUiState
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import com.focusdivergent.app.presentation.today.TodayContent
import com.focusdivergent.app.presentation.today.TodayUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Las pantallas de micro-pasos (RF-3.1), dibujadas con estado fijo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class StepsScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun step(id: Long, text: String = "Paso $id", done: Boolean = false) =
        TaskStep(id = id, taskId = 1, position = id.toInt(), text = text, done = done)

    // ---- Un paso por pantalla

    private fun focus(
        state: StepFocusUiState,
        onDone: () -> Unit = {},
        onNotNow: () -> Unit = {},
        onReview: () -> Unit = {},
        onFinishTask: () -> Unit = {},
        onEditSteps: () -> Unit = {},
        onBack: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            StepFocusContent(
                uiState = state,
                onBack = onBack,
                onDone = onDone,
                onNotNow = onNotNow,
                onReview = onReview,
                onFinishTask = onFinishTask,
                onEditSteps = onEditSteps,
            )
        }
    }

    @Test
    fun `se ve un solo paso grande con su lugar y la duracion orientativa`() {
        focus(StepFocusUiState.Current("Informe", step(2, "Abrir el documento"), number = 2, total = 5))

        compose.onNodeWithText("Abrir el documento").assertExists()
        compose.onNodeWithText("Paso 2 de 5").assertExists()
        compose.onNodeWithText("Unos 5 minutos, a tu ritmo").assertExists()
        compose.onNodeWithText("Para: «Informe»").assertExists()
    }

    @Test
    fun `hecho y ahora no pesan igual, mismo ancho y mismo alto`() {
        focus(StepFocusUiState.Current("Informe", step(1), number = 1, total = 3))

        val done = compose.onNodeWithText("Hecho").getUnclippedBoundsInRoot()
        val notNow = compose.onNodeWithText("Ahora no").getUnclippedBoundsInRoot()

        assertEquals(done.right - done.left, notNow.right - notNow.left)
        assertEquals(done.bottom - done.top, notNow.bottom - notNow.top)
        compose.onNodeWithText("Hecho").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Ahora no").assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun `los botones de hecho y ahora no avisan cada uno`() {
        var done = 0
        var notNow = 0
        focus(StepFocusUiState.Current("Informe", step(1), number = 1, total = 3), onDone = { done++ }, onNotNow = { notNow++ })

        compose.onNodeWithText("Hecho").performClick()
        compose.onNodeWithText("Ahora no").performClick()
        compose.onNodeWithText("Ahora no").performClick()

        assertEquals(1, done)
        assertEquals(2, notNow)
    }

    @Test
    fun `con todos los pasos hechos se ofrece dar la tarea por hecha, sin presionar`() {
        var finished = 0
        focus(StepFocusUiState.AllDone("Informe", canFinishTask = true), onFinishTask = { finished++ })

        compose.onNodeWithText("Has pasado por todos los pasos").assertExists()
        compose.onNodeWithText("Dar la tarea por hecha").performClick()
        compose.onNodeWithText("Volver").assertExists()
        assertEquals(1, finished)
    }

    @Test
    fun `si la tarea ya estaba hecha solo se ofrece volver`() {
        focus(StepFocusUiState.AllDone("Informe", canFinishTask = false))

        compose.onNodeWithText("Dar la tarea por hecha").assertDoesNotExist()
        compose.onNodeWithText("Volver").assertExists()
    }

    @Test
    fun `al apartar todos los pasos se dice con calma que esperan`() {
        var review = 0
        focus(StepFocusUiState.Paused("Informe"), onReview = { review++ })

        compose.onNodeWithText("Lo dejamos aquí por ahora").assertExists()
        compose.onNodeWithText("Los pasos que quedan te esperan, justo como están.").assertExists()
        compose.onNodeWithText("Volver a verlos").performClick()
        assertEquals(1, review)
    }

    @Test
    fun `una tarea sin pasos invita a prepararlos`() {
        var edit = 0
        focus(StepFocusUiState.NoSteps("Informe"), onEditSteps = { edit++ })

        compose.onNodeWithText("Esta tarea aún no tiene pasos").assertExists()
        compose.onNodeWithText("Preparar pasos").performClick()
        assertEquals(1, edit)
    }

    // ---- Preparar los pasos

    private fun editor(
        state: StepsEditorUiState,
        builtIn: List<StepTemplate> = emptyList(),
        onStart: () -> Unit = {},
        onAdd: (String) -> Unit = {},
        onDelete: (TaskStep) -> Unit = {},
        onMove: (TaskStep, Boolean) -> Unit = { _, _ -> },
        onSetDone: (TaskStep, Boolean) -> Unit = { _, _ -> },
        onApplyTemplate: (StepTemplate) -> Unit = {},
        onAskAi: () -> Unit = {},
        onAddSuggested: (List<String>) -> Unit = {},
        onDismissAi: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            StepsEditorContent(
                uiState = state,
                builtInTemplates = builtIn,
                onBack = {},
                onStart = onStart,
                onAdd = onAdd,
                onRename = { _, _ -> },
                onDelete = onDelete,
                onMove = onMove,
                onSetDone = onSetDone,
                onApplyTemplate = onApplyTemplate,
                onSaveTemplate = {},
                onDeleteTemplate = {},
                onDismissNotice = {},
                onAskAi = onAskAi,
                onAddSuggested = onAddSuggested,
                onDismissAi = onDismissAi,
            )
        }
    }

    private fun editorState(steps: List<TaskStep> = emptyList(), notice: StepsNotice? = null, ai: AiUiState = AiUiState()) =
        StepsEditorUiState(taskTitle = "Informe", steps = steps, notice = notice, ai = ai, loading = false)

    @Test
    fun `sin pasos el editor lo dice con tono tranquilo y no deja empezar`() {
        editor(editorState())

        compose.onNodeWithText("Aún no hay pasos").assertExists()
        compose.onNodeWithText("Empezar con el primer paso").assertIsNotEnabled()
        compose.onNodeWithText("Guardar como plantilla").assertDoesNotExist()
    }

    @Test
    fun `el editor lista los pasos y deja empezar con el primero`() {
        var started = 0
        editor(editorState(listOf(step(1, "Abrir el documento"), step(2, "Escribir el titulo"))), onStart = { started++ })

        compose.onNodeWithText("Abrir el documento").assertExists()
        compose.onNodeWithText("Escribir el titulo").assertExists()
        compose.onNodeWithText("Empezar con el primer paso").assertIsEnabled().performClick()
        compose.onNodeWithText("Guardar como plantilla").assertExists()
        assertEquals(1, started)
    }

    @Test
    fun `si todos los pasos estan hechos ya no hay un primer paso con el que empezar`() {
        editor(editorState(listOf(step(1, done = true))))

        compose.onNodeWithText("Empezar con el primer paso").assertIsNotEnabled()
    }

    @Test
    fun `marcar un paso desde la casilla avisa con su estado nuevo`() {
        var toggled: Pair<Long, Boolean>? = null
        editor(editorState(listOf(step(7, "Abrir el documento"))), onSetDone = { s, d -> toggled = s.id to d })

        compose.onNodeWithContentDescription("Paso hecho: Abrir el documento").performClick()

        assertEquals(7L to true, toggled)
    }

    @Test
    fun `el menu de un paso deja subirlo, bajarlo y quitarlo, pero no subir el primero`() {
        var moved: Pair<Long, Boolean>? = null
        var deleted: Long? = null
        editor(
            editorState(listOf(step(1, "Uno"), step(2, "Dos"))),
            onMove = { s, up -> moved = s.id to up },
            onDelete = { deleted = it.id },
        )

        compose.onNodeWithContentDescription("Más opciones del paso «Uno»").performClick()
        compose.onNodeWithText("Subir").assertDoesNotExist()
        compose.onNodeWithText("Bajar").performClick()
        assertEquals(1L to false, moved)

        compose.onNodeWithContentDescription("Más opciones del paso «Dos»").performClick()
        compose.onNodeWithText("Quitar el paso").performClick()
        assertEquals(2L, deleted)
    }

    @Test
    fun `un aviso neutro se ve bajo la lista`() {
        editor(editorState(listOf(step(1)), notice = StepsNotice.TemplateSaved("Hacer la compra")))

        compose.onNodeWithText("Plantilla guardada: «Hacer la compra».").assertExists()
    }

    @Test
    fun `con el maximo de pasos se explica en vez de ofrecer anadir mas`() {
        editor(editorState(List(20) { step(it + 1L) }))

        compose.onNodeWithText("Ya hay 20 pasos, que es el máximo para una tarea.").assertExists()
        compose.onNodeWithText("Añadir paso").assertDoesNotExist()
    }

    @Test
    fun `el selector enseña las plantillas de partida y las propias y aplica la que se toca`() {
        var picked: StepTemplate? = null
        val builtIn = listOf(StepTemplate(name = "Estudiar un tema", steps = listOf("a", "b", "c"), builtIn = true))
        val mine = listOf(StepTemplate(id = 3, name = "Hacer la compra", steps = listOf("Lista")))
        compose.setContent {
            FocusDivergentTheme {
                TemplatePickerContent(builtIn = builtIn, mine = mine, onPick = { picked = it }, onDelete = {})
            }
        }

        compose.onNodeWithText("De partida").assertExists()
        compose.onNodeWithText("Mis plantillas").assertExists()
        compose.onNodeWithText("3 pasos").assertExists()
        compose.onNodeWithText("1 paso").assertExists()
        compose.onNodeWithContentDescription("Borrar la plantilla «Hacer la compra»").assertExists()

        compose.onNodeWithText("Estudiar un tema").performClick()
        assertEquals("Estudiar un tema", picked?.name)
    }

    @Test
    fun `sin plantillas propias el selector explica como tener una`() {
        compose.setContent {
            FocusDivergentTheme {
                TemplatePickerContent(builtIn = emptyList(), mine = emptyList(), onPick = {}, onDelete = {})
            }
        }

        compose.onNodeWithText(
            "Tus plantillas aparecerán aquí. Prepara una lista de pasos y guárdala para reutilizarla.",
        ).assertExists()
    }

    @Test
    fun `las plantillas de partida existen, tienen pasos y ninguna se repite`() {
        var templates: List<StepTemplate> = emptyList()
        compose.setContent {
            FocusDivergentTheme { templates = rememberBuiltInStepTemplates() }
        }
        compose.waitForIdle()

        val names = templates.map { it.name }
        assertEquals(true, names.containsAll(listOf("Empezar a limpiar o a ordenar", "Preparar un trabajo", "Estudiar un tema")))
        assertEquals(templates.size, templates.map { it.name }.toSet().size)
        templates.forEach {
            assertEquals(true, it.builtIn)
            assertEquals(true, it.steps.size in 3..8)
            assertEquals(it.steps.size, it.steps.toSet().size)
        }
    }

    // ---- En el editor de tareas y en «Hoy»

    @Test
    fun `el editor de tareas ofrece dividir en pasos y despues enseña cuantos van hechos`() {
        var opened = 0
        compose.setContent {
            FocusDivergentTheme {
                TaskEditorContent(
                    uiState = TaskEditorUiState(draft = TaskDraft(), today = TEST_TODAY, loading = false),
                    onBack = {}, onTitle = {}, onType = {}, onEnergy = {}, onDate = {}, onTime = {}, onDuration = {},
                    onToggleDetails = {}, onWeekly = {}, onToggleWeekDay = {}, onSave = {}, onDelete = {},
                    onSteps = { opened++ },
                )
            }
        }

        compose.onNodeWithText("Dividir en pasos").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `el editor de tareas enseña el progreso de los pasos en el mismo boton`() {
        compose.setContent {
            FocusDivergentTheme {
                TaskEditorContent(
                    uiState = TaskEditorUiState(
                        draft = TaskDraft(),
                        today = TEST_TODAY,
                        steps = StepProgress.of(listOf(step(1, done = true), step(2), step(3))),
                        loading = false,
                    ),
                    onBack = {}, onTitle = {}, onType = {}, onEnergy = {}, onDate = {}, onTime = {}, onDuration = {},
                    onToggleDetails = {}, onWeekly = {}, onToggleWeekDay = {}, onSave = {}, onDelete = {},
                )
            }
        }

        compose.onNodeWithText("Pasos: 1 de 3 hechos").assertExists()
    }

    private fun today(
        state: TodayUiState,
        onOpenSteps: (Long) -> Unit = {},
        onSplitSteps: (Long) -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            TodayContent(
                uiState = state, onChooseForMe = {}, onLaunch = {}, onEnergy = {}, onEditTask = {}, onNewTask = {},
                onSeeAllTasks = {}, onTimer = {}, onAudio = {}, onSetDone = { _, _ -> }, onLeaveForLater = {},
                onOpenSteps = onOpenSteps, onSplitSteps = onSplitSteps,
            )
        }
    }

    private fun nowState(item: AgendaItem, steps: Map<Long, StepProgress> = emptyMap()) = TodayUiState(
        today = TEST_TODAY, energy = EnergyLevel.MEDIUM, nowItems = listOf(item), nowSteps = steps, hasAnyTask = true, loading = false,
    )

    @Test
    fun `Ahora ofrece el siguiente paso de la tarea en marcha y lo abre al tocarlo`() {
        var opened: Long? = null
        val item = AgendaItem(task(4, title = "Informe"), isDone = false, date = TEST_TODAY, time = null)
        val steps = StepProgress.of(listOf(step(1, done = true).copy(taskId = 4), step(2, "Escribir el titulo").copy(taskId = 4)))
        today(nowState(item, mapOf(4L to steps)), onOpenSteps = { opened = it })

        compose.onNodeWithText("Siguiente paso: Escribir el titulo").performClick()

        assertEquals(4L, opened)
        compose.onNodeWithText("1 de 2 pasos").assertExists()
        compose.onNodeWithText("Dividir en pasos").assertDoesNotExist()
    }

    @Test
    fun `Ahora ofrece dividir en pasos una tarea en marcha que no tiene ninguno`() {
        var split: Long? = null
        val item = AgendaItem(task(4, title = "Informe"), isDone = false, date = TEST_TODAY, time = null)
        today(nowState(item), onSplitSteps = { split = it })

        compose.onNodeWithText("Dividir en pasos").performClick()

        assertEquals(4L, split)
    }

    // ---- Pasos con la IA propia

    @Test
    fun `sin IA configurada no hay ni rastro del boton`() {
        editor(editorState(ai = AiUiState(available = false)))

        compose.onNodeWithText("Sugerir pasos con tu IA").assertDoesNotExist()
        compose.onNodeWithText("Con tu IA").assertDoesNotExist()
    }

    @Test
    fun `con IA configurada se ofrece y avisa al tocarlo`() {
        var asked = 0
        editor(editorState(ai = AiUiState(available = true, host = "api.example.com")), onAskAi = { asked++ })

        compose.onNodeWithText("Sugerir pasos con tu IA").performClick()

        assertEquals(1, asked)
    }

    @Test
    fun `mientras piensa lo dice con calma`() {
        editor(editorState(ai = AiUiState(available = true, phase = AiPhase.Loading)))

        compose.onNodeWithText("Pensando unos pasos…").assertExists()
    }

    @Test
    fun `un fallo se cuenta sin tecnicismos y deja volver a intentarlo`() {
        editor(editorState(ai = AiUiState(available = true, phase = AiPhase.Failed(AiFailure.NO_CONNECTION))))

        compose.onNodeWithText("No se pudo llegar a tu servicio. Puedes escribir los pasos a mano o intentarlo más tarde.").assertExists()
        compose.onNodeWithText("Sugerir pasos con tu IA").assertExists()
    }

    @Test
    fun `la propuesta se revisa antes de anadir y solo se anade lo que sigue marcado`() {
        var added: List<String>? = null
        editor(
            editorState(ai = AiUiState(available = true, phase = AiPhase.Review(listOf("Uno", "Dos", "Tres")))),
            onAddSuggested = { added = it },
        )

        compose.onNodeWithText("Propuesta para revisar").assertExists()
        compose.onNodeWithContentDescription("Incluir el paso: Dos").performClick()
        compose.onNodeWithText("Añadir los elegidos").performClick()

        assertEquals(listOf("Uno", "Tres"), added)
    }

    @Test
    fun `sin ningun paso marcado no se puede anadir y se puede descartar`() {
        var discarded = 0
        editor(
            editorState(ai = AiUiState(available = true, phase = AiPhase.Review(listOf("Uno")))),
            onDismissAi = { discarded++ },
        )

        compose.onNodeWithContentDescription("Incluir el paso: Uno").performClick()
        compose.onNodeWithText("Añadir los elegidos").assertIsNotEnabled()
        compose.onNodeWithText("Descartar la propuesta").performClick()

        assertEquals(1, discarded)
    }

    @Test
    fun `la pregunta de permiso dice a quien va y exactamente que se envia`() {
        compose.setContent {
            FocusDivergentTheme { AiConsentContent(host = "api.example.com", taskTitle = "Preparar el informe") }
        }

        compose.onNodeWithText("Para proponerte pasos, la app enviará a «api.example.com» este dato y nada más:").assertExists()
        compose.onNodeWithText("El título de la tarea: «Preparar el informe»").assertExists()
        compose.onNodeWithText(
            "No se envían notas, otras tareas ni datos de tu móvil. Tu clave va en la petición solo para identificarte. Puedes retirar este permiso quitando la clave en Bloqueos.",
        ).assertExists()
    }

    // ---- Ajustes de la IA

    private fun aiSettings(
        config: LlmConfig,
        onSave: (String, String, String) -> AiSaveResult = { _, _, _ -> AiSaveResult.SAVED },
        onRemoveKey: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme { AiSettingsContent(config = config, onBack = {}, onSave = onSave, onRemoveKey = onRemoveKey) }
    }

    @Test
    fun `los ajustes explican que es opcional y que solo se envia el titulo`() {
        aiSettings(LlmConfig())

        compose.onNodeWithText("Tu propia IA").assertExists()
        compose.onNodeWithText("Qué se envía: solo el título de la tarea", substring = true).assertExists()
        compose.onNodeWithText("Para usarla hacen falta una dirección, un modelo y una clave.").assertExists()
        compose.onNodeWithText("Quitar la clave y apagar la función").assertDoesNotExist()
    }

    @Test
    fun `con clave guardada se puede quitar, y los ajustes dicen que esta lista`() {
        var removed = 0
        aiSettings(LlmConfig(baseUrl = "https://api.example.com/v1", model = "m", hasKey = true), onRemoveKey = { removed++ })

        compose.onNodeWithText("Lista:", substring = true).assertExists()
        compose.onNodeWithText("Quitar la clave y apagar la función").performClick()

        assertEquals(1, removed)
        compose.onNodeWithText("Clave quitada. La función queda apagada.").assertExists()
    }

    @Test
    fun `una direccion sin https se rechaza con una frase clara`() {
        aiSettings(LlmConfig(baseUrl = "http://inseguro.com"), onSave = { _, _, _ -> AiSaveResult.INVALID_URL })

        compose.onNodeWithText("Guardar").performClick()

        compose.onNodeWithText("La dirección debe empezar por https://").assertExists()
    }
}
