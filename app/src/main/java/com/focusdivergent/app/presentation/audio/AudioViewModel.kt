package com.focusdivergent.app.presentation.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.audio.AmbientAudio
import com.focusdivergent.app.domain.audio.AmbientSound
import com.focusdivergent.app.domain.audio.AudioSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * @property selected el sonido que se enseña elegido: el que suena o, si no suena nada, el último que se eligió.
 * @property active hay algo en marcha o en pausa.
 * @property volume nivel de la barra, de 0 a 1.
 * @property sleepMinutes «parar solo en» elegido, o nulo para no parar.
 * @property remainingMinutes minutos que faltan para que se pare solo (redondeados hacia arriba), si hay cuenta atrás.
 */
data class AudioUiState(
    val selected: AmbientSound = AmbientSound.WHITE_NOISE,
    val active: Boolean = false,
    val playing: Boolean = false,
    val volume: Float = 0f,
    val sleepMinutes: Int? = null,
    val remainingMinutes: Int? = null,
)

@HiltViewModel
class AudioViewModel @Inject constructor(
    private val audio: AmbientAudio,
    private val settings: AudioSettings,
    private val clock: Clock,
) : ViewModel() {

    // La cuenta atrás de «parar solo» se enseña en minutos: renovarla cada medio minuto sobra.
    private val ticks = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MS)
        }
    }

    val uiState: StateFlow<AudioUiState> = combine(audio.state, settings.preferences, ticks) { state, prefs, now ->
        AudioUiState(
            selected = state.sound ?: prefs.sound,
            active = state.active,
            playing = state.playing,
            volume = prefs.volume,
            sleepMinutes = prefs.sleepMinutes,
            remainingMinutes = state.stopAt?.let { stopAt ->
                val millis = Duration.between(now, stopAt).toMillis().coerceAtLeast(0)
                ((millis + 59_999) / 60_000).toInt()
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = settings.preferences.value.let { AudioUiState(selected = it.sound, volume = it.volume, sleepMinutes = it.sleepMinutes) },
    )

    /** Si ya suena algo, cambia a este sonido al momento; si no, solo lo deja elegido para cuando se pulse reproducir. */
    fun select(sound: AmbientSound) {
        if (uiState.value.active) audio.play(sound) else settings.update { it.copy(sound = sound) }
    }

    /** Reproducir, pausar o reanudar, según lo que toque. */
    fun togglePlay() {
        val state = uiState.value
        when {
            !state.active -> audio.play(state.selected)
            state.playing -> audio.pause()
            else -> audio.resume()
        }
    }

    fun stop() = audio.stop()

    fun setVolume(level: Float) = audio.setVolume(level)

    fun setSleepTimer(minutes: Int?) = audio.setSleepTimer(minutes)

    private companion object {
        const val TICK_MS = 30_000L
    }
}
