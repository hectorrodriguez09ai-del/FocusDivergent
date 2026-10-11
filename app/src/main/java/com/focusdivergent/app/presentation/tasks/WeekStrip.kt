package com.focusdivergent.app.presentation.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R
import com.focusdivergent.app.presentation.common.formatFull
import com.focusdivergent.app.presentation.common.formatMonth
import com.focusdivergent.app.presentation.common.weekdayInitial
import com.focusdivergent.app.presentation.theme.Spacing
import java.time.LocalDate

/**
 * Tira de una semana: siete días en fila, con el mes encima y flechas para cambiar de semana.
 *
 * Es el calendario de la app: solo una semana a la vista, para que orientarse no cueste. El día elegido se
 * marca con relleno y borde; hoy, además, con el número en negrita y la palabra «hoy» debajo.
 */
@Composable
fun WeekStrip(
    week: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onGoToToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = selected.formatMonth(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (selected != today) {
                TextButton(onClick = onGoToToday) { Text(stringResource(R.string.week_go_to_today)) }
            }
            IconButton(onClick = onPreviousWeek) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.week_previous))
            }
            IconButton(onClick = onNextWeek) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.week_next))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            week.forEach { day ->
                DayCell(
                    day = day,
                    isSelected = day == selected,
                    isToday = day == today,
                    onClick = { onSelect(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayLabel = stringResource(R.string.week_today)
    val description = if (isToday) "$todayLabel, ${day.formatFull()}" else day.formatFull()
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            // El lector de pantalla oye la fecha completa, no «V 9».
            .clearAndSetSemantics {
                contentDescription = description
                selected = isSelected
                role = Role.Tab
            },
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Text(text = day.weekdayInitial(), style = MaterialTheme.typography.bodySmall)
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            // Siempre ocupa su línea para que las siete celdas midan lo mismo.
            Text(
                text = if (isToday) todayLabel else " ",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}
