package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.timer.TimerAlarmScheduler
import com.focusdivergent.app.domain.timer.TimerLimits
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerNotifier
import com.focusdivergent.app.domain.timer.TimerSession
import com.focusdivergent.app.domain.timer.TimerSettings
import com.focusdivergent.app.domain.timer.TimerState
import com.focusdivergent.app.domain.timer.TimerStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Reloj que se mueve a mano, para los tests donde el tiempo pasa. */
class MutableClock(var now: Instant = TEST_NOW) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}

class FakeTimerStore(initial: TimerSession? = null) : TimerStore {
    private val state = MutableStateFlow(initial)
    override val session: StateFlow<TimerSession?> = state

    override fun save(session: TimerSession) {
        state.value = session
    }

    override fun clear() {
        state.value = null
    }
}

class FakeTimerSettings(
    var single: TimerMode.Single = TimerLimits.DEFAULT_SINGLE,
    var pomodoro: TimerMode.Pomodoro = TimerLimits.DEFAULT_POMODORO,
) : TimerSettings {
    override fun single() = single
    override fun pomodoro() = pomodoro

    override fun save(mode: TimerMode) {
        when (mode) {
            is TimerMode.Single -> single = mode
            is TimerMode.Pomodoro -> pomodoro = mode
        }
    }
}

class FakeTimerAlarms : TimerAlarmScheduler {
    /** La alarma pendiente ahora mismo, o nulo si no hay ninguna. */
    var scheduled: Instant? = null

    override fun schedule(at: Instant) {
        scheduled = at
    }

    override fun cancel() {
        scheduled = null
    }
}

/** Anota lo que se enseñaría en el sistema, en orden. */
class FakeTimerNotifier : TimerNotifier {
    val events = mutableListOf<String>()

    override fun showRunning(session: TimerSession, state: TimerState.Running) {
        events += "running:${state.phaseIndex}"
    }

    override fun alertPhaseChange(session: TimerSession, state: TimerState.Running) {
        events += "phase:${state.phaseIndex}"
    }

    override fun alertFinished(session: TimerSession) {
        events += "finished"
    }

    override fun cancelAll() {
        events += "cancelAll"
    }

    override fun cancelRunning() {
        events += "cancelRunning"
    }
}
