package com.focusdivergent.app.service

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AndroidObligationAlarmSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val scheduler = AndroidObligationAlarmScheduler(context)
    private val at = Instant.parse("2026-10-10T12:50:00Z")

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `con permiso de alarmas exactas programa una alarma exacta que despierta el movil`() {
        scheduler.schedule(at)

        val alarm = shadowOf(alarmManager).nextScheduledAlarm!!
        assertNotNull(alarm)
        assertEquals(at.toEpochMilli(), alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue(alarm.allowWhileIdle)
        assertEquals("ventana 0 = alarma exacta", 0L, alarm.windowLengthMs)
    }

    @Test
    fun `sin permiso de alarmas exactas usa una aproximada en lugar de fallar`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.schedule(at)

        val alarm = shadowOf(alarmManager).nextScheduledAlarm!!
        assertEquals(at.toEpochMilli(), alarm.triggerAtMs)
        assertTrue(alarm.allowWhileIdle)
        assertNotEquals("una alarma aproximada no tiene ventana 0", 0L, alarm.windowLengthMs)
    }

    @Test
    fun `programar de nuevo sustituye a la alarma anterior`() {
        scheduler.schedule(at)
        scheduler.schedule(at.plusSeconds(3600))

        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(at.plusSeconds(3600).toEpochMilli(), scheduled.single().triggerAtMs)
    }

    @Test
    fun `cancelar quita la alarma pendiente`() {
        scheduler.schedule(at)

        scheduler.cancel()

        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }
}
