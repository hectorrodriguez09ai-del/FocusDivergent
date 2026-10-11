package com.focusdivergent.app.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProtectionReviverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `por defecto la proteccion no esta activada`() {
        assertFalse(ProtectionPreference.isEnabled(context))
    }

    @Test
    fun `activar y pausar se recuerdan`() {
        ProtectionPreference.setEnabled(context, true)
        assertTrue(ProtectionPreference.isEnabled(context))

        ProtectionPreference.setEnabled(context, false)
        assertFalse(ProtectionPreference.isEnabled(context))
    }

    @Test
    fun `con la proteccion activada arranca el servicio`() {
        ProtectionPreference.setEnabled(context, true)
        var starts = 0

        val revived = ProtectionReviver.revive(context) { starts++ }

        assertTrue(revived)
        assertEquals(1, starts)
    }

    @Test
    fun `si el usuario la pauso no arranca nada`() {
        ProtectionPreference.setEnabled(context, false)
        var starts = 0

        val revived = ProtectionReviver.revive(context) { starts++ }

        assertFalse(revived)
        assertEquals(0, starts)
    }

    @Test
    fun `si Android niega arrancar el servicio no se propaga el error`() {
        ProtectionPreference.setEnabled(context, true)

        val revived = ProtectionReviver.revive(context) { throw IllegalStateException("background start not allowed") }

        assertTrue(revived)
    }

    @Test
    fun `el arranque real marca la proteccion como activada y la parada como pausada`() {
        FocusBlockerService.start(context)
        assertTrue(ProtectionPreference.isEnabled(context))

        FocusBlockerService.stop(context)
        assertFalse(ProtectionPreference.isEnabled(context))
    }
}
