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
 * Devuelve la protección a su sitio tras reiniciar el móvil o actualizar la app (RNF-2), si el usuario la tenía
 * activada. También reprograma la alarma de Obligaciones, que Android borra al apagarse.
 *
 * Ambos eventos están en la lista de excepciones con las que Android deja arrancar un servicio en primer plano
 * desde segundo plano.
 */
@AndroidEntryPoint
class ProtectionRestartReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var scheduleNextAlarm: ScheduleNextObligationAlarmUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        ProtectionReviver.revive(context)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduleNextAlarm()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo programar la alarma de Obligaciones", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ProtectionRestart"
    }
}
