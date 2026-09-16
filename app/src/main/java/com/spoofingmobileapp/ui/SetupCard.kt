package com.spoofingmobileapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spoofingmobileapp.R

/** Walks the user through the steps Android requires before mock locations work. */
@Composable
fun SetupCard(
    setup: SetupStatus,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenAboutPhone: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleSmall)
            }
            if (!setup.hasLocationPermission) {
                if (permissionDenied) {
                    SetupStep(
                        text = stringResource(R.string.setup_permission_denied),
                        actionLabel = stringResource(R.string.setup_open_app_settings),
                        onAction = onOpenAppSettings,
                    )
                } else {
                    SetupStep(
                        text = stringResource(R.string.setup_permission),
                        actionLabel = stringResource(R.string.setup_permission_button),
                        onAction = onRequestPermission,
                    )
                }
            }
            if (!setup.isMockLocationApp) {
                if (setup.developerOptionsEnabled) {
                    SetupStep(
                        text = stringResource(R.string.setup_mock_app),
                        actionLabel = stringResource(R.string.setup_mock_app_button),
                        onAction = onOpenDeveloperOptions,
                    )
                } else {
                    SetupStep(
                        text = stringResource(R.string.setup_dev_options),
                        actionLabel = stringResource(R.string.setup_dev_options_button),
                        onAction = onOpenAboutPhone,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStep(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}
