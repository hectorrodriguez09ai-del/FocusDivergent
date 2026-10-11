package com.focusdivergent.app.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.focusdivergent.app.domain.audio.AmbientSound
import com.focusdivergent.app.domain.audio.SoundEngine
import com.focusdivergent.app.domain.audio.SoundSynth
import com.focusdivergent.app.domain.audio.SoundSynths
import kotlin.math.roundToInt

/**
 * Reproduce el sonido sintetizado con un `AudioTrack` en modo continuo. Un hilo propio genera bloques y los escribe;
 * la escritura bloquea cuando el búfer está lleno, así que es el propio audio el que marca el ritmo y casi no gasta
 * CPU. Al pausar, el hilo se queda parado en esa escritura hasta que se reanuda.
 */
class AudioTrackEngine : SoundEngine {

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile private var running = false

    override fun start(sound: AmbientSound, gain: Float) {
        stop()
        val synth = SoundSynths.create(sound)
        val audioTrack = buildTrack()
        audioTrack.setVolume(gain)
        // Subida suave al empezar: sin un chasquido al arrancar.
        track = audioTrack
        running = true
        audioTrack.play()
        thread = Thread({ pump(synth, audioTrack) }, "ambient-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    override fun pause() {
        track?.pause()
    }

    override fun resume() {
        track?.play()
    }

    override fun setGain(gain: Float) {
        track?.setVolume(gain)
    }

    override fun stop() {
        val audioTrack = track ?: return
        running = false
        try {
            // Vaciar el búfer desbloquea la escritura pendiente del hilo, que ve que ya no debe seguir.
            audioTrack.pause()
            audioTrack.flush()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "El audio ya estaba parado", e)
        }
        thread?.join(JOIN_MS)
        audioTrack.release()
        track = null
        thread = null
    }

    private fun pump(synth: SoundSynth, audioTrack: AudioTrack) {
        val samples = ShortArray(BLOCK_FRAMES * 2)
        var rampFrames = 0
        val rampTotal = SoundSynths.SAMPLE_RATE / 2
        while (running) {
            val block = synth.generate(BLOCK_FRAMES)
            for (i in block.indices) {
                val frame = i / 2
                val fade = if (rampFrames + frame < rampTotal) (rampFrames + frame).toFloat() / rampTotal else 1f
                samples[i] = (block[i] * fade * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            if (rampFrames < rampTotal) rampFrames += BLOCK_FRAMES
            val written = audioTrack.write(samples, 0, samples.size)
            if (written < 0) {
                Log.w(TAG, "Error al escribir el audio: $written")
                break
            }
        }
    }

    private fun buildTrack(): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SoundSynths.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(
            SoundSynths.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        // Medio segundo de búfer: aguanta un tirón del sistema sin cortes, y la latencia no importa en un ruido de fondo.
        val bufferBytes = maxOf(minBuffer, SoundSynths.SAMPLE_RATE / 2 * BYTES_PER_FRAME)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private companion object {
        const val TAG = "AmbientAudio"
        const val BLOCK_FRAMES = 2048
        const val BYTES_PER_FRAME = 4
        const val JOIN_MS = 500L
    }
}
