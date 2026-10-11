package com.focusdivergent.app.domain.audio

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** Los sonidos ambiente (RF-4.2). Todos se generan en el móvil: sin ficheros, sin red, sin licencias. */
enum class AmbientSound(val needsHeadphones: Boolean = false) {
    WHITE_NOISE,
    BROWN_NOISE,

    /** Dos tonos, uno por oído, con una diferencia fija: solo se nota con auriculares. */
    BINAURAL(needsHeadphones = true),
    RAIN,
}

/**
 * Lo que suena ahora mismo.
 *
 * @property sound el sonido activo, o nulo si no hay nada en marcha (ni en pausa).
 * @property playing suena; falso con [sound] distinto de nulo es «en pausa».
 * @property stopAt cuándo se parará solo, si hay temporizador de parada.
 */
data class AmbientAudioState(
    val sound: AmbientSound? = null,
    val playing: Boolean = false,
    val stopAt: Instant? = null,
) {
    /** Hay algo en marcha o en pausa (el servicio de reproducción está vivo). */
    val active: Boolean get() = sound != null
}

/**
 * Lo último que eligió el usuario.
 *
 * @property volume el nivel de 0 a 1 de la barra de volumen; el volumen real aplicado lo acota [AmbientVolume].
 * @property sleepMinutes parar solo pasados estos minutos, o nulo para no parar.
 */
data class AudioPreferences(
    val sound: AmbientSound = AmbientSound.WHITE_NOISE,
    val volume: Float = AmbientVolume.DEFAULT_LEVEL,
    val sleepMinutes: Int? = null,
)

/** Volumen con un tope seguro: aunque la barra esté al máximo, la app nunca sube de [MAX_GAIN]. */
object AmbientVolume {
    const val MAX_GAIN = 0.7f
    const val DEFAULT_LEVEL = 0.4f

    /** Mientras otra app habla encima (aviso de navegación…), el ruido baja a esta fracción. */
    const val DUCK_FACTOR = 0.25f

    fun gain(level: Float): Float = level.coerceIn(0f, 1f) * MAX_GAIN
}

/** Opciones de «parar solo en». Nulo es «sin límite». */
object SleepTimerOptions {
    val MINUTES = listOf(15, 30, 60)
}

interface AudioSettings {

    val preferences: StateFlow<AudioPreferences>

    fun update(change: (AudioPreferences) -> AudioPreferences)
}

/**
 * El reproductor de sonido ambiente visto desde la interfaz. El audio de verdad vive en un servicio en primer plano
 * (el sonido no se corta al salir de la app); esto solo le manda órdenes y refleja su estado.
 */
interface AmbientAudio {

    val state: StateFlow<AmbientAudioState>

    /** Empieza [sound] (o cambia a él si ya sonaba otro) con el volumen y el temporizador de parada guardados. */
    fun play(sound: AmbientSound)

    fun pause()

    fun resume()

    /** Para del todo y retira la notificación. */
    fun stop()

    /** Cambia el volumen (0 a 1) al momento y lo recuerda. */
    fun setVolume(level: Float)

    /** Pone, cambia o quita (nulo) el temporizador de parada, y lo recuerda. Si ya suena, cuenta desde ahora. */
    fun setSleepTimer(minutes: Int?)
}
