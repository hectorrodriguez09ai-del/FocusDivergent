package com.focusdivergent.app.data.settings

import android.content.Context
import androidx.core.content.edit
import com.focusdivergent.app.domain.timer.TimerLimits
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerSession
import com.focusdivergent.app.domain.timer.TimerSettings
import com.focusdivergent.app.domain.timer.TimerStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Guarda la sesión del temporizador en preferencias locales. Una sola a la vez. */
@Singleton
class SharedPreferencesTimerStore @Inject constructor(
    @ApplicationContext context: Context,
) : TimerStore {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override val session: StateFlow<TimerSession?> = state.asStateFlow()

    override fun save(session: TimerSession) {
        prefs.edit {
            clear()
            putLong(KEY_STARTED, session.startedAt.toEpochMilli())
            session.taskId?.let { putLong(KEY_TASK_ID, it) }
            session.taskTitle?.let { putString(KEY_TASK_TITLE, it) }
            when (val mode = session.mode) {
                is TimerMode.Single -> {
                    putString(KEY_KIND, KIND_SINGLE)
                    putInt(KEY_MINUTES, mode.minutes)
                }
                is TimerMode.Pomodoro -> {
                    putString(KEY_KIND, KIND_POMODORO)
                    putInt(KEY_FOCUS, mode.focusMinutes)
                    putInt(KEY_BREAK, mode.breakMinutes)
                    putInt(KEY_CYCLES, mode.cycles)
                }
            }
        }
        state.value = session
    }

    override fun clear() {
        prefs.edit { clear() }
        state.value = null
    }

    private fun read(): TimerSession? {
        if (!prefs.contains(KEY_STARTED)) return null
        val mode = when (prefs.getString(KEY_KIND, null)) {
            KIND_SINGLE -> TimerMode.Single(prefs.getInt(KEY_MINUTES, TimerLimits.DEFAULT_SINGLE.minutes))
            KIND_POMODORO -> TimerMode.Pomodoro(
                focusMinutes = prefs.getInt(KEY_FOCUS, TimerLimits.DEFAULT_POMODORO.focusMinutes),
                breakMinutes = prefs.getInt(KEY_BREAK, TimerLimits.DEFAULT_POMODORO.breakMinutes),
                cycles = prefs.getInt(KEY_CYCLES, TimerLimits.DEFAULT_POMODORO.cycles),
            )
            else -> return null
        }
        return TimerSession(
            mode = TimerLimits.normalize(mode),
            taskId = if (prefs.contains(KEY_TASK_ID)) prefs.getLong(KEY_TASK_ID, 0) else null,
            taskTitle = prefs.getString(KEY_TASK_TITLE, null),
            startedAt = Instant.ofEpochMilli(prefs.getLong(KEY_STARTED, 0)),
        )
    }

    private companion object {
        const val FILE = "focus_timer"
        const val KEY_KIND = "kind"
        const val KIND_SINGLE = "single"
        const val KIND_POMODORO = "pomodoro"
        const val KEY_MINUTES = "minutes"
        const val KEY_FOCUS = "focus_minutes"
        const val KEY_BREAK = "break_minutes"
        const val KEY_CYCLES = "cycles"
        const val KEY_TASK_ID = "task_id"
        const val KEY_TASK_TITLE = "task_title"
        const val KEY_STARTED = "started_at"
    }
}

/** Recuerda la última configuración de cada modo del temporizador. */
@Singleton
class SharedPreferencesTimerSettings @Inject constructor(
    @ApplicationContext context: Context,
) : TimerSettings {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun single(): TimerMode.Single = TimerLimits.normalize(
        TimerMode.Single(prefs.getInt(KEY_MINUTES, TimerLimits.DEFAULT_SINGLE.minutes)),
    ) as TimerMode.Single

    override fun pomodoro(): TimerMode.Pomodoro = TimerLimits.normalize(
        TimerMode.Pomodoro(
            focusMinutes = prefs.getInt(KEY_FOCUS, TimerLimits.DEFAULT_POMODORO.focusMinutes),
            breakMinutes = prefs.getInt(KEY_BREAK, TimerLimits.DEFAULT_POMODORO.breakMinutes),
            cycles = prefs.getInt(KEY_CYCLES, TimerLimits.DEFAULT_POMODORO.cycles),
        ),
    ) as TimerMode.Pomodoro

    override fun save(mode: TimerMode) {
        prefs.edit {
            when (mode) {
                is TimerMode.Single -> putInt(KEY_MINUTES, mode.minutes)
                is TimerMode.Pomodoro -> {
                    putInt(KEY_FOCUS, mode.focusMinutes)
                    putInt(KEY_BREAK, mode.breakMinutes)
                    putInt(KEY_CYCLES, mode.cycles)
                }
            }
        }
    }

    private companion object {
        const val FILE = "focus_timer_settings"
        const val KEY_MINUTES = "single_minutes"
        const val KEY_FOCUS = "focus_minutes"
        const val KEY_BREAK = "break_minutes"
        const val KEY_CYCLES = "cycles"
    }
}
