package com.focusdivergent.app.presentation.block

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R

private const val MILLIS_PER_MINUTE = 60_000L

// Oscurece la app de debajo sin taparla del todo: es una pregunta, no un bloqueo.
private val Scrim = Color.Black.copy(alpha = 0.6f)

/**
 * Reality Check (RF-4.3): modal translúcido sobre la app que lleva rato en uso.
 *
 * Pregunta sin juzgar. Tiene que contestarse con una de las dos opciones; no se cierra con el botón Atrás.
 */
@Composable
fun RealityCheckContent(
    appLabel: String,
    continuousMillis: Long,
    silenceMinutes: Int,
    onContinue: () -> Unit,
    onGoHome: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Scrim)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.reality_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                val minutes = (continuousMillis / MILLIS_PER_MINUTE).toInt()
                Text(
                    text = pluralStringResource(R.plurals.reality_message, minutes, minutes, appLabel),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(onClick = onContinue) {
                        Text(stringResource(R.string.reality_continue))
                    }
                    OutlinedButton(onClick = onGoHome) {
                        Text(stringResource(R.string.reality_go_home))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = pluralStringResource(R.plurals.reality_continue_hint, silenceMinutes, silenceMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
