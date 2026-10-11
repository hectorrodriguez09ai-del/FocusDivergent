package com.focusdivergent.app.service

import android.util.Log
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.usecase.ScheduleNextObligationAlarmUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Mantiene la alarma de Obligaciones al día mientras el proceso de la app está vivo: la reprograma al arrancar
 * y cada vez que cambia alguna Obligación (alta, cambio de hora, estado, borrado).
 */
@Singleton
class ObligationAlarmSync @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduleNextAlarm: ScheduleNextObligationAlarmUseCase,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            tasks.observeByType(TaskType.OBLIGATION).collectLatest {
                try {
                    scheduleNextAlarm()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo reprogramar la alarma de Obligaciones", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "ObligationAlarmSync"
    }
}
