package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.audio.AmbientAudio
import com.focusdivergent.app.domain.audio.AmbientAudioState
import com.focusdivergent.app.domain.audio.AmbientSound
import com.focusdivergent.app.domain.audio.AudioFocusPort
import com.focusdivergent.app.domain.audio.AudioPreferences
import com.focusdivergent.app.domain.audio.AudioSettings
import com.focusdivergent.app.domain.audio.FocusChange
import com.focusdivergent.app.domain.audio.PlaybackPresenter
import com.focusdivergent.app.domain.audio.SoundEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAudioSettings(initial: AudioPreferences = AudioPreferences()) : AudioSettings {
    private val state = MutableStateFlow(initial)
    override val preferences: StateFlow<AudioPreferences> = state

    override fun update(change: (AudioPreferences) -> AudioPreferences) {
        state.value = change(state.value)
    }
}

/** El reproductor visto desde la interfaz: anota las órdenes y se comporta como el real con los ajustes. */
class FakeAmbientAudio(private val settings: FakeAudioSettings = FakeAudioSettings()) : AmbientAudio {
    private val mutable = MutableStateFlow(AmbientAudioState())
    override val state: StateFlow<AmbientAudioState> = mutable
    val calls = mutableListOf<String>()

    fun setState(state: AmbientAudioState) {
        mutable.value = state
    }

    override fun play(sound: AmbientSound) {
        calls += "play:$sound"
        settings.update { it.copy(sound = sound) }
        mutable.value = AmbientAudioState(sound = sound, playing = true)
    }

    override fun pause() {
        calls += "pause"
        mutable.value = mutable.value.copy(playing = false)
    }

    override fun resume() {
        calls += "resume"
        mutable.value = mutable.value.copy(playing = true)
    }

    override fun stop() {
        calls += "stop"
        mutable.value = AmbientAudioState()
    }

    override fun setVolume(level: Float) {
        calls += "volume:$level"
        settings.update { it.copy(volume = level) }
    }

    override fun setSleepTimer(minutes: Int?) {
        calls += "sleep:$minutes"
        settings.update { it.copy(sleepMinutes = minutes) }
    }
}

class FakeSoundEngine : SoundEngine {
    val events = mutableListOf<String>()
    var currentGain = -1f

    override fun start(sound: AmbientSound, gain: Float) {
        events += "start:$sound"
        currentGain = gain
    }

    override fun pause() {
        events += "pause"
    }

    override fun resume() {
        events += "resume"
    }

    override fun setGain(gain: Float) {
        currentGain = gain
    }

    override fun stop() {
        events += "stop"
    }
}

class FakeAudioFocus(var grant: Boolean = true) : AudioFocusPort {
    var held = false
        private set
    var requests = 0
        private set
    private var listener: ((FocusChange) -> Unit)? = null

    override fun request(onChange: (FocusChange) -> Unit): Boolean {
        requests++
        listener = onChange
        held = grant
        return grant
    }

    override fun abandon() {
        held = false
    }

    /** El sistema avisa de un cambio de foco. */
    fun emit(change: FocusChange) = listener?.invoke(change)
}

class FakePresenter : PlaybackPresenter {
    /** Cada vez que se enseñó la notificación: el sonido y si sonaba. */
    val shown = mutableListOf<Pair<AmbientSound, Boolean>>()
    var hidden = 0
        private set

    override fun show(sound: AmbientSound, playing: Boolean) {
        shown += sound to playing
    }

    override fun hide() {
        hidden++
    }
}
