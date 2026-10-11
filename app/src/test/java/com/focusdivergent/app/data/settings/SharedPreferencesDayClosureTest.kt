package com.focusdivergent.app.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusdivergent.app.domain.closure.ClosureRecord
import com.focusdivergent.app.domain.closure.DayClosureConfig
import com.focusdivergent.app.domain.closure.DayClosureState
import com.focusdivergent.app.domain.closure.PendingClosureConfig
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SharedPreferencesDayClosureTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getSharedPreferences("day_closure", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("day_closure_record", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // ---- Configuración

    @Test
    fun `de serie el cierre esta activo, de 21 30 a 06 00, y sin nada pendiente`() {
        val state = SharedPreferencesDayClosureSettings(context).state.value

        assertEquals(DayClosureState(DayClosureConfig(true, LocalTime.of(21, 30), LocalTime.of(6, 0)), null), state)
    }

    @Test
    fun `la configuracion y el cambio pendiente sobreviven a reabrir la app`() {
        val state = DayClosureState(
            effective = DayClosureConfig(enabled = false, closeAt = LocalTime.of(22, 30), liftUntil = LocalTime.of(5, 0)),
            pending = PendingClosureConfig(DayClosureConfig(enabled = true, closeAt = LocalTime.of(20, 0), liftUntil = LocalTime.of(7, 0)), Instant.parse("2026-10-11T00:00:00Z")),
        )
        SharedPreferencesDayClosureSettings(context).update(state)

        assertEquals(state, SharedPreferencesDayClosureSettings(context).state.value)
    }

    @Test
    fun `quitar el cambio pendiente tambien se guarda`() {
        val settings = SharedPreferencesDayClosureSettings(context)
        settings.update(DayClosureState(pending = PendingClosureConfig(DayClosureConfig(enabled = false), Instant.parse("2026-10-11T00:00:00Z"))))
        settings.update(DayClosureState())

        assertNull(SharedPreferencesDayClosureSettings(context).state.value.pending)
    }

    @Test
    fun `una hora guardada fuera de rango vuelve a la de serie en vez de romper`() {
        context.getSharedPreferences("day_closure", Context.MODE_PRIVATE).edit()
            .putInt("effective_close_minute", 99_999)
            .putInt("effective_lift_minute", -5)
            .commit()

        assertEquals(DayClosureConfig(), SharedPreferencesDayClosureSettings(context).state.value.effective)
    }

    // ---- Último cierre

    @Test
    fun `sin ningun cierre no hay registro`() {
        assertNull(SharedPreferencesDayClosureStore(context).record.value)
    }

    @Test
    fun `el ultimo cierre se guarda con lo trasladado y lo respetado`() {
        val record = ClosureRecord(LocalDate.of(2026, 10, 10), moved = 3, respectedLimits = 2)
        SharedPreferencesDayClosureStore(context).save(record)

        assertEquals(record, SharedPreferencesDayClosureStore(context).record.value)
    }

    @Test
    fun `un cierre nuevo sustituye al anterior`() {
        val store = SharedPreferencesDayClosureStore(context)
        store.save(ClosureRecord(LocalDate.of(2026, 10, 9), 5, 5))
        store.save(ClosureRecord(LocalDate.of(2026, 10, 10), 1, 0))

        assertEquals(ClosureRecord(LocalDate.of(2026, 10, 10), 1, 0), SharedPreferencesDayClosureStore(context).record.value)
    }
}
