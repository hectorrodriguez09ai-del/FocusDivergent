package com.focusdivergent.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.focusdivergent.app.MainActivity
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.realitycheck.RealityCheckSettings
import com.focusdivergent.app.domain.usecase.AcknowledgeRealityCheckUseCase
import com.focusdivergent.app.domain.usecase.ApplyDueLimitChangesUseCase
import com.focusdivergent.app.domain.usecase.RunDayClosureUseCase
import com.focusdivergent.app.domain.usecase.ScheduleDayClosureUseCase
import com.focusdivergent.app.domain.usecase.EvaluateInterruptionUseCase
import com.focusdivergent.app.domain.usecase.FinishFocusLaunchUseCase
import com.focusdivergent.app.domain.usecase.GrantExtraTimeUseCase
import com.focusdivergent.app.domain.usecase.SnoozeObligationReminderUseCase
import com.focusdivergent.app.presentation.capture.CaptureActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que vigila qué app está en pantalla y la cubre cuando toca (RNF-2).
 *
 * Al ser un `ForegroundService` con notificación, Android no lo mata por ahorro de batería. Se
 * reevalúa cuando el servicio de accesibilidad avisa de un cambio de ventana y, como red de
 * seguridad, cada [POLL_INTERVAL_MS].
 */
@AndroidEntryPoint
class FocusBlockerService : Service() {

    @Inject lateinit var evaluateInterruption: EvaluateInterruptionUseCase
    @Inject lateinit var applyDueLimitChanges: ApplyDueLimitChangesUseCase
    @Inject lateinit var runDayClosure: RunDayClosureUseCase
    @Inject lateinit var scheduleDayClosure: ScheduleDayClosureUseCase
    @Inject lateinit var grantExtraTime: GrantExtraTimeUseCase
    @Inject lateinit var snoozeReminder: SnoozeObligationReminderUseCase
    @Inject lateinit var acknowledgeRealityCheck: AcknowledgeRealityCheckUseCase
    @Inject lateinit var finishFocusLaunch: FinishFocusLaunchUseCase
    @Inject lateinit var realityCheckSettings: RealityCheckSettings
    @Inject lateinit var trigger: BlockCheckTrigger
    @Inject lateinit var status: BlockerStatus

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlay: InterruptionOverlayController

    override fun onCreate() {
        super.onCreate()
        overlay = InterruptionOverlayController(
            this,
            onEarnExtension = ::earnExtension,
            onPostponeReminder = ::postponeReminder,
            onContinueRealityCheck = ::continueRealityCheck,
            realityCheckSilenceMinutes = { realityCheckSettings.silenceMinutes },
            onLaunchFinished = trigger::request,
            onLaunchContinue = ::continueLaunch,
            onLaunchStop = ::stopLaunch,
        )
        startInForeground()
        status.setRunning(true)
        scope.launch { monitor() }
    }

    // START_STICKY: si el sistema acaba con el proceso, lo vuelve a crear en cuanto puede.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        overlay.hide()
        status.setRunning(false)
        super.onDestroy()
    }

    // El reto ya está resuelto: se registra la ampliación y se vuelve a evaluar al instante, para que el
    // overlay se retire en cuanto la app tenga tiempo de nuevo.
    private fun earnExtension(packageName: String) {
        scope.launch {
            try {
                Log.i(TAG, "Ampliación para $packageName: ${grantExtraTime(packageName)}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo registrar la ampliación", e)
            }
            trigger.request()
        }
    }

    // El reto ya está resuelto: el aviso desaparece ahora y volverá a aparecer pasados los minutos pospuestos.
    private fun postponeReminder(taskId: Long) {
        snoozeReminder(taskId)
        trigger.request()
    }

    // «Sí, es a propósito»: el modal se retira ahora y no vuelve a preguntar hasta pasado el tiempo de silencio.
    private fun continueRealityCheck(packageName: String) {
        acknowledgeRealityCheck(packageName)
        trigger.request()
    }

    // La Lanzadera terminó su cuenta atrás: se vuelve a evaluar al instante para pasar a la pregunta, sin esperar al ciclo.
    // «Seguir» deja la tarea en marcha; «Lo dejo por ahora» no deja ninguna huella.
    private fun continueLaunch(taskId: Long?) {
        scope.launch {
            try {
                finishFocusLaunch.continueWork(taskId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo dejar la tarea en marcha", e)
            }
            trigger.request()
        }
    }

    private fun stopLaunch() {
        finishFocusLaunch.stop()
        trigger.request()
    }

    private suspend fun monitor() {
        val ticks = flow {
            while (true) {
                emit(Unit)
                delay(POLL_INTERVAL_MS)
            }
        }

        merge(trigger.events, ticks).conflate().collect {
            try {
                // Las relajaciones de límite que esperaban a medianoche entran en vigor aquí, aunque nadie abra la app.
                applyDueLimitChanges()
                // Cierre de Jornada: si ya toca y no se hizo (alarma retrasada, móvil apagado a esa hora), se hace aquí.
                if (runDayClosure() != null) scheduleDayClosure()
                // Con la pantalla apagada o en una llamada el caso de uso devuelve nulo y el overlay se retira.
                val interruption = evaluateInterruption()
                if (interruption != null) overlay.show(interruption) else overlay.hide()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Un fallo puntual al leer el uso no debe parar la vigilancia; se conserva lo que haya en pantalla.
                Log.w(TAG, "No se pudo comprobar si hay que interrumpir", e)
            }
            // Tope de frecuencia: los cambios de ventana pueden llegar en ráfagas.
            delay(MIN_CHECK_GAP_MS)
        }
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // «Anotar idea» (RF-5.1): como esta notificación está siempre a la vista, es el acceso más cercano a la captura.
        val capture = PendingIntent.getActivity(
            this,
            1,
            CaptureActivity.intent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_idea, getString(R.string.notification_action_capture), capture)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .build()

        // Desde Android 14 el tipo de servicio en primer plano es obligatorio.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "FocusBlockerService"
        private const val CHANNEL_ID = "focus_protection"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 5_000L
        private const val MIN_CHECK_GAP_MS = 300L

        /** Arranca la protección. Debe llamarse con la app a la vista (Android restringe iniciar servicios en primer plano desde segundo plano). */
        fun start(context: Context) {
            ProtectionPreference.setEnabled(context, true)
            ContextCompat.startForegroundService(context, Intent(context, FocusBlockerService::class.java))
        }

        /** Pausa la protección por decisión del usuario: ni las alarmas ni el reinicio la reanudarán. */
        fun stop(context: Context) {
            ProtectionPreference.setEnabled(context, false)
            context.stopService(Intent(context, FocusBlockerService::class.java))
        }
    }
}
