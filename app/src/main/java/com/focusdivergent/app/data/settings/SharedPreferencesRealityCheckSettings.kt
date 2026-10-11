package com.focusdivergent.app.data.settings

import android.content.Context
import androidx.core.content.edit
import com.focusdivergent.app.domain.policy.RealityCheckPolicy
import com.focusdivergent.app.domain.realitycheck.RealityCheckSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiempos del Reality Check guardados en local. Sin nada guardado valen los de [RealityCheckPolicy]; la
 * pantalla de ajustes de la Fase 3 los cambiará, y las pruebas por ADB los acortan para no esperar 20 minutos.
 */
@Singleton
class SharedPreferencesRealityCheckSettings @Inject constructor(
    @ApplicationContext context: Context,
) : RealityCheckSettings {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override var thresholdMinutes: Int
        get() = prefs.getInt(KEY_THRESHOLD, RealityCheckPolicy.DEFAULT_THRESHOLD_MINUTES)
        set(value) = prefs.edit { putInt(KEY_THRESHOLD, value.coerceAtLeast(1)) }

    override var silenceMinutes: Int
        get() = prefs.getInt(KEY_SILENCE, RealityCheckPolicy.DEFAULT_SILENCE_MINUTES)
        set(value) = prefs.edit { putInt(KEY_SILENCE, value.coerceAtLeast(1)) }

    /** Vuelve a los valores de serie. */
    fun reset() {
        prefs.edit { clear() }
    }

    private companion object {
        const val FILE = "reality_check"
        const val KEY_THRESHOLD = "threshold_minutes"
        const val KEY_SILENCE = "silence_minutes"
    }
}
