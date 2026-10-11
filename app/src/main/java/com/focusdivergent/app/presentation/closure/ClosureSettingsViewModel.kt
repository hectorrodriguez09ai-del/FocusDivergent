package com.focusdivergent.app.presentation.closure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.closure.DayClosureConfig
import com.focusdivergent.app.domain.closure.DayClosureSettings
import com.focusdivergent.app.domain.closure.PendingClosureConfig
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.usecase.ChangeClosureSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * @property effective lo que rige ahora.
 * @property pending el cambio que entrará en vigor a medianoche, si lo hay. Lo que se enseña como elegido es
 * [chosen]: lo último que pidió el usuario, aunque aún no rija.
 * @property leisureApps cuántas apps tienen la marca «de ocio».
 */
data class ClosureSettingsUiState(
    val effective: DayClosureConfig = DayClosureConfig(),
    val pending: PendingClosureConfig? = null,
    val leisureApps: Int = 0,
) {
    val chosen: DayClosureConfig get() = pending?.config ?: effective
}

@HiltViewModel
class ClosureSettingsViewModel @Inject constructor(
    settings: DayClosureSettings,
    limits: AppLimitRepository,
    private val change: ChangeClosureSettingsUseCase,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<ClosureSettingsUiState> = combine(settings.state, limits.observeAll()) { state, all ->
        val now = clock.instant()
        // Un cambio cuya hora ya llegó ha pasado a ser lo que rige: no se enseña como pendiente.
        val due = state.pending?.takeIf { now >= it.appliesAt }
        ClosureSettingsUiState(
            effective = due?.config ?: state.effective,
            pending = state.pending.takeIf { due == null },
            leisureApps = all.count { it.isLeisure },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ClosureSettingsUiState(),
    )

    fun setEnabled(enabled: Boolean) = change(uiState.value.chosen.copy(enabled = enabled))

    fun setCloseAt(time: LocalTime) = change(uiState.value.chosen.copy(closeAt = time))

    fun setLiftUntil(time: LocalTime) = change(uiState.value.chosen.copy(liftUntil = time))

    fun cancelPending() = change.cancelPending()

    /** Hoy, en días desde 1970: el día del resumen que se abre desde aquí. */
    fun today(): Long = clock.instant().atZone(clock.zone).toLocalDate().toEpochDay()
}
