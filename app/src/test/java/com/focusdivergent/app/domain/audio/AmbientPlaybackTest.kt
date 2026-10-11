package com.focusdivergent.app.domain.audio

import com.focusdivergent.app.domain.usecase.FakeAudioFocus
import com.focusdivergent.app.domain.usecase.FakePresenter
import com.focusdivergent.app.domain.usecase.FakeSoundEngine
import com.focusdivergent.app.domain.usecase.MutableClock
import com.focusdivergent.app.domain.usecase.TEST_NOW
import java.time.Duration
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las reglas de la reproducción, sin Android: foco de audio, pausas, auriculares, volumen y temporizador de parada. */
class AmbientPlaybackTest {

    private val engine = FakeSoundEngine()
    private val focus = FakeAudioFocus()
    private val presenter = FakePresenter()
    private val clock = MutableClock()
    private val published = mutableListOf<AmbientAudioState>()
    private var finished = 0
    private var prefs = AudioPreferences(volume = 0.4f)

    private fun TestScope.playback() = AmbientPlayback(
        engine = engine,
        focus = focus,
        presenter = presenter,
        preferences = { prefs },
        publish = { published += it },
        onFinished = { finished++ },
        clock = clock,
        scope = backgroundScope,
    )

    private val last get() = published.last()

    private fun gainFor(level: Float) = AmbientVolume.gain(level)

    // ---- Empezar

    @Test
    fun `empezar enseña la notificacion antes que nada, pide el foco y arranca con el volumen guardado`() = runTest {
        val playback = playback()

        playback.play(AmbientSound.RAIN)

        assertEquals(listOf(AmbientSound.RAIN to true), presenter.shown)
        assertTrue(focus.held)
        assertEquals(listOf("start:RAIN"), engine.events)
        assertEquals(gainFor(0.4f), engine.currentGain, 0.0001f)
        assertEquals(AmbientAudioState(sound = AmbientSound.RAIN, playing = true, stopAt = null), last)
    }

    @Test
    fun `si el sistema deniega el foco no suena nada y el servicio se cierra, tras haber enseñado la notificacion`() = runTest {
        focus.grant = false
        val playback = playback()

        playback.play(AmbientSound.WHITE_NOISE)

        assertEquals(1, presenter.shown.size)
        assertEquals(1, presenter.hidden)
        assertTrue(engine.events.none { it.startsWith("start") })
        assertEquals(AmbientAudioState(), last)
        assertEquals(1, finished)
    }

