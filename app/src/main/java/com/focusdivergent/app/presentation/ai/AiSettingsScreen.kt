package com.focusdivergent.app.presentation.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.ai.LlmConfig
import com.focusdivergent.app.presentation.components.FocusCard
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SecondaryActionButton
import com.focusdivergent.app.presentation.theme.Spacing

/** Dónde el usuario pone su servicio de IA, el modelo y su clave. Todo opcional: sin clave, la función no existe. */
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    AiSettingsContent(
        config = config,
        onBack = onBack,
        onSave = viewModel::save,
        onRemoveKey = viewModel::removeKey,
    )
}

@Composable
fun AiSettingsContent(
    config: LlmConfig,
    onBack: () -> Unit,
    onSave: (baseUrl: String, model: String, newKey: String) -> AiSaveResult,
    onRemoveKey: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(config.baseUrl) }
    var model by rememberSaveable { mutableStateOf(config.model) }
    var key by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }

    ScreenScaffold(title = stringResource(R.string.ai_settings_title), onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(text = stringResource(R.string.ai_settings_intro), style = MaterialTheme.typography.bodyLarge)
            FocusCard {
                Text(text = stringResource(R.string.ai_settings_what_is_sent), style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    message = null
                },
                label = { Text(stringResource(R.string.ai_settings_url_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    message = null
                },
                label = { Text(stringResource(R.string.ai_settings_model_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    message = null
                },
                label = { Text(stringResource(R.string.ai_settings_key_label)) },
                supportingText = {
                    Text(
                        stringResource(if (config.hasKey) R.string.ai_settings_key_saved else R.string.ai_settings_key_hint),
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            message?.let {
                Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }

            PrimaryActionButton(
                text = stringResource(R.string.ai_settings_save),
                onClick = {
                    message = when (onSave(url, model, key)) {
                        AiSaveResult.INVALID_URL -> R.string.ai_settings_url_invalid
                        AiSaveResult.SAVED -> R.string.ai_settings_saved
                    }
                    if (message == R.string.ai_settings_saved) key = ""
                },
            )

            Text(
                text = stringResource(if (config.isConfigured) R.string.ai_settings_ready else R.string.ai_settings_incomplete),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (config.hasKey) {
                SecondaryActionButton(
                    text = stringResource(R.string.ai_settings_remove),
                    onClick = {
                        onRemoveKey()
                        key = ""
                        message = R.string.ai_settings_removed
                    },
                )
            }
        }
    }
}
