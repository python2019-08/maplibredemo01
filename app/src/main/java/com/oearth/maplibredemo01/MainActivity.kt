package com.oearth.maplibredemo01

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.oearth.maplibredemo01.databinding.ActivityMainBinding
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.location.permissions.PermissionsListener
import org.maplibre.android.location.permissions.PermissionsManager
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
//import org.maplibre.android.permissions.PermissionsListener
//import org.maplibre.android.permissions.PermissionsManager
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mapView: MapView
    private lateinit var maplibreMap: MapLibreMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val markers = mutableListOf<Marker>()
    private var permissionsManager: PermissionsManager? = null
 

    companion object {
        private const val USER_LOCATION_SOURCE_ID = "user-location-source"
        private const val USER_LOCATION_LAYER_ID = "user-location-layer"
        private const val ROUTE_SOURCE_ID = "route-source"
        private const val ROUTE_LAYER_ID = "route-layer"
        private const val ROUTE_START_SOURCE_ID = "route-start-source"
        private const val ROUTE_START_LAYER_ID = "route-start-layer"
        private const val ROUTE_END_SOURCE_ID = "route-end-source"
        private const val ROUTE_END_LAYER_ID = "route-end-layer"
        private const val DESTINATION_LAYER_ID = "destination-layer"
        private const val DESTINATION_SOURCE_ID = "destination-source"

        private const val ICON_START_ID = "icon-start"
        private const val ICON_END_ID = "icon-end"
        private const val ICON_DESTINATION_ID = "icon-destination"
    }

    private val routeCoordinates = listOf(
        LatLng(37.422, -122.084),
        LatLng(37.424, -122.082),
        LatLng(37.420, -122.080),
        LatLng(37.418, -122.078)
    )

    private val destination = LatLng(37.418, -122.078)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapView = binding.mapView

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        binding.btnStartNavigation.setOnClickListener {
            startNavigation()
        }

        binding.btnClearRoute.setOnClickListener {
            clearRoute()
        }

        binding.btnLocateMe.setOnClickListener {
            // centerOnUserLocation()
        }
    }

    override fun onMapReady(map: MapLibreMap) {
        this.maplibreMap = map

        map.setStyle(Style.Builder().fromUri("https://americanamap.org/style.json")) { style ->
            addRouteSource(style)
            addRouteLayer(style)
            addMarkersToMap()
            addUserLocationSource(style)
            addUserLocationLayer(style)
            zoomToRoute(map, routeCoordinates)

            if (PermissionsManager.areLocationPermissionsGranted(this)) {
                enableLocationComponent(style)
                enableUserLocation()
            } else {
                permissionsManager = PermissionsManager(this)
                permissionsManager?.requestLocationPermissions(this)
            }
        }

        map.addOnMapClickListener { point ->
            Toast.makeText(
                this,
                "Clicked: ${point.latitude}, ${point.longitude}",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }


    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>) {
        Toast.makeText(this, "This app needs location permissions to show your location.", Toast.LENGTH_LONG).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            maplibreMap.getStyle { style ->
                enableLocationComponent(style)
                enableUserLocation()
            }
        } else {
            Toast.makeText(this, "Location permissions not granted.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(style: Style) {
        val locationComponentOptions = LocationComponentOptions.builder(this)
            .pulseEnabled(true)
            .build()

        val locationComponentActivationOptions =
            LocationComponentActivationOptions.builder(this, style)
                .locationComponentOptions(locationComponentOptions)
                .useDefaultLocationEngine(true)
                .build()

        maplibreMap.locationComponent.apply {
            activateLocationComponent(locationComponentActivationOptions)
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING
            renderMode = RenderMode.COMPASS
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableUserLocation() 
    {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                val initialLatLng: LatLng
                
                if (location != null)
                {
                    initialLatLng = LatLng(location.latitude, location.longitude)
                }
                else if (routeCoordinates.isNotEmpty())
                {
                    initialLatLng = routeCoordinates.first()
                    val startLocation = Location("default-provider").apply {
                        latitude = initialLatLng.latitude
                        longitude = initialLatLng.longitude
                    }
                    maplibreMap.locationComponent.forceLocationUpdate(startLocation)
                }  else {
                    return@addOnSuccessListener
                }

                updateUserLocation(initialLatLng)
                val cameraPosition = CameraPosition.Builder()
                    .target(initialLatLng)
                    .zoom(15.0)
                    .build()
                maplibreMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(cameraPosition),
                    1000
                )
            }
    }

    private fun addUserLocationSource(style: Style) {
        val geoJsonSource = GeoJsonSource(
            USER_LOCATION_SOURCE_ID,
            createUserLocationFeature(LatLng(0.0, 0.0))
        )
        style.addSource(geoJsonSource)
    }

    private fun addUserLocationLayer(style: Style) {
        val circleLayer = CircleLayer(USER_LOCATION_LAYER_ID, USER_LOCATION_SOURCE_ID)
            .withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(Color.BLUE),
                PropertyFactory.circleOpacity(0.8f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        style.addLayer(circleLayer)
    }

    private fun addRouteSource(style: Style) {
        val routeFeature = createRouteFeature(routeCoordinates)
        val routeSource = GeoJsonSource(ROUTE_SOURCE_ID, routeFeature)
        style.addSource(routeSource)
    }

    private fun addRouteLayer(style: Style) {
        val lineLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID)
            .withProperties(
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineColor(Color.parseColor("#3bb2d0")),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        style.addLayer(lineLayer)
    }

    private fun addMarkersToMap() {
        val iconFactory = IconFactory.getInstance(this)

        // Start Icon
        val startIconDrawable = ResourcesCompat.getDrawable(resources, R.drawable.maplibre_marker_icon_default, theme)!!
        val startIcon = iconFactory.fromBitmap(startIconDrawable.toBitmap())

        // End Icon - red tint
        val endIconDrawable = ResourcesCompat.getDrawable(resources, R.drawable.maplibre_marker_icon_default, theme)!!.mutate()
        endIconDrawable.setTint(Color.RED)
        val endIcon = iconFactory.fromBitmap(endIconDrawable.toBitmap())

        // Destination Icon - yellow tint
        val destinationIconDrawable = ResourcesCompat.getDrawable(resources, R.drawable.maplibre_marker_icon_default, theme)!!.mutate()
        destinationIconDrawable.setTint(Color.YELLOW)
        val destinationIcon = iconFactory.fromBitmap(destinationIconDrawable.toBitmap())

        if (routeCoordinates.isNotEmpty()) {
            val startPoint = routeCoordinates.first()
            val startMarkerOptions = MarkerOptions()
                .position(startPoint)
                .title("Start")
                .icon(startIcon)
            maplibreMap.addMarker(startMarkerOptions)?.let { markers.add(it) }
        }

        if (routeCoordinates.size > 1) {
            val endPoint = routeCoordinates.last()
            val endMarkerOptions = MarkerOptions()
                .position(endPoint)
                .title("End")
                .icon(endIcon)
            maplibreMap.addMarker(endMarkerOptions)?.let { markers.add(it) }
        }

        val destinationMarkerOptions = MarkerOptions()
            .position(destination)
            .title("Destination")
            .icon(destinationIcon)
        maplibreMap.addMarker(destinationMarkerOptions)?.let { markers.add(it) }
    }

    private fun updateUserLocation(latLng: LatLng) {
        val style = maplibreMap.style
        style?.let {
            val source = it.getSourceAs<GeoJsonSource>(USER_LOCATION_SOURCE_ID)
            source?.setGeoJson(createUserLocationFeature(latLng))
            val distance = calculateDistance(latLng, destination)
            binding.tvDistance.text = String.format("Distance to destination: %.2f km", distance)
        }
    }

    private fun startNavigation() {
        Toast.makeText(this, "Starting navigation", Toast.LENGTH_SHORT).show()
        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
            .includes(routeCoordinates)
            .build()

        maplibreMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, 100),
            1000
        )

        val totalDistance = calculateRouteDistance(routeCoordinates)
        binding.tvRouteInfo.text = String.format("Total route distance: %.2f km", totalDistance)
    }

    private fun clearRoute() {
        val style = maplibreMap.style
        style?.let {
            it.removeLayer(ROUTE_LAYER_ID)
            it.removeSource(ROUTE_SOURCE_ID)
        }

        markers.forEach { maplibreMap.removeMarker(it) }
        markers.clear()

        binding.tvRouteInfo.text = "Route cleared"
    }


    private fun centerOnUserLocation() 
    {
        maplibreMap.locationComponent.lastKnownLocation?.let { location ->
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(location.latitude, location.longitude))
                .zoom(16.0)
                .build()

            maplibreMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                500
            )
        }
    }

    private fun zoomToRoute(map: MapLibreMap, coordinates: List<LatLng>) {
        if (coordinates.isEmpty()) {
            return
        }

        val boundsBuilder = LatLngBounds.Builder()
        for (coordinate in coordinates) {
            boundsBuilder.include(coordinate)
        }
        val bounds = boundsBuilder.build()

        map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 1000)
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val lat1 = Math.toRadians(point1.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon2 = Math.toRadians(point2.longitude)

        val dlon = lon2 - lon1
        val dlat = lat2 - lat1
        val a = sin(dlat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dlon / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return 6371.0 * c
    }

    private fun calculateRouteDistance(route: List<LatLng>): Double {
        var totalDistance = 0.0
        for (i in 0 until route.size - 1) {
            totalDistance += calculateDistance(route[i], route[i + 1])
        }
        return totalDistance
    }

    private fun createUserLocationFeature(latLng: LatLng): String {
        return """
        {
            "type": "Feature",
            "geometry": {
                "type": "Point",
                "coordinates": [${latLng.longitude}, ${latLng.latitude}]
            }
        }
        """.trimIndent()
    }

    private fun createRouteFeature(coordinates: List<LatLng>): String {
        val points = coordinates.map { Point.fromLngLat(it.longitude, it.latitude) }
        val lineString = LineString.fromLngLats(points)
        return lineString.toJson()
    }

    private fun createPointFeature(latLng: LatLng, type: String, title: String): String {
        return """
        {
            "type": "Feature",
            "properties": {
                "type": "$type",
                "title": "$title"
            },
            "geometry": {
                "type": "Point",
                "coordinates": [${latLng.longitude}, ${latLng.latitude}]
            }
        }
        """.trimIndent()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }
    
}