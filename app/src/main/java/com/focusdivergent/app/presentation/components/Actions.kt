package com.focusdivergent.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.focusdivergent.app.R
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing

/**
 * La acción principal de una pantalla. Solo debe haber una por pantalla: ancha, alta y con el color primario,
 * para que no haya que buscarla.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.primaryAction),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Acción secundaria: mismo tamaño que la principal para acertar igual de fácil, pero solo con borde. */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.primaryAction),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Atajos de duración en minutos: se elige con un toque en vez de escribir un número.
 * [selected] es nulo cuando no hay duración elegida; volver a tocar la elegida la quita.
 */
@Composable
fun DurationChips(
    options: List<Int>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // FlowRow: con la letra del sistema muy grande los atajos pasan a una segunda línea en vez de cortarse.
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { minutes ->
            FilterChip(
                selected = minutes == selected,
                onClick = { onSelect(if (minutes == selected) null else minutes) },
                label = { Text(stringResource(R.string.duration_minutes_short, minutes)) },
                modifier = Modifier.heightIn(min = Sizes.touchTarget),
            )
        }
    }
}
