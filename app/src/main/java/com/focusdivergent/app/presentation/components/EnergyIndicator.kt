package com.focusdivergent.app.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.presentation.theme.FocusTheme
import com.focusdivergent.app.presentation.theme.Spacing

private const val MAX_BARS = 3

/**
 * Nivel de energía que pide una actividad (RF-2.1): de 1 a 3 barras crecientes, como una batería, y la palabra.
 *
 * La información va en las barras y en el texto; el color solo acompaña, así que se entiende igual con
 * daltonismo o en escala de grises. No hay rojo en ningún nivel (RNF-3).
 */
@Composable
fun EnergyIndicator(
    level: EnergyLevel,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val description = stringResource(R.string.energy_description, stringResource(level.labelRes))
    Row(
        // Para el lector de pantalla es un solo elemento: «Energía baja», no tres barras y una palabra.
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        EnergyBars(level)
        if (showLabel) {
            Text(
                text = stringResource(level.labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Solo las barras, para sitios donde la palabra ya está al lado (p. ej. los botones del selector). */
@Composable
fun EnergyBars(
    level: EnergyLevel,
    modifier: Modifier = Modifier,
    activeColor: Color = level.color,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(MAX_BARS) { index ->
            Box(
                Modifier
                    .width(6.dp)
                    .height((8 + index * 5).dp)
                    .background(
                        color = if (index < level.value) activeColor else FocusTheme.colors.track,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@get:StringRes
val EnergyLevel.labelRes: Int
    get() = when (this) {
        EnergyLevel.LOW -> R.string.energy_low
        EnergyLevel.MEDIUM -> R.string.energy_medium
        EnergyLevel.HIGH -> R.string.energy_high
    }

val EnergyLevel.color: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        EnergyLevel.LOW -> FocusTheme.colors.energyLow
        EnergyLevel.MEDIUM -> FocusTheme.colors.energyMedium
        EnergyLevel.HIGH -> FocusTheme.colors.energyHigh
    }
