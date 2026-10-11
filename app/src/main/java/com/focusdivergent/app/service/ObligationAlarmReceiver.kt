package com.focusdivergent.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.focusdivergent.app.domain.usecase.ScheduleNextObligationAlarmUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Suena 10 minutos antes de una Obligación. Asegura que el servicio de bloqueo está en marcha (el sistema pudo
 * matarlo) para que muestre el aviso, y programa la alarma de la siguiente Obligación.
 */
@AndroidEntryPoint
class ObligationAlarmReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var scheduleNextAlarm: ScheduleNextObligationAlarmUseCase
    @Inject lateinit var trigger: BlockCheckTrigger

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // Si el usuario pausó la protección, el aviso tampoco debe despertar el servicio.
        if (ProtectionReviver.revive(context)) trigger.request()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduleNextAlarm()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo programar la siguiente alarma", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ObligationAlarm"
    }
}
