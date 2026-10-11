package com.focusdivergent.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.focusdivergent.app.MainActivity
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.reminder.RoutineReminderAlarms
import com.focusdivergent.app.domain.reminder.RoutineReminderLog
import com.focusdivergent.app.domain.reminder.RoutineReminderNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Acciones de los botones del aviso y extra con la rutina a la que se refieren. */
internal object RoutineReminderIntents {
    const val ACTION_DONE = "com.focusdivergent.app.routine.DONE"
    const val ACTION_LATER = "com.focusdivergent.app.routine.LATER"
    const val EXTRA_TASK_ID = "task_id"

    /** Una dirección distinta por rutina y acción: así Android no mezcla los PendingIntent de unas con los de otras. */
    fun uri(kind: String, taskId: Long): Uri = Uri.parse("focusdivergent://routine/$kind/$taskId")
}

/**
 * Alarmas de los avisos de rutinas. Misma política que las demás: exacta si el usuario concedió el permiso; si no,
 * aproximada, que puede retrasarse unos minutos (el aviso admite hasta media hora de retraso).
 */
class AndroidRoutineReminderAlarms @Inject constructor(
    @ApplicationContext private val context: Context,
) : RoutineReminderAlarms {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant) = set(at, general())

    override fun cancel() = alarmManager.cancel(general())

    override fun scheduleSnooze(taskId: Long, at: Instant) = set(at, snooze(taskId))

    override fun cancelSnooze(taskId: Long) = alarmManager.cancel(snooze(taskId))

    private fun set(at: Instant, operation: PendingIntent) {
        val atMillis = at.toEpochMilli()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Sin permiso de alarmas exactas; se usa una aproximada", e)
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
    }

    private fun general(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_GENERAL,
        Intent(context, RoutineAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun snooze(taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_SNOOZE,
        Intent(context, RoutineSnoozeReceiver::class.java)
            .setData(RoutineReminderIntents.uri("snooze", taskId))
            .putExtra(RoutineReminderIntents.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "RoutineAlarm"

        // Distintos de los de Obligaciones (1), temporizador (2) y cierre (4).
        const val REQUEST_GENERAL = 6
        const val REQUEST_SNOOZE = 7
    }
}

/**
 * El aviso de una rutina: una notificación cálida, sin sonido de alarma, que no se repite ni escala. Se quita sola
 * pasadas unas horas para no quedarse colgada. Su canal es único y el usuario puede silenciarlo desde el sistema.
 */
class AndroidRoutineReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : RoutineReminderNotifier {

    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun show(task: Task, canSnooze: Boolean) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.routine_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(task.title)
            .setContentText(context.getString(R.string.routine_reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, context.getString(R.string.routine_reminder_done), action(RoutineReminderIntents.ACTION_DONE, "done", task.id))
        if (canSnooze) {
            builder.addAction(0, context.getString(R.string.routine_reminder_later), action(RoutineReminderIntents.ACTION_LATER, "later", task.id))
        }
        manager.notify(notificationId(task.id), builder.build())
    }

    override fun dismiss(taskId: Long) = manager.cancel(notificationId(taskId))

    private fun action(action: String, kind: String, taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ACTION,
        Intent(context, RoutineActionReceiver::class.java)
            .setAction(action)
            .setData(RoutineReminderIntents.uri(kind, taskId))
            .putExtra(RoutineReminderIntents.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // Un aviso por rutina, en una franja propia para no pisar los demás avisos de la app.
    private fun notificationId(taskId: Long): Int = ID_BASE + (taskId % ID_SPAN).toInt()

    private companion object {
        const val CHANNEL = "routine_reminders"
        const val ID_BASE = 100_000
        const val ID_SPAN = 50_000L
        const val REQUEST_OPEN = 8
        const val REQUEST_ACTION = 9
        const val TIMEOUT_MS = 3 * 60 * 60 * 1000L
    }
}

/** Qué rutinas se avisaron hoy, en preferencias. Al anotar un aviso se olvidan los de días anteriores. */
@Singleton
class SharedPreferencesRoutineReminderLog @Inject constructor(
    @ApplicationContext context: Context,
) : RoutineReminderLog {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun wasNotified(taskId: Long, day: LocalDate): Boolean =
        prefs.getStringSet(day.toEpochDay().toString(), emptySet()).orEmpty().contains(taskId.toString())

    override fun markNotified(taskId: Long, day: LocalDate) {
        val key = day.toEpochDay().toString()
        val updated = prefs.getStringSet(key, emptySet()).orEmpty() + taskId.toString()
        prefs.edit {
            prefs.all.keys.filter { it != key }.forEach { remove(it) }
            putStringSet(key, updated)
        }
    }

    private companion object {
        const val FILE = "routine_reminders"
    }
}
