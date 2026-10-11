package com.focusdivergent.app.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import com.focusdivergent.app.domain.model.BlockedApp
import com.focusdivergent.app.domain.model.Interruption
import com.focusdivergent.app.domain.model.ObligationReminder
import com.focusdivergent.app.presentation.block.InterruptionContent
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme

/**
 * Dibuja y quita la pantalla de una [Interruption] sobre cualquier app (permiso `SYSTEM_ALERT_WINDOW`).
 *
 * Toda su API debe llamarse desde el hilo principal.
 */
class InterruptionOverlayController(
    private val context: Context,
    private val onEarnExtension: (packageName: String) -> Unit,
    private val onPostponeReminder: (taskId: Long) -> Unit,
    private val onContinueRealityCheck: (packageName: String) -> Unit,
    private val realityCheckSilenceMinutes: () -> Int,
    private val onLaunchFinished: () -> Unit,
    private val onLaunchContinue: (taskId: Long?) -> Unit,
    private val onLaunchStop: () -> Unit,
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var shown: Shown? = null

    private class Shown(val key: String, val view: OverlayComposeView, val owner: OverlayLifecycleOwner)

    // ComposeView es final; AbstractComposeView permite interceptar el botón Atrás, que no puede cerrar la pausa.
    private class OverlayComposeView(
        context: Context,
        private val content: @Composable () -> Unit,
    ) : AbstractComposeView(context) {

        @Composable
        override fun Content() = content()

        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            event.keyCode == KeyEvent.KEYCODE_BACK || super.dispatchKeyEvent(event)
    }

    fun show(interruption: Interruption) {
        // Se compara la clave y no el contenido: p. ej. el tiempo de la app bloqueada sigue contando por debajo
        // del overlay, y no hay que redibujarlo en cada comprobación.
        if (shown?.key == interruption.key) return
        hide()

        val owner = OverlayLifecycleOwner()
        val silenceMinutes = realityCheckSilenceMinutes()
        val view = OverlayComposeView(context) {
            FocusDivergentTheme {
                InterruptionContent(
                    interruption,
                    labelOf = ::labelOf,
                    onGoHome = ::goHome,
                    onEarnExtension = onEarnExtension,
                    onPostponeReminder = onPostponeReminder,
                    onContinueRealityCheck = onContinueRealityCheck,
                    realityCheckSilenceMinutes = silenceMinutes,
                    onLaunchFinished = onLaunchFinished,
                    onLaunchContinue = onLaunchContinue,
                    onLaunchStop = onLaunchStop,
                )
            }
        }
        owner.attachTo(view)
        owner.start()

        try {
            windowManager.addView(view, layoutParams(secure = interruption is BlockedApp || interruption is ObligationReminder))
            shown = Shown(interruption.key, view, owner)
        } catch (e: RuntimeException) {
            // Sin el permiso de superposición el sistema rechaza la ventana; mejor perder el bloqueo que tumbar el servicio.
            Log.w(TAG, "No se pudo mostrar la pantalla de pausa", e)
            view.disposeComposition()
            owner.stop()
        }
    }

    fun hide() {
        val current = shown ?: return
        shown = null
        try {
            windowManager.removeViewImmediate(current.view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "La pantalla de pausa ya no estaba en la ventana", e)
        }
        current.view.disposeComposition()
        current.owner.stop()
    }

    // [secure]: FLAG_SECURE hace que la ventana salga en negro en capturas, grabaciones y asistentes de pantalla
    // («Circle to Search», Lens…), para que nadie resuelva el reto matemático con una herramienta externa. Solo lo
    // necesitan las pantallas con reto (pausa y aviso de Obligación); el Reality Check y la Lanzadera no.
    private fun layoutParams(secure: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (secure) WindowManager.LayoutParams.FLAG_SECURE else 0),
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    // Se envía al usuario al inicio, no se cierra la app: así no hace falta tocar nada de la app bloqueada.
    // La actividad se puede lanzar desde un servicio porque hay una ventana de superposición visible.
    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(home)
    }

    private fun labelOf(packageName: String): String = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    private companion object {
        const val TAG = "InterruptionOverlay"
    }
}
