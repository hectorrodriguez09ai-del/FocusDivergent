package com.focusdivergent.app.service

import android.content.Context
import android.util.Log

/**
 * Reanuda el servicio de bloqueo cuando algo ajeno al usuario lo ha parado: reinicio del móvil, actualización
 * de la app o una alarma que lo encuentra muerto. Respeta la decisión del usuario: si pausó la protección, no
 * hace nada.
 */
internal object ProtectionReviver {

    private const val TAG = "ProtectionReviver"

    /**
     * @param start cómo se arranca el servicio; se puede sustituir en pruebas.
     * @return `true` si la protección estaba activada y se intentó arrancar el servicio.
     */
    fun revive(context: Context, start: (Context) -> Unit = FocusBlockerService::start): Boolean {
        if (!ProtectionPreference.isEnabled(context)) return false
        try {
            start(context)
        } catch (e: Exception) {
            // Android puede negar arrancar un servicio en primer plano desde segundo plano (p. ej. una alarma
            // aproximada). No debe tumbar el receptor: el servicio, si seguía vivo, atiende igualmente.
            Log.w(TAG, "No se pudo arrancar el servicio de bloqueo", e)
        }
        return true
    }
}
