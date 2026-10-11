package com.focusdivergent.app.domain.audio

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los sonidos se prueban con números: lo que se puede medir (nivel, color del ruido, frecuencias, continuidad).
 * Cómo suenan lo juzga el oído de quien los escucha, no un test.
 */
class AmbientSynthsTest {

    private val rate = SoundSynths.SAMPLE_RATE
    private val seconds = 5
    private val frames = rate * seconds

    private fun synth(sound: AmbientSound, seed: Long = 1234) = SoundSynths.create(sound, rate, seed)

    private fun channel(samples: FloatArray, index: Int) = FloatArray(samples.size / 2) { samples[it * 2 + index] }

    private fun mean(x: FloatArray) = x.sumOf { it.toDouble() } / x.size

    private fun rms(x: FloatArray) = sqrt(x.sumOf { (it * it).toDouble() } / x.size)

    private fun peak(x: FloatArray) = x.maxOf { abs(it) }

    /** Correlación entre una muestra y la siguiente: ~0 en el ruido blanco, ~1 en lo muy grave y lento. */
    private fun lag1(x: FloatArray): Double {
        val m = mean(x)
        var num = 0.0
        var den = 0.0
        for (i in 0 until x.size - 1) num += (x[i] - m) * (x[i + 1] - m)
        for (v in x) den += (v - m) * (v - m)
        return num / den
    }

