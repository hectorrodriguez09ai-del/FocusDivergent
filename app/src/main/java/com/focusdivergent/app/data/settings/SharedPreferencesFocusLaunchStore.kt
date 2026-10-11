package com.focusdivergent.app.data.settings

import android.content.Context
import androidx.core.content.edit
import com.focusdivergent.app.domain.launch.FocusLaunchStore
import com.focusdivergent.app.domain.model.FocusLaunchSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Guarda la sesión de la Lanzadera en preferencias locales. Una sola a la vez. */
@Singleton
class SharedPreferencesFocusLaunchStore @Inject constructor(
    @ApplicationContext context: Context,
) : FocusLaunchStore {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun current(): FocusLaunchSession? {
        if (!prefs.contains(KEY_STARTED) || !prefs.contains(KEY_ENDS)) return null
        return FocusLaunchSession(
            taskId = if (prefs.contains(KEY_TASK_ID)) prefs.getLong(KEY_TASK_ID, 0) else null,
            taskTitle = prefs.getString(KEY_TASK_TITLE, null),
            startedAt = Instant.ofEpochMilli(prefs.getLong(KEY_STARTED, 0)),
            endsAt = Instant.ofEpochMilli(prefs.getLong(KEY_ENDS, 0)),
        )
    }

    override fun save(session: FocusLaunchSession) {
        prefs.edit {
            clear()
            session.taskId?.let { putLong(KEY_TASK_ID, it) }
            session.taskTitle?.let { putString(KEY_TASK_TITLE, it) }
            putLong(KEY_STARTED, session.startedAt.toEpochMilli())
            putLong(KEY_ENDS, session.endsAt.toEpochMilli())
        }
    }

    override fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val FILE = "focus_launch"
        const val KEY_TASK_ID = "task_id"
        const val KEY_TASK_TITLE = "task_title"
        const val KEY_STARTED = "started_at"
        const val KEY_ENDS = "ends_at"
    }
}
