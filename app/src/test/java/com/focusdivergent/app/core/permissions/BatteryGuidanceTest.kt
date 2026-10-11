package com.focusdivergent.app.core.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryGuidanceTest {

    @Test
    fun `Samsung recibe la guia propia sin importar mayusculas ni espacios`() {
        assertEquals(BatteryGuidance.SAMSUNG, BatteryGuidance.forManufacturer("samsung"))
        assertEquals(BatteryGuidance.SAMSUNG, BatteryGuidance.forManufacturer("SAMSUNG"))
        assertEquals(BatteryGuidance.SAMSUNG, BatteryGuidance.forManufacturer(" Samsung "))
    }

    @Test
    fun `el resto de fabricantes recibe la guia estandar`() {
        listOf("Google", "Xiaomi", "HUAWEI", "OnePlus", "").forEach {
            assertEquals(it, BatteryGuidance.GENERIC, BatteryGuidance.forManufacturer(it))
        }
    }
}
