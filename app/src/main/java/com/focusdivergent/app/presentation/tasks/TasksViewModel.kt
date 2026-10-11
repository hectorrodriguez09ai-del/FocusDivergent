package com.focusdivergent.app.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.tasks.AgendaItem
import com.focusdivergent.app.domain.tasks.TaskAgenda
import com.focusdivergent.app.domain.tasks.TaskFilter
import com.focusdivergent.app.domain.tasks.TaskSection
import com.focusdivergent.app.domain.tasks.TaskTrash
import com.focusdivergent.app.domain.usecase.RestoreDeletedTaskUseCase
import com.focusdivergent.app.domain.usecase.SetTaskDoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Un bloque de la lista. [section] es nulo en la agenda de un día que no es hoy, que es un único bloque sin título.
 */
data class TaskSectionUi(
    val section: TaskSection?,
    val items: List<AgendaItem>,
)

data class TasksUiState(
    val today: LocalDate,
    val selectedDay: LocalDate,
    val week: List<LocalDate>,
    val filter: TaskFilter = TaskFilter(),
    val sections: List<TaskSectionUi> = emptyList(),
    /** Hay actividades guardadas, aunque el filtro o el día elegido no enseñen ninguna. */
    val hasAnyTask: Boolean = false,
    /** Actividad recién borrada, mientras se puede deshacer. */
    val deletedTask: Task? = null,
    val loading: Boolean = true,
) {
    val isToday: Boolean get() = selectedDay == today

    /** Marcar como hecha solo tiene sentido hasta hoy: una rutina de un día futuro aún no se puede haber hecho. */
    val canToggleDone: Boolean get() = selectedDay <= today
}

/** Lo que elige el usuario en la pantalla: el día que mira y el filtro. */
private data class Selection(val today: LocalDate, val day: LocalDate, val filter: TaskFilter)

@HiltViewModel
class TasksViewModel @Inject constructor(
    tasks: TaskRepository,
    private val completions: RoutineCompletionRepository,
    private val trash: TaskTrash,
    private val setTaskDone: SetTaskDoneUseCase,
    private val restoreDeletedTask: RestoreDeletedTaskUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    private fun today(): LocalDate = clock.instant().atZone(clock.zone).toLocalDate()

    private val selection = MutableStateFlow(today().let { Selection(today = it, day = it, filter = TaskFilter()) })

    @OptIn(ExperimentalCoroutinesApi::class)
    private val routinesDone = selection
        .map { it.day }
        .distinctUntilChanged()
        .flatMapLatest { completions.observeCompletedOn(it) }

    val uiState: StateFlow<TasksUiState> =
        combine(selection, tasks.observeAll(), routinesDone, trash.lastDeleted) { selection, all, done, deleted ->
            val sections = if (selection.day == selection.today) {
                TaskAgenda.overview(all, done, selection.today, clock.zone, selection.filter)
                    .map { (section, items) -> TaskSectionUi(section, items) }
            } else {
                TaskAgenda.forDay(all, done, selection.day, clock.zone, selection.filter)
                    .takeIf { it.isNotEmpty() }
                    ?.let { listOf(TaskSectionUi(section = null, items = it)) }
                    .orEmpty()
            }
            TasksUiState(
                today = selection.today,
                selectedDay = selection.day,
                week = TaskAgenda.weekOf(selection.day, firstDayOfWeek),
                filter = selection.filter,
                sections = sections,
                hasAnyTask = all.isNotEmpty(),
                deletedTask = deleted,
                loading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = selection.value.let {
                TasksUiState(today = it.today, selectedDay = it.day, week = TaskAgenda.weekOf(it.day, firstDayOfWeek))
            },
        )

    /**
     * Al volver a la pantalla puede haber cambiado el día (la app quedó abierta por la noche). Si se estaba
     * mirando «hoy», se sigue mirando hoy.
     */
    fun refreshToday() {
        val now = today()
        selection.update { if (it.today == now) it else it.copy(today = now, day = if (it.day == it.today) now else it.day) }
    }

    fun selectDay(day: LocalDate) = selection.update { it.copy(day = day) }

    fun previousWeek() = selection.update { it.copy(day = it.day.minusWeeks(1)) }

    fun nextWeek() = selection.update { it.copy(day = it.day.plusWeeks(1)) }

    fun goToToday() = selection.update { it.copy(day = it.today) }

    /** Tocar el filtro que ya está puesto lo quita. */
    fun toggleEnergyFilter(level: EnergyLevel) = selection.update { current ->
        current.copy(filter = current.filter.copy(energy = if (current.filter.energy == level) null else level))
    }

    fun toggleTypeFilter(type: TaskType) = selection.update { current ->
        current.copy(filter = current.filter.copy(type = if (current.filter.type == type) null else type))
    }

    fun clearFilters() = selection.update { it.copy(filter = TaskFilter()) }

    fun setDone(item: AgendaItem, done: Boolean) {
        val day = selection.value.day
        viewModelScope.launch { setTaskDone(item.task, done, day) }
    }

    fun undoDelete() {
        viewModelScope.launch { restoreDeletedTask() }
    }

    /** El aviso de «borrada» se cerró sin deshacer: el borrado pasa a ser definitivo. */
    fun dismissDeleted(task: Task) = trash.clear(task)
}
