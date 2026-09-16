package com.spoofingmobileapp.ui

import android.content.Context
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spoofingmobileapp.R
import com.spoofingmobileapp.geo.LatLng
import kotlinx.coroutines.flow.Flow
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** OpenStreetMap view showing the chosen point or route and the live spoofed position. */
@Composable
fun SpoofMap(
    target: LatLng?,
    waypoints: List<LatLng>,
    loopRoute: Boolean,
    spoofedPosition: LatLng?,
    initialCenter: LatLng?,
    cameraMoves: Flow<CameraMove>,
    onTap: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnTap by rememberUpdatedState(onTap)
    val controller = remember { OsmMapController(context, initialCenter) { currentOnTap(it) } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> controller.mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(controller) {
        onDispose { controller.mapView.onDetach() }
    }
    LaunchedEffect(controller, cameraMoves) {
        cameraMoves.collect { controller.move(it) }
    }

    AndroidView(
        factory = { controller.mapView },
        modifier = modifier,
        update = { controller.render(target, waypoints, loopRoute, spoofedPosition) },
    )
}

private class OsmMapController(
    private val context: Context,
    initialCenter: LatLng?,
    private val onTap: (LatLng) -> Unit,
) {
    private val density = context.resources.displayMetrics.density

    val mapView: MapView = MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        isVerticalMapRepetitionEnabled = false
        setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
        setMinZoomLevel(3.0)
        setMaxZoomLevel(19.0)
        controller.setZoom(if (initialCenter != null) 15.0 else 3.0)
        controller.setCenter(initialCenter?.toGeoPoint() ?: GeoPoint(20.0, 0.0))
    }

    private val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
            if (p == null) return false
            onTap(LatLng.normalized(p.latitude, p.longitude))
            return true
        }

        override fun longPressHelper(p: GeoPoint?): Boolean = false
    })

    private val copyrightOverlay = CopyrightOverlay(context)

    private val routeLine = Polyline(mapView).apply {
        outlinePaint.color = ROUTE_COLOR
        outlinePaint.strokeWidth = 5f * density
        outlinePaint.strokeCap = Paint.Cap.ROUND
        setInfoWindow(null)
        // Let taps on the line fall through so they still add waypoints.
        setOnClickListener { _, _, _ -> false }
    }

    private val targetMarker = createMarker(R.drawable.ic_marker_target, Marker.ANCHOR_BOTTOM)
    private val spoofedMarker = createMarker(R.drawable.ic_marker_spoofed, Marker.ANCHOR_CENTER)
    private val waypointMarkers = mutableListOf<Marker>()

    fun render(target: LatLng?, waypoints: List<LatLng>, loopRoute: Boolean, spoofed: LatLng?) {
        val overlays = mapView.overlays
        overlays.clear()
        overlays += eventsOverlay
        if (waypoints.size >= 2) {
            val points = waypoints.map { it.toGeoPoint() }
            routeLine.setPoints(if (loopRoute && points.size > 2) points + points.first() else points)
            overlays += routeLine
        }
        while (waypointMarkers.size < waypoints.size) {
            waypointMarkers += createMarker(R.drawable.ic_marker_waypoint, Marker.ANCHOR_CENTER)
        }
        waypoints.forEachIndexed { index, waypoint ->
            overlays += waypointMarkers[index].apply { position = waypoint.toGeoPoint() }
        }
        if (target != null) overlays += targetMarker.apply { position = target.toGeoPoint() }
        if (spoofed != null) overlays += spoofedMarker.apply { position = spoofed.toGeoPoint() }
        overlays += copyrightOverlay
        mapView.invalidate()
    }

    fun move(move: CameraMove) {
        when (move) {
            is CameraMove.Center -> mapView.controller.animateTo(
                move.position.toGeoPoint(),
                move.zoom ?: maxOf(mapView.zoomLevelDouble, 15.0),
                ANIMATION_MILLIS,
            )
            is CameraMove.Fit -> mapView.zoomToBoundingBox(
                BoundingBox.fromGeoPoints(move.positions.map { it.toGeoPoint() }),
                true,
                (48 * density).toInt(),
            )
        }
    }

    private fun createMarker(@DrawableRes icon: Int, anchorV: Float) = Marker(mapView).apply {
        this.icon = ContextCompat.getDrawable(context, icon)
        setAnchor(Marker.ANCHOR_CENTER, anchorV)
        setInfoWindow(null)
        // Returning false lets taps on markers fall through to the map.
        setOnMarkerClickListener { _, _ -> false }
    }

    private companion object {
        val ROUTE_COLOR = 0xFFF57C00.toInt()
        const val ANIMATION_MILLIS = 600L
    }
}

private fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)
