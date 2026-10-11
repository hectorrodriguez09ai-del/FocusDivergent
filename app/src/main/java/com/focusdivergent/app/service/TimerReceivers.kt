package com.focusdivergent.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.focusdivergent.app.domain.usecase.SyncTimerUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Suena al acabar una fase del temporizador: avisa del cambio (o del fin) y programa la alarma de la siguiente. */
@AndroidEntryPoint
class TimerAlarmReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var sync: SyncTimerUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        try {
            sync.onBoundaryReached()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo avisar del cambio de fase", e)
        }
    }

    private companion object {
        const val TAG = "TimerAlarm"
    }
}

/**
 * Tras reiniciar el móvil o actualizar la app, Android borra las alarmas: se vuelve a poner la del temporizador y su
 * notificación en curso, sin avisar de nada (lo que ya pasó, ya pasó).
 */
@AndroidEntryPoint
class TimerRestartReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var sync: SyncTimerUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        try {
            sync.restore()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo recuperar el temporizador", e)
        }
    }

    private companion object {
        const val TAG = "TimerRestart"
    }
}
