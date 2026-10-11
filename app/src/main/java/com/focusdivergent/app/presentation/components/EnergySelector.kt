package com.focusdivergent.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.presentation.theme.Spacing

/**
 * Elegir un nivel de energía entre tres: tres botones grandes en fila, cada uno con sus barras y su palabra.
 *
 * Se usa tanto para «¿cuánta energía pide esta tarea?» como para «¿cómo estás de energía ahora?».
 * [selected] puede ser nulo cuando aún no se ha elegido nada.
 */
@Composable
fun EnergySelector(
    selected: EnergyLevel?,
    onSelect: (EnergyLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        EnergyLevel.entries.forEach { level ->
            val isSelected = level == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 72.dp)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(level) }),
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                // La selección se marca con el grosor y el color del borde, no solo con el relleno.
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) level.color else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = Spacing.md, horizontal = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
                ) {
                    EnergyBars(level)
                    Text(
                        text = stringResource(level.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
