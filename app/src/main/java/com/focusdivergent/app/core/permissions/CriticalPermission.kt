package com.focusdivergent.app.core.permissions

/**
 * Permisos especiales sin los que el motor de bloqueo no puede funcionar.
 *
 * Ninguno se concede con el diálogo estándar de Android: el usuario debe activarlos a mano en
 * una pantalla de Ajustes.
 */
enum class CriticalPermission {
    /** `SYSTEM_ALERT_WINDOW`: dibujar la pantalla de bloqueo sobre otras apps. */
    OVERLAY,

    /** `PACKAGE_USAGE_STATS`: leer el tiempo de uso de cada app. */
    USAGE_STATS,

    /** Servicio de accesibilidad: detectar al instante qué app está en primer plano. */
    ACCESSIBILITY,
}
