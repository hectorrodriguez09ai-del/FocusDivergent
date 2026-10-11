package com.focusdivergent.app.presentation.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.tasks.AgendaItem
import com.focusdivergent.app.presentation.common.formatFull
import com.focusdivergent.app.presentation.common.formatShort
import com.focusdivergent.app.presentation.components.EmptyState
import com.focusdivergent.app.presentation.components.EnergySelector
import com.focusdivergent.app.presentation.components.FocusCard
import com.focusdivergent.app.presentation.components.LinkCard
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SecondaryActionButton
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.mascot.MascotCard
import com.focusdivergent.app.presentation.tasks.TaskCard
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing

/**
 * Pestaña «Hoy»: la energía del día, el botón grande «Elige por mí» y lo justo de lo que toca, sin listas largas.
 * La idea es que, al abrirla, la decisión de por dónde empezar ya esté medio tomada.
 */
@Composable
fun TodayScreen(
    onChooseForMe: () -> Unit,
    onLaunch: () -> Unit,
    onEditTask: (Long) -> Unit,
    onNewTask: () -> Unit,
    onSeeAllTasks: () -> Unit,
    onTimer: () -> Unit,
    onAudio: () -> Unit,
    onMascot: () -> Unit,
    onOpenSteps: (Long) -> Unit = {},
    onSplitSteps: (Long) -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    TodayContent(
        uiState = uiState,
        onChooseForMe = onChooseForMe,
        onLaunch = onLaunch,
        onEnergy = viewModel::setEnergy,
        onEditTask = onEditTask,
        onNewTask = onNewTask,
        onSeeAllTasks = onSeeAllTasks,
        onTimer = onTimer,
        onAudio = onAudio,
        onMascot = onMascot,
        onSetDone = viewModel::setDone,
        onLeaveForLater = viewModel::leaveForLater,
        onOpenSteps = onOpenSteps,
        onSplitSteps = onSplitSteps,
    )
}

@Composable
fun TodayContent(
    uiState: TodayUiState,
    onChooseForMe: () -> Unit,
    onLaunch: () -> Unit,
    onEnergy: (EnergyLevel) -> Unit,
    onEditTask: (Long) -> Unit,
    onNewTask: () -> Unit,
    onSeeAllTasks: () -> Unit,
    onTimer: () -> Unit,
    onAudio: () -> Unit,
    onSetDone: (AgendaItem, Boolean) -> Unit,
    onLeaveForLater: (AgendaItem) -> Unit,
    onMascot: () -> Unit = {},
    onOpenSteps: (Long) -> Unit = {},
    onSplitSteps: (Long) -> Unit = {},
) {
    ScreenScaffold(title = stringResource(R.string.nav_today)) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "date") {
                Text(
                    text = uiState.today.formatFull().replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.progress?.let { progress ->
                item(key = "mascot") { MascotCard(progress = progress, onOpen = onMascot) }
            }

            item(key = "energy") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(stringResource(R.string.today_energy_title))
                    EnergySelector(selected = uiState.energy, onSelect = onEnergy)
                }
            }

            item(key = "choose") {
                Column(
                    modifier = Modifier.padding(vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    PrimaryActionButton(text = stringResource(R.string.today_choose), onClick = onChooseForMe)
                    Text(
                        text = stringResource(R.string.today_choose_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryActionButton(
                        text = stringResource(R.string.today_launch),
                        onClick = onLaunch,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                    Text(
                        text = stringResource(R.string.today_launch_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "focus") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(stringResource(R.string.today_focus_header))
                    LinkCard(
                        title = stringResource(R.string.today_timer),
                        description = stringResource(if (uiState.timerActive) R.string.today_timer_running else R.string.today_timer_description),
                        onClick = onTimer,
                    )
                    LinkCard(
                        title = stringResource(R.string.today_audio),
                        description = stringResource(
                            when {
                                uiState.audioActive && uiState.audioPlaying -> R.string.today_audio_playing
                                uiState.audioActive -> R.string.today_audio_paused
                                else -> R.string.today_audio_description
                            },
                        ),
                        onClick = onAudio,
                    )
                }
            }

            if (uiState.nowItems.isNotEmpty()) {
                item(key = "now-header") { SectionHeader(stringResource(R.string.today_now_header)) }
                items(uiState.nowItems, key = { "now-${it.task.id}" }) { item ->
                    androidx.compose.foundation.layout.Column {
                        TaskCard(
                            item = item,
                            onToggleDone = { done -> onSetDone(item, done) },
                            onClick = { onEditTask(item.task.id) },
                        )
                        val steps = uiState.nowSteps[item.task.id]
                        val next = steps?.next
                        if (steps != null && next != null) {
                            // Lo que toca ahora, a un toque: el paso siguiente, no la lista entera.
                            FocusCard(onClick = { onOpenSteps(item.task.id) }) {
                                Text(
                                    text = stringResource(R.string.today_steps_next, next.text),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = stringResource(R.string.today_steps_progress, steps.done, steps.total),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onLeaveForLater(item) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                                Text(stringResource(R.string.today_leave_for_later))
                            }
                            if (steps == null || !steps.hasSteps) {
                                TextButton(onClick = { onSplitSteps(item.task.id) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                                    Text(stringResource(R.string.today_steps_split))
                                }
                            }
                        }
                    }
                }
            }

            uiState.nextObligation?.let { obligation ->
                item(key = "obligation") {
                    FocusCard(onClick = { onEditTask(obligation.task.id) }) {
                        Text(
                            text = stringResource(R.string.today_next_obligation),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = obligation.task.title, style = MaterialTheme.typography.titleMedium)
                        obligation.time?.let {
                            Text(
                                text = stringResource(R.string.today_next_obligation_at, it.formatShort()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (uiState.todayItems.isNotEmpty()) {
                item(key = "today-header") { SectionHeader(stringResource(R.string.today_for_today)) }
                items(uiState.todayItems, key = { "today-${it.task.id}" }) { item ->
                    TaskCard(
                        item = item,
                        onToggleDone = { done -> onSetDone(item, done) },
                        onClick = { onEditTask(item.task.id) },
                    )
                }
                if (uiState.hasMoreToday) {
                    item(key = "see-all") {
                        TextButton(onClick = onSeeAllTasks, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                            Text(stringResource(R.string.today_see_all))
                        }
                    }
                }
            }

            // Reconocimiento tranquilo de lo hecho: un número, sin porcentajes ni comparaciones (RNF-3).
            if (uiState.doneToday > 0) {
                item(key = "done") {
                    Text(
                        text = pluralStringResource(R.plurals.today_done_count, uiState.doneToday, uiState.doneToday),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }

            val nothingToShow = uiState.nowItems.isEmpty() && uiState.nextObligation == null && uiState.todayItems.isEmpty()
            if (nothingToShow && !uiState.loading) {
                item(key = "empty") {
                    if (!uiState.hasAnyTask) {
                        EmptyState(
                            title = stringResource(R.string.today_none_title),
                            message = stringResource(R.string.today_none_message),
                            action = { PrimaryActionButton(stringResource(R.string.today_none_action), onNewTask) },
                        )
                    } else {
                        EmptyState(
                            title = stringResource(R.string.today_empty_title),
                            message = stringResource(R.string.today_empty_message),
                        )
                    }
                }
            }
        }
    }
}
