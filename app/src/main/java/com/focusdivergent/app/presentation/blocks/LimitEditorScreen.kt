package com.focusdivergent.app.presentation.blocks

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.friction.MathChallengeGenerator
import com.focusdivergent.app.domain.model.FrictionLevel
import com.focusdivergent.app.domain.policy.ExtensionPolicy
import com.focusdivergent.app.presentation.components.AppIcon
import com.focusdivergent.app.presentation.components.ChoiceCard
import com.focusdivergent.app.presentation.components.MinutesPicker
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.friction.MathChallengeScreen
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Crear o cambiar el límite de una app. Solo dos decisiones, y una frase que resume el resultado:
 * cuánto tiempo al día y qué cuesta saltárselo (el nivel de fricción).
 */
@Composable
fun LimitEditorScreen(
    onClose: () -> Unit,
    viewModel: LimitEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LimitEditorEvent.Close -> onClose()
            }
        }
    }

    LimitEditorContent(
        uiState = uiState,
        onBack = onClose,
        onMinutes = viewModel::setMinutes,
        onFriction = viewModel::setFriction,
        onLeisure = viewModel::setLeisure,
        onSave = viewModel::save,
        onRemove = viewModel::remove,
        onCancelPending = viewModel::cancelPendingChange,
    )
}

@Composable
fun LimitEditorContent(
    uiState: LimitEditorUiState,
    onBack: () -> Unit,
    onMinutes: (Int) -> Unit,
    onFriction: (FrictionLevel) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onCancelPending: () -> Unit,
    challengeGenerator: MathChallengeGenerator = remember { MathChallengeGenerator() },
    onLeisure: (Boolean) -> Unit = {},
) {
    // Quitar un límite es inmediato, pero antes hay que resolver el reto matemático, como al ganar tiempo extra.
    var removing by rememberSaveable { mutableStateOf(false) }
    if (removing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding(),
        ) {
            MathChallengeScreen(
                onSolved = onRemove,
                onBack = { removing = false },
                titleRes = R.string.challenge_title_remove,
                generator = challengeGenerator,
            )
        }
        return
    }

    ScreenScaffold(
        title = stringResource(if (uiState.hasLimit) R.string.limit_editor_edit else R.string.limit_editor_new),
        onBack = onBack,
        bottomBar = {
            PrimaryActionButton(
                text = stringResource(R.string.action_save),
                onClick = onSave,
                enabled = !uiState.loading,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AppIcon(uiState.packageName, size = 48.dp)
                Column {
                    Text(text = uiState.label, style = MaterialTheme.typography.titleLarge)
                    val usedMinutes = (uiState.usedMillis / MILLIS_PER_MINUTE).toInt()
                    Text(
                        text = stringResource(R.string.limit_editor_used_today, usedMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            uiState.pending?.let { pending ->
                PendingCard(pending.newLimit, onCancelPending)
            }

            // Una app cerrada no tiene tiempo diario: el selector no tendría efecto, así que no se enseña.
            if (uiState.friction != FrictionLevel.CLOSED) {
                SectionHeader(stringResource(R.string.limit_editor_time))
                MinutesPicker(minutes = uiState.minutes, onChange = onMinutes)
            }

            SectionHeader(stringResource(R.string.limit_editor_friction))
            FrictionLevel.entries.forEach { level ->
                ChoiceCard(
                    title = stringResource(level.labelRes),
                    description = stringResource(level.descriptionRes),
                    selected = uiState.friction == level,
                    onClick = { onFriction(level) },
                )
            }

            LeisureSwitch(checked = uiState.leisure, onChange = onLeisure)

            SectionHeader(stringResource(R.string.limit_editor_summary))
            Text(
                text = summaryText(uiState),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (uiState.hasLimit) TimingNote(uiState.timing)

            if (uiState.hasLimit) {
                TextButton(onClick = { removing = true }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                    Text(stringResource(R.string.limit_remove))
                }
                Text(
                    text = stringResource(R.string.limit_remove_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** «App de ocio»: con el Cierre de Jornada queda libre de noche. Toda la fila es el interruptor. */
@Composable
private fun LeisureSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTarget)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.limit_leisure_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = null)
        }
        Text(
            text = stringResource(R.string.limit_leisure_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Frase que dice, en claro, lo que va a pasar con la app. */
@Composable
private fun summaryText(uiState: LimitEditorUiState): String {
    val app = uiState.label
    return when (uiState.friction) {
        FrictionLevel.FLEXIBLE -> stringResource(
            R.string.limit_summary_flexible,
            app,
            uiState.minutes,
            AppLimit.DEFAULT_EXTENSIONS_PER_DAY,
            ExtensionPolicy.MINUTES_PER_EXTENSION,
        )
        FrictionLevel.FIRM -> stringResource(R.string.limit_summary_firm, app, uiState.minutes)
        FrictionLevel.CLOSED -> stringResource(R.string.limit_summary_closed, app)
    }
}

/** Cuándo entra en vigor lo que se está editando: lo que más se agradece saber antes de pulsar «Guardar». */
@Composable
private fun TimingNote(timing: ChangeTiming) {
    val message = when (timing) {
        ChangeTiming.NEW, ChangeTiming.UNCHANGED -> return
        ChangeTiming.NOW -> R.string.limit_timing_now
        ChangeTiming.TOMORROW -> R.string.limit_timing_tomorrow
        ChangeTiming.PARTIAL -> R.string.limit_timing_partial
    }
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        // Ámbar: es un aviso para leer, no una alarma.
        color = if (timing == ChangeTiming.NOW) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
}

/** Cambio que ya está guardado y espera a medianoche, con la posibilidad de descartarlo. */
@Composable
private fun PendingCard(newLimit: AppLimit?, onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = if (newLimit == null) {
                    stringResource(R.string.limit_pending_removal)
                } else {
                    stringResource(R.string.limit_pending_change, limitSummary(newLimit))
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = stringResource(R.string.limit_pending_hint), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(stringResource(R.string.limit_pending_cancel))
            }
        }
    }
}

@get:StringRes
private val FrictionLevel.descriptionRes: Int
    get() = when (this) {
        FrictionLevel.FLEXIBLE -> R.string.friction_flexible_description
        FrictionLevel.FIRM -> R.string.friction_firm_description
        FrictionLevel.CLOSED -> R.string.friction_closed_description
    }
