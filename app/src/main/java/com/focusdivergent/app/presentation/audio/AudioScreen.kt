package com.focusdivergent.app.presentation.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.audio.AmbientSound
import com.focusdivergent.app.domain.audio.SleepTimerOptions
import com.focusdivergent.app.presentation.components.ChoiceCard
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SecondaryActionButton
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing
import com.focusdivergent.app.service.labelRes
import kotlin.math.roundToInt

/** Sonido ambiente (RF-4.2): ruido blanco, marrón, binaural o lluvia, sin conexión y sin voces. */
@Composable
fun AudioScreen(
    onBack: () -> Unit,
    viewModel: AudioViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AudioContent(
        uiState = uiState,
        onBack = onBack,
        onSelect = viewModel::select,
        onTogglePlay = viewModel::togglePlay,
        onStop = viewModel::stop,
        onVolume = viewModel::setVolume,
        onSleepTimer = viewModel::setSleepTimer,
    )
}

private fun AmbientSound.descriptionRes(): Int = when (this) {
    AmbientSound.WHITE_NOISE -> R.string.audio_sound_white_description
    AmbientSound.BROWN_NOISE -> R.string.audio_sound_brown_description
    AmbientSound.BINAURAL -> R.string.audio_sound_binaural_description
    AmbientSound.RAIN -> R.string.audio_sound_rain_description
}

@Composable
fun AudioContent(
    uiState: AudioUiState,
    onBack: () -> Unit,
    onSelect: (AmbientSound) -> Unit,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onVolume: (Float) -> Unit,
    onSleepTimer: (Int?) -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.audio_title), onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (uiState.active) {
                val sound = stringResource(uiState.selected.labelRes())
                val status = stringResource(if (uiState.playing) R.string.audio_status_playing else R.string.audio_status_paused, sound)
                Text(text = status, style = MaterialTheme.typography.titleMedium)
                uiState.remainingMinutes?.let { minutes ->
                    Text(
                        text = stringResource(R.string.audio_status_remaining, minutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AmbientSound.entries.forEach { sound ->
                    ChoiceCard(
                        title = stringResource(sound.labelRes()),
                        description = stringResource(sound.descriptionRes()),
                        selected = uiState.selected == sound,
                        onClick = { onSelect(sound) },
                    )
                }
            }

            if (uiState.selected.needsHeadphones) {
                Text(
                    text = stringResource(R.string.audio_binaural_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrimaryActionButton(
                text = stringResource(
                    when {
                        !uiState.active -> R.string.audio_play
                        uiState.playing -> R.string.audio_pause
                        else -> R.string.audio_resume
                    },
                ),
                onClick = onTogglePlay,
            )

            VolumeControl(volume = uiState.volume, onVolume = onVolume)

            SleepTimerControl(selected = uiState.sleepMinutes, onSelect = onSleepTimer)

            if (uiState.active) {
                SecondaryActionButton(text = stringResource(R.string.audio_stop), onClick = onStop)
            }
        }
    }
}

@Composable
private fun VolumeControl(volume: Float, onVolume: (Float) -> Unit) {
    val percent = (volume * 100).roundToInt()
    val description = stringResource(R.string.audio_volume_value, percent)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(stringResource(R.string.audio_volume))
        Slider(
            value = volume,
            onValueChange = onVolume,
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTarget)
                .semantics { contentDescription = description },
        )
        Text(
            text = stringResource(R.string.audio_volume_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SleepTimerControl(selected: Int?, onSelect: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.audio_sleep))
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.audio_sleep_off)) },
                modifier = Modifier.heightIn(min = Sizes.touchTarget),
            )
            SleepTimerOptions.MINUTES.forEach { minutes ->
                FilterChip(
                    selected = selected == minutes,
                    onClick = { onSelect(minutes) },
                    label = { Text(stringResource(R.string.duration_minutes_short, minutes)) },
                    modifier = Modifier.heightIn(min = Sizes.touchTarget),
                )
            }
        }
    }
}
