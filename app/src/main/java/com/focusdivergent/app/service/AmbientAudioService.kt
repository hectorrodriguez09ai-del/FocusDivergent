package com.focusdivergent.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.focusdivergent.app.MainActivity
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.audio.AmbientAudioState
import com.focusdivergent.app.domain.audio.AmbientPlayback
import com.focusdivergent.app.domain.audio.AmbientSound
import com.focusdivergent.app.domain.audio.AudioSettings
import com.focusdivergent.app.domain.audio.PlaybackPresenter
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano de tipo reproducción de medios (RF-4.2): mantiene el sonido ambiente aunque se cierre la
 * app. Es solo la parte de Android; las reglas (foco de audio, pausas, temporizador de parada) están en
 * [AmbientPlayback], que no sabe nada de Android y está probada con dobles.
 *
 * No se reinicia solo si el sistema lo mata: un sonido que vuelve a sonar sin que nadie lo pida sería peor que el silencio.
 */
@AndroidEntryPoint
class AmbientAudioService : Service() {

    @Inject lateinit var audio: AndroidAmbientAudio
    @Inject lateinit var settings: AudioSettings
    @Inject lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var playback: AmbientPlayback
    private var inForeground = false

    // Se desenchufan los auriculares: el sonido se pausa en vez de pasar de golpe al altavoz.
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            playback.onBecomingNoisy()
        }
    }

    override fun onCreate() {
        super.onCreate()
        playback = AmbientPlayback(
            engine = AudioTrackEngine(),
            focus = AndroidAudioFocus(this),
            presenter = NotificationPresenter(),
            preferences = { settings.preferences.value },
            publish = audio::publish,
            onFinished = ::stopSelf,
            clock = clock,
            scope = scope,
        )
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        // El volumen y el temporizador de parada se cambian desde la pantalla guardándolos en los ajustes: aquí se
        // aplican en cuanto cambian, sin una orden por cada movimiento de la barra.
        scope.launch { settings.preferences.map { it.volume }.distinctUntilChanged().drop(1).collect { playback.onVolumeChanged() } }
        scope.launch { settings.preferences.map { it.sleepMinutes }.distinctUntilChanged().drop(1).collect { playback.onSleepTimerChanged() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val sound = intent.getStringExtra(EXTRA_SOUND)?.let { name -> AmbientSound.entries.firstOrNull { it.name == name } }
                playback.play(sound ?: settings.preferences.value.sound)
            }
            ACTION_PAUSE -> playback.pause()
            ACTION_RESUME -> playback.resume()
            ACTION_STOP -> playback.stop()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        playback.release()
        audio.publish(AmbientAudioState())
        scope.cancel()
        super.onDestroy()
    }

    /** La notificación fija del servicio, con «Pausar o Reanudar» y «Parar». */
    private inner class NotificationPresenter : PlaybackPresenter {

        override fun show(sound: AmbientSound, playing: Boolean) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.audio_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
            val notification = buildNotification(sound, playing)
            if (inForeground) {
                manager.notify(NOTIFICATION_ID, notification)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                inForeground = true
            }
        }

        override fun hide() {
            if (inForeground) stopForeground(STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
    }

    private fun buildNotification(sound: AmbientSound, playing: Boolean): Notification {
        val toggle = if (playing) {
            NotificationCompat.Action(0, getString(R.string.audio_pause), action(ACTION_PAUSE, REQUEST_PAUSE))
        } else {
            NotificationCompat.Action(0, getString(R.string.audio_resume), action(ACTION_RESUME, REQUEST_RESUME))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_audio)
            .setContentTitle(getString(R.string.audio_title))
            .setContentText(getString(if (playing) R.string.audio_status_playing else R.string.audio_status_paused, getString(sound.labelRes())))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    REQUEST_OPEN,
                    MainActivity.openAudioIntent(this),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .addAction(toggle)
            .addAction(NotificationCompat.Action(0, getString(R.string.audio_stop), action(ACTION_STOP, REQUEST_STOP)))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }

    private fun action(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(this, requestCode, intent(this, action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        const val ACTION_PLAY = "com.focusdivergent.app.audio.PLAY"
        const val ACTION_PAUSE = "com.focusdivergent.app.audio.PAUSE"
        const val ACTION_RESUME = "com.focusdivergent.app.audio.RESUME"
        const val ACTION_STOP = "com.focusdivergent.app.audio.STOP"
        const val EXTRA_SOUND = "sound"

        private const val CHANNEL_ID = "ambient_audio"
        private const val NOTIFICATION_ID = 30
        private const val REQUEST_OPEN = 4
        private const val REQUEST_PAUSE = 5
        private const val REQUEST_RESUME = 6
        private const val REQUEST_STOP = 7

        fun intent(context: Context, action: String): Intent =
            Intent(context, AmbientAudioService::class.java).setAction(action)
    }
}

/** El nombre del sonido tal como se lee en la interfaz. */
fun AmbientSound.labelRes(): Int = when (this) {
    AmbientSound.WHITE_NOISE -> R.string.audio_sound_white
    AmbientSound.BROWN_NOISE -> R.string.audio_sound_brown
    AmbientSound.BINAURAL -> R.string.audio_sound_binaural
    AmbientSound.RAIN -> R.string.audio_sound_rain
}
