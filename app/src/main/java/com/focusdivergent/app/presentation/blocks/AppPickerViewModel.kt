package com.focusdivergent.app.presentation.blocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.device.DeviceState
import com.focusdivergent.app.domain.model.InstalledApp
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.repository.InstalledAppsRepository
import com.focusdivergent.app.domain.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Una app que se puede elegir, con lo que ya lleva hoy y si ya tiene límite. */
data class PickerApp(
    val app: InstalledApp,
    val usedMillis: Long,
    val hasLimit: Boolean,
)

data class AppPickerUiState(
    val query: String = "",
    val apps: List<PickerApp> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    limits: AppLimitRepository,
    private val installedApps: InstalledAppsRepository,
    private val usage: UsageRepository,
    private val device: DeviceState,
    @Named("ownPackage") private val ownPackage: String,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val candidates = MutableStateFlow<List<InstalledApp>?>(null)
    private val usedToday = MutableStateFlow<Map<String, Long>>(emptyMap())

    init {
        viewModelScope.launch {
            // La propia app no se bloquea nunca, y el teléfono y las llamadas quedan fuera por seguridad: no tiene
            // sentido ofrecerlas.
            val excluded = device.safePackages() + ownPackage
            candidates.value = installedApps.launchableApps().filter { it.packageName !in excluded }
            usedToday.value = try {
                usage.readToday().usedMillisByPackage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    val uiState: StateFlow<AppPickerUiState> = combine(
        query, candidates, usedToday, limits.observeAll(),
    ) { query, candidates, used, limitList ->
        val limited = limitList.map { it.packageName }.toSet()
        val needle = query.normalizedForSearch()
        AppPickerUiState(
            query = query,
            apps = candidates.orEmpty()
                .filter { needle.isEmpty() || it.label.normalizedForSearch().contains(needle) }
                .map { PickerApp(it, used[it.packageName] ?: 0L, it.packageName in limited) }
                // Primero las que más se usan hoy (suelen ser las que se quieren limitar), luego por nombre.
                .sortedWith(compareByDescending<PickerApp> { it.usedMillis }.thenBy { it.app.label.lowercase() }),
            loading = candidates == null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPickerUiState(),
    )

    fun setQuery(text: String) = query.update { text }
}

/** Minúsculas y sin acentos, para que «camara» encuentre «Cámara». */
internal fun String.normalizedForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
