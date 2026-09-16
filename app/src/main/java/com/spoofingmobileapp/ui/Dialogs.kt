package com.spoofingmobileapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spoofingmobileapp.R
import com.spoofingmobileapp.data.SpoofSettings
import com.spoofingmobileapp.geo.CoordinateParser
import com.spoofingmobileapp.geo.LatLng
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun CoordinatesDialog(initial: LatLng?, onConfirm: (LatLng) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial?.format(6).orEmpty()) }
    val parsed = remember(text) { CoordinateParser.parse(text) }
    val showError = text.isNotBlank() && parsed == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_coordinates_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.label_coordinates)) },
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.error_coordinates)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onDone = { parsed?.let(onConfirm) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun SaveFavoriteDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.dialog_save_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSave(name) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun SettingsDialog(
    settings: SpoofSettings,
    onChange: ((SpoofSettings) -> SpoofSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var accuracy by remember { mutableFloatStateOf(settings.accuracyMeters) }
    var intervalSeconds by remember { mutableFloatStateOf(settings.updateIntervalMillis / 1000f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_accuracy, accuracy.roundToInt()))
                Slider(
                    value = accuracy,
                    onValueChange = { accuracy = it.roundToInt().toFloat() },
                    onValueChangeFinished = { onChange { it.copy(accuracyMeters = accuracy) } },
                    valueRange = 1f..50f,
                )
                Text(
                    stringResource(
                        R.string.settings_interval,
                        String.format(Locale.getDefault(), "%.1f s", intervalSeconds),
                    ),
                )
                Slider(
                    value = intervalSeconds,
                    onValueChange = { intervalSeconds = (it * 2).roundToInt() / 2f },
                    onValueChangeFinished = {
                        onChange { it.copy(updateIntervalMillis = (intervalSeconds * 1000).roundToLong()) }
                    },
                    valueRange = 0.5f..5f,
                    steps = 8,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_fused))
                        Text(
                            stringResource(R.string.settings_fused_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.mockPlayServices,
                        onCheckedChange = { checked -> onChange { it.copy(mockPlayServices = checked) } },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}
