package com.focusdivergent.app.service

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerPlan
import com.focusdivergent.app.domain.timer.TimerSession
import com.focusdivergent.app.domain.timer.TimerState
import com.focusdivergent.app.domain.usecase.TEST_NOW
import java.time.Duration
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

/** El temporizador hacia el sistema: la alarma del fin de cada fase y sus notificaciones. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AndroidTimerSystemTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val scheduler = AndroidTimerAlarmScheduler(context)
    private val notifier = AndroidTimerNotifier(context)

    private val at = Instant.parse("2026-10-10T12:45:00Z")
    private val pomodoro = TimerMode.Pomodoro(focusMinutes = 45, breakMinutes = 20, cycles = 2)
    private val session = TimerSession(pomodoro, taskId = 1, taskTitle = "Informe", startedAt = TEST_NOW)

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun running(minutesIn: Long) =
        TimerPlan.stateAt(session, TEST_NOW.plus(Duration.ofMinutes(minutesIn))) as TimerState.Running

    private fun notification(id: Int): Notification? = shadowOf(notificationManager).getNotification(id)

    // ---- Alarma

    @Test
    fun `con permiso de alarmas exactas la alarma del temporizador es exacta y despierta el movil`() {
        scheduler.schedule(at)

        val alarm = shadowOf(alarmManager).nextScheduledAlarm!!
        assertEquals(at.toEpochMilli(), alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue(alarm.allowWhileIdle)
        assertEquals(0L, alarm.windowLengthMs)
    }

    @Test
    fun `sin permiso de alarmas exactas usa una aproximada en lugar de fallar`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.schedule(at)

        val alarm = shadowOf(alarmManager).nextScheduledAlarm!!
        assertEquals(at.toEpochMilli(), alarm.triggerAtMs)
        assertNotEquals(0L, alarm.windowLengthMs)
    }

    @Test
    fun `programar de nuevo sustituye a la anterior y cancelar la quita`() {
        scheduler.schedule(at)
        scheduler.schedule(at.plusSeconds(3600))
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)

        scheduler.cancel()

        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }

    @Test
    fun `la alarma del temporizador y la de las Obligaciones son independientes`() {
        AndroidObligationAlarmScheduler(context).schedule(at.plusSeconds(600))
        scheduler.schedule(at)
        assertEquals(2, shadowOf(alarmManager).scheduledAlarms.size)

        scheduler.cancel()

        val left = shadowOf(alarmManager).scheduledAlarms.single()
        assertEquals(at.plusSeconds(600).toEpochMilli(), left.triggerAtMs)
    }

    // ---- Notificación en curso

    @Test
    fun `la notificacion en curso lleva la cuenta atras del sistema hasta el fin de la fase`() {
        val state = running(minutesIn = 0)

        notifier.showRunning(session, state)

        val n = notification(AndroidTimerNotifier.ID_RUNNING)!!
        assertEquals(AndroidTimerNotifier.CHANNEL_RUNNING, n.channelId)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(state.phaseEndsAt.toEpochMilli(), n.`when`)
        assertEquals("Enfoque 1 de 2", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Informe", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNotNull(n.contentIntent)
    }

    @Test
    fun `en el descanso la notificacion lo dice y se actualiza en el mismo sitio`() {
        notifier.showRunning(session, running(0))
        notifier.showRunning(session, running(50))

        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
        val n = notification(AndroidTimerNotifier.ID_RUNNING)!!
        assertEquals("Descanso", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun `un temporizador suelto no numera los bloques ni lleva texto de tarea`() {
        val plain = TimerSession(TimerMode.Single(25), null, null, TEST_NOW)
        notifier.showRunning(plain, TimerPlan.stateAt(plain, TEST_NOW) as TimerState.Running)

        val n = notification(AndroidTimerNotifier.ID_RUNNING)!!
        assertEquals("Enfoque", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Tu temporizador está en marcha.", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    // ---- Avisos

    @Test
    fun `el aviso de descanso usa su propio canal, se cierra al tocarlo y desaparece solo`() {
        notifier.alertPhaseChange(session, running(50))

        val n = notification(AndroidTimerNotifier.ID_ALERT)!!
        assertEquals(AndroidTimerNotifier.CHANNEL_ALERTS, n.channelId)
        assertEquals("Toca descansar", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Descanso de 20 min.", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertTrue(n.timeoutAfter > 0)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT == 0)
    }

    @Test
    fun `el aviso de vuelta al enfoque dice que bloque toca`() {
        notifier.alertPhaseChange(session, running(65))

        val n = notification(AndroidTimerNotifier.ID_ALERT)!!
        assertEquals("Vuelve al enfoque", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Bloque 2 de 2, 45 min.", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `el aviso final pregunta por la tarea o felicita con calma`() {
        notifier.alertFinished(session)
        assertEquals("¿Terminaste «Informe»?", notification(AndroidTimerNotifier.ID_ALERT)!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString())

        notifier.alertFinished(TimerSession(TimerMode.Single(25), null, null, TEST_NOW))
        val n = notification(AndroidTimerNotifier.ID_ALERT)!!
        assertEquals("Sesión terminada", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Haber dedicado este tiempo ya cuenta.", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `cancelar quita la notificacion en curso y los avisos, o solo la primera`() {
        notifier.showRunning(session, running(0))
        notifier.alertFinished(session)

        notifier.cancelRunning()
        assertNull(notification(AndroidTimerNotifier.ID_RUNNING))
        assertNotNull(notification(AndroidTimerNotifier.ID_ALERT))

        notifier.showRunning(session, running(0))
        notifier.cancelAll()
        assertNull(notification(AndroidTimerNotifier.ID_RUNNING))
        assertNull(notification(AndroidTimerNotifier.ID_ALERT))
    }

    @Test
    fun `los canales existen y el de la cuenta atras es silencioso`() {
        notifier.showRunning(session, running(0))

        val running = notificationManager.getNotificationChannel(AndroidTimerNotifier.CHANNEL_RUNNING)
        val alerts = notificationManager.getNotificationChannel(AndroidTimerNotifier.CHANNEL_ALERTS)
        assertEquals(NotificationManager.IMPORTANCE_LOW, running.importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, alerts.importance)
    }
}
