package com.focusdivergent.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.focusdivergent.app.domain.timer.TimerAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/**
 * Alarma del fin de cada fase del temporizador sobre `AlarmManager`. Misma política que la de Obligaciones: exacta
 * si el usuario concedió el permiso; si no, aproximada, que Android puede retrasar unos minutos. La pantalla no
 * depende de ella (calcula lo que queda con la hora); solo el aviso con la app cerrada.
 */
class AndroidTimerAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TimerAlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant) {
        val operation = pendingIntent()
        val atMillis = at.toEpochMilli()
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
                return
            } catch (e: SecurityException) {
                // El usuario pudo retirar el permiso justo entre la comprobación y la llamada.
                Log.w(TAG, "Sin permiso de alarmas exactas; se usa una aproximada", e)
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, TimerAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "TimerAlarm"

        // Distinto del de las Obligaciones (1): son dos alarmas independientes.
        const val REQUEST_CODE = 2
    }
}
