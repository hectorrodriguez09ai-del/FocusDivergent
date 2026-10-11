package com.focusdivergent.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing
import com.focusdivergent.app.presentation.theme.tabularNumbers

/**
 * Elegir los minutos al día de un límite: atajos de un toque para los valores habituales y, para afinar, botones
 * de −5 y +5 minutos alrededor del valor. Nada de escribir números.
 *
 * El valor se queda siempre entre [min] y [max]; los botones se desactivan al llegar a un extremo.
 */
@Composable
fun MinutesPicker(
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<Int> = listOf(15, 30, 45, 60, 90, 120),
    min: Int = 5,
    max: Int = 480,
    step: Int = 5,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val less = stringResource(R.string.minutes_less, step)
            OutlinedButton(
                onClick = { onChange((minutes - step).coerceAtLeast(min)) },
                enabled = minutes > min,
                modifier = Modifier
                    .heightIn(min = Sizes.primaryAction)
                    .widthIn(min = Sizes.primaryAction)
                    .semantics { contentDescription = less },
            ) { Text("−$step") }

            Text(
                text = stringResource(R.string.minutes_value, minutes),
                style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            val more = stringResource(R.string.minutes_more, step)
            OutlinedButton(
                onClick = { onChange((minutes + step).coerceAtMost(max)) },
                enabled = minutes < max,
                modifier = Modifier
                    .heightIn(min = Sizes.primaryAction)
                    .widthIn(min = Sizes.primaryAction)
                    .semantics { contentDescription = more },
            ) { Text("+$step") }
        }

        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = preset == minutes,
                    onClick = { onChange(preset) },
                    label = { Text(stringResource(R.string.duration_minutes_short, preset)) },
                    modifier = Modifier.heightIn(min = Sizes.touchTarget),
                )
            }
        }
    }
}
