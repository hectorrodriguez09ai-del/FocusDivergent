package com.focusdivergent.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.focusdivergent.app.MainActivity
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.closure.ClosureNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * El aviso del Cierre de Jornada: una sola notificación, tranquila y sin sonido de alarma, que abre el resumen del día.
 * No se repite ni escala. El usuario puede silenciar su canal desde los ajustes del sistema.
 */
class AndroidClosureNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ClosureNotifier {

    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun show(day: LocalDate, ideasWaiting: Int) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.closure_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val text = if (ideasWaiting > 0) {
            context.resources.getQuantityString(R.plurals.closure_notification_ideas, ideasWaiting, ideasWaiting)
        } else {
            context.getString(R.string.closure_notification_text)
        }
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            MainActivity.openClosureIntent(context, day),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_closure)
            .setContentTitle(context.getString(R.string.closure_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(ID, notification)
    }

    private companion object {
        const val CHANNEL = "day_closure"
        const val ID = 30
        const val REQUEST_OPEN = 5
    }
}
