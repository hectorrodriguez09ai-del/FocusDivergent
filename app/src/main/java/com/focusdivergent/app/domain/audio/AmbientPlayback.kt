package com.focusdivergent.app.domain.audio

import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Quien produce el sonido de verdad (en Android, un `AudioTrack` alimentado por el sintetizador). */
interface SoundEngine {

    fun start(sound: AmbientSound, gain: Float)

    fun pause()

    fun resume()

    fun setGain(gain: Float)

    /** Para y libera todo. Se puede volver a llamar a [start] después. */
    fun stop()
}

/** Lo que pasa con el foco de audio del sistema (otra app que quiere sonar, una llamada…). */
enum class FocusChange { GAIN, LOSS, LOSS_TRANSIENT, DUCK }

interface AudioFocusPort {

    /** Pide el foco. [onChange] avisa de cada cambio posterior. Devuelve `false` si el sistema lo deniega. */
    fun request(onChange: (FocusChange) -> Unit): Boolean

    fun abandon()
}

/** Lo que se ve fuera de la app mientras suena (la notificación del servicio en primer plano). */
interface PlaybackPresenter {

    /** Enseña (o actualiza) la notificación. Es la primera llamada la que pone el servicio en primer plano. */
    fun show(sound: AmbientSound, playing: Boolean)

    fun hide()
}

/**
 * Las reglas de la reproducción, sin nada de Android: qué hacer al empezar, pausar, perder el foco de audio,
 * desenchufar los auriculares o llegar a la hora de parar. Todo el sonido real se delega en los puertos, así que
 * se prueba con dobles y con el tiempo virtual.
 *
 * Reglas de cortesía con el resto del móvil:
 * - Una llamada o una app que pide el foco **pausa** el sonido, y vuelve solo cuando el foco regresa.
 * - Si otra app pide el foco para siempre (otra música), se **pausa sin volver solo**: ya no es el momento.
 * - Un aviso breve (navegación…) solo **baja** el volumen mientras habla.
 * - Si se desenchufan los auriculares, **se pausa**: no debe empezar a sonar por el altavoz de pronto.
 */
class AmbientPlayback(
    private val engine: SoundEngine,
    private val focus: AudioFocusPort,
    private val presenter: PlaybackPresenter,
    private val preferences: () -> AudioPreferences,
    private val publish: (AmbientAudioState) -> Unit,
    private val onFinished: () -> Unit,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {

    private var sound: AmbientSound? = null
    private var playing = false
    private var resumeOnGain = false
    private var ducked = false
    private var stopAt: java.time.Instant? = null
    private var timer: Job? = null

    /** Empieza [newSound], o cambia a él si ya sonaba otro. */
    fun play(newSound: AmbientSound) {
        // La notificación va primero: un servicio lanzado en primer plano debe enseñarla sí o sí.
        presenter.show(newSound, playing = true)
        if (!focus.request(::onFocusChange)) {
            finish()
            return
        }
        if (sound != null) engine.stop()
        sound = newSound
        playing = true
        resumeOnGain = false
        ducked = false
        engine.start(newSound, gain())
        restartTimer()
        publishState()
    }

    fun pause() {
        val current = sound ?: return
        if (!playing) return
        engine.pause()
        playing = false
        resumeOnGain = false
        focus.abandon()
        presenter.show(current, playing = false)
        publishState()
    }

    fun resume() {
        val current = sound ?: return
        if (playing) return
        if (!focus.request(::onFocusChange)) return
        ducked = false
        engine.setGain(gain())
        engine.resume()
        playing = true
        presenter.show(current, playing = true)
        publishState()
    }

    fun stop() = finish()

    /** El volumen guardado cambió. */
    fun onVolumeChanged() {
        if (sound != null) engine.setGain(gain())
    }

    /** El temporizador de parada guardado cambió: si suena, cuenta desde ahora con el nuevo valor. */
    fun onSleepTimerChanged() {
        if (sound == null) return
        restartTimer()
        publishState()
    }

    fun onBecomingNoisy() = pause()

    fun onFocusChange(change: FocusChange) {
        val current = sound ?: return
        when (change) {
            FocusChange.GAIN -> {
                ducked = false
                engine.setGain(gain())
                if (!playing && resumeOnGain) {
                    resumeOnGain = false
                    engine.resume()
                    playing = true
                    presenter.show(current, playing = true)
                    publishState()
                }
            }
            FocusChange.LOSS_TRANSIENT -> {
                if (playing) {
                    engine.pause()
                    playing = false
                    resumeOnGain = true
                    presenter.show(current, playing = false)
                    publishState()
                }
            }
            FocusChange.LOSS -> {
                if (playing) {
                    engine.pause()
                    playing = false
                    presenter.show(current, playing = false)
                    publishState()
                }
                resumeOnGain = false
                focus.abandon()
            }
            FocusChange.DUCK -> {
                ducked = true
                engine.setGain(gain())
            }
        }
    }

    /** El servicio se destruye: suelta todo sin avisar a nadie. */
    fun release() {
        timer?.cancel()
        if (sound != null) {
            engine.stop()
            focus.abandon()
        }
        sound = null
        playing = false
    }

    private fun finish() {
        timer?.cancel()
        timer = null
        engine.stop()
        focus.abandon()
        sound = null
        playing = false
        resumeOnGain = false
        ducked = false
        stopAt = null
        presenter.hide()
        publish(AmbientAudioState())
        onFinished()
    }

    private fun gain(): Float = AmbientVolume.gain(preferences().volume) * if (ducked) AmbientVolume.DUCK_FACTOR else 1f

    private fun restartTimer() {
        timer?.cancel()
        timer = null
        stopAt = null
        val minutes = preferences().sleepMinutes ?: return
        stopAt = clock.instant() + Duration.ofMinutes(minutes.toLong())
        timer = scope.launch {
            delay(Duration.ofMinutes(minutes.toLong()).toMillis())
            finish()
        }
    }

    private fun publishState() = publish(AmbientAudioState(sound = sound, playing = playing, stopAt = stopAt))
}
