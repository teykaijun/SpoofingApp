package com.spoofingmobileapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spoofingmobileapp.R
import com.spoofingmobileapp.update.UpdateInfo
import com.spoofingmobileapp.update.UpdateState
import java.util.Locale
import kotlin.math.roundToInt

/** Shows whatever the updater is currently doing. Renders nothing while idle. */
@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    onUpdate: (UpdateInfo) -> Unit,
    onOpenReleaseNotes: (String) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        UpdateState.Idle -> Unit

        UpdateState.Checking -> BusyDialog(stringResource(R.string.update_checking), onDismiss)

        is UpdateState.UpToDate -> MessageDialog(
            message = stringResource(R.string.update_up_to_date, state.versionName),
            onDismiss = onDismiss,
        )

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            R.string.update_available_body,
                            state.info.versionName,
                            currentVersion,
                        ),
                    )
                    if (state.info.sizeBytes > 0) {
                        Text(
                            stringResource(R.string.update_download_size, formatBytes(state.info.sizeBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.info.releaseUrl.isNotEmpty()) {
                        TextButton(
                            onClick = { onOpenReleaseNotes(state.info.releaseUrl) },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(stringResource(R.string.update_release_notes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onUpdate(state.info) }) { Text(stringResource(R.string.update_action)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            // Not dismissible: the download is already running.
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.update_downloading, (state.progress * 100).roundToInt()))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
        )

        UpdateState.Installing -> BusyDialog(stringResource(R.string.update_installing), onDismiss = null)

        UpdateState.Installed -> MessageDialog(
            message = stringResource(R.string.update_installed),
            onDismiss = onDismiss,
        )

        is UpdateState.Failed -> MessageDialog(
            message = state.message?.let { stringResource(R.string.update_failed, it) }
                ?: stringResource(R.string.update_failed_generic),
            onDismiss = onDismiss,
        )

        is UpdateState.PermissionRequired -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_body)) },
            confirmButton = {
                TextButton(onClick = onOpenPermissionSettings) {
                    Text(stringResource(R.string.update_permission_button))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun BusyDialog(message: String, onDismiss: (() -> Unit)?) {
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(16.dp))
                Text(message)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun MessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

private fun formatBytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
