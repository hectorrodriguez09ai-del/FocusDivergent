package com.focusdivergent.app.presentation.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.BrainDumpRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.repository.TaskStepRepository
import com.focusdivergent.app.domain.steps.StepProgress
import com.focusdivergent.app.domain.tasks.Recurrence
import com.focusdivergent.app.domain.tasks.TaskDraft
import com.focusdivergent.app.domain.tasks.TaskProblem
import com.focusdivergent.app.domain.usecase.DeleteTaskUseCase
import com.focusdivergent.app.domain.usecase.SaveTaskResult
import com.focusdivergent.app.domain.usecase.SaveTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property weekly la rutina se repite solo algunos días ([weekDays]); si no, todos los días.
 * @property detailsExpanded se ven los campos opcionales de una tarea (día, hora, duración).
 * @property problems lo que falta para guardar; solo se rellena al intentar guardar, no mientras se escribe.
 * @property steps cuántos micro-pasos tiene la actividad y cuántos van hechos (nada si aún no está guardada).
 */
data class TaskEditorUiState(
    val draft: TaskDraft = TaskDraft(),
    val today: LocalDate,
    val weekly: Boolean = false,
    val weekDays: Set<DayOfWeek> = emptySet(),
    val detailsExpanded: Boolean = false,
    val problems: Set<TaskProblem> = emptySet(),
    val steps: StepProgress = StepProgress.NONE,
    val loading: Boolean = true,
)

/** Sucesos de una sola vez: se envían por un canal para que un giro de pantalla no los repita. */
sealed interface TaskEditorEvent {
    /** Guardada, borrada o inexistente: el editor ya no tiene nada que hacer. */
    data object Close : TaskEditorEvent

