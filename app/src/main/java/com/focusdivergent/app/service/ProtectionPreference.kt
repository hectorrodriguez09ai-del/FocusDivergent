package com.focusdivergent.app.service

import android.content.Context
import androidx.core.content.edit

/**
 * Recuerda si el usuario tiene la protección activada, para poder reanudarla sin que abra la app (alarmas de
 * Obligaciones y, más adelante, reinicio del móvil).
 *
 * Solo la cambian [FocusBlockerService.start] y [FocusBlockerService.stop], o sea, el usuario. Que el sistema
 * mate el servicio no la toca: la protección sigue "activada".
 */
internal object ProtectionPreference {

    private const val FILE = "protection"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, enabled) }
    }
}
