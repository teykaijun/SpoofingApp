package com.spoofingmobileapp.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spoofingmobileapp.BuildConfig
import com.spoofingmobileapp.R
import com.spoofingmobileapp.spoof.MockLocationSetup
import com.spoofingmobileapp.spoof.SpoofError
import com.spoofingmobileapp.spoof.SpoofState
import com.spoofingmobileapp.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val spoofState by viewModel.spoofState.collectAsStateWithLifecycle()
    val spoofError by viewModel.spoofError.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCoordinates by rememberSaveable { mutableStateOf(false) }
    var showSaveFavorite by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var startAfterPermission by rememberSaveable { mutableStateOf(false) }

    // Setup can change while the user is in system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSetup() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.refreshSetup()
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        permissionDenied = !granted
        if (granted && startAfterPermission) viewModel.startSpoofing()
        startAfterPermission = false
    }
    val requestPermissions: () -> Unit = {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    val errorMessage = when (val error = spoofError) {
        null -> null
        SpoofError.NotMockLocationApp -> stringResource(R.string.error_not_mock_app)
        SpoofError.MissingLocationPermission -> stringResource(R.string.error_permission)
        SpoofError.ForegroundServiceBlocked -> stringResource(R.string.error_foreground_blocked)
        is SpoofError.Unexpected -> stringResource(R.string.error_generic, error.message)
    }
    val developerOptionsLabel = stringResource(R.string.setup_mock_app_button)
    LaunchedEffect(spoofError) {
        val error = spoofError ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = errorMessage.orEmpty(),
            actionLabel = if (error == SpoofError.NotMockLocationApp) developerOptionsLabel else null,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) MockLocationSetup.openDeveloperOptions(context)
        viewModel.clearError()
        viewModel.refreshSetup()
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(context.getString(it)) }
    }

    val active = spoofState as? SpoofState.Active
    val pendingConfig = remember(ui, settings) { ui.toConfig(settings) }
    val startSpoofing: () -> Unit = {
        if (ui.setup.hasLocationPermission) {
            viewModel.startSpoofing()
        } else {
            startAfterPermission = true
            requestPermissions()
        }
    }

    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val openLinkError = stringResource(R.string.error_open_link)
    val openLink: (String) -> Unit = { url ->
        try {
            uriHandler.openUri(url)
        } catch (e: IllegalArgumentException) {
            scope.launch { snackbarHostState.showSnackbar(openLinkError) }
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(openLinkError) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                SpoofMap(
                    target = ui.target.takeIf { ui.mode == SpoofMode.Fixed },
                    waypoints = if (ui.mode == SpoofMode.Route) ui.waypoints else emptyList(),
                    loopRoute = settings.loopRoute,
                    spoofedPosition = active?.sample?.position,
                    initialCenter = ui.target,
                    cameraMoves = viewModel.cameraMoves,
                    onTap = viewModel::onMapTap,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchBox(
                        state = ui.search,
                        onSearch = viewModel::search,
                        onChoose = viewModel::choosePlace,
                        onDismiss = viewModel::dismissSearch,
                    )
                    if (!ui.setup.isComplete) {
                        SetupCard(
                            setup = ui.setup,
                            permissionDenied = permissionDenied,
                            onRequestPermission = requestPermissions,
                            onOpenAppSettings = { MockLocationSetup.openAppSettings(context) },
                            onOpenAboutPhone = { MockLocationSetup.openAboutPhone(context) },
                            onOpenDeveloperOptions = { MockLocationSetup.openDeveloperOptions(context) },
                        )
                    }
                }
                MapButtons(
                    onMyLocation = {
                        if (ui.setup.hasLocationPermission) viewModel.centerOnDeviceLocation() else requestPermissions()
                    },
                    onCenterSelection = viewModel::centerOnSelection,
                    onFavorites = { showFavorites = true },
                    onSettings = { showSettings = true },
                    onCheckUpdates = viewModel::checkForUpdates,
                    onSupport = { openLink(BUY_ME_A_COFFEE_URL) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                )
            }
            ControlPanel(
                ui = ui,
                settings = settings,
                active = active,
                pendingConfig = pendingConfig,
                onModeChange = viewModel::setMode,
                onEditCoordinates = { showCoordinates = true },
                onSaveFavorite = { showSaveFavorite = true },
                onUndoWaypoint = viewModel::undoWaypoint,
                onClearWaypoints = viewModel::clearWaypoints,
                onSpeedChange = viewModel::setSpeedKmh,
                onLoopChange = viewModel::setLoopRoute,
                onStart = startSpoofing,
                onStop = viewModel::stopSpoofing,
            )
        }
    }

    if (showFavorites) {
        FavoritesSheet(
            favorites = favorites,
            onChoose = {
                showFavorites = false
                viewModel.chooseFavorite(it)
            },
            onDelete = viewModel::deleteFavorite,
            onDismiss = { showFavorites = false },
        )
    }
    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onChange = viewModel::updateSettings,
            onDismiss = { showSettings = false },
        )
    }
    if (showCoordinates) {
        CoordinatesDialog(
            initial = ui.target,
            onConfirm = {
                showCoordinates = false
                viewModel.setTarget(it)
            },
            onDismiss = { showCoordinates = false },
        )
    }
    if (showSaveFavorite) {
        SaveFavoriteDialog(
            onSave = {
                showSaveFavorite = false
                viewModel.saveFavorite(it)
            },
            onDismiss = { showSaveFavorite = false },
        )
    }

    UpdateDialog(
        state = updateState,
        currentVersion = BuildConfig.VERSION_NAME,
        onUpdate = viewModel::installUpdate,
        onOpenReleaseNotes = openLink,
        onOpenPermissionSettings = {
            viewModel.dismissUpdate()
            viewModel.openInstallPermissionSettings()
        },
        onDismiss = viewModel::dismissUpdate,
    )
}

@Composable
private fun MapButtons(
    onMyLocation: () -> Unit,
    onCenterSelection: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    onCheckUpdates: () -> Unit,
    onSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            SmallFloatingActionButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_title)) },
                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onSettings()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_check_updates)) },
                    leadingIcon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onCheckUpdates()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_support)) },
                    leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                    trailingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onSupport()
                    },
                )
            }
        }
        SmallFloatingActionButton(onClick = onFavorites) {
            Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.cd_favorites))
        }
        SmallFloatingActionButton(onClick = onCenterSelection) {
            Icon(Icons.Filled.CenterFocusStrong, contentDescription = stringResource(R.string.cd_center_target))
        }
        FloatingActionButton(onClick = onMyLocation) {
            Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.cd_my_location))
        }
    }
}

private const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/casunoxd"
