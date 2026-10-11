package com.focusdivergent.app.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusdivergent.app.data.settings.SharedPreferencesAudioSettings
import com.focusdivergent.app.domain.audio.AmbientAudioState
import com.focusdivergent.app.domain.audio.AmbientSound
import com.focusdivergent.app.domain.audio.AmbientVolume
import com.focusdivergent.app.domain.audio.AudioPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Los ajustes del sonido ambiente y las órdenes que la interfaz le manda al servicio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AmbientAudioStorageAndControlTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application = context as Application

    @Before
    fun clean() {
        context.getSharedPreferences("ambient_audio", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // ---- Ajustes

    @Test
    fun `sin nada guardado se usan los valores por defecto`() {
        val prefs = SharedPreferencesAudioSettings(context).preferences.value

        assertEquals(AudioPreferences(), prefs)
        assertEquals(AmbientVolume.DEFAULT_LEVEL, prefs.volume, 0f)
        assertNull(prefs.sleepMinutes)
    }

    @Test
    fun `sonido, volumen y temporizador se guardan y se recuperan al reabrir`() {
        SharedPreferencesAudioSettings(context).update {
            it.copy(sound = AmbientSound.RAIN, volume = 0.75f, sleepMinutes = 45)
        }

        val reopened = SharedPreferencesAudioSettings(context).preferences.value
        assertEquals(AudioPreferences(AmbientSound.RAIN, 0.75f, 45), reopened)
    }

    @Test
    fun `quitar el temporizador lo borra`() {
        val settings = SharedPreferencesAudioSettings(context)
        settings.update { it.copy(sleepMinutes = 30) }

        settings.update { it.copy(sleepMinutes = null) }

        assertNull(SharedPreferencesAudioSettings(context).preferences.value.sleepMinutes)
    }

    @Test
    fun `un valor guardado que ya no existe no rompe la lectura`() {
        context.getSharedPreferences("ambient_audio", Context.MODE_PRIVATE).edit()
            .putString("sound", "SONIDO_QUE_YA_NO_EXISTE").putFloat("volume", 7f).commit()

        val prefs = SharedPreferencesAudioSettings(context).preferences.value

        assertEquals(AudioPreferences().sound, prefs.sound)
        assertEquals(1f, prefs.volume, 0f)
    }

    // ---- Órdenes al servicio

    private fun audio() = AndroidAmbientAudio(context, SharedPreferencesAudioSettings(context))

    @Test
    fun `reproducir recuerda el sonido y arranca el servicio con el`() {
        val audio = audio()

        audio.play(AmbientSound.RAIN)

        val started = shadowOf(application).nextStartedService
        assertNotNull(started)
        assertEquals(AmbientAudioService::class.java.name, started.component?.className)
        assertEquals(AmbientAudioService.ACTION_PLAY, started.action)
        assertEquals("RAIN", started.getStringExtra(AmbientAudioService.EXTRA_SOUND))
        assertEquals(AmbientSound.RAIN, SharedPreferencesAudioSettings(context).preferences.value.sound)
    }

    @Test
    fun `pausar, reanudar y parar sin nada sonando no despiertan al servicio`() {
        val audio = audio()

        audio.pause()
        audio.resume()
        audio.stop()

        assertNull(shadowOf(application).nextStartedService)
    }

    @Test
    fun `con algo sonando, pausar reanudar y parar llegan al servicio`() {
        val audio = audio()
        audio.publish(AmbientAudioState(sound = AmbientSound.RAIN, playing = true))

        audio.pause()
        audio.resume()
        audio.stop()

        val actions = generateSequence { shadowOf(application).nextStartedService }.map { it.action }.toList()
        assertEquals(
            listOf(AmbientAudioService.ACTION_PAUSE, AmbientAudioService.ACTION_RESUME, AmbientAudioService.ACTION_STOP),
            actions,
        )
    }

    @Test
    fun `el volumen se guarda acotado y el temporizador tambien, sin mandar ordenes`() {
        val audio = audio()
        audio.publish(AmbientAudioState(sound = AmbientSound.RAIN, playing = true))

        audio.setVolume(3f)
        audio.setSleepTimer(15)

        val prefs = SharedPreferencesAudioSettings(context).preferences.value
        assertEquals(1f, prefs.volume, 0f)
        assertEquals(15, prefs.sleepMinutes)
        // El servicio vigila los ajustes: no hace falta una orden por cada cambio.
        assertNull(shadowOf(application).nextStartedService)
    }

    @Test
    fun `el estado publicado por el servicio es el que ve la interfaz`() {
        val audio = audio()
        assertFalse(audio.state.value.active)

        audio.publish(AmbientAudioState(sound = AmbientSound.BINAURAL, playing = true))

        assertTrue(audio.state.value.active)
        assertEquals(AmbientSound.BINAURAL, audio.state.value.sound)
    }
}
