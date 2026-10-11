package com.focusdivergent.app.domain.model

/**
 * Energía que requiere una tarea (RF-2.1).
 *
 * [value] es el entero que se persiste (1, 2, 3), tal como define el esquema del SRD.
 */
enum class EnergyLevel(val value: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    companion object {
        fun fromValue(value: Int): EnergyLevel = entries.first { it.value == value }
    }
}
