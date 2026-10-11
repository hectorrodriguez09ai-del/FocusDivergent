package com.focusdivergent.app.presentation.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.policy.ReminderPolicy
import com.focusdivergent.app.presentation.friction.MathChallengeScreen
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

private const val REFRESH_MS = 10_000L

/**
 * Aviso preventivo de una Obligación (RF-1.2).
 *
 * Pantalla completa sin botón de cerrar: se retira sola cuando se apaga la pantalla o llega la hora, y la única
 * acción es posponerla resolviendo el reto matemático. El tono es de ayuda a prepararse, no de alarma (RNF-3).
 */
@Composable
fun ObligationReminderContent(
    title: String,
    startTime: Instant,
    onPostpone: () -> Unit,
) {
    var challenging by remember { mutableStateOf(false) }

    if (challenging) {
        MathChallengeScreen(
            onSolved = onPostpone,
            onBack = { challenging = false },
            titleRes = R.string.challenge_title_postpone,
        )
        return
    }

    // La clave del overlay no cambia mientras dura el aviso, así que la cuenta atrás se refresca desde aquí.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_MS)
            now = Instant.now()
        }
    }
    val minutesLeft = minutesUntil(startTime, now)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.reminder_title, title, minutesLeft),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.reminder_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick = { challenging = true }) {
                Text(stringResource(R.string.reminder_postpone, ReminderPolicy.SNOOZE_MINUTES))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.reminder_postpone_hint),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Minutos que faltan para [startTime], redondeados hacia arriba y nunca menos de uno mientras el aviso está en pantalla. */
internal fun minutesUntil(startTime: Instant, now: Instant): Long {
    val seconds = Duration.between(now, startTime).seconds
    return ((seconds + 59) / 60).coerceAtLeast(1)
}
