package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.reward.RewardLedger
import com.focusdivergent.app.domain.timer.TimerAlarmScheduler
import com.focusdivergent.app.domain.timer.TimerLimits
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerNotifier
import com.focusdivergent.app.domain.timer.TimerPlan
import com.focusdivergent.app.domain.timer.TimerSession
import com.focusdivergent.app.domain.timer.TimerSettings
import com.focusdivergent.app.domain.timer.TimerState
import com.focusdivergent.app.domain.timer.TimerStore
import java.time.Clock
import javax.inject.Inject

/**
 * Pone la notificación y la alarma de la sesión en curso en el estado que toca **ahora**. Es lo único que habla con
 * el sistema; el resto de casos de uso lo llaman.
 *
 * - [restore] deja todo en su sitio sin avisar de nada: al empezar, al reiniciar el móvil o al actualizar la app
 *   (Android borra las alarmas al apagarse).
 * - [onBoundaryReached] es lo que hace la alarma al acabar una fase: lo mismo, y además avisa del cambio o del fin.
 *   Si la alarma llega tarde y se saltó varias fases, avisa solo de la que toca ahora.
 */
class SyncTimerUseCase @Inject constructor(
    private val store: TimerStore,
    private val notifier: TimerNotifier,
    private val alarms: TimerAlarmScheduler,
    private val clock: Clock,
) {

    fun restore() = sync(alert = false)

    fun onBoundaryReached() = sync(alert = true)

    private fun sync(alert: Boolean) {
        val session = store.session.value
        if (session == null) {
            alarms.cancel()
            notifier.cancelAll()
            return
        }
        when (val state = TimerPlan.stateAt(session, clock.instant())) {
            is TimerState.Running -> {
                notifier.showRunning(session, state)
                alarms.schedule(state.phaseEndsAt)
                if (alert && state.phaseIndex > 0) notifier.alertPhaseChange(session, state)
            }
            is TimerState.Finished -> {
                alarms.cancel()
                notifier.cancelRunning()
                if (alert) notifier.alertFinished(session)
            }
        }
    }
}

/**
 * Empieza una sesión ahora. Recuerda la configuración elegida para la próxima vez y, si acompaña a una tarea, la
 * deja «en marcha». Empezar una sesión sustituye a cualquier otra que hubiera; de la anterior se anotan los bloques
 * que se llegaron a completar.
 */
class StartTimerUseCase @Inject constructor(
    private val store: TimerStore,
    private val settings: TimerSettings,
    private val startTask: StartTaskUseCase,
    private val sync: SyncTimerUseCase,
    private val clock: Clock,
    private val rewards: RewardLedger,
) {

    suspend operator fun invoke(mode: TimerMode, task: Task? = null): TimerSession {
        val normalized = TimerLimits.normalize(mode)
        settings.save(normalized)
        store.session.value?.let { rewards.focusSession(it) }
        if (task != null) startTask(task)
        val session = TimerSession(
            mode = normalized,
            taskId = task?.id,
            taskTitle = task?.title,
            startedAt = clock.instant(),
        )
        store.save(session)
        sync.restore()
        return session
    }
}

/**
 * Para la sesión y retira sus avisos. No toca la tarea. Los bloques de enfoque que ya se habían completado sí
 * cuentan; el que estaba a medias, ni suma ni resta.
 */
class StopTimerUseCase @Inject constructor(
    private val store: TimerStore,
    private val sync: SyncTimerUseCase,
    private val rewards: RewardLedger,
) {

    suspend operator fun invoke() {
        store.session.value?.let { rewards.focusSession(it) }
        store.clear()
        sync.restore()
    }
}

/**
 * Cierra una sesión terminada y anota sus bloques de enfoque. Si acompañaba a una tarea y el usuario dice que la
 * terminó, la marca como hecha; si dice que todavía no, no deja ninguna huella (sin reproche, RNF-3).
 */
class FinishTimerUseCase @Inject constructor(
    private val store: TimerStore,
    private val tasks: TaskRepository,
    private val setTaskDone: SetTaskDoneUseCase,
    private val sync: SyncTimerUseCase,
    private val rewards: RewardLedger,
) {

    suspend operator fun invoke(taskDone: Boolean) {
        val session = store.session.value ?: return
        rewards.focusSession(session)
        if (taskDone && session.taskId != null) {
            tasks.getById(session.taskId)?.let { setTaskDone(it, done = true) }
        }
        store.clear()
        sync.restore()
    }
}
