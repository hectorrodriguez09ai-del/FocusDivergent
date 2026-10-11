package com.focusdivergent.app.presentation.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.audio.AmbientAudio
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.repository.TaskStepRepository
import com.focusdivergent.app.domain.settings.DailyEnergyStore
import com.focusdivergent.app.domain.steps.StepProgress
import com.focusdivergent.app.domain.tasks.AgendaItem
import com.focusdivergent.app.domain.tasks.TaskAgenda
import com.focusdivergent.app.domain.tasks.TaskSection
import com.focusdivergent.app.domain.timer.TimerStore
import com.focusdivergent.app.domain.usecase.ObserveProgressUseCase
import com.focusdivergent.app.domain.usecase.Progress
import com.focusdivergent.app.domain.usecase.SetTaskDoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lo que enseña «Hoy». Solo lo justo para decidir qué hacer ahora, sin listas largas.
 *
 * @property nowItems tareas que se han empezado y siguen en marcha.
 * @property nextObligation la próxima Obligación de hoy que aún no ha empezado.
 * @property todayItems hasta [TodayViewModel.MAX_TODAY_ITEMS] actividades pendientes para hoy.
 * @property todayPending cuántas hay pendientes para hoy en total (puede ser más que las que se enseñan).
 * @property doneToday lo completado hoy.
 */
data class TodayUiState(
    val today: LocalDate,
    val energy: EnergyLevel? = null,
    val nowItems: List<AgendaItem> = emptyList(),
    val nextObligation: AgendaItem? = null,
    val todayItems: List<AgendaItem> = emptyList(),
    val todayPending: Int = 0,
    val doneToday: Int = 0,
    val hasAnyTask: Boolean = false,
    /** Hay una sesión del temporizador en curso (o terminada y sin cerrar). */
    val timerActive: Boolean = false,
    /** Hay un sonido ambiente en marcha o en pausa; [audioPlaying] dice si suena. */
    val audioActive: Boolean = false,
    val audioPlaying: Boolean = false,
    /** La mascota, el nivel y la racha; nulo hasta que llegan los primeros datos. */
    val progress: Progress? = null,
    /** Los micro-pasos de las tareas en marcha que los tienen, por id de tarea. */
    val nowSteps: Map<Long, StepProgress> = emptyMap(),
    val loading: Boolean = true,
) {
    val hasMoreToday: Boolean get() = todayPending > todayItems.size
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    tasks: TaskRepository,
    completions: RoutineCompletionRepository,
    steps: TaskStepRepository,
    private val energyStore: DailyEnergyStore,
    private val setTaskDone: SetTaskDoneUseCase,
    timerStore: TimerStore,
    ambientAudio: AmbientAudio,
    observeProgress: ObserveProgressUseCase,
    private val clock: Clock,
) : ViewModel() {

    private fun today(): LocalDate = clock.instant().atZone(clock.zone).toLocalDate()

    private val day = MutableStateFlow(today())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayUiState> = day.flatMapLatest { today ->
        combine(
            agenda(tasks, completions, energyStore, timerStore, ambientAudio, today),
            observeProgress(today),
            steps.observeAll().map { all -> all.groupBy { it.taskId }.mapValues { StepProgress.of(it.value) } },
        ) { state, progress, stepsByTask -> state.copy(progress = progress, nowSteps = stepsByTask) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodayUiState(today = day.value),
    )

    private fun agenda(
        tasks: TaskRepository,
        completions: RoutineCompletionRepository,
        energyStore: DailyEnergyStore,
        timerStore: TimerStore,
        ambientAudio: AmbientAudio,
        today: LocalDate,
    ): Flow<TodayUiState> =
        combine(
            tasks.observeAll(),
            completions.observeCompletedOn(today),
            energyStore.observe(today),
            timerStore.session,
            ambientAudio.state,
        ) { all, routinesDone, energy, timer, audio ->
            val sections = TaskAgenda.overview(all, routinesDone, today, clock.zone)
            val now = clock.instant()

            val inProgress = sections.values.flatten().filter { it.task.status == TaskStatus.IN_PROGRESS }
            val dueToday = sections[TaskSection.TODAY].orEmpty().filter { it.task.status != TaskStatus.IN_PROGRESS }
            val nextObligation = dueToday.firstOrNull { item ->
                item.task.type == TaskType.OBLIGATION && item.task.startTime?.let { it >= now } == true
            }
            // La próxima Obligación tiene su propia tarjeta: no se repite en la lista.
            val list = dueToday.filter { it != nextObligation }

            TodayUiState(
                today = today,
                energy = energy,
                nowItems = inProgress,
                nextObligation = nextObligation,
                todayItems = list.take(MAX_TODAY_ITEMS),
                todayPending = list.size,
                doneToday = sections[TaskSection.DONE_TODAY]?.size ?: 0,
                hasAnyTask = all.isNotEmpty(),
                timerActive = timer != null,
                audioActive = audio.active,
                audioPlaying = audio.playing,
                loading = false,
            )
        }

    /** Si la app quedó abierta pasada la medianoche, «hoy» pasa a ser el día nuevo. */
    fun refreshToday() = day.update { today() }

    fun setEnergy(level: EnergyLevel) = energyStore.set(day.value, level)

    fun setDone(item: AgendaItem, done: Boolean) {
        viewModelScope.launch { setTaskDone(item.task, done, day.value) }
    }

    /** Deja una tarea en marcha para más tarde: vuelve a pendiente, sin ninguna marca de «atrasada». */
    fun leaveForLater(item: AgendaItem) {
        viewModelScope.launch { setTaskDone(item.task, done = false) }
    }

    companion object {
        const val MAX_TODAY_ITEMS = 3
    }
}
