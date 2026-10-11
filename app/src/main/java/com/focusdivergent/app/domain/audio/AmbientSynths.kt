package com.focusdivergent.app.domain.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Un generador de sonido en continuo. Cada llamada a [generate] sigue donde acabó la anterior, sin saltos, así que
 * se puede pedir en bloques de cualquier tamaño y el resultado es el mismo. Es determinista: con la misma semilla
 * da siempre el mismo sonido.
 */
interface SoundSynth {

    /** [frames] fotogramas estéreo intercalados (izquierda, derecha, izquierda…): `2 * frames` valores entre -[PEAK] y [PEAK]. */
    fun generate(frames: Int): FloatArray

    companion object {
        /** Ningún valor generado supera esto, de modo que el sonido nunca satura. */
        const val PEAK = 0.9f
    }
}

object SoundSynths {

    const val SAMPLE_RATE = 44_100

    fun create(sound: AmbientSound, sampleRate: Int = SAMPLE_RATE, seed: Long = System.nanoTime()): SoundSynth = when (sound) {
        AmbientSound.WHITE_NOISE -> WhiteNoiseSynth(seed)
        AmbientSound.BROWN_NOISE -> BrownNoiseSynth(seed)
        AmbientSound.BINAURAL -> BinauralSynth(sampleRate)
        AmbientSound.RAIN -> RainSynth(seed)
    }
}

/** Limitador suave: nunca pasa de [SoundSynth.PEAK] y no recorta de golpe. */
private fun soft(x: Float): Float = (tanh(x.toDouble()) * SoundSynth.PEAK).toFloat()

private fun Random.signed(): Float = nextFloat() * 2f - 1f

/** Ruido blanco: todas las frecuencias por igual. Constante y neutro. */
internal class WhiteNoiseSynth(seed: Long) : SoundSynth {
    private val random = Random(seed)

    override fun generate(frames: Int): FloatArray = FloatArray(frames * 2) { random.signed() * GAIN }

    private companion object {
        const val GAIN = 0.35f
    }
}

/** Ruido marrón: el blanco integrado. Más grave y suave, con la energía en los tonos bajos. */
internal class BrownNoiseSynth(seed: Long) : SoundSynth {
    private val random = Random(seed)
    private val last = FloatArray(2)

    override fun generate(frames: Int): FloatArray {
        val out = FloatArray(frames * 2)
        for (i in out.indices) {
            val channel = i and 1
            val value = (last[channel] + STEP * random.signed()) / (1f + STEP)
            last[channel] = value
            out[i] = soft(value * GAIN)
        }
        return out
    }

    private companion object {
        const val STEP = 0.02f
        const val GAIN = 3.5f
    }
}

/**
 * Binaural: un tono por oído, [LEFT_HZ] y [RIGHT_HZ], con [BEAT_HZ] de diferencia. Puros y suaves; con auriculares el
 * cerebro percibe un pulso lento. Se presenta como un sonido más, sin prometer efectos médicos.
 */
internal class BinauralSynth(private val sampleRate: Int) : SoundSynth {
    private var leftPhase = 0.0
    private var rightPhase = 0.0

    override fun generate(frames: Int): FloatArray {
        val out = FloatArray(frames * 2)
        val leftStep = 2.0 * PI * LEFT_HZ / sampleRate
        val rightStep = 2.0 * PI * RIGHT_HZ / sampleRate
        for (frame in 0 until frames) {
            out[frame * 2] = (sin(leftPhase) * AMPLITUDE).toFloat()
            out[frame * 2 + 1] = (sin(rightPhase) * AMPLITUDE).toFloat()
            leftPhase = (leftPhase + leftStep) % (2.0 * PI)
            rightPhase = (rightPhase + rightStep) % (2.0 * PI)
        }
        return out
    }

    companion object {
        const val LEFT_HZ = 200.0
        const val BEAT_HZ = 10.0
        const val RIGHT_HZ = LEFT_HZ + BEAT_HZ
        private const val AMPLITUDE = 0.25
    }
}

/**
 * Lluvia: un siseo de banda media (ruido sin los graves ni los agudos extremos) con gotas al azar, cada una un
 * chasquido corto que se apaga enseguida. Cada oído tiene su propia lluvia. Es lo más subjetivo de los cuatro.
 */
internal class RainSynth(seed: Long) : SoundSynth {
    private val random = Random(seed)
    private val fast = FloatArray(2)
    private val slow = FloatArray(2)
    private val drop = FloatArray(2)

    override fun generate(frames: Int): FloatArray {
        val out = FloatArray(frames * 2)
        for (frame in 0 until frames) {
            for (channel in 0..1) {
                val noise = random.signed()
                fast[channel] += FAST * (noise - fast[channel])
                slow[channel] += SLOW * (noise - slow[channel])
                val hiss = (fast[channel] - slow[channel]) * HISS_GAIN

                if (random.nextFloat() < DROP_PROBABILITY) drop[channel] = MIN_DROP + random.nextFloat() * DROP_RANGE
                val splash = drop[channel] * random.signed()
                drop[channel] *= DROP_DECAY

                out[frame * 2 + channel] = soft(hiss + splash)
            }
        }
        return out
    }

    private companion object {
        const val FAST = 0.5f
        const val SLOW = 0.03f
        const val HISS_GAIN = 0.5f

        // Unas 70 gotas por segundo y oído, cada una de unos 3 ms.
        const val DROP_PROBABILITY = 70f / SoundSynths.SAMPLE_RATE
        const val DROP_DECAY = 0.9925f
        const val MIN_DROP = 0.15f
        const val DROP_RANGE = 0.25f
    }
}
