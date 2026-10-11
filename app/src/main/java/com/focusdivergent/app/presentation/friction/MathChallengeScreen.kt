package com.focusdivergent.app.presentation.friction

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.friction.MathChallengeGenerator

/**
 * Reto matemático que hay que resolver para ganar tiempo extra (RF-1.3).
 *
 * La respuesta se escribe solo con el teclado numérico propio de esta pantalla. Si se falla, aparece una
 * operación nueva (no se puede ir probando) con un mensaje amable, sin castigo (RNF-3).
 */
@Composable
fun MathChallengeScreen(
    onSolved: () -> Unit,
    onBack: () -> Unit,
    @StringRes titleRes: Int = R.string.challenge_title,
    generator: MathChallengeGenerator = remember { MathChallengeGenerator() },
) {
    var challenge by remember { mutableStateOf(generator.generate()) }
    var input by remember { mutableStateOf(AnswerInput()) }
    var missed by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf(false) }

    fun confirm() {
        if (solved) return
        val answer = input.value ?: return
        if (answer == challenge.answer) {
            solved = true
            onSolved()
        } else {
            missed = true
            challenge = generator.generate()
            input = AnswerInput()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.challenge_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            // Texto plano, sin SelectionContainer: el enunciado no se puede seleccionar ni copiar.
            Text(
                text = challenge.expression,
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (input.isEmpty) "·" else input.digits,
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (missed) stringResource(R.string.challenge_missed) else " ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            NumericKeypad(
                onDigit = { input = input.append(it) },
                onBackspace = { input = input.backspace() },
                onConfirm = ::confirm,
            )

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.challenge_back))
            }
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { digits ->
            KeypadRow {
                digits.forEach { digit -> KeypadKey(label = digit.toString(), onClick = { onDigit(digit) }) }
            }
        }
        KeypadRow {
            KeypadKey(
                label = "⌫",
                description = stringResource(R.string.challenge_key_delete),
                onClick = onBackspace,
            )
            KeypadKey(label = "0", onClick = { onDigit(0) })
            KeypadKey(
                label = "✓",
                description = stringResource(R.string.challenge_key_confirm),
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun KeypadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun RowScope.KeypadKey(
    label: String,
    onClick: () -> Unit,
    description: String = label,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .semantics { contentDescription = description },
    ) {
        Text(text = label, style = MaterialTheme.typography.headlineSmall)
    }
}
