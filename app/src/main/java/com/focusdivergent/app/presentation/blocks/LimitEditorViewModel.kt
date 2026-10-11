package com.focusdivergent.app.presentation.blocks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.FrictionLevel
import com.focusdivergent.app.domain.model.PendingLimitChange
import com.focusdivergent.app.domain.model.appLimitOf
import com.focusdivergent.app.domain.model.friction
import com.focusdivergent.app.domain.policy.LimitChangePlan
import com.focusdivergent.app.domain.policy.LimitChangePolicy
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.repository.InstalledAppsRepository
import com.focusdivergent.app.domain.repository.PendingLimitChangeRepository
import com.focusdivergent.app.domain.repository.UsageRepository
import com.focusdivergent.app.domain.time.startOfTomorrow
import com.focusdivergent.app.domain.usecase.CancelPendingLimitChangeUseCase
import com.focusdivergent.app.domain.usecase.SaveAppLimitUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cuándo entraría en vigor lo que se está editando, tal como se le explica al usuario antes de guardar. */
enum class ChangeTiming {
    /** La app aún no tenía límite: empieza ya. */
    NEW,

    /** Lo editado es igual a lo que ya hay. */
    UNCHANGED,

    /** Solo endurece: entra en vigor ahora. */
    NOW,

    /** Relaja: entra en vigor a medianoche. */
    TOMORROW,

    /** Una parte endurece (rige ya) y otra relaja (a medianoche). */
    PARTIAL,
}

data class LimitEditorUiState(
    val packageName: String,
    val label: String = "",
    val minutes: Int = DEFAULT_MINUTES,
    val friction: FrictionLevel = FrictionLevel.FLEXIBLE,
    /** App «de ocio»: el Cierre de Jornada la deja libre de noche. */
    val leisure: Boolean = false,
    /** Ya tiene un límite guardado (se está editando, no creando). */
    val hasLimit: Boolean = false,
    /** Tiempo de uso de hoy, para poner el límite con datos reales delante. */
    val usedMillis: Long = 0L,
    val pending: PendingLimitChange? = null,
    val timing: ChangeTiming = ChangeTiming.NEW,
    val loading: Boolean = true,
) {
    companion object {
        const val DEFAULT_MINUTES = 30
    }
}

sealed interface LimitEditorEvent {
    data object Close : LimitEditorEvent
}

@HiltViewModel
class LimitEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val limits: AppLimitRepository,
    private val pendingChanges: PendingLimitChangeRepository,
    private val installedApps: InstalledAppsRepository,
    private val usage: UsageRepository,
    private val saveLimit: SaveAppLimitUseCase,
    private val cancelPending: CancelPendingLimitChangeUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val packageName: String = checkNotNull(savedStateHandle.get<String>(ARG_PACKAGE_NAME)) {
        "El editor de límites necesita el paquete de la app"
    }

    private val _uiState = MutableStateFlow(LimitEditorUiState(packageName = packageName))
    val uiState: StateFlow<LimitEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<LimitEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Límite que rige ahora: es con lo que se compara lo que se edita para saber cuándo entraría en vigor.
    private var current: AppLimit? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val limit = limits.getByPackage(packageName)
            val pending = pendingChanges.get(packageName)
            current = limit

            // Se parte de lo último que el usuario decidió: el cambio pendiente si lo hay, si no, el límite actual.
            val draft = pending?.newLimit ?: limit
            val used = try {
                usage.readToday().usedMillisByPackage[packageName] ?: 0L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                0L
            }
            _uiState.update {
                it.copy(
                    label = installedApps.labelOf(packageName) ?: packageName,
                    minutes = draft?.dailyMinutesAllowed ?: LimitEditorUiState.DEFAULT_MINUTES,
                    friction = draft?.friction ?: FrictionLevel.FLEXIBLE,
                    leisure = draft?.isLeisure ?: false,
                    hasLimit = limit != null,
                    usedMillis = used,
                    pending = pending,
                    loading = false,
                ).withTiming()
            }
        }
    }

    fun setMinutes(minutes: Int) = _uiState.update {
        it.copy(minutes = minutes.coerceIn(SaveAppLimitUseCase.MIN_MINUTES, SaveAppLimitUseCase.MAX_MINUTES)).withTiming()
    }

    fun setFriction(friction: FrictionLevel) = _uiState.update { it.copy(friction = friction).withTiming() }

    fun setLeisure(leisure: Boolean) = _uiState.update { it.copy(leisure = leisure).withTiming() }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            saveLimit(packageName, appLimitOf(packageName, state.minutes, state.friction, state.leisure))
            _events.send(LimitEditorEvent.Close)
        }
    }

    /** Quita el límite en el acto. La pantalla solo llama aquí después de resolver el reto matemático. */
    fun remove() {
        viewModelScope.launch {
            saveLimit(packageName, null)
            _events.send(LimitEditorEvent.Close)
        }
    }

    /** Descarta el cambio que esperaba a medianoche: el límite actual sigue como estaba. */
    fun cancelPendingChange() {
        viewModelScope.launch {
            cancelPending(packageName)
            load()
        }
    }

    // Calcula cuándo entraría en vigor lo editado, con la misma regla que luego aplica SaveAppLimitUseCase.
    private fun LimitEditorUiState.withTiming(): LimitEditorUiState {
        val desired = appLimitOf(packageName, minutes, friction, leisure)
        val plan = LimitChangePolicy.plan(packageName, current, desired, clock.startOfTomorrow())
        val timing = when {
            current == null -> ChangeTiming.NEW
            plan is LimitChangePlan.NoChange -> ChangeTiming.UNCHANGED
            plan is LimitChangePlan.ApplyNow -> ChangeTiming.NOW
            plan is LimitChangePlan.Schedule && plan.applyNow != null -> ChangeTiming.PARTIAL
            else -> ChangeTiming.TOMORROW
        }
        return copy(timing = timing)
    }

    companion object {
        const val ARG_PACKAGE_NAME = "packageName"
    }
}
