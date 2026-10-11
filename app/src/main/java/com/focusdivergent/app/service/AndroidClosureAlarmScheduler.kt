package com.focusdivergent.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.focusdivergent.app.domain.closure.ClosureAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/**
 * Alarma de la hora de cierre sobre `AlarmManager`. Misma política que las demás: exacta si el usuario concedió el
 * permiso; si no, aproximada, que Android puede retrasar unos minutos. El cierre no depende de ella: el servicio de
 * bloqueo y la propia app lo comprueban también, y las apps de ocio se liberan por la hora, no por la alarma.
 */
class AndroidClosureAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ClosureAlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant) {
        val operation = pendingIntent()
        val atMillis = at.toEpochMilli()
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
                return
            } catch (e: SecurityException) {
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
        Intent(context, ClosureAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "ClosureAlarm"

        // Distinto del de las Obligaciones (1) y del temporizador (2): son alarmas independientes.
        const val REQUEST_CODE = 4
    }
}
