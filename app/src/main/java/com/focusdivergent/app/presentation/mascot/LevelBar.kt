package com.focusdivergent.app.presentation.mascot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.reward.LevelProgress
import com.focusdivergent.app.presentation.theme.LocalFocusColors
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing
import com.focusdivergent.app.presentation.theme.focusTween

/**
 * La barra que lleva al siguiente nivel. Limpia: solo el nivel y la barra. Los números de XP aparecen al tocarla
 * y se vuelven a esconder al tocar otra vez.
 *
 * @param xpToday XP ganado hoy, que se enseña junto a los demás números.
 */
@Composable
fun LevelBar(
    level: LevelProgress,
    xpToday: Int,
    modifier: Modifier = Modifier,
) {
    var showNumbers by rememberSaveable { mutableStateOf(false) }
    val fraction by animateFloatAsState(level.fraction, animationSpec = focusTween(), label = "levelFraction")
    val levelText = stringResource(R.string.mascot_level, level.level)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTarget)
                .clickable(onClickLabel = stringResource(R.string.mascot_level_action), role = Role.Button) {
                    showNumbers = !showNumbers
                }
                .semantics { contentDescription = levelText },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(LocalFocusColors.current.track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        if (showNumbers) {
            Text(
                text = stringResource(R.string.mascot_xp_detail, level.xpIntoLevel, level.xpForLevel, xpToday),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
    }
}
