package com.itravel.app.map

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.itravel.app.data.PlaceWithPhotos
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapController(
    private val context: Context,
    private val onLongPress: (GeoPoint) -> Unit,
    private val onPlaceClick: (Long) -> Unit,
    private val onMapTouch: () -> Unit = {}
) {
    val mapView = MapView(context)

    private val locationOverlay: MyLocationNewOverlay
    private var draftMarker: Marker? = null
    private var lastItems: List<PlaceWithPhotos> = emptyList()

    val hasLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    init {
        mapView.setTileSource(GaodeRoadTileSource)
        mapView.setUseDataConnection(true)
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 2.5
        mapView.maxZoomLevel = 19.0
        mapView.controller.setZoom(4.0)
        mapView.controller.setCenter(GeoPoint(35.0, 106.0))

        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)

        // 手指触摸地图时通知外部（用于折叠底部面板）
        mapView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) onMapTouch()
            false
        }

        rebuildOverlays()
    }

    private fun rebuildOverlays() {
        mapView.overlays.clear()

        mapView.overlays.add(RotationGestureOverlay(mapView).apply { isEnabled = true })

        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint): Boolean {
                onLongPress(p)
                return true
            }
        }
        mapView.overlays.add(MapEventsOverlay(receiver))

        val compass = CompassOverlay(context, mapView).apply { enableCompass() }
        mapView.overlays.add(compass)

        mapView.overlays.add(ScaleBarOverlay(mapView))

        // place markers
        lastItems.forEach { item -> mapView.overlays.add(buildPlaceMarker(item)) }

        draftMarker?.let { mapView.overlays.add(it) }

        mapView.overlays.add(locationOverlay)

        mapView.invalidate()
    }

    private fun buildPlaceMarker(item: PlaceWithPhotos): Marker {
        val p = item.place
        return Marker(mapView).apply {
            position = GeoPoint(p.latitude, p.longitude)
            icon = Thumbnails.markerIcon(context, p.coverPath)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = p.title
            snippet = p.placeName
            setOnMarkerClickListener { _, _ ->
                onPlaceClick(p.id)
                true
            }
        }
    }

    fun renderPlaces(items: List<PlaceWithPhotos>) {
        lastItems = items
        rebuildOverlays()
    }

    // ------------------------------------------------------------- draft point

    fun showDraft(point: GeoPoint) {
        draftMarker?.let { mapView.overlays.remove(it) }
        draftMarker = Marker(mapView).apply {
            position = point
            icon = draftDrawable()
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        rebuildOverlays()
        mapView.controller.animateTo(point)
        mapView.controller.zoomTo(13.0)
    }

    fun clearDraft() {
        draftMarker = null
        rebuildOverlays()
    }

    private fun draftDrawable(): BitmapDrawable {
        val px = (42 * context.resources.displayMetrics.density).toInt()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E65100") }
        canvas.drawCircle(px / 2f, px / 2f, px * 0.32f, paint)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = px * 0.08f
            color = Color.WHITE
        }
        canvas.drawCircle(px / 2f, px / 2f, px * 0.32f, white)
        return BitmapDrawable(context.resources, bmp)
    }

    // ------------------------------------------------------------ location ops

    fun enableLocation(centerOnFix: Boolean = false) {
        if (!hasLocationPermission) return
        if (!locationOverlay.isMyLocationEnabled) {
            locationOverlay.enableMyLocation()
        }
        if (centerOnFix) {
            locationOverlay.runOnFirstFix {
                mapView.post {
                    locationOverlay.myLocation?.let { moveTo(it, 14.0) }
                }
            }
        }
    }

    /** Last known location from any available provider, without waiting for a fix. */
    fun lastKnownLocation(): GeoPoint? {
        if (!hasLocationPermission) return null
        locationOverlay.myLocation?.let { return it }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        for (provider in providers) {
            runCatching {
                lm.getLastKnownLocation(provider)?.let {
                    return GeoPoint(it.latitude, it.longitude)
                }
            }
        }
        return null
    }

    fun moveTo(point: GeoPoint, zoom: Double = 14.0) {
        mapView.controller.animateTo(point)
        mapView.controller.zoomTo(zoom)
    }

    fun onFirstFix(action: (GeoPoint) -> Unit) {
        if (!hasLocationPermission) return
        locationOverlay.runOnFirstFix {
            mapView.post { locationOverlay.myLocation?.let { action(it) } }
        }
    }

    fun fitAll(items: List<PlaceWithPhotos>) {
        if (items.isEmpty()) return
        val points = items.map { GeoPoint(it.place.latitude, it.place.longitude) }
        val north = points.maxOf { it.latitude }
        val south = points.minOf { it.latitude }
        val east = points.maxOf { it.longitude }
        val west = points.minOf { it.longitude }
        val box = BoundingBox(north, east, south, west)
        mapView.post {
            runCatching { mapView.zoomToBoundingBox(box, true, 120) }
        }
    }

    // --------------------------------------------------------------- lifecycle

    fun onResume() {
        mapView.onResume()
        if (hasLocationPermission && !locationOverlay.isMyLocationEnabled) {
            locationOverlay.enableMyLocation()
        }
    }

    fun onPause() {
        mapView.onPause()
    }

    fun onDetach() {
        runCatching { locationOverlay.disableMyLocation() }
        mapView.onDetach()
    }
}
