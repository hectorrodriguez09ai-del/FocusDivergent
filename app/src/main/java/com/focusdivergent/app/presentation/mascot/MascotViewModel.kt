package com.focusdivergent.app.presentation.mascot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.Mascot
import com.focusdivergent.app.domain.reward.MascotSettings
import com.focusdivergent.app.domain.usecase.BuyCosmeticUseCase
import com.focusdivergent.app.domain.usecase.EquipCosmeticUseCase
import com.focusdivergent.app.domain.usecase.ObserveProgressUseCase
import com.focusdivergent.app.domain.usecase.Progress
import com.focusdivergent.app.domain.usecase.PurchaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** @property progress nulo hasta que llegan los primeros datos. */
data class MascotUiState(
    val progress: Progress? = null,
)

sealed interface MascotEvent {
    /** Se compró [cosmetic] y ya lo lleva puesto. */
    data class Bought(val cosmetic: Cosmetic) : MascotEvent

    /** Todavía no hay monedas para [cosmetic]. */
    data class NotEnoughCoins(val cosmetic: Cosmetic) : MascotEvent
}

@HiltViewModel
class MascotViewModel @Inject constructor(
    observeProgress: ObserveProgressUseCase,
    private val settings: MascotSettings,
    private val buyCosmetic: BuyCosmeticUseCase,
    private val equipCosmetic: EquipCosmeticUseCase,
    clock: Clock,
) : ViewModel() {

    private val _events = Channel<MascotEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<MascotUiState> = observeProgress(clock.instant().atZone(clock.zone).toLocalDate())
        .map { MascotUiState(progress = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MascotUiState(),
        )

    /** Cambiar de mascota no toca el nivel: el progreso es del usuario. */
    fun chooseMascot(mascot: Mascot) = settings.update { it.copy(mascot = mascot) }

    /** Compra [cosmetic] si hay monedas; si ya se tiene, lo pone o lo quita. */
    fun onCosmetic(cosmetic: Cosmetic) {
        viewModelScope.launch {
            val owned = uiState.value.progress?.owned.orEmpty()
            if (cosmetic in owned) {
                equipCosmetic(cosmetic)
                return@launch
            }
            when (buyCosmetic(cosmetic)) {
                PurchaseResult.BOUGHT -> _events.send(MascotEvent.Bought(cosmetic))
                PurchaseResult.NOT_ENOUGH_COINS -> _events.send(MascotEvent.NotEnoughCoins(cosmetic))
                PurchaseResult.ALREADY_OWNED -> equipCosmetic(cosmetic)
            }
        }
    }
}
