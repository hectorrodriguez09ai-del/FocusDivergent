package com.focusdivergent.app.presentation.closure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.closure.DaySummary
import com.focusdivergent.app.presentation.common.formatFull
import com.focusdivergent.app.presentation.common.formatShort
import com.focusdivergent.app.presentation.components.FocusCard
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SecondaryActionButton
import com.focusdivergent.app.presentation.theme.Spacing

/**
 * El resumen del Cierre de Jornada (RF-6.2, «Brain Off»): lo que se hizo ese día, dicho con calma. Solo lo hecho: lo
 * que no se hizo no aparece, y lo que se traslada a mañana se cuenta sin ningún reproche (RNF-3).
 */
@Composable
fun ClosureSummaryScreen(
    onClose: () -> Unit,
    onReviewInbox: () -> Unit,
    viewModel: ClosureSummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ClosureSummaryContent(uiState = uiState, onClose = onClose, onReviewInbox = onReviewInbox)
}

@Composable
fun ClosureSummaryContent(
    uiState: ClosureSummaryUiState,
    onClose: () -> Unit,
    onReviewInbox: () -> Unit,
) {
    ScreenScaffold(
        title = stringResource(R.string.closure_title),
        onBack = onClose,
        bottomBar = {
            PrimaryActionButton(
                text = stringResource(R.string.closure_finish),
                onClick = onClose,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            )
        },
    ) { innerPadding ->
        val summary = uiState.summary
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (summary != null) {
                Text(
                    text = summary.day.formatFull().replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DoneCard(summary)
                if (summary.focusMinutes > 0) {
                    FocusCard {
                        Text(
                            text = pluralStringResource(R.plurals.closure_focus, summary.focusMinutes, summary.focusMinutes),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (summary.xp > 0 || summary.coins > 0) {
                    FocusCard {
                        Text(
                            text = if (summary.coins > 0) {
                                pluralStringResource(R.plurals.closure_rewards_both, summary.coins, summary.xp, summary.coins)
                            } else {
                                stringResource(R.string.closure_rewards_xp, summary.xp)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (summary.respectedLimits > 0) {
                    FocusCard {
                        Text(
                            text = pluralStringResource(R.plurals.closure_respected, summary.respectedLimits, summary.respectedLimits),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (summary.moved > 0) {
                    FocusCard {
                        Text(
                            text = pluralStringResource(R.plurals.closure_moved, summary.moved, summary.moved),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.closure_moved_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (summary.ideasWaiting > 0) {
                    FocusCard {
                        Text(
                            text = pluralStringResource(R.plurals.closure_ideas, summary.ideasWaiting, summary.ideasWaiting),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.closure_ideas_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SecondaryActionButton(text = stringResource(R.string.closure_ideas_action), onClick = onReviewInbox)
                    }
                }
                val liftUntil = uiState.liftUntil
                if (uiState.leisureApps > 0 && liftUntil != null) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.closure_leisure_note,
                            uiState.leisureApps,
                            uiState.leisureApps,
                            liftUntil.formatShort(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Lo hecho ese día, con los títulos de las primeras cosas. Si no hubo nada, una frase tranquila en su lugar. */
@Composable
private fun DoneCard(summary: DaySummary) {
    FocusCard {
        if (summary.doneCount == 0) {
            Text(
                text = stringResource(if (summary.isQuiet) R.string.closure_quiet else R.string.closure_quiet_focus),
                style = MaterialTheme.typography.bodyLarge,
            )
            return@FocusCard
        }
        Text(
            text = pluralStringResource(R.plurals.closure_done_count, summary.doneCount, summary.doneCount),
            style = MaterialTheme.typography.titleMedium,
        )
        summary.doneTitles.take(ClosureSummaryViewModel.MAX_TITLES).forEach { title ->
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
        val more = summary.doneTitles.size - ClosureSummaryViewModel.MAX_TITLES
        if (more > 0) {
            Text(
                text = stringResource(R.string.closure_done_more, more),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