    @Test
    fun `cambiar de sonido mientras suena para el anterior y arranca el nuevo`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.WHITE_NOISE)

        playback.play(AmbientSound.BROWN_NOISE)

        assertEquals(listOf("start:WHITE_NOISE", "stop", "start:BROWN_NOISE"), engine.events)
        assertEquals(AmbientSound.BROWN_NOISE, last.sound)
        assertTrue(last.playing)
        assertEquals(0, finished)
    }

    // ---- Pausar, reanudar, parar

    @Test
    fun `pausar detiene el audio, suelta el foco y deja el sonido elegido`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)

        playback.pause()

        assertEquals("pause", engine.events.last())
        assertFalse(focus.held)
        assertEquals(AmbientSound.RAIN to false, presenter.shown.last())
        assertEquals(AmbientAudioState(sound = AmbientSound.RAIN, playing = false), last)
        assertEquals(0, finished)
    }

    @Test
    fun `reanudar vuelve a pedir el foco y sigue sonando`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)
        playback.pause()

        playback.resume()

        assertEquals("resume", engine.events.last())
        assertTrue(focus.held)
        assertTrue(last.playing)
        assertEquals(AmbientSound.RAIN to true, presenter.shown.last())
    }

    @Test
    fun `si al reanudar el sistema no da el foco, sigue en pausa`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)
        playback.pause()
        focus.grant = false

        playback.resume()

        assertFalse(last.playing)
        assertTrue(engine.events.none { it == "resume" })
    }

    @Test
    fun `parar lo libera todo, retira la notificacion y cierra el servicio`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)

        playback.stop()

        assertEquals("stop", engine.events.last())
        assertFalse(focus.held)
        assertEquals(1, presenter.hidden)
        assertEquals(AmbientAudioState(), last)
        assertEquals(1, finished)
    }

    @Test
    fun `pausar, reanudar o parar sin nada sonando no hace nada`() = runTest {
        val playback = playback()

        playback.pause()
        playback.resume()
        playback.onFocusChange(FocusChange.LOSS_TRANSIENT)
        playback.onVolumeChanged()

        assertTrue(engine.events.isEmpty())
        assertTrue(published.isEmpty())
    }

    // ---- Foco de audio

    @Test
    fun `una llamada pausa el sonido y, al terminar, vuelve solo`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)

        focus.emit(FocusChange.LOSS_TRANSIENT)
        assertFalse(last.playing)
        assertEquals("pause", engine.events.last())

        focus.emit(FocusChange.GAIN)
        assertTrue(last.playing)
        assertEquals("resume", engine.events.last())
    }

    @Test
    fun `si el usuario pauso, recuperar el foco no lo reanuda por su cuenta`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)
        playback.pause()
        engine.events.clear()

        focus.emit(FocusChange.GAIN)

        assertTrue(engine.events.none { it == "resume" })
        assertFalse(last.playing)
    }

    @Test
    fun `si otra app toma el foco para siempre, se pausa y no vuelve sola`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)

        focus.emit(FocusChange.LOSS)
        assertFalse(last.playing)
        assertFalse(focus.held)

        focus.emit(FocusChange.GAIN)
        assertFalse(last.playing)
        assertEquals(1, engine.events.count { it == "pause" })
    }

    @Test
    fun `un aviso breve baja el volumen mientras habla y lo devuelve despues`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)

        focus.emit(FocusChange.DUCK)
        assertEquals(gainFor(0.4f) * AmbientVolume.DUCK_FACTOR, engine.currentGain, 0.0001f)
        assertTrue(last.playing)

        focus.emit(FocusChange.GAIN)
        assertEquals(gainFor(0.4f), engine.currentGain, 0.0001f)
    }

    // ---- Auriculares

    @Test
    fun `al desenchufar los auriculares se pausa, para no pasar de golpe al altavoz`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.BINAURAL)

        playback.onBecomingNoisy()

        assertFalse(last.playing)
        assertEquals("pause", engine.events.last())
    }

    // ---- Volumen

    @Test
    fun `el volumen se aplica al instante y nunca pasa del tope`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)

        prefs = prefs.copy(volume = 1f)
        playback.onVolumeChanged()
        assertEquals(AmbientVolume.MAX_GAIN, engine.currentGain, 0.0001f)

        prefs = prefs.copy(volume = 0f)
        playback.onVolumeChanged()
        assertEquals(0f, engine.currentGain, 0f)
    }

    @Test
    fun `cambiar el volumen durante un aviso sigue respetando el volumen bajo`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)
        focus.emit(FocusChange.DUCK)

        prefs = prefs.copy(volume = 0.8f)
        playback.onVolumeChanged()

        assertEquals(gainFor(0.8f) * AmbientVolume.DUCK_FACTOR, engine.currentGain, 0.0001f)
    }

    // ---- Temporizador de parada

    @Test
    fun `con temporizador, el sonido se para solo al cumplirse y lo cuenta el estado`() = runTest {
        prefs = prefs.copy(sleepMinutes = 15)
        val playback = playback()

        playback.play(AmbientSound.RAIN)
        assertEquals(TEST_NOW + Duration.ofMinutes(15), last.stopAt)

        advanceTimeBy(Duration.ofMinutes(14).toMillis())
        runCurrent()
        assertTrue(last.playing)
        assertEquals(0, finished)

        advanceTimeBy(Duration.ofMinutes(1).toMillis() + 1)
        runCurrent()
        assertEquals(AmbientAudioState(), last)
        assertEquals("stop", engine.events.last())
        assertEquals(1, finished)
    }

    @Test
    fun `sin temporizador no se para nunca solo`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)

        advanceTimeBy(Duration.ofHours(10).toMillis())
        runCurrent()

        assertTrue(last.playing)
        assertNull(last.stopAt)
        assertEquals(0, finished)
    }

    @Test
    fun `cambiar el temporizador mientras suena cuenta desde ahora`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)
        advanceTimeBy(Duration.ofMinutes(10).toMillis())
        clock.now = TEST_NOW + Duration.ofMinutes(10)

        prefs = prefs.copy(sleepMinutes = 30)
        playback.onSleepTimerChanged()

        assertEquals(clock.now + Duration.ofMinutes(30), last.stopAt)
        advanceTimeBy(Duration.ofMinutes(29).toMillis())
        runCurrent()
        assertTrue(last.playing)
        advanceTimeBy(Duration.ofMinutes(1).toMillis() + 1)
        runCurrent()
        assertEquals(AmbientAudioState(), last)
    }

    @Test
    fun `quitar el temporizador cancela la parada pendiente`() = runTest {
        prefs = prefs.copy(sleepMinutes = 15)
        val playback = playback()
        playback.play(AmbientSound.RAIN)

        prefs = prefs.copy(sleepMinutes = null)
        playback.onSleepTimerChanged()
        advanceTimeBy(Duration.ofMinutes(60).toMillis())
        runCurrent()

        assertTrue(last.playing)
        assertNull(last.stopAt)
        assertEquals(0, finished)
    }

    @Test
    fun `el temporizador tambien para un sonido que esta en pausa`() = runTest {
        prefs = prefs.copy(sleepMinutes = 15)
        val playback = playback()
        playback.play(AmbientSound.RAIN)
        playback.pause()

        advanceTimeBy(Duration.ofMinutes(15).toMillis() + 1)
        runCurrent()

        assertEquals(AmbientAudioState(), last)
        assertEquals(1, finished)
    }

    @Test
    fun `parar a mano cancela el temporizador, sin parar dos veces`() = runTest {
        prefs = prefs.copy(sleepMinutes = 15)
        val playback = playback()
        playback.play(AmbientSound.RAIN)

        playback.stop()
        advanceTimeBy(Duration.ofMinutes(30).toMillis())
        runCurrent()

        assertEquals(1, finished)
        assertEquals(1, engine.events.count { it == "stop" })
    }

    // ---- Liberar

    @Test
    fun `liberar al destruir el servicio suelta el audio y el foco sin avisar a nadie`() = runTest {
        val playback = playback()
        playback.play(AmbientSound.RAIN)
        val publishedBefore = published.size

        playback.release()

        assertEquals("stop", engine.events.last())
        assertFalse(focus.held)
        assertEquals(publishedBefore, published.size)
        assertEquals(0, finished)
    }
}
