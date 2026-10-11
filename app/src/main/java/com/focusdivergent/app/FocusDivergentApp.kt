package com.focusdivergent.app

import android.app.Application
import com.focusdivergent.app.domain.usecase.ScheduleDayClosureUseCase
import com.focusdivergent.app.service.ObligationAlarmSync
import com.focusdivergent.app.service.RoutineReminderSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Punto de entrada de la app. `@HiltAndroidApp` genera el contenedor raíz de dependencias. */
@HiltAndroidApp
class FocusDivergentApp : Application() {

    @Inject lateinit var obligationAlarmSync: ObligationAlarmSync
    @Inject lateinit var routineReminderSync: RoutineReminderSync
    @Inject lateinit var scheduleDayClosure: ScheduleDayClosureUseCase

    // Vive lo que el proceso: las alarmas de Obligaciones se mantienen al día mientras la app esté en memoria.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        obligationAlarmSync.start(appScope)
        routineReminderSync.start(appScope)
        // La alarma de la hora de cierre se deja puesta cada vez que arranca el proceso (solo lee ajustes, no la base de datos).
        scheduleDayClosure()
    }
}
