package com.focusdivergent.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.focusdivergent.app.domain.usecase.RunDayClosureUseCase
import com.focusdivergent.app.domain.usecase.ScheduleDayClosureUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Suena a la hora de cierre: cierra el día, avisa de que el resumen está listo y deja puesta la alarma de mañana. */
@AndroidEntryPoint
class ClosureAlarmReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var runClosure: RunDayClosureUseCase
    @Inject lateinit var schedule: ScheduleDayClosureUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runClosure()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cerrar la jornada", e)
            } finally {
                try {
                    schedule()
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo programar el próximo cierre", e)
                }
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ClosureAlarm"
    }
}

/** Tras reiniciar el móvil o actualizar la app, Android borra las alarmas: se vuelve a poner la del cierre. */
@AndroidEntryPoint
class ClosureRestartReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var schedule: ScheduleDayClosureUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        try {
            schedule()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo programar el cierre tras reiniciar", e)
        }
    }

    private companion object {
        const val TAG = "ClosureRestart"
    }
}
