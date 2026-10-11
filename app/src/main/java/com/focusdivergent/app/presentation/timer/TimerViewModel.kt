package com.focusdivergent.app.presentation.timer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.repository.TaskStepRepository
import com.focusdivergent.app.domain.steps.StepProgress
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerPlan
import com.focusdivergent.app.domain.timer.TimerSession
import com.focusdivergent.app.domain.timer.TimerSettings
import com.focusdivergent.app.domain.timer.TimerState
import com.focusdivergent.app.domain.timer.TimerStore
import com.focusdivergent.app.domain.usecase.FinishTimerUseCase
import com.focusdivergent.app.domain.usecase.StartTimerUseCase
import com.focusdivergent.app.domain.usecase.StopTimerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TimerKind { SINGLE, POMODORO }

/**
 * @property task la tarea con la que se va a empezar una sesión nueva (si se llegó desde una tarea).
 * @property session la sesión en curso, si la hay; entonces [state] dice en qué punto va ahora mismo.
 */
data class TimerUiState(
    val kind: TimerKind = TimerKind.SINGLE,
    val single: TimerMode.Single,
    val pomodoro: TimerMode.Pomodoro,
    val task: Task? = null,
    val session: TimerSession? = null,
    val state: TimerState? = null,
    /** Los micro-pasos de la tarea a la que acompaña el temporizador, si la hay y los tiene. */
    val steps: StepProgress = StepProgress.NONE,
) {
    /** La configuración que se aplicaría al pulsar «Empezar». */
    val selectedMode: TimerMode get() = if (kind == TimerKind.SINGLE) single else pomodoro
}

sealed interface TimerEvent {
    /** La sesión terminada ya se cerró: la pantalla no tiene nada más que enseñar. */
    data object Close : TimerEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TaskRepository,
    taskSteps: TaskStepRepository,
    private val store: TimerStore,
    settings: TimerSettings,
    private val startTimer: StartTimerUseCase,
    private val stopTimer: StopTimerUseCase,
    private val finishTimer: FinishTimerUseCase,
    private val clock: Clock,
) : ViewModel() {

    // 0 = temporizador suelto (los ids de la base empiezan en 1).
    private val taskId: Long = savedStateHandle.get<Long>(ARG_TASK_ID) ?: 0L

    private data class Setup(val kind: TimerKind, val single: TimerMode.Single, val pomodoro: TimerMode.Pomodoro)

    private val setup = MutableStateFlow(Setup(TimerKind.SINGLE, settings.single(), settings.pomodoro()))
    private val task = MutableStateFlow<Task?>(null)

    private val _events = Channel<TimerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Mientras hay una sesión, la hora se renueva cada segundo; sin sesión no hay nada que renovar.
    private val now: Flow<Instant> = store.session.flatMapLatest { session ->
        if (session == null) {
            flowOf(clock.instant())
        } else {
            flow {
                while (true) {
                    emit(clock.instant())
                    delay(TICK_MS)
                }
            }
        }
    }

    // La tarea de la sesión en curso o, si aún no hay sesión, aquella desde la que se llegó.
    private val steps: Flow<StepProgress> = store.session
        .map { session -> session?.taskId ?: taskId.takeIf { it != 0L } }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(StepProgress.NONE) else taskSteps.observeForTask(id).map { StepProgress.of(it) }
        }

    val uiState: StateFlow<TimerUiState> = combine(setup, task, store.session, now, steps) { setup, task, session, now, steps ->
        TimerUiState(
            kind = setup.kind,
            single = setup.single,
            pomodoro = setup.pomodoro,
            task = task,
            session = session,
            state = session?.let { TimerPlan.stateAt(it, now) },
            steps = steps,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TimerUiState(single = settings.single(), pomodoro = settings.pomodoro()),
    )

    init {
        if (taskId != 0L) viewModelScope.launch { task.value = tasks.getById(taskId) }
    }

    fun setKind(kind: TimerKind) = setup.update { it.copy(kind = kind) }

    fun setSingleMinutes(minutes: Int) = setup.update { it.copy(single = TimerMode.Single(minutes)) }

    fun setFocusMinutes(minutes: Int) = setup.update { it.copy(pomodoro = it.pomodoro.copy(focusMinutes = minutes)) }

    fun setBreakMinutes(minutes: Int) = setup.update { it.copy(pomodoro = it.pomodoro.copy(breakMinutes = minutes)) }

    fun setCycles(cycles: Int) = setup.update { it.copy(pomodoro = it.pomodoro.copy(cycles = cycles)) }

    fun start() {
        val current = setup.value
        val mode = if (current.kind == TimerKind.SINGLE) current.single else current.pomodoro
        viewModelScope.launch { startTimer(mode, task.value) }
    }

    fun stop() {
        viewModelScope.launch { stopTimer() }
    }

    /** Cierra la sesión terminada. [taskDone]: el usuario dice que terminó la tarea a la que acompañaba. */
    fun finish(taskDone: Boolean) {
        viewModelScope.launch {
            finishTimer(taskDone)
            _events.send(TimerEvent.Close)
        }
    }

    companion object {
        const val ARG_TASK_ID = "taskId"
        private const val TICK_MS = 1_000L
    }
}
