package com.focusdivergent.app.presentation.blocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.PendingLimitChange
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.repository.InstalledAppsRepository
import com.focusdivergent.app.domain.repository.PendingLimitChangeRepository
import com.focusdivergent.app.domain.repository.UsageRepository
import com.focusdivergent.app.domain.usecase.ApplyDueLimitChangesUseCase
import com.focusdivergent.app.service.BlockerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Una app con límite en la lista.
 *
 * @property label nombre de la app, o su nombre de paquete si ya no está instalada.
 * @property usedMillis tiempo de uso de hoy.
 * @property pending cambio que espera a medianoche, si lo hay.
 */
data class LimitItem(
    val limit: AppLimit,
    val label: String,
    val usedMillis: Long,
    val pending: PendingLimitChange?,
)

data class BlocksUiState(
    val items: List<LimitItem> = emptyList(),
    val protectionRunning: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class BlocksViewModel @Inject constructor(
    limits: AppLimitRepository,
    pending: PendingLimitChangeRepository,
    private val usage: UsageRepository,
    private val installedApps: InstalledAppsRepository,
    blockerStatus: BlockerStatus,
    private val applyDueLimitChanges: ApplyDueLimitChangesUseCase,
) : ViewModel() {

    // Los nombres no cambian mientras la pantalla está abierta: se piden una vez por app.
    private val labels = ConcurrentHashMap<String, String>()

    val uiState: StateFlow<BlocksUiState> = combine(
        limits.observeAll(),
        pending.observeAll(),
        usedTodayTicker(),
        blockerStatus.isRunning,
    ) { limitList, pendingList, used, running ->
        val pendingByPackage = pendingList.associateBy { it.packageName }
        BlocksUiState(
            items = limitList
                .map { limit ->
                    LimitItem(
                        limit = limit,
                        label = labelOf(limit.packageName),
                        usedMillis = used[limit.packageName] ?: 0L,
                        pending = pendingByPackage[limit.packageName],
                    )
                }
                .sortedBy { it.label.lowercase() },
            protectionRunning = running,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BlocksUiState(protectionRunning = blockerStatus.isRunning.value),
    )

    /** Al abrir la pantalla, lo que esperaba a medianoche y ya ha llegado a su hora entra en vigor. */
    fun refresh() {
        viewModelScope.launch {
            try {
                applyDueLimitChanges()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Si falla no pasa nada: el servicio de bloqueo lo vuelve a intentar en su siguiente ciclo.
            }
        }
    }

    private suspend fun labelOf(packageName: String): String =
        labels[packageName] ?: (installedApps.labelOf(packageName) ?: packageName).also { labels[packageName] = it }

    // Uso de hoy por app, refrescado mientras la pantalla se ve. Sin el permiso de uso, lectura vacía.
    private fun usedTodayTicker(): Flow<Map<String, Long>> = flow {
        while (true) {
            emit(
                try {
                    usage.readToday().usedMillisByPackage
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyMap()
                },
            )
            delay(USAGE_REFRESH_MS)
        }
    }

    private companion object {
        const val USAGE_REFRESH_MS = 15_000L
    }
}
