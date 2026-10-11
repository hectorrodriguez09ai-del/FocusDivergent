package com.focusdivergent.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.usecase.FireRoutineRemindersUseCase
import com.focusdivergent.app.domain.usecase.RoutineReminderActionsUseCase
import com.focusdivergent.app.domain.usecase.ScheduleRoutineRemindersUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "RoutineReminder"

/** Suena la alarma general: avisa de las rutinas que tocan ahora y deja puesta la del siguiente aviso. */
@AndroidEntryPoint
class RoutineAlarmReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var fire: FireRoutineRemindersUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fire()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo avisar de las rutinas", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Llega el «Más tarde» de una rutina. */
@AndroidEntryPoint
class RoutineSnoozeReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var actions: RoutineReminderActionsUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val taskId = intent.getLongExtra(RoutineReminderIntents.EXTRA_TASK_ID, 0L)
        if (taskId == 0L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                actions.snoozeElapsed(taskId)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo repetir el aviso", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Los botones del aviso: «Hecha» y «Más tarde». */
@AndroidEntryPoint
class RoutineActionReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var actions: RoutineReminderActionsUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val taskId = intent.getLongExtra(RoutineReminderIntents.EXTRA_TASK_ID, 0L)
        if (taskId == 0L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    RoutineReminderIntents.ACTION_DONE -> actions.done(taskId)
                    RoutineReminderIntents.ACTION_LATER -> actions.later(taskId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo atender el botón del aviso", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Tras reiniciar o actualizar, Android borra las alarmas: se vuelve a poner la de los avisos (y se avisa si aún llega a tiempo). */
@AndroidEntryPoint
class RoutineRestartReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var fire: FireRoutineRemindersUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fire()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo programar los avisos tras reiniciar", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Mantiene la alarma de los avisos al día mientras la app está viva: cada vez que cambia alguna rutina. */
@Singleton
class RoutineReminderSync @Inject constructor(
    private val tasks: TaskRepository,
    private val schedule: ScheduleRoutineRemindersUseCase,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            tasks.observeByType(TaskType.ROUTINE).collectLatest {
                try {
                    schedule()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo reprogramar los avisos de rutinas", e)
                }
            }
        }
    }
}
