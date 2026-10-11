package com.focusdivergent.app.presentation.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.presentation.components.AppIcon
import com.focusdivergent.app.presentation.components.EmptyState
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing

private const val MILLIS_PER_MINUTE = 60_000L

/** Elegir la app a la que poner un límite: buscador arriba y, debajo, las apps por uso de hoy. */
@Composable
fun AppPickerScreen(
    onBack: () -> Unit,
    onPick: (packageName: String) -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppPickerContent(uiState = uiState, onBack = onBack, onPick = onPick, onQuery = viewModel::setQuery)
}

@Composable
fun AppPickerContent(
    uiState: AppPickerUiState,
    onBack: () -> Unit,
    onPick: (packageName: String) -> Unit,
    onQuery: (String) -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.app_picker_title), onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQuery,
                label = { Text(stringResource(R.string.app_picker_search)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(uiState.apps, key = { it.app.packageName }) { app ->
                    PickerRow(app = app, onClick = { onPick(app.app.packageName) })
                }
                if (uiState.apps.isEmpty() && !uiState.loading) {
                    item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.app_picker_empty_title),
                            message = stringResource(R.string.app_picker_empty_message),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(app: PickerApp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AppIcon(app.app.packageName, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.app.label, style = MaterialTheme.typography.titleMedium)
                val detail = when {
                    app.hasLimit -> stringResource(R.string.app_picker_has_limit)
                    app.usedMillis >= MILLIS_PER_MINUTE ->
                        stringResource(R.string.app_picker_used_today, (app.usedMillis / MILLIS_PER_MINUTE).toInt())
                    else -> null
                }
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
