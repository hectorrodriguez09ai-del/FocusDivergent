package com.focusdivergent.app.domain.device

/**
 * Estado del dispositivo que decide si es seguro interrumpir al usuario.
 *
 * Ninguna interrupción debe estorbar una llamada ni una emergencia, ni dibujarse con la pantalla apagada.
 */
interface DeviceState {

    /** La pantalla está encendida y el usuario puede estar usando el móvil. */
    fun isInteractive(): Boolean

    /** Hay una llamada en curso, sonando o una comunicación de voz (incluidas las de apps de mensajería). */
    fun isInCall(): Boolean

    /** Paquetes que nunca se cubren: el marcador del teléfono y las pantallas de llamada y emergencia. */
    fun safePackages(): Set<String>
}
