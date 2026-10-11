package com.focusdivergent.app.presentation.mascot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.CosmeticKind
import com.focusdivergent.app.domain.reward.Mascot
import com.focusdivergent.app.domain.reward.MascotStages
import com.focusdivergent.app.domain.usecase.Progress
import com.focusdivergent.app.presentation.components.FocusCard
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing

/**
 * «Mi mascota»: ella y su nivel, quién te acompaña, las etapas por las que irá pasando y la tienda donde se gastan
 * las monedas de enfoque. Nada aquí se pierde ni caduca (RNF-3).
 */
@Composable
fun MascotScreen(
    onBack: () -> Unit,
    viewModel: MascotViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is MascotEvent.Bought -> resources.getString(R.string.cosmetic_bought, resources.getString(event.cosmetic.labelRes()))
                is MascotEvent.NotEnoughCoins -> resources.getString(R.string.cosmetic_not_enough)
            }
            snackbar.showSnackbar(message)
        }
    }

    MascotContent(
        uiState = uiState,
        snackbarHostState = snackbar,
        onBack = onBack,
        onChooseMascot = viewModel::chooseMascot,
        onCosmetic = viewModel::onCosmetic,
    )
}

@Composable
fun MascotContent(
    uiState: MascotUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onChooseMascot: (Mascot) -> Unit,
    onCosmetic: (Cosmetic) -> Unit,
) {
    ScreenScaffold(
        title = stringResource(R.string.mascot_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val progress = uiState.progress
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (progress != null) {
                Hero(progress)
                MascotChooser(progress, onChooseMascot)
                Stages(progress)
                Shop(progress, onCosmetic)
            }
        }
    }
}

@Composable
private fun Hero(progress: Progress) {
    val mascot = progress.selection.mascot
    MascotStage(
        mascot = mascot,
        stage = progress.stage,
        scenery = progress.selection.scenery,
        prop = progress.selection.prop,
        modifier = Modifier.fillMaxWidth(),
        contentDescription = mascotImageDescription(mascot, progress.stage),
    )
    Text(
        text = stringResource(R.string.mascot_stage_description, stringResource(mascot.labelRes()), mascot.stageName(progress.stage)),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        text = stringResource(R.string.mascot_level, progress.level.level),
        style = MaterialTheme.typography.titleMedium,
    )
    LevelBar(level = progress.level, xpToday = progress.xpToday)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stringResource(R.string.mascot_coins, progress.coins),
            style = MaterialTheme.typography.bodyLarge,
        )
        StreakText(progress.streak)
    }
}

@Composable
private fun MascotChooser(progress: Progress, onChoose: (Mascot) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.mascot_choose_header))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Mascot.entries.forEach { mascot ->
                MascotTile(
                    mascot = mascot,
                    stage = progress.stage,
                    selected = mascot == progress.selection.mascot,
                    onClick = { onChoose(mascot) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = stringResource(R.string.mascot_choose_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MascotTile(
    mascot: Mascot,
    stage: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = Sizes.touchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            MascotSprite(mascot = mascot, stage = stage, modifier = Modifier.height(72.dp))
            Text(
                text = stringResource(mascot.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Stages(progress: Progress) {
    val mascot = progress.selection.mascot
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.mascot_stages_header))
        for (stage in 1..MascotStages.COUNT) {
            val reached = stage <= progress.stage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                MascotSprite(mascot = mascot, stage = stage, modifier = Modifier.size(56.dp), silhouette = !reached)
                Column(modifier = Modifier.weight(1f)) {
                    if (reached) Text(text = mascot.stageName(stage), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.mascot_stage_locked, MascotStages.LEVELS[stage - 1]),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (stage == progress.stage) {
                    Text(
                        text = stringResource(R.string.mascot_stage_current),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Shop(progress: Progress, onCosmetic: (Cosmetic) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.mascot_shop_header))
        Text(
            text = stringResource(R.string.mascot_shop_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.mascot_shop_scenery),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Cosmetic.entries.filter { it.kind == CosmeticKind.SCENERY }.forEach { CosmeticRow(it, progress, onCosmetic) }
        Text(
            text = stringResource(R.string.mascot_shop_props),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Cosmetic.entries.filter { it.kind == CosmeticKind.PROP }.forEach { CosmeticRow(it, progress, onCosmetic) }
    }
}

@Composable
private fun CosmeticRow(cosmetic: Cosmetic, progress: Progress, onCosmetic: (Cosmetic) -> Unit) {
    val selection = progress.selection
    val owned = cosmetic in progress.owned
    val worn = cosmetic == selection.scenery || cosmetic == selection.prop
    val name = stringResource(cosmetic.labelRes())

    FocusCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // La vista previa pone el cosmético sobre lo que el usuario ya lleva, para ver cómo quedaría.
            MascotStage(
                mascot = selection.mascot,
                stage = progress.stage,
                scenery = if (cosmetic.kind == CosmeticKind.SCENERY) cosmetic else selection.scenery,
                prop = if (cosmetic.kind == CosmeticKind.PROP) cosmetic else selection.prop,
                modifier = Modifier.width(120.dp),
                animated = false,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                val detail = when {
                    owned && cosmetic.isFree -> stringResource(R.string.cosmetic_free)
                    owned -> null
                    else -> pluralStringResource(R.plurals.cosmetic_price, cosmetic.price, cosmetic.price)
                }
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    // Un fondo puesto no se quita: se cambia por otro.
                    worn && cosmetic.kind == CosmeticKind.SCENERY -> Text(
                        text = stringResource(R.string.cosmetic_worn),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.heightIn(min = Sizes.touchTarget),
                    )
                    worn -> OutlinedButton(onClick = { onCosmetic(cosmetic) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                        Text(stringResource(R.string.cosmetic_remove))
                    }
                    owned -> OutlinedButton(onClick = { onCosmetic(cosmetic) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                        Text(stringResource(R.string.cosmetic_wear))
                    }
                    else -> Button(onClick = { onCosmetic(cosmetic) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                        Text(stringResource(R.string.cosmetic_buy, cosmetic.price))
                    }
                }
            }
        }
    }
}
