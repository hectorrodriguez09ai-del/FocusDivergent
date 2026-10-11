package com.focusdivergent.app.data.settings

import android.content.Context
import androidx.core.content.edit
import com.focusdivergent.app.domain.settings.OnboardingState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPreferencesOnboardingState @Inject constructor(
    @ApplicationContext context: Context,
) : OnboardingState {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override val isDone: Boolean
        get() = prefs.getBoolean(KEY_DONE, false)

    override fun markDone() {
        prefs.edit { putBoolean(KEY_DONE, true) }
    }

    private companion object {
        const val FILE = "onboarding"
        const val KEY_DONE = "done"
    }
}
