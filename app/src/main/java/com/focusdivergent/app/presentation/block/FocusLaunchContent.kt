package com.focusdivergent.app.presentation.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.SecondaryActionButton
import com.focusdivergent.app.presentation.theme.Spacing
import com.focusdivergent.app.presentation.theme.tabularNumbers
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

private const val TICK_MS = 250L

/**
 * El bloqueo de la Lanzadera de 2 minutos (RF-3.2): pantalla completa con la tarea y una cuenta atrás.
 *
 * No tiene botón para cerrarla y Atrás no hace nada: son 2 minutos, y la gracia es no poder escapar a otra cosa.
 * Solo se retira al terminar, o por seguridad durante una llamada o con la pantalla apagada. El tono es de
 * ánimo, no de vigilancia (RNF-3).
 *
 * @param onFinished se avisa una vez cuando la cuenta llega a cero, para pasar a la pregunta sin esperar al ciclo
 * del servicio.
 */
@Composable
fun FocusLaunchContent(
    taskTitle: String?,
    endsAt: Instant,
    onFinished: () -> Unit,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = Instant.now()
            if (now >= endsAt) {
                onFinished()
                break
            }
            delay(TICK_MS)
        }
    }
    val remaining = remainingSeconds(endsAt, now)
    val countdown = countdownText(remaining)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.launch_overlay_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            if (!taskTitle.isNullOrBlank()) {
                Text(
                    text = taskTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.lg))
            }
            // El lector de pantalla lee «Quedan 1:45», no cuatro caracteres sueltos, y no repite cada segundo.
            val description = stringResource(R.string.launch_remaining, countdown)
            Text(
                text = countdown,
                style = MaterialTheme.typography.displayLarge.tabularNumbers(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = description },
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = stringResource(R.string.launch_overlay_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * La pregunta con la que acaba la Lanzadera: seguir o dejarlo, sin culpa. Las dos salidas pesan igual (mismo tamaño)
 * y la frase final reconoce que empezar ya cuenta.
 */
@Composable
fun FocusLaunchPromptContent(
    taskTitle: String?,
    onContinue: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.launch_prompt_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = if (taskTitle.isNullOrBlank()) {
                    stringResource(R.string.launch_prompt_message)
                } else {
                    stringResource(R.string.launch_prompt_message_task, taskTitle)
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
            PrimaryActionButton(text = stringResource(R.string.launch_prompt_continue), onClick = onContinue)
            Spacer(Modifier.height(Spacing.sm))
            SecondaryActionButton(text = stringResource(R.string.launch_prompt_stop), onClick = onStop)
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = stringResource(R.string.launch_prompt_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Segundos que faltan hasta [endsAt], redondeados hacia arriba y nunca negativos. */
internal fun remainingSeconds(endsAt: Instant, now: Instant): Long {
    val millis = Duration.between(now, endsAt).toMillis()
    return if (millis <= 0) 0 else (millis + 999) / 1000
}

/** «1:45», «0:07»: minutos y segundos con dos cifras de segundos, para que la cuenta no «salte» de ancho. */
internal fun countdownText(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(safe / 60, safe % 60)
}
