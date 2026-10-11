package com.focusdivergent.app.data.settings

import android.content.Context
import androidx.core.content.edit
import com.focusdivergent.app.domain.audio.AmbientSound
import com.focusdivergent.app.domain.audio.AudioPreferences
import com.focusdivergent.app.domain.audio.AudioSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Recuerda el último sonido, volumen y temporizador de parada. */
@Singleton
class SharedPreferencesAudioSettings @Inject constructor(
    @ApplicationContext context: Context,
) : AudioSettings {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override val preferences: StateFlow<AudioPreferences> = state.asStateFlow()

    override fun update(change: (AudioPreferences) -> AudioPreferences) {
        val updated = change(state.value)
        prefs.edit {
            putString(KEY_SOUND, updated.sound.name)
            putFloat(KEY_VOLUME, updated.volume)
            if (updated.sleepMinutes == null) remove(KEY_SLEEP) else putInt(KEY_SLEEP, updated.sleepMinutes)
        }
        state.value = updated
    }

    private fun read(): AudioPreferences {
        val defaults = AudioPreferences()
        return AudioPreferences(
            sound = AmbientSound.entries.firstOrNull { it.name == prefs.getString(KEY_SOUND, null) } ?: defaults.sound,
            volume = prefs.getFloat(KEY_VOLUME, defaults.volume).coerceIn(0f, 1f),
            sleepMinutes = if (prefs.contains(KEY_SLEEP)) prefs.getInt(KEY_SLEEP, 0).takeIf { it > 0 } else null,
        )
    }

    private companion object {
        const val FILE = "ambient_audio"
        const val KEY_SOUND = "sound"
        const val KEY_VOLUME = "volume"
        const val KEY_SLEEP = "sleep_minutes"
    }
}
