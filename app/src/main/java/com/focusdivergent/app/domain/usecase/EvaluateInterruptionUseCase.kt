package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.device.DeviceState
import com.focusdivergent.app.domain.model.Interruption
import com.focusdivergent.app.domain.repository.UsageRepository
import javax.inject.Inject

/**
 * Punto único de decisión: qué interrupción, si alguna, hay que mostrar ahora mismo.
 *
 * Primero aplica las exenciones de seguridad y después, por orden de prioridad, cada tipo de interrupción:
 * el aviso de una Obligación (lo que el usuario no puede perderse), la Lanzadera de 2 minutos (que ha pedido el
 * propio usuario y cubre todo el móvil), el bloqueo de una app y, por último, el Reality Check.
 */
class EvaluateInterruptionUseCase @Inject constructor(
    private val usage: UsageRepository,
    private val device: DeviceState,
    private val findObligationReminder: FindObligationReminderUseCase,
    private val findFocusLaunch: FindFocusLaunchUseCase,
    private val findBlockedApp: FindBlockedAppUseCase,
    private val findRealityCheck: FindRealityCheckUseCase,
) {

    suspend operator fun invoke(): Interruption? {
        // Pantalla apagada o llamada en curso: no se interrumpe nunca y ni siquiera se consulta el uso.
        if (!device.isInteractive() || device.isInCall()) return null

        val snapshot = usage.readToday()
        val foreground = snapshot.foregroundPackage
        if (foreground != null && foreground in device.safePackages()) return null

        return findObligationReminder() ?: findFocusLaunch() ?: findBlockedApp(snapshot) ?: findRealityCheck(snapshot)
    }
}
