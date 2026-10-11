package com.focusdivergent.app.presentation.chooseforme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.settings.DailyEnergyStore
import com.focusdivergent.app.domain.tasks.TaskChooser
import com.focusdivergent.app.domain.usecase.StartTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Los pasos de «Elige por mí»: como mucho dos preguntas, una por pantalla, y la propuesta. */
enum class ChooseStep {
    /** ¿Con cuánta energía estás? Solo se pregunta si hoy no se ha dicho ya. */
    ENERGY,

    /** ¿De cuánto tiempo dispones? */
    TIME,

    /** Una sola actividad propuesta. */
    RESULT,

    /** Con esa energía y ese tiempo no hay nada que proponer. */
    NONE_FOUND,
}

/**
 * @property minutes tiempo elegido; nulo es «sin prisa».
 * @property pick la actividad propuesta.
 * @property canAskAnother quedan alternativas y no se ha llegado al máximo de cambios.
 */
data class ChooseForMeUiState(
    val step: ChooseStep,
    val energy: EnergyLevel? = null,
    val minutes: Int? = null,
    val pick: Task? = null,
    val canAskAnother: Boolean = false,
)

/** Sucesos de una sola vez, por un canal para que un giro de pantalla no los repita. */
sealed interface ChooseForMeEvent {
    /** Se empezó la actividad: se vuelve a «Hoy». */
    data object Started : ChooseForMeEvent

    /** Se agotaron los cambios: que mire su lista. */
    data object SeeList : ChooseForMeEvent

    /** No había nada que proponer: crear una tarea rápida. */
    data object CreateTask : ChooseForMeEvent
}

@HiltViewModel
class ChooseForMeViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val completions: RoutineCompletionRepository,
    private val energyStore: DailyEnergyStore,
    private val startTask: StartTaskUseCase,
    private val clock: Clock,
    private val random: Random,
) : ViewModel() {

    private val today: LocalDate = clock.instant().atZone(clock.zone).toLocalDate()

    // La energía se recuerda todo el día: si ya se dijo, no se vuelve a preguntar.
    private val _uiState = MutableStateFlow(
        energyStore.get(today).let { energy ->
            ChooseForMeUiState(step = if (energy == null) ChooseStep.ENERGY else ChooseStep.TIME, energy = energy)
        },
    )
    val uiState: StateFlow<ChooseForMeUiState> = _uiState.asStateFlow()

    private val _events = Channel<ChooseForMeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Lo ya enseñado en esta ronda y cuántas veces se ha pedido otra.
    private val seen = mutableSetOf<Long>()
    private var othersShown = 0

    fun setEnergy(level: EnergyLevel) {
        energyStore.set(today, level)
        _uiState.update { it.copy(step = ChooseStep.TIME, energy = level, pick = null) }
    }

    fun changeEnergy() = _uiState.update { it.copy(step = ChooseStep.ENERGY) }

    fun backToTime() = _uiState.update { it.copy(step = ChooseStep.TIME, pick = null) }

    /** @param minutes tiempo del que dispone, o nulo si no tiene prisa. */
    fun chooseTime(minutes: Int?) {
        seen.clear()
        othersShown = 0
        _uiState.update { it.copy(minutes = minutes) }
        viewModelScope.launch { propose() }
    }

    /** «Otra»: vale [MAX_OTHERS] veces; pasado eso, el botón se convierte en «Ver mi lista». */
    fun another() {
        if (!_uiState.value.canAskAnother) return
        othersShown++
        viewModelScope.launch { propose() }
    }

    fun start() {
        val pick = _uiState.value.pick ?: return
        viewModelScope.launch {
            startTask(pick)
            _events.send(ChooseForMeEvent.Started)
        }
    }

    fun seeList() {
        viewModelScope.launch { _events.send(ChooseForMeEvent.SeeList) }
    }

    fun createTask() {
        viewModelScope.launch { _events.send(ChooseForMeEvent.CreateTask) }
    }

    private suspend fun propose() {
        val state = _uiState.value
        val energy = state.energy ?: return
        val pick = TaskChooser.pick(
            tasks = tasks.observeAll().first(),
            routinesDoneToday = completions.observeCompletedOn(today).first(),
            today = today,
            zone = clock.zone,
            energy = energy,
            availableMinutes = state.minutes,
            random = random,
            exclude = seen,
        )
        if (pick == null) {
            _uiState.update { it.copy(step = ChooseStep.NONE_FOUND, pick = null, canAskAnother = false) }
            return
        }
        seen += pick.task.id
        _uiState.update {
            it.copy(
                step = ChooseStep.RESULT,
                pick = pick.task,
                canAskAnother = othersShown < MAX_OTHERS && pick.othersLeft > 0,
            )
        }
    }

    companion object {
        /** Cuántas veces se puede pedir «Otra». Más sería cambiar la parálisis por análisis por otra parálisis. */
        const val MAX_OTHERS = 2
    }
}
