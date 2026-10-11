package com.focusdivergent.app.service

import android.content.Context
import android.content.Intent
import com.focusdivergent.app.domain.audio.AmbientAudio
import com.focusdivergent.app.domain.audio.AmbientAudioState
import com.focusdivergent.app.domain.audio.AmbientSound
import com.focusdivergent.app.domain.audio.AudioSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El reproductor visto desde la interfaz: guarda lo que el usuario elige y le manda órdenes al servicio en primer
 * plano, que es quien suena de verdad y quien publica aquí el estado ([publish]).
 */
@Singleton
class AndroidAmbientAudio @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AudioSettings,
) : AmbientAudio {

    private val _state = MutableStateFlow(AmbientAudioState())
    override val state: StateFlow<AmbientAudioState> = _state.asStateFlow()

    /** Lo llama el servicio cada vez que cambia lo que suena. */
    internal fun publish(state: AmbientAudioState) {
        _state.value = state
    }

    override fun play(sound: AmbientSound) {
        settings.update { it.copy(sound = sound) }
        // El servicio se lanza en primer plano desde la pantalla, con el usuario delante: Android lo permite.
        context.startForegroundService(AmbientAudioService.intent(context, AmbientAudioService.ACTION_PLAY).putExtra(AmbientAudioService.EXTRA_SOUND, sound.name))
    }

    override fun pause() = sendIfActive(AmbientAudioService.ACTION_PAUSE)

    override fun resume() = sendIfActive(AmbientAudioService.ACTION_RESUME)

    override fun stop() = sendIfActive(AmbientAudioService.ACTION_STOP)

    // El servicio vigila los ajustes: basta con guardarlos para que el cambio se aplique al instante.
    override fun setVolume(level: Float) {
        settings.update { it.copy(volume = level.coerceIn(0f, 1f)) }
    }

    override fun setSleepTimer(minutes: Int?) {
        settings.update { it.copy(sleepMinutes = minutes) }
    }

    // Con el servicio ya vivo (y en primer plano) basta un `startService`; si no hay nada sonando, no hay a quién avisar.
    private fun sendIfActive(action: String) {
        if (!_state.value.active) return
        context.startService(AmbientAudioService.intent(context, action))
    }
}
