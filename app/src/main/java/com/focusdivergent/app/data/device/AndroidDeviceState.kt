package com.focusdivergent.app.data.device

import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.telecom.TelecomManager
import com.focusdivergent.app.domain.device.DeviceState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidDeviceState @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceState {

    private val power = context.getSystemService(PowerManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val telecom: TelecomManager? = context.getSystemService(TelecomManager::class.java)

    override fun isInteractive(): Boolean = power.isInteractive

    // Cualquier modo de audio distinto del normal implica una llamada: sonando, en curso, en filtrado o
    // de voz por IP. No requiere permisos, a diferencia de TelecomManager.isInCall().
    override fun isInCall(): Boolean = audio.mode != AudioManager.MODE_NORMAL

    // El marcador por defecto del usuario se consulta; el resto son los habituales de cada fabricante,
    // porque la pantalla de «Llamada de emergencia» no siempre es la app marcadora por defecto.
    override fun safePackages(): Set<String> = buildSet {
        addAll(KNOWN_DIALER_PACKAGES)
        telecom?.defaultDialerPackage?.let(::add)
    }

    private companion object {
        val KNOWN_DIALER_PACKAGES = setOf(
            "com.android.dialer",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.app.telephonyui",
        )
    }
}
