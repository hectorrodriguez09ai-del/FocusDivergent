package com.focusdivergent.app.presentation.mascot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.reward.StreakInfo
import com.focusdivergent.app.domain.usecase.Progress
import com.focusdivergent.app.presentation.theme.Spacing

/**
 * La mascota en «Hoy»: ella, el nivel con su barra y la racha. Toda la tarjeta lleva a «Mi mascota»; la barra tiene
 * su propio toque para ver los números.
 */
@Composable
fun MascotCard(
    progress: Progress,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mascot = progress.selection.mascot
    val description = stringResource(
        R.string.mascot_description,
        stringResource(mascot.labelRes()),
        mascot.stageName(progress.stage),
        progress.level.level,
    )
    Surface(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description; role = Role.Button },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Compacta: la mascota a un lado y el nivel y la racha al otro, para que «Elige por mí» siga a la vista.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MascotStage(
                    mascot = mascot,
                    stage = progress.stage,
                    scenery = progress.selection.scenery,
                    prop = progress.selection.prop,
                    modifier = Modifier.height(112.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = stringResource(R.string.mascot_level, progress.level.level),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    StreakText(progress.streak, alignEnd = false)
                }
            }
            LevelBar(level = progress.level, xpToday = progress.xpToday)
        }
    }
}

/**
 * La racha en una o dos líneas cortas. Sin racha no se avisa de nada: se invita a empezar y, si hubo una mejor, se
 * recuerda. Nunca un mensaje de pérdida (RNF-3).
 */
@Composable
fun StreakText(streak: StreakInfo, modifier: Modifier = Modifier, alignEnd: Boolean = true) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        when {
            streak.current > 0 -> Text(
                text = pluralStringResource(R.plurals.streak_current, streak.current, streak.current),
                style = MaterialTheme.typography.bodyMedium,
            )
            streak.best == 0 -> Text(
                text = stringResource(R.string.streak_start),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (streak.best > 0) {
            Text(
                text = pluralStringResource(R.plurals.streak_best, streak.best, streak.best),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
