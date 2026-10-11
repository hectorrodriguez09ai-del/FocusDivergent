package com.focusdivergent.app.core.permissions

/**
 * Qué instrucciones de batería enseñar. Cada fabricante esconde su propia lista de «apps que nunca se suspenden»
 * y el sistema no ofrece una forma estándar de comprobarla ni de abrirla, así que se guía al usuario paso a paso.
 */
enum class BatteryGuidance {
    /** One UI de Samsung: además de la optimización estándar tiene su propia lista de apps que nunca se suspenden. */
    SAMSUNG,

    /** Cualquier otro: basta con la exclusión estándar de Android. */
    GENERIC,
    ;

    companion object {
        /** [manufacturer] es `Build.MANUFACTURER`. */
        fun forManufacturer(manufacturer: String): BatteryGuidance =
            if (manufacturer.trim().equals("samsung", ignoreCase = true)) SAMSUNG else GENERIC
    }
}
