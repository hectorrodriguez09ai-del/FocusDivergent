package com.focusdivergent.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.focusdivergent.app.domain.audio.AudioFocusPort
import com.focusdivergent.app.domain.audio.FocusChange

/** El foco de audio del sistema: educación con las llamadas, los avisos de otras apps y otra música. */
class AndroidAudioFocus(context: Context) : AudioFocusPort {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    override fun request(onChange: (FocusChange) -> Unit): Boolean {
        // Una petición nueva sustituye a la anterior: no se acumulan.
        abandon()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> onChange(FocusChange.GAIN)
                    AudioManager.AUDIOFOCUS_LOSS -> onChange(FocusChange.LOSS)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onChange(FocusChange.LOSS_TRANSIENT)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onChange(FocusChange.DUCK)
                }
            }
            .build()
        request = focusRequest
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        request?.let(audioManager::abandonAudioFocusRequest)
        request = null
    }
}
