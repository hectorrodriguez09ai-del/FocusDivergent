package com.focusdivergent.app.presentation.closure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.closure.DayClosureSettings
import com.focusdivergent.app.domain.closure.DaySummary
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.usecase.ObserveDaySummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * @property summary nulo hasta que llegan los datos.
 * @property leisureApps cuántas apps tienen la marca «de ocio» y [liftUntil] hasta qué hora quedan libres esta noche;
 * nulo si el Cierre está desactivado.
 */
data class ClosureSummaryUiState(
    val summary: DaySummary? = null,
    val leisureApps: Int = 0,
    val liftUntil: LocalTime? = null,
)

@HiltViewModel
class ClosureSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSummary: ObserveDaySummaryUseCase,
    limits: AppLimitRepository,
    settings: DayClosureSettings,
    clock: Clock,
) : ViewModel() {

    private val day: LocalDate = savedStateHandle.get<Long>(ARG_DAY)
        ?.takeIf { it != 0L }
        ?.let(LocalDate::ofEpochDay)
        ?: clock.instant().atZone(clock.zone).toLocalDate()

    val uiState: StateFlow<ClosureSummaryUiState> = combine(
        observeSummary(day),
        limits.observeAll(),
        settings.state,
    ) { summary, all, state ->
        val config = state.configAt(clock.instant())
        ClosureSummaryUiState(
            summary = summary,
            leisureApps = all.count { it.isLeisure },
            liftUntil = config.liftUntil.takeIf { config.enabled },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ClosureSummaryUiState(),
    )

    companion object {
        const val ARG_DAY = "day"

        /** Cuántos títulos de lo hecho se enseñan antes de resumir el resto en «y N más». */
        const val MAX_TITLES = 6
    }
}
