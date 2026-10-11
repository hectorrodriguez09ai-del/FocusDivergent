package com.focusdivergent.app.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SharedPreferencesCaptureTileOfferTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getSharedPreferences("capture_tile_offer", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `al principio la oferta esta pendiente`() {
        assertTrue(SharedPreferencesCaptureTileOffer(context).pending.value)
    }

    @Test
    fun `una vez respondida no vuelve, ni siquiera tras reiniciar la app`() {
        val offer = SharedPreferencesCaptureTileOffer(context)

        offer.markHandled()

        assertFalse(offer.pending.value)
        assertFalse(SharedPreferencesCaptureTileOffer(context).pending.value)
    }
}
