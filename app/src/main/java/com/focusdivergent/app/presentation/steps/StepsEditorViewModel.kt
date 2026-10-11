package com.focusdivergent.app.presentation.steps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.ai.LlmSettings
import com.focusdivergent.app.domain.ai.SuggestResult
import com.focusdivergent.app.domain.repository.StepTemplateRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.repository.TaskStepRepository
import com.focusdivergent.app.domain.steps.StepRules
import com.focusdivergent.app.domain.steps.StepTemplate
import com.focusdivergent.app.domain.steps.TaskStep
import com.focusdivergent.app.domain.usecase.AddStepsUseCase
import com.focusdivergent.app.domain.usecase.SaveStepTemplateUseCase
import com.focusdivergent.app.domain.usecase.SaveTemplateResult
import com.focusdivergent.app.domain.usecase.SetStepDoneUseCase
import com.focusdivergent.app.domain.usecase.SuggestStepsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Aviso breve bajo la lista de pasos. Siempre neutro: dice qué pasó o qué falta, sin reproche (RNF-3). */
sealed interface StepsNotice {
    data class TemplateSaved(val name: String) : StepsNotice
    data object TemplateNeedsName : StepsNotice
    data object TemplateNeedsSteps : StepsNotice
    data object TemplateLimit : StepsNotice
    data object TemplateApplied : StepsNotice
    data class StepLimit(val max: Int) : StepsNotice
}

/** Por qué no salió una propuesta. Cada uno tiene su texto, siempre neutro. */
enum class AiFailure { NO_CONNECTION, REJECTED, UNREADABLE, NOT_READY }

/** En qué punto está la sugerencia de pasos con la IA propia del usuario. */
sealed interface AiPhase {
    data object Idle : AiPhase

    /** Falta que el usuario acepte enviar el título al servicio. */
    data object AskingConsent : AiPhase

    data object Loading : AiPhase

    /** La propuesta, para revisarla antes de añadir nada. */
    data class Review(val steps: List<String>) : AiPhase

    data class Failed(val reason: AiFailure) : AiPhase
}

/**
 * @property available la IA propia está configurada: solo entonces se ofrece el botón.
 * @property host el servicio al que se enviaría el título.
 */
data class AiUiState(
    val available: Boolean = false,
    val host: String? = null,
    val phase: AiPhase = AiPhase.Idle,
)

/**
 * @property taskTitle título de la tarea; nulo mientras carga o si ya no existe.
 * @property userTemplates las plantillas que ha guardado el usuario. Las de partida las pone la pantalla.
 */
data class StepsEditorUiState(
    val taskTitle: String? = null,
    val steps: List<TaskStep> = emptyList(),
    val userTemplates: List<StepTemplate> = emptyList(),
    val notice: StepsNotice? = null,
    val ai: AiUiState = AiUiState(),
    val loading: Boolean = true,
) {
    val canAddMore: Boolean get() = steps.size < StepRules.MAX_STEPS_PER_TASK
    val doneCount: Int get() = steps.count { it.done }
}

