package com.focusdivergent.app.presentation.launch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.core.permissions.CriticalPermission
import com.focusdivergent.app.core.permissions.PermissionManager
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.tasks.TaskAgenda
import com.focusdivergent.app.domain.tasks.TaskSection
import com.focusdivergent.app.domain.usecase.StartFocusLaunchUseCase
import com.focusdivergent.app.service.BlockCheckTrigger
import com.focusdivergent.app.service.BlockerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property candidates las actividades entre las que se puede elegir qué empezar (pocas, a propósito).
 * @property selectedId la elegida; por defecto la que ya está en marcha o la primera. Nulo es «lo que sea».
 * @property ready se puede lanzar: la protección está activa y se tiene permiso para mostrarse sobre otras apps.
 */
data class FocusLaunchUiState(
    val candidates: List<Task> = emptyList(),
    val selectedId: Long? = null,
    val protectionRunning: Boolean = false,
    val overlayGranted: Boolean = false,
    val loading: Boolean = true,
) {
    val ready: Boolean get() = protectionRunning && overlayGranted
}

sealed interface FocusLaunchEvent {
    /** La Lanzadera ya está en marcha: se vuelve a «Hoy» y el bloqueo cubre el móvil. */
    data object Started : FocusLaunchEvent
}

/** Lo que el usuario ha elegido empezar. */
private sealed interface Choice {
    /** Aún no ha tocado nada: se usa la sugerencia, para ahorrarle una decisión. */
    data object Suggested : Choice

    /** «Lo que sea; solo empezar». */
    data object Anything : Choice

    data class Specific(val taskId: Long) : Choice
}

@HiltViewModel
class FocusLaunchViewModel @Inject constructor(
    tasks: TaskRepository,
    completions: RoutineCompletionRepository,
    blockerStatus: BlockerStatus,
    private val permissions: PermissionManager,
    private val startFocusLaunch: StartFocusLaunchUseCase,
    private val trigger: BlockCheckTrigger,
    private val clock: Clock,
) : ViewModel() {

    private val choice = MutableStateFlow<Choice>(Choice.Suggested)
    private val overlayGranted = MutableStateFlow(permissions.isGranted(CriticalPermission.OVERLAY))

    private val _events = Channel<FocusLaunchEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<FocusLaunchUiState> = combine(
        tasks.observeAll(),
        completions.observeCompletedOn(clock.instant().atZone(clock.zone).toLocalDate()),
        blockerStatus.isRunning,
        overlayGranted,
        choice,
    ) { all, routinesDone, running, overlay, chosen ->
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val sections = TaskAgenda.overview(all, routinesDone, today, clock.zone)
        // Lo que se puede empezar ahora: lo que ya está en marcha, lo de hoy y lo que no tiene fecha. Sin
        // Obligaciones, que tienen su hora y su aviso.
        val candidates = (sections[TaskSection.TODAY].orEmpty() + sections[TaskSection.SOMEDAY].orEmpty())
            .map { it.task }
            .filter { it.type != TaskType.OBLIGATION }
            .sortedByDescending { it.status == TaskStatus.IN_PROGRESS }
            .take(MAX_CANDIDATES)
        FocusLaunchUiState(
            candidates = candidates,
            selectedId = when (chosen) {
                Choice.Suggested -> candidates.firstOrNull()?.id
                Choice.Anything -> null
                is Choice.Specific -> chosen.taskId.takeIf { id -> candidates.any { it.id == id } } ?: candidates.firstOrNull()?.id
            },
            protectionRunning = running,
            overlayGranted = overlay,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FocusLaunchUiState(
            protectionRunning = blockerStatus.isRunning.value,
            overlayGranted = overlayGranted.value,
        ),
    )

    /** @param taskId la actividad que se va a empezar, o nulo para «lo que sea». */
    fun select(taskId: Long?) = choice.update { if (taskId == null) Choice.Anything else Choice.Specific(taskId) }

    /** Los permisos se conceden fuera de la app: al volver a la pantalla hay que recomprobarlos. */
    fun refreshPermissions() {
        overlayGranted.value = permissions.isGranted(CriticalPermission.OVERLAY)
    }

    fun start() {
        val state = uiState.value
        if (!state.ready) return
        val task = state.candidates.firstOrNull { it.id == state.selectedId }
        viewModelScope.launch {
            if (startFocusLaunch(task)) {
                // El servicio la recoge al instante, sin esperar a su ciclo de 5 segundos.
                trigger.request()
                _events.send(FocusLaunchEvent.Started)
            }
        }
    }

    private companion object {
        const val MAX_CANDIDATES = 4
    }
}
