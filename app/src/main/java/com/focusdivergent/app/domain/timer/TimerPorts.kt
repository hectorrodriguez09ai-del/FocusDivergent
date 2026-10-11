package com.focusdivergent.app.domain.timer

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * La sesión de temporizador en curso, si hay alguna. Se guarda en disco: lo que manda es la hora a la que empezó,
 * no un contador en memoria. Se puede observar para que «Hoy» y la pantalla del temporizador reaccionen.
 */
interface TimerStore {

    val session: StateFlow<TimerSession?>

    fun save(session: TimerSession)

    fun clear()
}

/** Lo último que el usuario eligió, para no tener que volver a configurarlo cada vez. */
interface TimerSettings {

    fun single(): TimerMode.Single

    fun pomodoro(): TimerMode.Pomodoro

    fun save(mode: TimerMode)
}

/**
 * Puerto hacia el sistema de alarmas para el temporizador: despierta a la app cuando acaba una fase, aunque no
 * esté abierta. Hay una sola alarma pendiente a la vez: programar sustituye a la anterior.
 */
interface TimerAlarmScheduler {

    fun schedule(at: Instant)

    fun cancel()
}

/**
 * Lo que el temporizador enseña fuera de la app, con la app cerrada.
 *
 * - La notificación **en curso** lleva la cuenta atrás que dibuja el propio sistema (no hay servicio en primer plano
 *   ni la app gasta batería actualizándola).
 * - Los **avisos** son puntuales: cambio de fase y fin. Sin tono alarmante (RNF-3).
 */
interface TimerNotifier {

    fun showRunning(session: TimerSession, state: TimerState.Running)

    fun alertPhaseChange(session: TimerSession, state: TimerState.Running)

    fun alertFinished(session: TimerSession)

    /** Retira la notificación en curso y los avisos. */
    fun cancelAll()

    /** Retira solo la notificación en curso (por ejemplo, al terminar la sesión, que deja su aviso). */
    fun cancelRunning()
}