@HiltViewModel
class StepsEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TaskRepository,
    private val steps: TaskStepRepository,
    private val templates: StepTemplateRepository,
    private val addSteps: AddStepsUseCase,
    private val setStepDone: SetStepDoneUseCase,
    private val saveTemplate: SaveStepTemplateUseCase,
    private val llm: LlmSettings,
    private val suggestSteps: SuggestStepsUseCase,
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>(ARG_TASK_ID) ?: 0L

    private val title = MutableStateFlow<String?>(null)
    private val loaded = MutableStateFlow(false)
    private val notice = MutableStateFlow<StepsNotice?>(null)
    private val aiPhase = MutableStateFlow<AiPhase>(AiPhase.Idle)

    private val header = combine(title, loaded) { title, loaded -> title to loaded }
    private val ai = combine(llm.config, aiPhase) { config, phase ->
        AiUiState(available = config.isConfigured, host = config.host, phase = phase)
    }

    val uiState: StateFlow<StepsEditorUiState> = combine(
        header,
        steps.observeForTask(taskId),
        templates.observeAll(),
        notice,
        ai,
    ) { (title, loaded), stepList, userTemplates, notice, ai ->
        StepsEditorUiState(
            taskTitle = title,
            steps = stepList,
            userTemplates = userTemplates,
            notice = notice,
            ai = ai,
            loading = !loaded,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StepsEditorUiState(),
    )

    init {
        viewModelScope.launch {
            title.value = tasks.getById(taskId)?.title
            loaded.value = true
        }
    }

    /** Añade un paso escrito a mano. */
    fun add(text: String) {
        viewModelScope.launch {
            if (!uiState.value.canAddMore) {
                notice.value = StepsNotice.StepLimit(StepRules.MAX_STEPS_PER_TASK)
                return@launch
            }
            addSteps(taskId, listOf(text))
            notice.value = null
        }
    }

    fun rename(step: TaskStep, text: String) {
        val clean = StepRules.cleanStep(text) ?: return
        viewModelScope.launch { steps.rename(step.id, clean) }
    }

    fun delete(step: TaskStep) {
        viewModelScope.launch { steps.delete(step.id) }
    }

    fun move(step: TaskStep, up: Boolean) {
        viewModelScope.launch { steps.move(step.id, up) }
    }

    fun setDone(step: TaskStep, done: Boolean) {
        viewModelScope.launch { setStepDone(step, done) }
    }

    /** Añade al final de lo que ya hay los pasos de una plantilla (de partida o propia). */
    fun applyTemplate(template: StepTemplate) {
        viewModelScope.launch {
            val added = addSteps(taskId, template.steps)
            notice.value = if (added == 0 && template.steps.isNotEmpty()) {
                StepsNotice.StepLimit(StepRules.MAX_STEPS_PER_TASK)
            } else {
                StepsNotice.TemplateApplied
            }
        }
    }

    /** Guarda los pasos actuales como plantilla propia con el nombre dado. */
    fun saveAsTemplate(name: String) {
        val texts = uiState.value.steps.map { it.text }
        viewModelScope.launch {
            notice.value = when (saveTemplate(name, texts)) {
                SaveTemplateResult.Saved -> StepsNotice.TemplateSaved(StepRules.cleanName(name).orEmpty())
                SaveTemplateResult.NeedsName -> StepsNotice.TemplateNeedsName
                SaveTemplateResult.NeedsSteps -> StepsNotice.TemplateNeedsSteps
                SaveTemplateResult.TooMany -> StepsNotice.TemplateLimit
            }
        }
    }

    fun deleteTemplate(template: StepTemplate) {
        viewModelScope.launch { templates.delete(template.id) }
    }

    fun dismissNotice() = notice.update { null }

    // ---- Sugerencias con la IA propia

    /** Pide una propuesta. Si el usuario aún no aceptó enviar el título a ese servidor, primero se lo pregunta. */
    fun askAi() {
        val config = llm.config.value
        if (!config.isConfigured) {
            aiPhase.value = AiPhase.Failed(AiFailure.NOT_READY)
            return
        }
        if (!config.isConsented) {
            aiPhase.value = AiPhase.AskingConsent
            return
        }
        val taskTitle = title.value.orEmpty()
        aiPhase.value = AiPhase.Loading
        viewModelScope.launch {
            aiPhase.value = when (val result = suggestSteps(taskTitle)) {
                is SuggestResult.Steps -> AiPhase.Review(result.steps)
                SuggestResult.NotReady -> AiPhase.Failed(AiFailure.NOT_READY)
                SuggestResult.NoConnection -> AiPhase.Failed(AiFailure.NO_CONNECTION)
                is SuggestResult.Rejected -> AiPhase.Failed(AiFailure.REJECTED)
                SuggestResult.Unreadable -> AiPhase.Failed(AiFailure.UNREADABLE)
            }
        }
    }

    /** El usuario acepta enviar el título a su servicio: se recuerda y se pide la propuesta. */
    fun acceptAiConsent() {
        val host = llm.config.value.host ?: return
        llm.consent(host)
        askAi()
    }

    /** Añade solo los pasos que el usuario dejó marcados en la propuesta. */
    fun addSuggested(chosen: List<String>) {
        aiPhase.value = AiPhase.Idle
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            val added = addSteps(taskId, chosen)
            notice.value = if (added == 0) StepsNotice.StepLimit(StepRules.MAX_STEPS_PER_TASK) else null
        }
    }

    fun dismissAi() {
        aiPhase.value = AiPhase.Idle
    }

    companion object {
        const val ARG_TASK_ID = "taskId"
    }
}