    private fun correlation(a: FloatArray, b: FloatArray): Double {
        val ma = mean(a)
        val mb = mean(b)
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in a.indices) {
            num += (a[i] - ma) * (b[i] - mb)
            da += (a[i] - ma) * (a[i] - ma)
            db += (b[i] - mb) * (b[i] - mb)
        }
        return num / sqrt(da * db)
    }

    private fun zeroCrossings(x: FloatArray): Int {
        var count = 0
        for (i in 1 until x.size) if ((x[i - 1] < 0f) != (x[i] < 0f)) count++
        return count
    }

    // ---- Comunes a los cuatro

    @Test
    fun `cada bloque es estereo intercalado y ningun valor pasa del limite, en ninguno de los sonidos`() {
        AmbientSound.entries.forEach { sound ->
            val block = synth(sound).generate(frames)

            assertEquals(sound.name, frames * 2, block.size)
            assertTrue("$sound pico ${peak(block)}", peak(block) <= SoundSynth.PEAK)
        }
    }

    @Test
    fun `ningun sonido tiene componente continua audible`() {
        AmbientSound.entries.forEach { sound ->
            val left = channel(synth(sound).generate(frames), 0)
            assertTrue("$sound media ${mean(left)}", abs(mean(left)) < 0.02)
        }
    }

    @Test
    fun `ningun sonido es silencio ni ensordece`() {
        AmbientSound.entries.forEach { sound ->
            val level = rms(synth(sound).generate(frames))
            assertTrue("$sound rms $level", level in 0.05..0.5)
        }
    }

    @Test
    fun `con la misma semilla sale siempre lo mismo, y con otra sale otra cosa`() {
        listOf(AmbientSound.WHITE_NOISE, AmbientSound.BROWN_NOISE, AmbientSound.RAIN).forEach { sound ->
            assertArrayEquals(sound.name, synth(sound, 7).generate(5000), synth(sound, 7).generate(5000), 0f)
            assertFalse(sound.name, synth(sound, 7).generate(5000).contentEquals(synth(sound, 8).generate(5000)))
        }
    }

    @Test
    fun `pedir el sonido en bloques da exactamente lo mismo que pedirlo de una vez, sin saltos`() {
        AmbientSound.entries.forEach { sound ->
            val whole = synth(sound, 99).generate(6000)
            val parts = synth(sound, 99).let { it.generate(1000) + it.generate(2500) + it.generate(2500) }

            assertArrayEquals("$sound", whole, parts, 0f)
        }
    }

    // ---- Ruido blanco

    @Test
    fun `el ruido blanco no tiene memoria entre muestras y sus dos oidos son independientes`() {
        val block = synth(AmbientSound.WHITE_NOISE).generate(frames)
        val left = channel(block, 0)
        val right = channel(block, 1)

        assertTrue("lag1 ${lag1(left)}", abs(lag1(left)) < 0.02)
        assertTrue("canales ${correlation(left, right)}", abs(correlation(left, right)) < 0.02)
    }

    // ---- Ruido marrón

    @Test
    fun `el ruido marron es mucho mas grave y lento que el blanco`() {
        val brown = channel(synth(AmbientSound.BROWN_NOISE).generate(frames), 0)
        val white = channel(synth(AmbientSound.WHITE_NOISE).generate(frames), 0)

        assertTrue("marrón lag1 ${lag1(brown)}", lag1(brown) > 0.9)
        assertTrue(lag1(brown) > lag1(white) + 0.8)
        // Mucha menos variación de una muestra a la siguiente que de punta a punta: poca energía aguda.
        val step = FloatArray(brown.size - 1) { brown[it + 1] - brown[it] }
        assertTrue(rms(step) < rms(brown) * 0.35)
    }

    // ---- Binaural

    @Test
    fun `el binaural pone un tono por oido con la diferencia exacta de frecuencia`() {
        val block = synth(AmbientSound.BINAURAL).generate(frames)
        val leftHz = zeroCrossings(channel(block, 0)) / (2.0 * seconds)
        val rightHz = zeroCrossings(channel(block, 1)) / (2.0 * seconds)

        assertEquals(BinauralSynth.LEFT_HZ, leftHz, 0.3)
        assertEquals(BinauralSynth.RIGHT_HZ, rightHz, 0.3)
        assertEquals(BinauralSynth.BEAT_HZ, rightHz - leftHz, 0.3)
        assertEquals(10.0, BinauralSynth.BEAT_HZ, 0.0)
    }

    @Test
    fun `los tonos binaurales son suaves y siempre iguales`() {
        val a = synth(AmbientSound.BINAURAL, 1).generate(2000)
        val b = synth(AmbientSound.BINAURAL, 2).generate(2000)

        assertArrayEquals(a, b, 0f)
        assertTrue(peak(a) <= 0.26f)
    }

    // ---- Lluvia

    @Test
    fun `la lluvia tiene sus dos oidos independientes y es mas aguda que el marron`() {
        val block = synth(AmbientSound.RAIN).generate(frames)
        val left = channel(block, 0)
        val right = channel(block, 1)
        val brown = channel(synth(AmbientSound.BROWN_NOISE).generate(frames), 0)

        assertTrue("canales ${correlation(left, right)}", abs(correlation(left, right)) < 0.05)
        assertTrue("lluvia lag1 ${lag1(left)} frente a marrón ${lag1(brown)}", lag1(left) < lag1(brown) - 0.3)
    }

    // ---- Cuáles necesitan auriculares

    @Test
    fun `solo el binaural necesita auriculares`() {
        assertEquals(listOf(AmbientSound.BINAURAL), AmbientSound.entries.filter { it.needsHeadphones })
    }

    // ---- Volumen

    @Test
    fun `el volumen tiene un tope seguro aunque la barra este al maximo`() {
        assertEquals(0f, AmbientVolume.gain(0f), 0f)
        assertEquals(AmbientVolume.MAX_GAIN, AmbientVolume.gain(1f), 0f)
        assertEquals(AmbientVolume.MAX_GAIN, AmbientVolume.gain(5f), 0f)
        assertEquals(0f, AmbientVolume.gain(-1f), 0f)
        assertTrue(AmbientVolume.MAX_GAIN < 1f)
        assertEquals(0.4f * AmbientVolume.MAX_GAIN, AmbientVolume.gain(0.4f), 0.0001f)
    }
}
