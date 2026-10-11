package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.closure.ClosureAlarmScheduler
import com.focusdivergent.app.domain.closure.ClosureNotifier
import com.focusdivergent.app.domain.closure.ClosureRecord
import com.focusdivergent.app.domain.closure.DayClosureConfig
import com.focusdivergent.app.domain.closure.DayClosureSettings
import com.focusdivergent.app.domain.closure.DayClosureState
import com.focusdivergent.app.domain.closure.DayClosureStore
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** La configuración del Cierre en memoria. Por defecto, la de serie: activo de 21:30 a 06:00. */
class FakeDayClosureSettings(initial: DayClosureState = DayClosureState()) : DayClosureSettings {
    constructor(config: DayClosureConfig) : this(DayClosureState(effective = config))

    private val mutable = MutableStateFlow(initial)
    override val state: StateFlow<DayClosureState> = mutable

    override fun update(state: DayClosureState) {
        mutable.value = state
    }
}

class FakeDayClosureStore(initial: ClosureRecord? = null) : DayClosureStore {
    private val mutable = MutableStateFlow(initial)
    override val record: StateFlow<ClosureRecord?> = mutable

    override fun save(record: ClosureRecord) {
        mutable.value = record
    }
}

class FakeClosureAlarms : ClosureAlarmScheduler {
    var scheduledAt: Instant? = null
        private set
    var cancels = 0
        private set

    override fun schedule(at: Instant) {
        scheduledAt = at
    }

    override fun cancel() {
        scheduledAt = null
        cancels++
    }
}

class FakeClosureNotifier : ClosureNotifier {
    /** Cada aviso: el día y las ideas que esperaban. */
    val shown = mutableListOf<Pair<LocalDate, Int>>()

    override fun show(day: LocalDate, ideasWaiting: Int) {
        shown += day to ideasWaiting
    }
}
