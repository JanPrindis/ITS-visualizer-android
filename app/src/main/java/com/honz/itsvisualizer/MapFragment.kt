package com.honz.itsvisualizer

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMoveListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import kotlinx.coroutines.launch
import utils.storage.MessageStorage
import utils.visualization.Visualizer
import utils.visualization.VisualizerInstance

object LatestGPSLocation {
    var location: Location? = null
}

class MapFragment : Fragment() {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                MapboxNavigationApp.attach(owner)
            }

            override fun onPause(owner: LifecycleOwner) {
                MapboxNavigationApp.detach(owner)
            }
        })
    }

    private lateinit var mapView: MapView
    private lateinit var connectionToggleFab: FloatingActionButton
    private lateinit var cameraCenteringToggleFab: FloatingActionButton

    private lateinit var detailsCard: MaterialCardView

    private var isTripSessionStarted = false
    private var centerCamera = true

    private val navigationLocationProvider = NavigationLocationProvider()

    // For external camera track source
    private var externalCameraTracking = false
    private var oldCenterCamera = false
    private var externalCameraTrackingCancelled = false

    /**
     * locationObserver passes new location data to update camera position
     */
    private val locationObserver = object: LocationObserver {
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            navigationLocationProvider.changePosition(
                enhancedLocation,
                locationMatcherResult.keyPoints
            )

            LatestGPSLocation.location = enhancedLocation
            if(!externalCameraTracking || externalCameraTrackingCancelled)
                updateCameraPosition(enhancedLocation)
        }

        // Not implemented
        override fun onNewRawLocation(rawLocation: Location) {}
    }

    /**
     * OnMoveListener that disables camera centering after moves the map
     */
    private val onMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {

            // If user moves the map while current GPS position is tracked
            if(centerCamera)
                setCameraCentering(false)

            // If user moves the map while external target is tracked
            if(externalCameraTracking) {
                externalCameraTrackingCancelled = true
                oldCenterCamera = false
            }
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {}
    }

    private val onMapClickListener = OnMapClickListener {
        if(VisualizerInstance.visualizer?.detailsCardOpened == true) {
            VisualizerInstance.visualizer?.removeCurrentFocused(true)
            true
        } else false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Signals from SocketService
        val stateFilter = IntentFilter("itsVisualizer.SERVICE_STATE")
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(stateReceiver, stateFilter)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_map, container, false)


        // Connection toggle FAB
        connectionToggleFab = view.findViewById(R.id.connectionToggleFab)
        connectionToggleFab.setOnClickListener { toggleConnection() }

        // Update icon based on current state
        val intent = Intent("itsVisualizer.SOCKET_SERVICE_STATE_REQUEST")
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)

        // Camera centering FAB
        cameraCenteringToggleFab = view.findViewById(R.id.cameraCenteringToggleFab)
        cameraCenteringToggleFab.setOnClickListener {
            if(externalCameraTracking)
                externalCameraTrackingCancelled = true
            setCameraCentering(null)
        }

        // Init mapbox
        mapView = view.findViewById(R.id.mapView)
        initMap()

        // Location init
        initNavigation()

        // Camera position update if available
        LatestGPSLocation.location?.let { updateCameraPosition(it) }

        // Visualization
        detailsCard = view.findViewById(R.id.detailsCard)

        VisualizerInstance.visualizer = Visualizer(view.context.applicationContext, mapView, detailsCard, childFragmentManager)
        VisualizerInstance.visualizer?.setOnTrackedPositionChangedListener {
            // Tracking is off
            if(it == null) {
                externalCameraTracking = false

                if(oldCenterCamera && !externalCameraTrackingCancelled)
                    setCameraCentering(true)

                externalCameraTrackingCancelled = false

                return@setOnTrackedPositionChangedListener
            }
            // User decided to cancel tracking
            if(externalCameraTrackingCancelled) {
                return@setOnTrackedPositionChangedListener
            }

            externalCameraTracking = true
            oldCenterCamera = centerCamera

            setCameraCentering(false)

            val loc = Location("Custom")
            loc.latitude = it.lat
            loc.longitude = it.lon

            updateCameraPosition(loc, true)
        }
        lifecycleScope.launch {
            MessageStorage.drawAll()
        }

        mapView.getMapboxMap().addOnMapClickListener(onMapClickListener)

        return view
    }

    /**
     * Mapbox navigation element that matches current position to nearest road
     */
    private val mapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.registerLocationObserver(locationObserver)

                // Check if user granted location permissions
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // Permissions denied, show a message to the user
                    AlertDialog.Builder(activity)
                        .setTitle(R.string.perm_disabled_title)
                        .setMessage(R.string.perm_disabled_text)
                        .setNeutralButton(R.string.cancel) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setCancelable(false)
                        .show()

                    setCameraCentering(false)
                    cameraCenteringToggleFab.isEnabled = false
                    return
                }

                if (!isTripSessionStarted) {
                    mapboxNavigation.startTripSession()
                    isTripSessionStarted = true
                }
                else {
                    LatestGPSLocation.location?.let { updateCameraPosition(it) }
                }

            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterLocationObserver(locationObserver)
            }
        },
    )

    /**
     * Gets boolean representing current state of socket to update FAB image
     */
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            when(intent?.getBooleanExtra("socketState", true)) {
                true -> connectionToggleFab.setImageResource(R.drawable.wifi)
                false -> connectionToggleFab.setImageResource(R.drawable.wifi_off)
                null -> {}
            }
        }
    }

    /**
     * Sets up the Mapbox mapView element
     */
    private fun initMap() {
        mapView.getMapboxMap().loadStyleUri(Style.TRAFFIC_DAY)
        mapView.getMapboxMap().addOnMoveListener(onMoveListener)
        mapView.logo.updateSettings { marginRight = 80.0f }
        mapView.scalebar.updateSettings { ratio = 0.25f }
        mapView.compass.updateSettings { marginTop = 100.0f }
    }

    /**
     * Sets up the location provider and location puck image
     */
    private fun initNavigation() {

        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup {
                NavigationOptions.Builder(requireActivity().applicationContext)
                    .accessToken(getString(R.string.mapbox_access_token))
                    .build()
            }
        }

        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            locationPuck = LocationPuck2D(
                bearingImage = AppCompatResources.getDrawable(
                    requireActivity().applicationContext,
                    com.mapbox.navigation.ui.maps.R.drawable.mapbox_navigation_puck_icon2
                ),
                shadowImage = AppCompatResources.getDrawable(
                    requireActivity().applicationContext,
                    com.mapbox.navigation.ui.maps.R.drawable.mapbox_navigation_puck_icon2_shadow
                ),
            )

            enabled = true
        }
    }

    /**
     * Eases the camera to position provided in 'location' parameter
     */
     private fun updateCameraPosition(location: Location, externalSource: Boolean = false) {
        if(!centerCamera && !externalSource) return

        val mapAnimationOptions =
            MapAnimationOptions.Builder()
                .duration(1000L)
                .build()

        var xOffset = 0.0f
        val yOffset = mapView.height * 0.5f

        if(VisualizerInstance.visualizer?.detailsCardOpened == true) {
            xOffset = mapView.width * 0.25f
        }

        if(externalSource) {
            mapView.camera.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(location.longitude, location.latitude))
                    .bearing(0.0)
                    .zoom(18.0)
                    .pitch(0.0)
                    .padding(EdgeInsets(0.0, xOffset.toDouble(), 0.0, 0.0))
                    .build(),
                mapAnimationOptions
            )
        }
        else {
            mapView.camera.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(location.longitude, location.latitude))
                    .bearing(location.bearing.toDouble())
                    .zoom(18.0)
                    .pitch(45.0)
                    .padding(EdgeInsets(yOffset.toDouble(), xOffset.toDouble(), 0.0, 0.0))
                    .build(),
                mapAnimationOptions
            )
        }
    }

    /**
     * Controls if camera follows current location.
     * Passing 'null' as a parameter toggles current state.
     */
    private fun setCameraCentering(enabled: Boolean?) {
        centerCamera = enabled ?: !centerCamera

        if(centerCamera) {
            cameraCenteringToggleFab.setImageResource(R.drawable.location)
            LatestGPSLocation.location?.let { updateCameraPosition(it) }
        }
        else {
            cameraCenteringToggleFab.setImageResource(R.drawable.location_off)
        }
    }

    /**
     * Toggles the current state of socket connection
     */
    private fun toggleConnection() {
        val intent = Intent("itsVisualizer.TOGGLE_SOCKET_SERVICE")
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        VisualizerInstance.visualizer?.removeAllMarkers()
        VisualizerInstance.visualizer?.removeOnTrackedPositionChangedListener()
        VisualizerInstance.visualizer = null
        MapboxNavigationApp.detach(this)

        externalCameraTracking = false
        externalCameraTrackingCancelled = false
        centerCamera = true
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(stateReceiver)
    }
}