package com.focusdivergent.app.presentation.steps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.repository.TaskStepRepository
import com.focusdivergent.app.domain.steps.StepQueue
import com.focusdivergent.app.domain.steps.TaskStep
import com.focusdivergent.app.domain.usecase.SetStepDoneUseCase
import com.focusdivergent.app.domain.usecase.SetTaskDoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lo que enseña la pantalla de pasos de uno en uno. */
sealed interface StepFocusUiState {
    data object Loading : StepFocusUiState

    /** La tarea no tiene pasos todavía. */
    data class NoSteps(val taskTitle: String) : StepFocusUiState

    /** Un paso a pantalla completa. [number] es su lugar entre todos los pasos (1 = el primero). */
    data class Current(val taskTitle: String, val step: TaskStep, val number: Int, val total: Int) : StepFocusUiState

    /** Todos los pasos están hechos. [canFinishTask] es falso si la tarea ya se había dado por hecha. */
    data class AllDone(val taskTitle: String, val canFinishTask: Boolean) : StepFocusUiState

    /** Se han apartado todos los pasos que quedaban: la sesión se acaba con calma y los pasos esperan. */
    data class Paused(val taskTitle: String) : StepFocusUiState
}

sealed interface StepFocusEvent {
    data object Close : StepFocusEvent
}

@HiltViewModel
class StepFocusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TaskRepository,
    steps: TaskStepRepository,
    private val setStepDone: SetStepDoneUseCase,
    private val setTaskDone: SetTaskDoneUseCase,
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>(ARG_TASK_ID) ?: 0L

    private val task = MutableStateFlow<Task?>(null)
    private val loaded = MutableStateFlow(false)

    // Los pasos apartados con «Ahora no» valen solo para esta visita: no se guardan ni cambian el orden.
    private val setAside = MutableStateFlow<Set<Long>>(emptySet())

    private val _events = Channel<StepFocusEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<StepFocusUiState> = combine(
        task,
        loaded,
        if (taskId == 0L) flowOf(emptyList()) else steps.observeForTask(taskId),
        setAside,
    ) { task, loaded, stepList, aside ->
        val title = task?.title.orEmpty()
        when {
            !loaded -> StepFocusUiState.Loading
            task == null -> StepFocusUiState.NoSteps("")
            stepList.isEmpty() -> StepFocusUiState.NoSteps(title)
            stepList.all { it.done } -> StepFocusUiState.AllDone(title, canFinishTask = task.status != TaskStatus.DONE)
            else -> {
                val current = StepQueue.current(stepList, aside)
                if (current == null) {
                    StepFocusUiState.Paused(title)
                } else {
                    val ordered = stepList.sortedWith(compareBy({ it.position }, { it.id }))
                    StepFocusUiState.Current(title, current, number = ordered.indexOf(current) + 1, total = ordered.size)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StepFocusUiState.Loading,
    )

    init {
        viewModelScope.launch {
            task.value = tasks.getById(taskId)
            loaded.value = true
        }
    }

    /** El paso actual está hecho. */
    fun done() {
        val current = (uiState.value as? StepFocusUiState.Current)?.step ?: return
        viewModelScope.launch { setStepDone(current, true) }
    }

    /** «Ahora no»: aparta el paso por esta visita y pasa al siguiente, sin marcar nada. */
    fun notNow() {
        val current = (uiState.value as? StepFocusUiState.Current)?.step ?: return
        setAside.update { it + current.id }
    }

    /** Vuelve a poner delante los pasos que se apartaron. */
    fun reviewAgain() = setAside.update { emptySet() }

    /** El usuario da la tarea por hecha (los pasos completos solo se lo ofrecen). */
    fun finishTask() {
        val current = task.value ?: return
        viewModelScope.launch {
            setTaskDone(current, done = true)
            _events.send(StepFocusEvent.Close)
        }
    }

    companion object {
        const val ARG_TASK_ID = "taskId"
    }
}
