package com.focusdivergent.app.presentation.closure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.closure.ClosureOptions
import com.focusdivergent.app.domain.closure.PendingClosureConfig
import com.focusdivergent.app.presentation.common.formatShort
import com.focusdivergent.app.presentation.components.FocusCard
import com.focusdivergent.app.presentation.components.LinkCard
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing
import java.time.LocalTime

/**
 * Ajustes del Cierre de Jornada. Los cambios se aplican al tocar, con la regla de los límites: lo que da más margen a
 * las apps de ocio rige desde la medianoche y lo que da menos, al momento. La pantalla lo dice siempre.
 */
@Composable
fun ClosureSettingsScreen(
    onBack: () -> Unit,
    onOpenSummary: (day: Long) -> Unit,
    viewModel: ClosureSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ClosureSettingsContent(
        uiState = uiState,
        onBack = onBack,
        onEnabled = viewModel::setEnabled,
        onCloseAt = viewModel::setCloseAt,
        onLiftUntil = viewModel::setLiftUntil,
        onCancelPending = viewModel::cancelPending,
        onOpenSummary = { onOpenSummary(viewModel.today()) },
    )
}

@Composable
fun ClosureSettingsContent(
    uiState: ClosureSettingsUiState,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onCloseAt: (LocalTime) -> Unit,
    onLiftUntil: (LocalTime) -> Unit,
    onCancelPending: () -> Unit,
    onOpenSummary: () -> Unit,
) {
    val chosen = uiState.chosen
    ScreenScaffold(title = stringResource(R.string.closure_title), onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.closure_settings_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Toda la fila es el interruptor: un objetivo grande y con su texto.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .toggleable(value = chosen.enabled, role = Role.Switch, onValueChange = onEnabled),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.closure_enabled),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = chosen.enabled, onCheckedChange = null)
            }

            uiState.pending?.let { PendingCard(it, onCancelPending) }

            if (chosen.enabled) {
                TimeChoice(
                    header = stringResource(R.string.closure_time_header),
                    options = ClosureOptions.CLOSE_TIMES,
                    selected = chosen.closeAt,
                    onSelect = onCloseAt,
                )
                TimeChoice(
                    header = stringResource(R.string.closure_lift_header),
                    options = ClosureOptions.LIFT_TIMES,
                    selected = chosen.liftUntil,
                    onSelect = onLiftUntil,
                )
                Text(
                    text = stringResource(R.string.closure_timing_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader(stringResource(R.string.closure_what_header))
            FocusCard {
                listOf(
                    R.string.closure_what_summary,
                    R.string.closure_what_move,
                    R.string.closure_what_inbox,
                    R.string.closure_what_leisure,
                ).forEach { Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }

            Text(
                text = pluralStringResource(R.plurals.closure_leisure_apps, uiState.leisureApps, uiState.leisureApps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LinkCard(
                title = stringResource(R.string.closure_open_summary_title),
                description = stringResource(R.string.closure_open_summary_description),
                onClick = onOpenSummary,
            )
        }
    }
}

@Composable
private fun TimeChoice(header: String, options: List<LocalTime>, selected: LocalTime, onSelect: (LocalTime) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(header)
        // FlowRow: con la letra del sistema muy grande los atajos pasan a otra línea en vez de cortarse.
        FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            options.forEach { time ->
                FilterChip(
                    selected = time == selected,
                    onClick = { onSelect(time) },
                    label = { Text(time.formatShort()) },
                    modifier = Modifier.heightIn(min = Sizes.touchTarget),
                )
            }
        }
    }
}

/** El cambio que ya está pedido y entra en vigor a medianoche, con la posibilidad de descartarlo. */
@Composable
private fun PendingCard(pending: PendingClosureConfig, onCancel: () -> Unit) {
    val config = pending.config
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        // Ámbar: es un aviso para leer, no una alarma.
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = stringResource(R.string.closure_pending_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (config.enabled) {
                    stringResource(R.string.closure_pending_on, config.closeAt.formatShort(), config.liftUntil.formatShort())
                } else {
                    stringResource(R.string.closure_pending_off)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(stringResource(R.string.closure_pending_cancel))
            }
        }
    }
}
