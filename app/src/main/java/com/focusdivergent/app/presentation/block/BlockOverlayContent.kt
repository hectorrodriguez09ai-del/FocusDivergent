package com.focusdivergent.app.presentation.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.model.BlockReason
import com.focusdivergent.app.domain.policy.ExtensionPolicy
import com.focusdivergent.app.presentation.friction.MathChallengeScreen

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Pantalla de pausa que cubre la app bloqueada.
 *
 * El tono es de cuidado, no de castigo (RNF-3): sin rojos ni lenguaje acusatorio, y recordando que
 * el límite lo eligió el propio usuario. Si quedan ampliaciones hoy, ofrece ganar tiempo extra con un
 * reto matemático; cuando se agotan, o si el límite es firme y nunca las tuvo, el botón desaparece y el
 * bloqueo es absoluto hasta mañana.
 */
@Composable
fun BlockOverlayContent(
    appLabel: String,
    reason: BlockReason,
    extensionsLeft: Int,
    extensionsPerDay: Int,
    onGoHome: () -> Unit,
    onEarnExtension: () -> Unit,
) {
    var challenging by remember { mutableStateOf(false) }

    if (challenging) {
        MathChallengeScreen(onSolved = onEarnExtension, onBack = { challenging = false })
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.block_title, appLabel),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = when (reason) {
                    is BlockReason.LimitReached -> {
                        val minutes = (reason.allowedMillis / MILLIS_PER_MINUTE).toInt()
                        pluralStringResource(R.plurals.block_message_limit, minutes, minutes)
                    }
                    BlockReason.StrictlyBlocked -> stringResource(R.string.block_message_strict)
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onGoHome) {
                Text(stringResource(R.string.block_go_home))
            }

            if (extensionsLeft > 0) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { challenging = true }) {
                    Text(stringResource(R.string.block_earn_extension, ExtensionPolicy.MINUTES_PER_EXTENSION))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(R.plurals.block_extensions_left, extensionsLeft, extensionsLeft),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            } else if (reason is BlockReason.LimitReached) {
                Spacer(Modifier.height(16.dp))
                Text(
                    // Sin ampliaciones desde el principio (nivel firme) no se dice que se hayan gastado.
                    text = stringResource(if (extensionsPerDay > 0) R.string.block_no_more_extensions else R.string.block_firm_note),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
