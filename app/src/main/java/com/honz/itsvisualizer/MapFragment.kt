package com.honz.itsvisualizer

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
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
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.eq
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.get
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.literal
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.FillExtrusionLayer
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
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.TripSessionState
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.speedlimit.api.MapboxSpeedInfoApi
import com.mapbox.navigation.ui.speedlimit.view.MapboxSpeedInfoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import utils.storage.MessageStorage
import utils.storage.TestingData
import utils.visualization.Visualizer
import utils.visualization.VisualizerInstance

object LatestGPSLocation {
    var location: Location? = null
}

class MapFragment : Fragment() {

    // Navigation lifecycle handler
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                MapboxNavigationApp.attach(owner)
            }

            override fun onStart(owner: LifecycleOwner) {
                MapboxNavigationApp.attach(owner)
            }

            override fun onStop(owner: LifecycleOwner) {
                MapboxNavigationApp.detach(owner)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                MapboxNavigationApp.detach(owner)
            }
        })
    }

    // UI elements
    private lateinit var mapView: MapView
    private lateinit var connectionToggleFab: FloatingActionButton
    private lateinit var cameraCenteringToggleFab: FloatingActionButton
    private lateinit var deleteFab: FloatingActionButton
    private lateinit var detailsCard: MaterialCardView
    private lateinit var speedInfoView: MapboxSpeedInfoView

    // Speed limit API
    private lateinit var distanceFormatterOptions: DistanceFormatterOptions
    private lateinit var speedInfoApi: MapboxSpeedInfoApi

    // For UI tests
    private lateinit var testingFab: FloatingActionButton

    // Navigation
    private lateinit var mapboxNavigation: MapboxNavigation
    private val navigationLocationProvider = NavigationLocationProvider()

    // Camera tracking GPS
    private var centerCamera = true

    // For external camera track source
    private var externalCameraTracking = false
    private var oldCenterCamera = false
    private var externalCameraTrackingCancelled = false

    // Settings
    private var mapThemeIndex = 0
    private var displayBuildingExteriors = false
    private var buildingExteriorOpacity = 0.5f
    private var units = 0
    private var cameraFaceNorth = false
    private var cameraTrackUserSelected = true
    private var cameraReturnToGpsTrack = true
    private var cameraDefaultZoom = 18.0f

    private var uiTestingEnabled = false

    private var userDeniedLocationPermission = false

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

            val value = speedInfoApi.updatePostedAndCurrentSpeed(locationMatcherResult, distanceFormatterOptions)
            speedInfoView.render(value)
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
            VisualizerInstance.visualizer?.removeCurrentFocused(closeDetailsTab = true, userCanceled = true)
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
        val sharedPreferences = requireActivity().applicationContext.getSharedPreferences("Settings", Context.MODE_PRIVATE)

        mapThemeIndex = sharedPreferences.getInt("mapThemeIndex", 0)
        displayBuildingExteriors = sharedPreferences.getBoolean("displayBuildingExteriors", false)
        buildingExteriorOpacity = sharedPreferences.getFloat("buildingExteriorsOpacity", 0.5f)
        units = sharedPreferences.getInt("mapUnitsIndex", 0)
        cameraFaceNorth = sharedPreferences.getBoolean("cameraFaceNorth", false)
        cameraTrackUserSelected = sharedPreferences.getBoolean("cameraTrackUserSelected", true)
        cameraReturnToGpsTrack = sharedPreferences.getBoolean("cameraReturnToGpsTrackSelected", true)
        cameraDefaultZoom = sharedPreferences.getFloat("cameraDefaultZoom", 18.0f)

        uiTestingEnabled = sharedPreferences.getBoolean("uiTestEnabled", false)

        // Connection toggle FAB
        connectionToggleFab = view.findViewById(R.id.connectionToggleFab)
        connectionToggleFab.setOnClickListener { toggleConnection() }

        // Delete FAB
        deleteFab = view.findViewById(R.id.deleteAllFab)
        deleteFab.setOnClickListener {
            CoroutineScope(Dispatchers.Default).launch {
                MessageStorage.clearStorage()
            }
        }

        // Testing FAB
        testingFab = view.findViewById(R.id.testFab)
        testingFab.setOnClickListener{ addTestingData() }
        testingFab.visibility = if(uiTestingEnabled) View.VISIBLE else View.GONE

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

        // Speed limits
        speedInfoView = view.findViewById(R.id.speedInfoView)

        // Camera position update if available
        LatestGPSLocation.location?.let { updateCameraPosition(it) }

        // Visualization
        detailsCard = view.findViewById(R.id.detailsCard)
        VisualizerInstance.visualizer = Visualizer(view.context.applicationContext, mapView, detailsCard, childFragmentManager)

        if(cameraTrackUserSelected) {
            VisualizerInstance.visualizer?.setOnTrackedPositionChangedListener {
                // Tracking is off
                if (it == null) {
                    externalCameraTracking = false

                    if(cameraReturnToGpsTrack)
                        setCameraCentering(true)

                    if (oldCenterCamera && !externalCameraTrackingCancelled)
                        setCameraCentering(true)

                    externalCameraTrackingCancelled = false

                    return@setOnTrackedPositionChangedListener
                }
                // User decided to cancel tracking
                if (externalCameraTrackingCancelled) {
                    if(cameraReturnToGpsTrack)
                        setCameraCentering(true)

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
        }

        lifecycleScope.launch {
            MessageStorage.drawAll()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Navigation initialization can be only called, when the fragment is created and attached, so it can access Application context
        initNavigation()

        requireMapboxNavigation(
            onResumedObserver = object : MapboxNavigationObserver {
                override fun onAttached(mapboxNavigation: MapboxNavigation) {
                    this@MapFragment.mapboxNavigation = mapboxNavigation
                    if(!userDeniedLocationPermission)
                        handleLocationPermissions()
                }

                override fun onDetached(mapboxNavigation: MapboxNavigation) {
                    mapboxNavigation.unregisterLocationObserver(locationObserver)
                }
            },
        )
    }

    /**
     * Function checks if location permissions are granted, if they are, turn on trip session, if not, ask user
     */
    private fun handleLocationPermissions() {
        if(userDeniedLocationPermission) return
        mapboxNavigation.registerLocationObserver(locationObserver)

        // Check if location permissions allowed
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Ask user for permission
            requestLocationPermissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )

        } else {
            // Granted
            if (mapboxNavigation.getTripSessionState() == TripSessionState.STOPPED) {
                mapboxNavigation.startTripSession()
            } else {
                LatestGPSLocation.location?.let { updateCameraPosition(it) }
            }
        }
    }

    /**
     * Permission result, if user denied, shows warning message box informing user, that location tracking will not be available,
     * if they are granted, it re-runs handleLocationPermissions function, to turn on trip session
     */
    private val requestLocationPermissions =
        this.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.all { it.value }

            if (!allPermissionsGranted) {
                userDeniedLocationPermission = true
                // Permissions denied
                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.perm_disabled_title)
                    .setMessage(R.string.perm_disabled_text)
                    .setNeutralButton(R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .show()

                setCameraCentering(false)
                cameraCenteringToggleFab.isEnabled = false
            }
            else {
                // Permissions granted
                userDeniedLocationPermission = false
                handleLocationPermissions()
            }
        }

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

        val style = when(mapThemeIndex) {
            0 -> Style.TRAFFIC_DAY
            1 -> Style.TRAFFIC_NIGHT
            else -> Style.TRAFFIC_DAY
        }

        mapView.getMapboxMap().loadStyleUri(style) {
            if(displayBuildingExteriors)
                setupBuildings(it)
        }
        mapView.getMapboxMap().addOnMoveListener(onMoveListener)
        mapView.getMapboxMap().addOnMapClickListener(onMapClickListener)
        mapView.logo.updateSettings { marginRight = 80.0f }
        mapView.scalebar.updateSettings { ratio = 0.25f }
        mapView.compass.updateSettings { marginTop = 100.0f }
    }

    /**
     * Adds a 3D building layer to map
     */
    private fun setupBuildings(style: Style) {
        val color = when(mapThemeIndex) {
            0 -> R.color.map_building_extrusion_color_light
            1 -> R.color.map_building_extrusion_color_dark
            else -> R.color.map_building_extrusion_color_light
        }

        val fillExtrusionLayer = FillExtrusionLayer("3d-buildings", "composite")
        fillExtrusionLayer.sourceLayer("building")
        fillExtrusionLayer.filter(eq(get("extrude"), literal("true")))
        fillExtrusionLayer.minZoom(15.0)
        fillExtrusionLayer.fillExtrusionColor(Color.parseColor(getString(color)))
        fillExtrusionLayer.fillExtrusionHeight(get("height"))
        fillExtrusionLayer.fillExtrusionBase(get("min_height"))
        fillExtrusionLayer.fillExtrusionOpacity(buildingExteriorOpacity.toDouble())
        fillExtrusionLayer.fillExtrusionAmbientOcclusionIntensity(0.3)
        fillExtrusionLayer.fillExtrusionAmbientOcclusionRadius(3.0)
        fillExtrusionLayer.fillExtrusionVerticalGradient(true)
        style.addLayer(fillExtrusionLayer)
    }

    /**
     * Sets up the location provider and location puck image
     */
    private fun initNavigation() {
        if (!MapboxNavigationApp.isSetup()) {
            if(!isAdded) return

            val context = requireActivity().applicationContext
            MapboxNavigationApp.setup {
                NavigationOptions.Builder(context)
                    .accessToken(getString(R.string.mapbox_access_token))
                    .build()
            }.attach(this@MapFragment)
        }

        // Init speed limit API
        speedInfoApi = MapboxSpeedInfoApi()
        distanceFormatterOptions = DistanceFormatterOptions.Builder(requireContext())
            .unitType(
                if(units == 0) UnitType.METRIC
                else UnitType.IMPERIAL
            )
            .build()

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

        if(VisualizerInstance.visualizer?.detailsCardOpened == true) {
            xOffset = mapView.width * 0.25f
        }

        if(externalSource) {
            mapView.camera.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(location.longitude, location.latitude))
                    .bearing(0.0)
                    .zoom(cameraDefaultZoom.toDouble())
                    .pitch(0.0)
                    .padding(EdgeInsets(0.0, xOffset.toDouble(), 0.0, 0.0))
                    .build(),
                mapAnimationOptions
            )
        }
        else {
            val bearing = if(cameraFaceNorth) 0.0 else location.bearing.toDouble()
            val pitch = if(cameraFaceNorth) 0.0 else 45.0
            val yOffset = if(cameraFaceNorth) 0.0f else mapView.height * 0.5f

            mapView.camera.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(location.longitude, location.latitude))
                    .bearing(bearing)
                    .zoom(cameraDefaultZoom.toDouble())
                    .pitch(pitch)
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

    /**
     * Adds testing data on map and moves camera near it.
     * This function is used only for UI testing and might be removed later.
     */
    private fun addTestingData() {
        setCameraCentering(false)

        val l = Location("Custom")
        l.latitude = 49.83548118939259
        l.longitude = 18.15842231267649
        l.altitude = 0.0
        updateCameraPosition(l, true)

        runBlocking {
            MessageStorage.add(TestingData.testCam)
            MessageStorage.add(TestingData.testDenm)
            MessageStorage.add(TestingData.testSrem)
            MessageStorage.add(TestingData.testSsem)
            MessageStorage.add(TestingData.testMapem)
            MessageStorage.add(TestingData.testSpatem)

            // Its here twice, so it updates the annotation
            MessageStorage.add(TestingData.testCam)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapboxNavigation.unregisterLocationObserver(locationObserver)

        VisualizerInstance.visualizer?.destroy()
        VisualizerInstance.visualizer = null

        MapboxNavigationApp.detach(this) // Possible redundant

        externalCameraTracking = false
        externalCameraTrackingCancelled = false
        centerCamera = true
    }

    override fun onDestroy() {
        super.onDestroy()
        MapboxNavigationApp.disable()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(stateReceiver)
    }
}