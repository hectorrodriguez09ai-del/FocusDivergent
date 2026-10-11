package com.focusdivergent.app.presentation.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.tasks.AgendaItem
import com.focusdivergent.app.presentation.common.formatShort
import com.focusdivergent.app.presentation.components.EnergyIndicator
import com.focusdivergent.app.presentation.theme.FocusTheme
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing

/**
 * Una actividad en la lista: a la izquierda el círculo para marcarla como hecha y, en el resto, lo justo para
 * decidir si es el momento de hacerla (qué es, cuándo, cuánto dura y cuánta energía pide).
 *
 * Tocar el círculo la marca; tocar el resto la abre para editar. Son dos zonas grandes y separadas para que un
 * toque impreciso no haga lo contrario de lo que se quería.
 *
 * @param showDate enseña el día además de la hora (en «Próximamente», donde cada fila es de un día distinto).
 * @param canToggle `false` cuando aún no se puede marcar (una rutina de un día futuro).
 */
@Composable
fun TaskCard(
    item: AgendaItem,
    onToggleDone: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    canToggle: Boolean = true,
) {
    val task = item.task
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DoneToggle(
                done = item.isDone,
                enabled = canToggle,
                label = stringResource(R.string.task_done_toggle, task.title),
                onToggle = onToggleDone,
            )
            Surface(
                onClick = onClick,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.md, end = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        // Hecha: se atenúa y se tacha, sin celebración ni color llamativo.
                        color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metaLine(item, showDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EnergyIndicator(task.energyLevel)
                }
            }
        }
    }
}

/** Círculo de 48 dp para marcar como hecha. Vacío con borde si está pendiente; relleno con una marca si está hecha. */
@Composable
private fun DoneToggle(
    done: Boolean,
    enabled: Boolean,
    label: String,
    onToggle: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(Spacing.xs)
            .size(Sizes.touchTarget + Spacing.sm)
            .toggleable(value = done, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = if (done) FocusTheme.colors.done else MaterialTheme.colorScheme.surfaceContainer,
            border = if (done) {
                null
            } else {
                BorderStroke(2.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant)
            },
        ) {
            if (done) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(5.dp),
                )
            }
        }
    }
}

// «Obligación · vie 9 oct · 10:30 · 15 min»: solo las partes que existen.
@Composable
private fun metaLine(item: AgendaItem, showDate: Boolean): String {
    val parts = buildList {
        add(stringResource(item.task.type.labelRes))
        if (showDate) item.date?.let { add(it.formatShort()) }
        item.time?.let { add(it.formatShort()) }
        item.task.estimatedMinutes?.let { add(stringResource(R.string.duration_minutes_short, it)) }
    }
    return parts.joinToString(" · ")
}

@get:StringRes
val TaskType.labelRes: Int
    get() = when (this) {
        TaskType.TASK -> R.string.task_type_task
        TaskType.OBLIGATION -> R.string.task_type_obligation
        TaskType.ROUTINE -> R.string.task_type_routine
    }