    /** Abrir los pasos de la actividad (que ya está guardada). El editor se queda debajo. */
    data class OpenSteps(val taskId: Long) : TaskEditorEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TaskRepository,
    taskSteps: TaskStepRepository,
    private val brainDump: BrainDumpRepository,
    private val saveTask: SaveTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val clock: Clock,
) : ViewModel() {

    // Con rutas tipadas, cada propiedad de la ruta llega aquí con su nombre. 0 = actividad nueva.
    private val taskId: Long = savedStateHandle.get<Long>(ARG_TASK_ID) ?: 0L

    // Si la actividad nace de una idea del Brain Dump, arranca con su título y la idea se da por procesada al guardar.
    // Si se sale sin guardar, la idea sigue esperando en la bandeja.
    private val ideaId: Long = savedStateHandle.get<Long>(ARG_IDEA_ID) ?: 0L

    private val _uiState = MutableStateFlow(
        TaskEditorUiState(
            draft = TaskDraft(title = if (taskId == 0L) savedStateHandle.get<String>(ARG_TITLE).orEmpty() else ""),
            today = clock.instant().atZone(clock.zone).toLocalDate(),
            loading = taskId != 0L,
        ),
    )
    val uiState: StateFlow<TaskEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<TaskEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Los pasos de la actividad que se está editando; si aún no está guardada, no hay ninguno.
        viewModelScope.launch {
            _uiState.map { it.draft.original?.id ?: 0L }.distinctUntilChanged()
                .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else taskSteps.observeForTask(id) }
                .collect { steps -> _uiState.update { it.copy(steps = StepProgress.of(steps)) } }
        }
        if (taskId != 0L) {
            viewModelScope.launch {
                val task = tasks.getById(taskId)
                if (task == null) {
                    _events.send(TaskEditorEvent.Close)
                    return@launch
                }
                val draft = TaskDraft.from(task, clock.zone)
                val weekly = draft.recurrence as? Recurrence.Weekly
                _uiState.update {
                    it.copy(
                        draft = draft,
                        weekly = weekly != null,
                        weekDays = weekly?.days.orEmpty(),
                        // Si ya tenía algún detalle, se enseña abierto: nada de lo guardado queda escondido.
                        detailsExpanded = draft.date != null || draft.time != null || draft.estimatedMinutes != null,
                        loading = false,
                    )
                }
            }
        }
    }

    fun setTitle(title: String) = edit(clears = TaskProblem.TITLE_BLANK) { it.copy(title = title) }

    fun setType(type: TaskType) = edit(clears = TaskProblem.OBLIGATION_NEEDS_DATE_AND_TIME) { it.copy(type = type) }

    fun setEnergy(energy: EnergyLevel) = edit { it.copy(energy = energy) }

    fun setDate(date: LocalDate?) = edit(clears = TaskProblem.OBLIGATION_NEEDS_DATE_AND_TIME) { it.copy(date = date) }

    fun setTime(time: LocalTime?) = edit(clears = TaskProblem.OBLIGATION_NEEDS_DATE_AND_TIME) { it.copy(time = time) }

    fun setEstimatedMinutes(minutes: Int?) = edit { it.copy(estimatedMinutes = minutes) }

    fun toggleDetails() = _uiState.update { it.copy(detailsExpanded = !it.detailsExpanded) }

    fun setWeekly(weekly: Boolean) = _uiState.update { it.copy(weekly = weekly).withRecurrence() }

    fun toggleWeekDay(day: DayOfWeek) = _uiState.update {
        it.copy(weekDays = if (day in it.weekDays) it.weekDays - day else it.weekDays + day).withRecurrence()
    }

    fun save() {
        val draft = _uiState.value.draft
        viewModelScope.launch {
            when (val result = saveTask(draft)) {
                is SaveTaskResult.Saved -> {
                    if (ideaId != 0L) brainDump.markProcessed(ideaId)
                    _events.send(TaskEditorEvent.Close)
                }
                is SaveTaskResult.Invalid -> _uiState.update {
                    // Lo que falta puede estar en la zona plegada: se abre para que se vea.
                    it.copy(problems = result.problems, detailsExpanded = true)
                }
            }
        }
    }

    /**
     * Abre los pasos de la actividad. Una actividad nueva se guarda antes, porque los pasos cuelgan de ella; si le
     * falta algo para guardarse (el título), se enseña y no se abre nada. El editor sigue abierto debajo y, al volver,
     * ya edita la actividad guardada.
     */
    fun openSteps() {
        val draft = _uiState.value.draft
        viewModelScope.launch {
            val existing = draft.original
            if (existing != null) {
                _events.send(TaskEditorEvent.OpenSteps(existing.id))
                return@launch
            }
            when (val result = saveTask(draft)) {
                is SaveTaskResult.Saved -> {
                    if (ideaId != 0L) brainDump.markProcessed(ideaId)
                    val saved = tasks.getById(result.id)
                    _uiState.update { it.copy(draft = it.draft.copy(original = saved)) }
                    _events.send(TaskEditorEvent.OpenSteps(result.id))
                }
                is SaveTaskResult.Invalid -> _uiState.update { it.copy(problems = result.problems, detailsExpanded = true) }
            }
        }
    }

    fun delete() {
        val original = _uiState.value.draft.original ?: return
        viewModelScope.launch {
            deleteTask(original)
            _events.send(TaskEditorEvent.Close)
        }
    }

    private fun edit(clears: TaskProblem? = null, change: (TaskDraft) -> TaskDraft) = _uiState.update {
        it.copy(draft = change(it.draft), problems = if (clears == null) it.problems else it.problems - clears)
    }

    // «Algunos días» sin ninguno marcado equivale a todos los días (ver Recurrence.of).
    private fun TaskEditorUiState.withRecurrence() =
        copy(draft = draft.copy(recurrence = if (weekly) Recurrence.of(weekDays) else Recurrence.Daily))

    companion object {
        const val ARG_TASK_ID = "taskId"
        const val ARG_TITLE = "title"
        const val ARG_IDEA_ID = "ideaId"
    }
}
