package com.focusdivergent.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.focusdivergent.app.domain.closure.ClosureRecord
import com.focusdivergent.app.domain.closure.DayClosureConfig
import com.focusdivergent.app.domain.closure.DayClosureSettings
import com.focusdivergent.app.domain.closure.DayClosureState
import com.focusdivergent.app.domain.closure.DayClosureStore
import com.focusdivergent.app.domain.closure.PendingClosureConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Guarda la configuración del Cierre de Jornada, y el cambio que espera a medianoche, en el móvil. */
@Singleton
class SharedPreferencesDayClosureSettings @Inject constructor(
    @ApplicationContext context: Context,
) : DayClosureSettings {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())

    override val state: StateFlow<DayClosureState> = mutable.asStateFlow()

    override fun update(state: DayClosureState) {
        prefs.edit {
            write(PREFIX_EFFECTIVE, state.effective)
            val pending = state.pending
            if (pending == null) {
                remove(KEY_PENDING_AT)
            } else {
                write(PREFIX_PENDING, pending.config)
                putLong(KEY_PENDING_AT, pending.appliesAt.toEpochMilli())
            }
        }
        mutable.value = state
    }

    private fun SharedPreferences.Editor.write(prefix: String, config: DayClosureConfig) {
        putBoolean(prefix + KEY_ENABLED, config.enabled)
        putInt(prefix + KEY_CLOSE, config.closeAt.toSecondOfDay() / 60)
        putInt(prefix + KEY_LIFT, config.liftUntil.toSecondOfDay() / 60)
    }

    private fun readConfig(prefix: String): DayClosureConfig {
        val defaults = DayClosureConfig()
        return DayClosureConfig(
            enabled = prefs.getBoolean(prefix + KEY_ENABLED, defaults.enabled),
            closeAt = minuteOfDay(prefs.getInt(prefix + KEY_CLOSE, -1)) ?: defaults.closeAt,
            liftUntil = minuteOfDay(prefs.getInt(prefix + KEY_LIFT, -1)) ?: defaults.liftUntil,
        )
    }

    private fun read(): DayClosureState = DayClosureState(
        effective = readConfig(PREFIX_EFFECTIVE),
        pending = if (prefs.contains(KEY_PENDING_AT)) {
            PendingClosureConfig(readConfig(PREFIX_PENDING), Instant.ofEpochMilli(prefs.getLong(KEY_PENDING_AT, 0L)))
        } else {
            null
        },
    )

    private fun minuteOfDay(minutes: Int): LocalTime? = if (minutes in 0 until MINUTES_PER_DAY) LocalTime.of(minutes / 60, minutes % 60) else null

    private companion object {
        const val FILE = "day_closure"
        const val PREFIX_EFFECTIVE = "effective_"
        const val PREFIX_PENDING = "pending_"
        const val KEY_ENABLED = "enabled"
        const val KEY_CLOSE = "close_minute"
        const val KEY_LIFT = "lift_minute"
        const val KEY_PENDING_AT = "pending_applies_at"
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** Recuerda el último día cerrado y lo que pasó al cerrarlo. */
@Singleton
class SharedPreferencesDayClosureStore @Inject constructor(
    @ApplicationContext context: Context,
) : DayClosureStore {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())

    override val record: StateFlow<ClosureRecord?> = mutable.asStateFlow()

    override fun save(record: ClosureRecord) {
        prefs.edit {
            putLong(KEY_DAY, record.day.toEpochDay())
            putInt(KEY_MOVED, record.moved)
            putInt(KEY_RESPECTED, record.respectedLimits)
        }
        mutable.value = record
    }

    private fun read(): ClosureRecord? {
        if (!prefs.contains(KEY_DAY)) return null
        return ClosureRecord(
            day = LocalDate.ofEpochDay(prefs.getLong(KEY_DAY, 0L)),
            moved = prefs.getInt(KEY_MOVED, 0),
            respectedLimits = prefs.getInt(KEY_RESPECTED, 0),
        )
    }

    private companion object {
        const val FILE = "day_closure_record"
        const val KEY_DAY = "day"
        const val KEY_MOVED = "moved"
        const val KEY_RESPECTED = "respected"
    }
}
