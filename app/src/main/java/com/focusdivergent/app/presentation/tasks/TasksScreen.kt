package com.focusdivergent.app.presentation.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.tasks.AgendaItem
import com.focusdivergent.app.domain.tasks.TaskSection
import com.focusdivergent.app.presentation.common.formatFull
import com.focusdivergent.app.presentation.components.EmptyState
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SecondaryActionButton
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.components.labelRes
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing
import java.time.LocalDate

/** Pestaña «Tareas»: tira semanal, filtros por energía y tipo, y la lista de actividades del día elegido. */
@Composable
fun TasksScreen(
    onNewTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    // Borrar no pide confirmación; a cambio, durante unos segundos se puede deshacer.
    val deleted = uiState.deletedTask
    val deletedMessage = stringResource(R.string.task_deleted, deleted?.title.orEmpty())
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(deleted) {
        if (deleted == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.dismissDeleted(deleted)
    }

    TasksContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNewTask = onNewTask,
        onEditTask = onEditTask,
        onSelectDay = viewModel::selectDay,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onGoToToday = viewModel::goToToday,
        onToggleEnergy = viewModel::toggleEnergyFilter,
        onToggleType = viewModel::toggleTypeFilter,
        onClearFilters = viewModel::clearFilters,
        onSetDone = viewModel::setDone,
    )
}

@Composable
fun TasksContent(
    uiState: TasksUiState,
    onNewTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onGoToToday: () -> Unit,
    onToggleEnergy: (EnergyLevel) -> Unit,
    onToggleType: (TaskType) -> Unit,
    onClearFilters: () -> Unit,
    onSetDone: (AgendaItem, Boolean) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var doneExpanded by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(R.string.nav_tasks),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTask,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.task_new)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Hueco al final para que el botón flotante no tape la última tarjeta.
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "week") {
                WeekStrip(
                    week = uiState.week,
                    selected = uiState.selectedDay,
                    today = uiState.today,
                    onSelect = onSelectDay,
                    onPreviousWeek = onPreviousWeek,
                    onNextWeek = onNextWeek,
                    onGoToToday = onGoToToday,
                )
            }
            item(key = "filters") {
                Filters(uiState, onToggleEnergy, onToggleType)
            }

            if (!uiState.isToday) {
                item(key = "day-title") { SectionHeader(uiState.selectedDay.formatFull().replaceFirstChar { it.titlecase() }) }
            }

            uiState.sections.forEach { section ->
                // «Hechas hoy» va plegado: está para quien quiera verlo, sin ocupar la pantalla.
                val collapsible = section.section == TaskSection.DONE_TODAY
                if (collapsible) {
                    item(key = "header-done") {
                        TextButton(
                            onClick = { doneExpanded = !doneExpanded },
                            modifier = Modifier.heightIn(min = Sizes.touchTarget),
                        ) {
                            Text(
                                stringResource(
                                    if (doneExpanded) R.string.tasks_done_today_hide else R.string.tasks_done_today_show,
                                    section.items.size,
                                ),
                            )
                        }
                    }
                } else if (section.section != null) {
                    item(key = "header-${section.section}") { SectionHeader(stringResource(section.section.titleRes)) }
                }
                if (collapsible && !doneExpanded) return@forEach
                items(section.items, key = { "${section.section}-${it.task.id}" }) { item ->
                    TaskCard(
                        item = item,
                        onToggleDone = { done -> onSetDone(item, done) },
                        onClick = { onEditTask(item.task.id) },
                        showDate = section.section == TaskSection.UPCOMING,
                        canToggle = uiState.canToggleDone && section.section != TaskSection.UPCOMING,
                    )
                }
            }

            if (uiState.sections.isEmpty() && !uiState.loading) {
                item(key = "empty") { EmptyTasks(uiState, onNewTask, onClearFilters) }
            }
        }
    }
}

@Composable
private fun Filters(
    uiState: TasksUiState,
    onToggleEnergy: (EnergyLevel) -> Unit,
    onToggleType: (TaskType) -> Unit,
) {
    // Sin ningún filtro pulsado se ve todo. FlowRow: con letra grande pasan a otra línea en vez de cortarse.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        EnergyLevel.entries.forEach { level ->
            FilterChip(
                selected = uiState.filter.energy == level,
                onClick = { onToggleEnergy(level) },
                label = { Text(stringResource(R.string.filter_energy, stringResource(level.labelRes))) },
                modifier = Modifier.heightIn(min = Sizes.touchTarget),
            )
        }
        TaskType.entries.forEach { type ->
            FilterChip(
                selected = uiState.filter.type == type,
                onClick = { onToggleType(type) },
                label = { Text(stringResource(type.pluralLabelRes)) },
                modifier = Modifier.heightIn(min = Sizes.touchTarget),
            )
        }
    }
}

@Composable
private fun EmptyTasks(uiState: TasksUiState, onNewTask: () -> Unit, onClearFilters: () -> Unit) {
    when {
        // No hay nada guardado todavía.
        !uiState.hasAnyTask -> EmptyState(
            title = stringResource(R.string.tasks_empty_title),
            message = stringResource(R.string.tasks_empty_message),
            action = { PrimaryActionButton(stringResource(R.string.tasks_empty_action), onNewTask) },
        )
        // Hay actividades, pero el filtro las deja fuera.
        uiState.filter.isActive -> EmptyState(
            title = stringResource(R.string.tasks_filtered_title),
            message = stringResource(R.string.tasks_filtered_message),
            action = { SecondaryActionButton(stringResource(R.string.tasks_filtered_action), onClearFilters) },
        )
        // Un día sin nada previsto (o un «hoy» con todo hecho otros días): es una buena noticia, no un error.
        else -> EmptyState(
            title = stringResource(R.string.tasks_day_empty_title),
            message = stringResource(R.string.tasks_day_empty_message),
        )
    }
}

@get:StringRes
private val TaskSection.titleRes: Int
    get() = when (this) {
        TaskSection.TODAY -> R.string.tasks_section_today
        TaskSection.UPCOMING -> R.string.tasks_section_upcoming
        TaskSection.SOMEDAY -> R.string.tasks_section_someday
        TaskSection.DONE_TODAY -> R.string.tasks_section_done_today
    }

@get:StringRes
private val TaskType.pluralLabelRes: Int
    get() = when (this) {
        TaskType.TASK -> R.string.task_type_task_plural
        TaskType.OBLIGATION -> R.string.task_type_obligation_plural
        TaskType.ROUTINE -> R.string.task_type_routine_plural
    }
