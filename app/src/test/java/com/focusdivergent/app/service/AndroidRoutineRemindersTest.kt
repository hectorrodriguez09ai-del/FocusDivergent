package com.focusdivergent.app.service

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.usecase.task
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AndroidRoutineRemindersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val alarms = AndroidRoutineReminderAlarms(context)
    private val notifier = AndroidRoutineReminderNotifier(context)
    private val at = Instant.parse("2026-10-10T08:00:00Z")

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    // ---- Alarmas

    @Test
    fun `la alarma general es exacta y despierta el movil`() {
        alarms.schedule(at)

        val alarm = shadowOf(alarmManager).nextScheduledAlarm!!
        assertEquals(at.toEpochMilli(), alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(0L, alarm.windowLengthMs)
    }

    @Test
    fun `sin permiso de alarmas exactas usa una aproximada en lugar de fallar`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        alarms.schedule(at)

        assertTrue(shadowOf(alarmManager).nextScheduledAlarm!!.windowLengthMs != 0L)
    }

    @Test
    fun `programar de nuevo sustituye a la alarma general y cancelar la quita`() {
        alarms.schedule(at)
        alarms.schedule(at.plusSeconds(60))
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)

        alarms.cancel()

        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }

    @Test
    fun `cada rutina tiene su propio mas tarde, independiente de los demas y de la alarma general`() {
        alarms.schedule(at)
        alarms.scheduleSnooze(1, at.plusSeconds(3600))
        alarms.scheduleSnooze(2, at.plusSeconds(7200))
        assertEquals(3, shadowOf(alarmManager).scheduledAlarms.size)

        alarms.scheduleSnooze(1, at.plusSeconds(5400))
        assertEquals(3, shadowOf(alarmManager).scheduledAlarms.size)

        alarms.cancelSnooze(1)
        assertEquals(2, shadowOf(alarmManager).scheduledAlarms.size)
        alarms.cancelSnooze(2)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    // ---- Aviso

    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val routine = task(7, title = "Meditar 5 minutos", type = TaskType.ROUTINE)

    @Test
    fun `el aviso lleva el nombre de la rutina, un texto amable y los botones Hecha y Mas tarde`() {
        notifier.show(routine, canSnooze = true)

        val notification = shadowOf(manager).allNotifications.single()
        assertEquals("Meditar 5 minutos", notification.extras.getString("android.title"))
        assertEquals("Es la hora de tu rutina. Si hoy no puede ser, no pasa nada.", notification.extras.getString("android.text"))
        assertEquals(listOf("Hecha", "Más tarde"), notification.actions.map { it.title.toString() })
    }

    @Test
    fun `el segundo aviso solo ofrece Hecha, para no aplazar sin fin`() {
        notifier.show(routine, canSnooze = false)

        assertEquals(listOf("Hecha"), shadowOf(manager).allNotifications.single().actions.map { it.title.toString() })
    }

    @Test
    fun `el aviso usa un solo canal silenciable y sin sonido de alarma`() {
        notifier.show(routine, canSnooze = true)

        val channel = manager.getNotificationChannel("routine_reminders")
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals("Avisos de rutinas", channel.name.toString())
    }

    @Test
    fun `cada rutina tiene su aviso y se quita por separado`() {
        notifier.show(routine, canSnooze = true)
        notifier.show(task(8, type = TaskType.ROUTINE), canSnooze = true)
        assertEquals(2, shadowOf(manager).allNotifications.size)

        notifier.dismiss(7)

        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    // ---- Registro

    @Test
    fun `el registro recuerda lo avisado hoy y olvida los dias anteriores`() {
        val log = SharedPreferencesRoutineReminderLog(context)
        val yesterday = LocalDate.of(2026, 10, 9)
        val today = LocalDate.of(2026, 10, 10)

        log.markNotified(1, yesterday)
        assertTrue(log.wasNotified(1, yesterday))

        log.markNotified(2, today)
        assertTrue(log.wasNotified(2, today))
        assertFalse(log.wasNotified(1, today))
        assertFalse("lo de ayer se ha olvidado", log.wasNotified(1, yesterday))
    }
}
