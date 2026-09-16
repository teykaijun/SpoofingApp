package com.spoofingmobileapp.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.spoofingmobileapp.R
import com.spoofingmobileapp.data.SpoofSettings
import com.spoofingmobileapp.geo.GeoMath
import com.spoofingmobileapp.geo.LatLng
import com.spoofingmobileapp.geo.Units
import com.spoofingmobileapp.spoof.SpoofConfig
import com.spoofingmobileapp.spoof.SpoofState
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class SpeedPreset(val kmh: Float, @StringRes val label: Int) {
    Walk(5f, R.string.speed_walk),
    Run(10f, R.string.speed_run),
    Bike(18f, R.string.speed_bike),
    Car(50f, R.string.speed_car),
}

@Composable
fun ControlPanel(
    ui: MainUiState,
    settings: SpoofSettings,
    active: SpoofState.Active?,
    pendingConfig: SpoofConfig?,
    onModeChange: (SpoofMode) -> Unit,
    onEditCoordinates: () -> Unit,
    onSaveFavorite: () -> Unit,
    onUndoWaypoint: () -> Unit,
    onClearWaypoints: () -> Unit,
    onSpeedChange: (Double) -> Unit,
    onLoopChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeSelector(ui.mode, onModeChange)
            when (ui.mode) {
                SpoofMode.Fixed -> FixedPointSection(ui.target, onEditCoordinates, onSaveFavorite)
                SpoofMode.Route -> RouteSection(
                    ui.waypoints, settings, onUndoWaypoint, onClearWaypoints, onSpeedChange, onLoopChange,
                )
            }
            if (active != null) ActiveStatus(active)
            ActionButtons(
                active = active,
                canStart = pendingConfig != null,
                hasChanges = active != null && pendingConfig != null && pendingConfig != active.config,
                onStart = onStart,
                onStop = onStop,
            )
            if (active == null && ui.mode == SpoofMode.Route && ui.waypoints.size < 2) {
                Text(
                    stringResource(R.string.route_needs_two_points),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(mode: SpoofMode, onModeChange: (SpoofMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SpoofMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onModeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, SpoofMode.entries.size),
                icon = {
                    SegmentedButtonDefaults.Icon(
                        active = mode == option,
                        inactiveContent = {
                            Icon(
                                if (option == SpoofMode.Fixed) Icons.Filled.Place else Icons.Filled.Route,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                },
                label = {
                    Text(stringResource(if (option == SpoofMode.Fixed) R.string.mode_fixed else R.string.mode_route))
                },
            )
        }
    }
}

@Composable
private fun FixedPointSection(target: LatLng?, onEditCoordinates: () -> Unit, onSaveFavorite: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(
                text = target?.format(5) ?: stringResource(R.string.hint_tap_map_fixed),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (target != null) FontFamily.Monospace else null,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEditCoordinates) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_edit_coordinates))
            }
            OutlinedButton(onClick = onSaveFavorite, enabled = target != null) {
                Icon(Icons.Outlined.StarOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_save_favorite))
            }
        }
    }
}

@Composable
private fun RouteSection(
    waypoints: List<LatLng>,
    settings: SpoofSettings,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSpeedChange: (Double) -> Unit,
    onLoopChange: (Boolean) -> Unit,
) {
    val distance = remember(waypoints, settings.loopRoute) {
        val legs = if (settings.loopRoute && waypoints.size > 1) waypoints + waypoints.first() else waypoints
        legs.zipWithNext { a, b -> GeoMath.distanceMeters(a, b) }.sum()
    }
    var speedKmh by remember(settings.speedKmh) { mutableFloatStateOf(settings.speedKmh.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Route, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (waypoints.isEmpty()) {
                    stringResource(R.string.hint_tap_map_route)
                } else {
                    pluralStringResource(
                        R.plurals.route_summary, waypoints.size, waypoints.size, Units.formatDistance(distance),
                    )
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onUndo, enabled = waypoints.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.action_undo_waypoint))
            }
            IconButton(onClick = onClear, enabled = waypoints.isNotEmpty()) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_clear_route))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.speed_label, Units.formatSpeed(Units.kmhToMps(speedKmh.toDouble()))),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.loop_route), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Switch(checked = settings.loopRoute, onCheckedChange = onLoopChange)
        }
        Slider(
            value = speedKmh,
            onValueChange = { speedKmh = it.roundToInt().toFloat() },
            onValueChangeFinished = { onSpeedChange(speedKmh.toDouble()) },
            valueRange = 1f..150f,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedPreset.entries.forEach { preset ->
                FilterChip(
                    selected = abs(speedKmh - preset.kmh) < 0.5f,
                    onClick = {
                        speedKmh = preset.kmh
                        onSpeedChange(preset.kmh.toDouble())
                    },
                    label = { Text(stringResource(preset.label)) },
                )
            }
        }
    }
}

@Composable
private fun ActiveStatus(active: SpoofState.Active) {
    val sample = active.sample
    val text = when {
        !active.config.isRoute -> stringResource(R.string.status_fixed, sample.position.format(5))
        sample.finished -> stringResource(R.string.status_route_finished)
        else -> stringResource(
            R.string.status_route,
            Units.formatDistance(sample.distanceMeters),
            Units.formatDistance(sample.totalDistanceMeters),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(ActiveGreen, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionButtons(
    active: SpoofState.Active?,
    canStart: Boolean,
    hasChanges: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (active == null) {
            Button(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_start))
            }
        } else {
            if (hasChanges) {
                FilledTonalButton(
                    onClick = onStart,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_apply))
                }
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_stop))
            }
        }
    }
}

private val ActiveGreen = Color(0xFF2E7D32)
