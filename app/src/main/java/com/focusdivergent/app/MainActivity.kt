package com.focusdivergent.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.focusdivergent.app.presentation.navigation.AppNavHost
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Solo al crearse de verdad: al girar la pantalla la intención sigue ahí, pero no hay que volver a navegar.
        val openTimer = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_TIMER, false)
        val openAudio = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_AUDIO, false)
        val openClosureDay = if (savedInstanceState == null && intent.hasExtra(EXTRA_OPEN_CLOSURE_DAY)) {
            intent.getLongExtra(EXTRA_OPEN_CLOSURE_DAY, 0L)
        } else {
            null
        }
        setContent {
            FocusDivergentTheme {
                AppNavHost(openTimer = openTimer, openAudio = openAudio, openClosureDay = openClosureDay)
            }
        }
    }

    companion object {
        private const val EXTRA_OPEN_TIMER = "open_timer"
        private const val EXTRA_OPEN_AUDIO = "open_audio"
        private const val EXTRA_OPEN_CLOSURE_DAY = "open_closure_day"

        /** Abre la app directamente en el resumen del día [day] (días desde 1970): es lo que hace la notificación del cierre. */
        fun openClosureIntent(context: Context, day: LocalDate): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_CLOSURE_DAY, day.toEpochDay())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /** Abre la app directamente en el temporizador: es lo que hacen sus notificaciones. */
        fun openTimerIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TIMER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /** Abre la app directamente en el sonido ambiente: es lo que hace su notificación. */
        fun openAudioIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_AUDIO, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
