package com.focusdivergent.app.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusdivergent.app.domain.timer.TimerLimits
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerSession
import com.focusdivergent.app.domain.usecase.TEST_NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SharedPreferencesTimerStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getSharedPreferences("focus_timer", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("focus_timer_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `al principio no hay sesion`() {
        assertNull(SharedPreferencesTimerStore(context).session.value)
    }

    @Test
    fun `una sesion suelta se guarda y se recupera tras reiniciar la app`() {
        val session = TimerSession(TimerMode.Single(25), taskId = null, taskTitle = null, startedAt = TEST_NOW)

        SharedPreferencesTimerStore(context).save(session)

        assertEquals(session, SharedPreferencesTimerStore(context).session.value)
    }

    @Test
    fun `una sesion de Pomodoro con tarea conserva todo`() {
        val session = TimerSession(TimerMode.Pomodoro(50, 10, 4), taskId = 7, taskTitle = "Informe", startedAt = TEST_NOW)
        val store = SharedPreferencesTimerStore(context)

        store.save(session)

        assertEquals(session, store.session.value)
        assertEquals(session, SharedPreferencesTimerStore(context).session.value)
    }

    @Test
    fun `guardar otra sesion no arrastra datos de la anterior`() {
        val store = SharedPreferencesTimerStore(context)
        store.save(TimerSession(TimerMode.Pomodoro(50, 10, 4), 7, "Informe", TEST_NOW))

        val plain = TimerSession(TimerMode.Single(15), null, null, TEST_NOW.plusSeconds(60))
        store.save(plain)

        assertEquals(plain, SharedPreferencesTimerStore(context).session.value)
    }

    @Test
    fun `borrar la sesion la quita de memoria y de disco`() {
        val store = SharedPreferencesTimerStore(context)
        store.save(TimerSession(TimerMode.Single(25), null, null, TEST_NOW))

        store.clear()

        assertNull(store.session.value)
        assertNull(SharedPreferencesTimerStore(context).session.value)
    }

    @Test
    fun `lo guardado fuera de los limites se ajusta al leerlo`() {
        context.getSharedPreferences("focus_timer", Context.MODE_PRIVATE).edit()
            .putString("kind", "single").putInt("minutes", 99999).putLong("started_at", TEST_NOW.toEpochMilli()).commit()

        assertEquals(TimerMode.Single(TimerLimits.SINGLE_MAX), SharedPreferencesTimerStore(context).session.value?.mode)
    }

    // ---- Ajustes

    @Test
    fun `sin nada guardado se usan los valores por defecto`() {
        val settings = SharedPreferencesTimerSettings(context)

        assertEquals(TimerLimits.DEFAULT_SINGLE, settings.single())
        assertEquals(TimerMode.Pomodoro(45, 20, 3), settings.pomodoro())
    }

    @Test
    fun `cada modo recuerda lo suyo sin pisar al otro`() {
        val settings = SharedPreferencesTimerSettings(context)

        settings.save(TimerMode.Single(15))
        settings.save(TimerMode.Pomodoro(30, 5, 2))

        val reopened = SharedPreferencesTimerSettings(context)
        assertEquals(TimerMode.Single(15), reopened.single())
        assertEquals(TimerMode.Pomodoro(30, 5, 2), reopened.pomodoro())
    }
}
