package com.focusdivergent.app.data.settings

import android.content.Context
import androidx.core.content.edit
import com.focusdivergent.app.domain.settings.CaptureTileOffer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SharedPreferencesCaptureTileOffer @Inject constructor(
    @ApplicationContext context: Context,
) : CaptureTileOffer {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(!prefs.getBoolean(KEY_HANDLED, false))

    override val pending: StateFlow<Boolean> = state.asStateFlow()

    override fun markHandled() {
        prefs.edit { putBoolean(KEY_HANDLED, true) }
        state.value = false
    }

    private companion object {
        const val FILE = "capture_tile_offer"
        const val KEY_HANDLED = "handled"
    }
}
