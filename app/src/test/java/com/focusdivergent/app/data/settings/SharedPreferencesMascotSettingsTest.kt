package com.focusdivergent.app.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.Mascot
import com.focusdivergent.app.domain.reward.MascotSelection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SharedPreferencesMascotSettingsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getSharedPreferences("mascot", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `de serie lleva el zorro, el fondo tranquilo y ningun objeto`() {
        assertEquals(MascotSelection(Mascot.FOX, Cosmetic.SCENERY_CALM, null), SharedPreferencesMascotSettings(context).selection.value)
    }

    @Test
    fun `lo elegido se guarda y sobrevive a reabrir la app`() {
        SharedPreferencesMascotSettings(context).update { MascotSelection(Mascot.ALIEN, Cosmetic.SCENERY_NEBULA, Cosmetic.PROP_STAR) }

        val reopened = SharedPreferencesMascotSettings(context)

        assertEquals(MascotSelection(Mascot.ALIEN, Cosmetic.SCENERY_NEBULA, Cosmetic.PROP_STAR), reopened.selection.value)
    }

    @Test
    fun `quitar el objeto tambien se guarda`() {
        val settings = SharedPreferencesMascotSettings(context)
        settings.update { it.copy(prop = Cosmetic.PROP_BOOKS) }
        settings.update { it.copy(prop = null) }

        assertEquals(null, SharedPreferencesMascotSettings(context).selection.value.prop)
    }

    @Test
    fun `un valor desconocido o de otro tipo vuelve al de serie en vez de romper`() {
        context.getSharedPreferences("mascot", Context.MODE_PRIVATE).edit()
            .putString("mascot", "DRAGON")
            .putString("scenery", "PROP_LAMP") // un objeto no puede ser un fondo
            .putString("prop", "SCENERY_NIGHT") // ni un fondo, un objeto
            .commit()

        assertEquals(MascotSelection(), SharedPreferencesMascotSettings(context).selection.value)
    }
}
