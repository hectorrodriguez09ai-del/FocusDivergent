package com.focusdivergent.app.presentation.audio

import com.focusdivergent.app.domain.audio.AmbientAudioState
import com.focusdivergent.app.domain.audio.AmbientSound
import com.focusdivergent.app.domain.audio.AudioPreferences
import com.focusdivergent.app.domain.usecase.FakeAmbientAudio
import com.focusdivergent.app.domain.usecase.FakeAudioSettings
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.usecase.TEST_NOW
import com.focusdivergent.app.presentation.MainDispatcherRule
import java.time.Duration
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AudioViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeAudioSettings(AudioPreferences(sound = AmbientSound.BROWN_NOISE, volume = 0.6f, sleepMinutes = 30))
    private val audio = FakeAmbientAudio(settings)

    private fun TestScope.viewModel(): AudioViewModel =
        AudioViewModel(audio, settings, TEST_CLOCK).also { vm ->
            backgroundScope.launch(mainDispatcher.dispatcher) { vm.uiState.collect() }
        }

    @Test
    fun `al abrir enseña lo ultimo que se eligio y nada sonando`() = runTest {
        val state = viewModel().uiState.value

        assertEquals(AmbientSound.BROWN_NOISE, state.selected)
        assertEquals(0.6f, state.volume, 0f)
        assertEquals(30, state.sleepMinutes)
        assertFalse(state.active)
        assertFalse(state.playing)
        assertNull(state.remainingMinutes)
    }

    @Test
    fun `elegir un sonido sin que suene nada solo lo deja elegido`() = runTest {
        val vm = viewModel()

        vm.select(AmbientSound.RAIN)

        assertEquals(AmbientSound.RAIN, vm.uiState.value.selected)
        assertTrue(audio.calls.isEmpty())
    }

    @Test
    fun `elegir un sonido mientras suena otro cambia al momento`() = runTest {
        val vm = viewModel()
        vm.togglePlay()

        vm.select(AmbientSound.RAIN)

        assertEquals(listOf("play:BROWN_NOISE", "play:RAIN"), audio.calls)
        assertEquals(AmbientSound.RAIN, vm.uiState.value.selected)
    }

    @Test
    fun `el boton principal reproduce, pausa y reanuda segun toque`() = runTest {
        val vm = viewModel()

        vm.togglePlay()
        assertTrue(vm.uiState.value.playing)
        vm.togglePlay()
        assertFalse(vm.uiState.value.playing)
        assertTrue(vm.uiState.value.active)
        vm.togglePlay()
        assertTrue(vm.uiState.value.playing)

        assertEquals(listOf("play:BROWN_NOISE", "pause", "resume"), audio.calls)
    }

    @Test
    fun `parar vuelve a no tener nada en marcha`() = runTest {
        val vm = viewModel()
        vm.togglePlay()

        vm.stop()

        assertFalse(vm.uiState.value.active)
        assertEquals("stop", audio.calls.last())
    }

    @Test
    fun `el volumen y el temporizador de parada se mandan y se recuerdan`() = runTest {
        val vm = viewModel()

        vm.setVolume(0.8f)
        vm.setSleepTimer(15)

        assertEquals(0.8f, vm.uiState.value.volume, 0f)
        assertEquals(15, vm.uiState.value.sleepMinutes)

        vm.setSleepTimer(null)
        assertNull(vm.uiState.value.sleepMinutes)
        assertEquals(listOf("volume:0.8", "sleep:15", "sleep:null"), audio.calls)
    }

    @Test
    fun `si suena otro sonido que el guardado, se enseña el que suena`() = runTest {
        val vm = viewModel()

        audio.setState(AmbientAudioState(sound = AmbientSound.RAIN, playing = true))

        assertEquals(AmbientSound.RAIN, vm.uiState.value.selected)
        assertTrue(vm.uiState.value.active)
    }

    @Test
    fun `los minutos que faltan para parar se redondean hacia arriba`() = runTest {
        val vm = viewModel()

        audio.setState(
            AmbientAudioState(
                sound = AmbientSound.RAIN,
                playing = true,
                stopAt = TEST_NOW + Duration.ofMinutes(14).plusSeconds(30),
            ),
        )
        assertEquals(15, vm.uiState.value.remainingMinutes)

        audio.setState(AmbientAudioState(sound = AmbientSound.RAIN, playing = true, stopAt = TEST_NOW - Duration.ofMinutes(1)))
        assertEquals(0, vm.uiState.value.remainingMinutes)

        audio.setState(AmbientAudioState(sound = AmbientSound.RAIN, playing = true))
        assertNull(vm.uiState.value.remainingMinutes)
    }
}
