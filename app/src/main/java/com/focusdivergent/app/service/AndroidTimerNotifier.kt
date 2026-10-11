package com.focusdivergent.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.focusdivergent.app.MainActivity
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.timer.TimerNotifier
import com.focusdivergent.app.domain.timer.TimerPhaseKind
import com.focusdivergent.app.domain.timer.TimerSession
import com.focusdivergent.app.domain.timer.TimerState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Notificaciones del temporizador.
 *
 * La notificación **en curso** es silenciosa y lleva un cronómetro regresivo que dibuja el sistema: la app no hace
 * nada mientras corre. Los **avisos** (cambio de fase y fin) usan otro canal con sonido normal, no de alarma, y
 * desaparecen solos pasados unos minutos.
 */
class AndroidTimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : TimerNotifier {

    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun showRunning(session: TimerSession, state: TimerState.Running) {
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(phaseTitle(state))
            .setContentText(session.taskTitle ?: context.getString(R.string.timer_notification_running))
            .setContentIntent(openTimer())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            // Cuenta atrás hasta el fin de la fase, dibujada por Android.
            .setShowWhen(true)
            .setWhen(state.phaseEndsAt.toEpochMilli())
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .build()
        manager.notify(ID_RUNNING, notification)
    }

    override fun alertPhaseChange(session: TimerSession, state: TimerState.Running) {
        ensureChannels()
        val isBreak = state.phase.kind == TimerPhaseKind.BREAK
        val title = context.getString(if (isBreak) R.string.timer_alert_break_title else R.string.timer_alert_focus_title)
        val text = if (isBreak) {
            context.getString(R.string.timer_alert_break_text, state.phase.minutes)
        } else {
            context.getString(R.string.timer_alert_focus_text, state.focusNumber, state.focusTotal, state.phase.minutes)
        }
        alert(title, text)
    }

    override fun alertFinished(session: TimerSession) {
        ensureChannels()
        alert(
            title = context.getString(R.string.timer_alert_finished_title),
            text = session.taskTitle?.let { context.getString(R.string.timer_alert_finished_task, it) }
                ?: context.getString(R.string.timer_alert_finished_text),
        )
    }

    override fun cancelAll() {
        manager.cancel(ID_RUNNING)
        manager.cancel(ID_ALERT)
    }

    override fun cancelRunning() {
        manager.cancel(ID_RUNNING)
    }

    private fun alert(title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openTimer())
            .setAutoCancel(true)
            .setTimeoutAfter(ALERT_TIMEOUT_MS)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(ID_ALERT, notification)
    }

    private fun phaseTitle(state: TimerState.Running): String = when {
        state.phase.kind == TimerPhaseKind.BREAK -> context.getString(R.string.timer_phase_break)
        state.focusTotal > 1 -> context.getString(R.string.timer_phase_focus_of, state.focusNumber, state.focusTotal)
        else -> context.getString(R.string.timer_phase_focus)
    }

    private fun openTimer(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        MainActivity.openTimerIntent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, context.getString(R.string.timer_channel_running), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.timer_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    companion object {
        const val CHANNEL_RUNNING = "focus_timer"
        const val CHANNEL_ALERTS = "focus_timer_alerts"
        const val ID_RUNNING = 20
        const val ID_ALERT = 21
        private const val REQUEST_OPEN = 3
        private const val ALERT_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
