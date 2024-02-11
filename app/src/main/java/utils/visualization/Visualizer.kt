package utils.visualization

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.honz.itsvisualizer.LatestGPSLocation
import com.honz.itsvisualizer.R
import com.honz.itsvisualizer.cards.CamCard
import com.honz.itsvisualizer.cards.DenmCard
import com.honz.itsvisualizer.cards.MapemCard
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import utils.storage.data.*
import java.lang.ref.WeakReference
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object VisualizerInstance {
    var visualizer: Visualizer? = null
}

class Visualizer(
    fragmentContext: Context,
    mapView: MapView,
    private val detailsCard: MaterialCardView,
    private val fragmentManager: FragmentManager
) {
    private var isBeingDestroyed = false
    private val fragmentContextRef: WeakReference<Context> = WeakReference(fragmentContext)
    private val context: Context?
        get() = fragmentContextRef.get()

    private val annotationApi = mapView.annotations

    // Items will be drawn in the same order the managers were created (latest created -> on top)
    private val camLineAnnotationManager = annotationApi.createPolylineAnnotationManager()
    private val denmLineAnnotationManager = annotationApi.createPolylineAnnotationManager()
    private val mapemLineAnnotationManager = annotationApi.createPolylineAnnotationManager()

    private val camPointAnnotationManager = annotationApi.createPointAnnotationManager()
    private val mapemPointAnnotationManager = annotationApi.createPointAnnotationManager()
    private val denmPointAnnotationManager = annotationApi.createPointAnnotationManager()

    private val pointList: MutableMap<String, PointAnnotation> = mutableMapOf()
    private val lineList: MutableMap<String, PolylineAnnotation> = mutableMapOf()

    private val gson = Gson()

    var detailsCardOpened = false
    private var displayedDetailsCard: Fragment? = null

    private val handler = Handler(Looper.getMainLooper())
    private val fragmentLock = Any()

    private var focused: Message? = null
    private var focusedDistance: Double? = null
    private var isFocusedByUser = false

    // Settings
    private val sharedPreferences = context?.getSharedPreferences("Settings", Context.MODE_PRIVATE)
    private val mapemPriority = sharedPreferences?.getInt("autoPriorityIndex", 0) == 0
    private val displayDenmNotifications = sharedPreferences?.getBoolean("autoShowDenm", true) ?: true
    private val displayMapemNotifications = sharedPreferences?.getBoolean("autoShowMapem", true) ?: true
    private val drawMapemGeometry = sharedPreferences?.getBoolean("showMapemGeometry", false) ?: false
    private val autoNotificationsAudioEnabled = sharedPreferences?.getBoolean("autoAudioEnabled", false) ?: false

    private var lastSelectedSignalGroup: Int? = null

    companion object {
        /**
         * If message beyond this distance (in meters) it is considered too far, and removed
         */
        const val AUTO_NOTIFICATION_MAX_DIST = 150.0

        /**
         * If intersection origin is closer than distance (in meters) it is set as visited
         */
        const val AUTO_NOTIFICATION_INTERSECTION_VISITED_DIST = 25.0

        /**
         * If intersection is set as visited and is beyond this distance (in meters) it is considered too far, and removed
         */
        const val AUTO_NOTIFICATION_INTERSECTION_REMOVE_VISITED_DIST = 30.0
    }

    // User selected message position
    private var onTrackedPositionChangedListener: ((Position?) -> Unit)? = null
    fun setOnTrackedPositionChangedListener(listener: (Position?) -> Unit) {
        onTrackedPositionChangedListener = listener
    }

    fun removeOnTrackedPositionChangedListener() {
        onTrackedPositionChangedListener = null
    }

    private fun onTrackedPositionChanged(newValue: Position?) {
        onTrackedPositionChangedListener?.invoke(newValue)
    }

    /**
     * Adds a CAM annotation to map
     */
    fun drawCam(cam: Cam) {
        val position = cam.originPosition ?: return
        val icon = when(cam.stationType) {
            1 -> R.drawable.pedestrian_icon
            2 -> R.drawable.cyclist_icon
            3 -> R.drawable.moped_icon
            4 -> R.drawable.motorbike_icon
            5 -> R.drawable.car_icon
            6 -> R.drawable.bus_icon
            7 -> R.drawable.light_truck_icon
            8 -> R.drawable.heavy_truck_icon
            10 -> R.drawable.ambulance_icon // Special Vehicle?
            11 -> R.drawable.tram_icon
            15 -> R.drawable.roadside_unit_icon
            else -> R.drawable.unknown_icon
        }

        val json = gson.toJsonTree(cam)

        bitmapFromDrawableRes(icon)?.let { bitmap ->
            val existingPoint = pointList["${cam.stationID}"]

            val currentFocused = focused
            if (existingPoint != null) {
                if(currentFocused is Cam && currentFocused.stationID == cam.stationID) {
                    setFocused(cam, true)
                }

                // Update existing point
                existingPoint.point = Point.fromLngLat(position.lon, position.lat)
                existingPoint.iconImageBitmap = bitmap
                existingPoint.setData(json)
                camPointAnnotationManager.update(existingPoint)
            } else {
                // Create new point
                val newPointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(position.lon, position.lat))
                    .withIconImage(bitmap)
                    .withData(json)

                val newPoint = camPointAnnotationManager.create(newPointAnnotationOptions)
                pointList["${cam.stationID}"] = newPoint

                camPointAnnotationManager.iconPadding = 10.0
                camPointAnnotationManager.addClickListener(OnPointAnnotationClickListener {
                    val restoredCam = gson.fromJson(it.getData(), Cam::class.java)

                    synchronized(fragmentLock) {isFocusedByUser = true}
                    setFocused(restoredCam)

                    true
                })
            }
        }
    }

    /**
     * Removes CAM annotation
     */
    fun removeCam(cam: Cam) {
        val currentFocused = focused
        if(currentFocused is Cam && currentFocused.stationID == cam.stationID)
            removeFocused(cam, true)

        val point = pointList["${cam.stationID}"] ?: return
        camPointAnnotationManager.delete(point)
        pointList.remove("${cam.stationID}")
    }

    /**
     * Adds a DENM annotation to map
     */
    fun drawDenm(denm: Denm) {
        val position = denm.originPosition ?: return

        val icon = when(denm.causeCode) {
            0 -> R.drawable.denm_general
            1 -> R.drawable.denm_traffic
            2 -> R.drawable.denm_accident
            3 -> R.drawable.denm_roadwork
            6 -> R.drawable.denm_weather
            9 -> R.drawable.denm_road_condition
            10 -> R.drawable.denm_road_obstacle
            11 -> R.drawable.denm_road_animal
            12 -> R.drawable.denm_road_human
            14 -> R.drawable.denm_car
            15 -> R.drawable.denm_emergency
            17 -> R.drawable.denm_weather
            19 -> R.drawable.denm_weather
            26 -> R.drawable.denm_car
            27 -> R.drawable.denm_traffic
            91 -> R.drawable.denm_breakdown
            92 -> R.drawable.denm_accident
            93 -> R.drawable.denm_human_problem
            94 -> R.drawable.denm_car
            95 -> R.drawable.denm_emergency
            96 -> R.drawable.denm_dangerous_curve
            97 -> R.drawable.denm_car
            98 -> R.drawable.denm_car
            99 -> R.drawable.denm_general
            else -> R.drawable.denm_general
        }

        val json = gson.toJsonTree(denm)

        val id = "${denm.stationID}${denm.sequenceNumber}"

        bitmapFromDrawableRes(icon)?.let { bitmap ->
            val existingPoint = pointList[id]

            val currentFocused = focused
            if (existingPoint != null) {
                if( currentFocused is Denm &&
                    currentFocused.stationID == denm.stationID &&
                    currentFocused.sequenceNumber == denm.sequenceNumber) {
                    setFocused(denm, true)
                }

                // Update existing point
                existingPoint.point = Point.fromLngLat(position.lon, position.lat)
                existingPoint.iconImageBitmap = bitmap
                existingPoint.setData(json)
                denmPointAnnotationManager.update(existingPoint)
            } else {
                // Create new point
                val newPointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(position.lon, position.lat))
                    .withIconImage(bitmap)
                    .withData(json)

                val newPoint = denmPointAnnotationManager.create(newPointAnnotationOptions)
                pointList[id] = newPoint

                denmPointAnnotationManager.iconPadding = 10.0
                denmPointAnnotationManager.addClickListener(OnPointAnnotationClickListener {
                    val restoredDenm = gson.fromJson(it.getData(), Denm::class.java)

                    synchronized(fragmentLock) {isFocusedByUser = true}
                    setFocused(restoredDenm)

                    true
                })
            }
        }

        // Check if it should be displayed automatically
        displayPriorityNotification(denm)
    }

    /**
     * Removes DENM annotation
     */
    fun removeDenm(denm: Denm) {
        val currentFocused = focused
        if( currentFocused is Denm &&
            currentFocused.stationID == denm.stationID &&
            currentFocused.sequenceNumber == denm.sequenceNumber)
            removeFocused(denm, true)

        val id = "${denm.stationID}${denm.sequenceNumber}"
        val point = pointList[id] ?: return
        denmPointAnnotationManager.delete(point)
        pointList.remove(id)
    }

    /**
     * Adds a MAPEM annotation to map
     */
    fun drawMapem(mapem: Mapem) {
        for (signal in mapem.signalGroups) {
            val spatem = mapem.latestSpatem?.movementStates?.find { it.signalGroup == signal.signalGroup }

            val signalIcon = when (signal.maneuversAllowed) {
                Maneuver.LEFT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    MovementEvent.Companion.StateColor.RED -> R.drawable.traffic_light_red_left_icon
                    MovementEvent.Companion.StateColor.AMBER -> R.drawable.traffic_light_yellow_left_icon
                    MovementEvent.Companion.StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_left_icon
                    MovementEvent.Companion.StateColor.GREEN -> R.drawable.traffic_light_green_left_icon
                    else -> R.drawable.traffic_light_blank_left_icon
                }
                Maneuver.LEFT_STRAIGHT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    MovementEvent.Companion.StateColor.RED -> R.drawable.traffic_light_red_straight_left_icon
                    MovementEvent.Companion.StateColor.AMBER -> R.drawable.traffic_light_yellow_straight_left_icon
                    MovementEvent.Companion.StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_straight_left_icon
                    MovementEvent.Companion.StateColor.GREEN -> R.drawable.traffic_light_green_straight_left_icon
                    else -> R.drawable.traffic_light_blank_straight_left_icon
                }
                Maneuver.STRAIGHT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    MovementEvent.Companion.StateColor.RED -> R.drawable.traffic_light_red_straight_icon
                    MovementEvent.Companion.StateColor.AMBER -> R.drawable.traffic_light_yellow_straight_icon
                    MovementEvent.Companion.StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_straight_icon
                    MovementEvent.Companion.StateColor.GREEN -> R.drawable.traffic_light_green_straight_icon
                    else -> R.drawable.traffic_light_blank_straight_icon
                }
                Maneuver.RIGHT_STRAIGHT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    MovementEvent.Companion.StateColor.RED -> R.drawable.traffic_light_red_straight_right_icon
                    MovementEvent.Companion.StateColor.AMBER -> R.drawable.traffic_light_yellow_straight_right_icon
                    MovementEvent.Companion.StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_straight_right_icon
                    MovementEvent.Companion.StateColor.GREEN -> R.drawable.traffic_light_green_straight_right_icon
                    else -> R.drawable.traffic_light_blank_straight_right_icon
                }
                Maneuver.RIGHT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    MovementEvent.Companion.StateColor.RED -> R.drawable.traffic_light_red_right_icon
                    MovementEvent.Companion.StateColor.AMBER -> R.drawable.traffic_light_yellow_right_icon
                    MovementEvent.Companion.StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_right_icon
                    MovementEvent.Companion.StateColor.GREEN -> R.drawable.traffic_light_green_right_icon
                    else -> R.drawable.traffic_light_blank_right_icon
                }
                Maneuver.LEFT_RIGHT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    MovementEvent.Companion.StateColor.RED -> R.drawable.traffic_light_red_left_right_icon
                    MovementEvent.Companion.StateColor.AMBER -> R.drawable.traffic_light_yellow_left_right_icon
                    MovementEvent.Companion.StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_left_right_icon
                    MovementEvent.Companion.StateColor.GREEN -> R.drawable.traffic_light_green_left_right_icon
                    else -> R.drawable.traffic_light_blank_left_right_icon
                }
                else -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    MovementEvent.Companion.StateColor.RED -> R.drawable.traffic_light_red_icon
                    MovementEvent.Companion.StateColor.AMBER -> R.drawable.traffic_light_yellow_icon
                    MovementEvent.Companion.StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_icon
                    MovementEvent.Companion.StateColor.GREEN -> R.drawable.traffic_light_green_icon
                    else -> R.drawable.traffic_light_blank_icon
                }
            }

            mapem.visualizerSignalGroupID = signal.signalGroup
            val json = gson.toJsonTree(mapem)

            mapem.currentIconIDs.add(signal.id)

            bitmapFromDrawableRes(signalIcon)?.let { bitmap ->
                val existingPoint = pointList["${signal.id}"]

                val currentFocused = focused
                if (existingPoint != null) {
                    if(currentFocused is Mapem && currentFocused.intersectionID == mapem.intersectionID) {
                        setFocused(mapem, true)
                    }

                    // Update existing point
                    existingPoint.point = Point.fromLngLat(signal.position.lon, signal.position.lat)
                    existingPoint.iconImageBitmap = bitmap
                    existingPoint.setData(json)
                    mapemPointAnnotationManager.update(existingPoint)
                } else {
                    // Create new point
                    val newPointAnnotationOptions = PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(signal.position.lon, signal.position.lat))
                        .withIconImage(bitmap)
                        .withData(json)

                    val newPoint = mapemPointAnnotationManager.create(newPointAnnotationOptions)
                    pointList["${signal.id}"] = newPoint

                    mapemPointAnnotationManager.iconPadding = 10.0
                    mapemPointAnnotationManager.addClickListener(OnPointAnnotationClickListener {
                        val restoredMapem = gson.fromJson(it.getData(), Mapem::class.java)
                        lastSelectedSignalGroup = restoredMapem.visualizerSignalGroupID

                        synchronized(fragmentLock) {isFocusedByUser = true}
                        setFocused(restoredMapem)

                        true
                    })
                }
            }
        }

        // Check if it should be displayed automatically
        displayPriorityNotification(mapem)
    }

    /**
     * Removes MAPEM annotation
     */
    fun removeMapem(mapem: Mapem) {
        if(focused == mapem)
            removeFocused(mapem, true)

        for (id in mapem.currentIconIDs) {
            val point = pointList["$id"] ?: return
            mapemPointAnnotationManager.delete(point)
            pointList.remove("$id")
        }

        lastSelectedSignalGroup = null
        mapem.currentIconIDs.clear()
    }

    /**
     * Removes all annotations
     */
    fun removeAllMarkers() {
        removeCurrentFocused(true)

        camPointAnnotationManager.deleteAll()
        camLineAnnotationManager.deleteAll()

        denmPointAnnotationManager.deleteAll()
        denmLineAnnotationManager.deleteAll()

        mapemPointAnnotationManager.deleteAll()
        mapemLineAnnotationManager.deleteAll()

        pointList.clear()
        lineList.clear()
    }

    /**
     * Draws CAM path history and displays details card
     */
    private fun setFocused(cam: Cam, isSame: Boolean = false) {
        if(!isSame) removeCurrentFocused(false)
        synchronized(fragmentLock) {
            focused = cam

            // Draw line
            val existingLine = lineList["${cam.stationID}"]
            val c = context ?: return
            val colorInt = ContextCompat.getColor(c, R.color.map_line_cam)
            val colorHexString = String.format("#%06X", 0xFFFFFF and colorInt)

            if (existingLine != null) {
                // Update existing line
                existingLine.points = positionListToPointList(cam.calculatedPathHistory)
                existingLine.lineColorString = colorHexString
                camLineAnnotationManager.update(existingLine)
            } else {
                // Create new line
                val newPolyLineAnnotationOptions = PolylineAnnotationOptions()
                    .withPoints(positionListToPointList(cam.calculatedPathHistory))
                    .withLineColor(colorHexString)
                    .withLineOpacity(0.5)
                    .withLineWidth(10.0)

                val newLine = camLineAnnotationManager.create(newPolyLineAnnotationOptions)
                lineList["${cam.stationID}"] = newLine
            }

            // Show fragment
            val displayedCard = displayedDetailsCard
            if (displayedCard is CamCard) {
                handler.post {
                    displayedCard.updateValues(cam)
                }
            }
            else {
                handler.post {
                    val camCard = CamCard(cam)
                    updateFragment(camCard)
                }
            }

            if(isFocusedByUser)
                onTrackedPositionChanged(cam.originPosition)
        }
    }

    /**
     * Removes CAM path history from map and optionally closes the details card
     */
    private fun removeFocused(cam: Cam, closeDetailsTab: Boolean) {
        val line = lineList["${cam.stationID}"] ?: return

        synchronized(fragmentLock) {
            camLineAnnotationManager.delete(line)
            lineList.remove("${cam.stationID}")

            focused = null

            if (closeDetailsTab)
                handler.post { setDetailsCardState(DetailsCardState.CLOSE) }
        }
    }

    /**
     * Highlights affected path by DENM and displays details card
     */
    private fun setFocused(denm: Denm, isSame: Boolean = false) {
        if(!isSame) removeCurrentFocused(false)
        synchronized(fragmentLock) {
            focused = denm

            // Draw line
            val c = context ?: return
            val colorInt = ContextCompat.getColor(c, R.color.map_line_denm)
            val colorHexString = String.format("#%06X", 0xFFFFFF and colorInt)

            for (i in denm.calculatedPathHistory.indices) {

                val path = denm.calculatedPathHistory[i]
                val id = "${denm.stationID}${denm.sequenceNumber}${i}"
                val existingLine = lineList[id]

                if (existingLine != null) {
                    // Update existing line
                    existingLine.points = positionListToPointList(path)
                    existingLine.lineColorString = colorHexString
                    denmLineAnnotationManager.update(existingLine)
                } else {
                    // Create new line
                    val newPolyLineAnnotationOptions = PolylineAnnotationOptions()
                        .withPoints(positionListToPointList(path))
                        .withLineColor(colorHexString)
                        .withLineOpacity(0.5)
                        .withLineWidth(10.0)

                    val newLine = denmLineAnnotationManager.create(newPolyLineAnnotationOptions)
                    lineList[id] = newLine
                }
            }

            // Show fragment
            val displayedCard = displayedDetailsCard
            if (displayedCard is DenmCard) {
                handler.post {
                    displayedCard.updateValues(denm)
                }
            }
            else {
                handler.post {
                    val denmCard = DenmCard(denm)
                    updateFragment(denmCard)
                }
            }

            if(isFocusedByUser)
                onTrackedPositionChanged(denm.originPosition)
        }
    }

    /**
     * Removes affected path from map and optionally closes the details card
     */
    private fun removeFocused(denm: Denm, closeDetailsTab: Boolean) {
        synchronized(fragmentLock) {
            for (i in denm.calculatedPathHistory.indices) {
                val id = "${denm.stationID}${denm.sequenceNumber}${i}"

                val line = lineList[id] ?: return
                denmLineAnnotationManager.delete(line)
                lineList.remove(id)
            }

            focused = null
            if (closeDetailsTab)
                handler.post { setDetailsCardState(DetailsCardState.CLOSE) }
        }
    }

    /**
     * Displays detail card containing either user selected signal group, or signal group closest to current GPS heading
     */
    private fun setFocused(mapem: Mapem, isSame: Boolean = false) {
        val selected = lastSelectedSignalGroup
        if(!isSame) removeCurrentFocused(false)

        synchronized(fragmentLock) {
            focused = mapem

            // Draw intersection geometry
            if(drawMapemGeometry && !isSame) {
                for (lane in mapem.lanes) {
                    val id = "${mapem.intersectionID}${lane.laneID}"
                    val existingLine = lineList[id]

                    // Draw only if new, don't redraw every time
                    if(existingLine == null) {
                        val c = context ?: return
                        val colorInt = when(lane.type) {
                            0 -> ContextCompat.getColor(c, R.color.map_line_mapem_vehicle)
                            1 -> ContextCompat.getColor(c, R.color.map_line_mapem_crosswalk)
                            2 -> ContextCompat.getColor(c, R.color.map_line_mapem_bike_lane)
                            3 -> ContextCompat.getColor(c, R.color.map_line_mapem_sidewalk)
                            4 -> ContextCompat.getColor(c, R.color.map_line_mapem_median)
                            5 -> ContextCompat.getColor(c, R.color.map_line_mapem_striping)
                            6 -> ContextCompat.getColor(c, R.color.map_line_mapem_tracked_vehicle)
                            7 -> ContextCompat.getColor(c, R.color.map_line_mapem_parking)
                            else -> ContextCompat.getColor(c, R.color.map_line_mapem_unknown)
                        }
                        val colorHexString = String.format("#%06X", 0xFFFFFF and colorInt)

                        // Create new line
                        val newPolyLineAnnotationOptions = PolylineAnnotationOptions()
                            .withPoints(positionListToPointList(lane.calculatedLaneOffset))
                            .withLineColor(colorHexString)
                            .withLineOpacity(0.5)
                            .withLineWidth(10.0)

                        val newLine = mapemLineAnnotationManager.create(newPolyLineAnnotationOptions)
                        lineList[id] = newLine
                    }
                }
            }

            // Show fragment
            val displayedCard = displayedDetailsCard
            if (displayedCard is MapemCard) {
                handler.post {
                    if(isFocusedByUser)
                        displayedCard.updateValues(mapem, selected)
                    else
                        displayedCard.updateValues(mapem, null)
                }
            }
            else {
                handler.post {
                    val mapemCard = if(isFocusedByUser)
                        MapemCard(mapem, selected)
                    else
                        MapemCard(mapem, null)
                    updateFragment(mapemCard)
                }
            }

            if(isFocusedByUser)
                onTrackedPositionChanged(mapem.originPosition)
        }
    }

    /**
     * Removes MAPEM card, optionally closes the details card
     */
    private fun removeFocused(mapem: Mapem, closeDetailsTab: Boolean) {
        synchronized(fragmentLock) {
            focused = null
            if (closeDetailsTab) {
                lastSelectedSignalGroup = null
                handler.post { setDetailsCardState(DetailsCardState.CLOSE) }
            }

            for(lane in mapem.lanes) {
                val id = "${mapem.intersectionID}${lane.laneID}"
                val existingLine = lineList[id] ?: continue

                mapemLineAnnotationManager.delete(existingLine)
                lineList.remove(id)
            }
        }
    }


    /**
     * Removes current focused card, optionally closes the details card
     */
    fun removeCurrentFocused(closeDetailsTab: Boolean, userCanceled: Boolean = false) {
        when(val message = focused) {
            is Cam -> removeFocused(message, closeDetailsTab)
            is Denm -> {
                if(userCanceled)
                    message.autoNotifyCanceled = true
                removeFocused(message, closeDetailsTab)
            }
            is Mapem -> {
                if(userCanceled)
                    message.autoNotifyCanceled = true
                removeFocused(message, closeDetailsTab)
            }
            null -> return
            else -> {
                synchronized(fragmentLock) {
                    focused = null
                    handler.post {
                        setDetailsCardState(DetailsCardState.CLOSE)
                    }
                }
            }
        }
    }


    /**
     * Changes the currently displayed card for fragment passed as parameter
     */
    private fun updateFragment(fragment: Fragment) {
        if (!fragmentManager.isStateSaved) {
            val transaction = fragmentManager.beginTransaction()
            transaction.replace(R.id.detailsContainer, fragment)
            transaction.addToBackStack(null)
            transaction.commit()
            setDetailsCardState(DetailsCardState.OPEN)

            displayedDetailsCard = fragment
        }
    }

    enum class DetailsCardState {
        OPEN,
        CLOSE,
        TOGGLE
    }

    /**
     * Sets the details card state and animates opening/closing
     */
    private fun setDetailsCardState(set: DetailsCardState) {

        detailsCardOpened = when (set) {
            DetailsCardState.OPEN -> {
                if(detailsCardOpened) return
                true
            }

            DetailsCardState.CLOSE -> {
                if(!detailsCardOpened) return
                false
            }
            DetailsCardState.TOGGLE -> !detailsCardOpened
        }

        if (detailsCardOpened) {
            val initialX = -detailsCard.width.toFloat()
            val finalX = 0f

            val animator = ObjectAnimator.ofFloat(detailsCard, "translationX", initialX, finalX)
            animator.duration = 500

            animator.doOnStart {
                detailsCard.visibility = View.VISIBLE
            }
            animator.start()
        } else {
            val initialX = 0f
            val finalX = -detailsCard.width.toFloat()

            val animator = ObjectAnimator.ofFloat(detailsCard, "translationX", initialX, finalX)
            animator.duration = 500
            animator.doOnEnd {
                detailsCard.visibility = View.INVISIBLE
                displayedDetailsCard = null
                focused = null
                focusedDistance = null
                isFocusedByUser = false
                onTrackedPositionChanged(null)

                if(!fragmentManager.isStateSaved) {
                    val transaction = fragmentManager.beginTransaction()
                    displayedDetailsCard?.let { fragment -> transaction.remove(fragment) }
                    transaction.commit()
                }
            }
            animator.start()
        }
    }

    /**
     * Translates list of Position to list of Point
     */
    private fun positionListToPointList(positionList: List<Position>) : List<Point> {
        val points = mutableListOf<Point>()

        for(position in positionList) {
            points.add(positionToPoint(position))
        }

        return points
    }

    /**
     * Translates Position object to Point object
     */
    private fun positionToPoint(position: Position) : Point {
        return Point.fromLngLat(position.lon, position.lat)
    }

    private fun bitmapFromDrawableRes(@DrawableRes resourceId: Int) =
        convertDrawableToBitmap(context?.let { AppCompatResources.getDrawable(it, resourceId) })

    private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
        if (sourceDrawable == null) {
            return null
        }
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
            // copying drawable object to not manipulate on the same reference
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            val bitmap: Bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth, drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

    /**
     * Calculates the distance between two points in meters
     */
    private fun calculateHaversineDistance(pos1: Position?, pos2: Location?) : Double? {
        pos2 ?: return null
        return calculateHaversineDistance(pos1, Position(pos2.latitude, pos2.longitude, pos2.altitude))
    }

    /**
     * Calculates the distance between two points in meters
     */
    private fun calculateHaversineDistance(pos1: Position?, pos2: Position?) : Double? {

        if(pos1 == null || pos2 == null) return null

        val earthR = 6371.0 // Earth radius in kilometers

        val dLat = Math.toRadians(pos2.lat - pos1.lat)
        val dLon = Math.toRadians(pos2.lon - pos1.lon)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(pos1.lat)) * cos(Math.toRadians(pos2.lat)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthR * c * 1000.0  // In meters
    }

    /**
     * Handles automatic displaying of notifications (details card) on screen
     */
    private fun displayPriorityNotification(message: Message) {
        // Focused messages by user have priority over auto
        if (isFocusedByUser) return

        // Check if current message type is allowed
        if(message is Denm && !displayDenmNotifications) return
        if(message is Mapem && !displayMapemNotifications) return

        // Calculate distance to message origin
        val newDistance = calculateHaversineDistance(message.originPosition, LatestGPSLocation.location) ?: return
        val oldDistance = focusedDistance

        // Check if already focused
        val currentFocused = focused
        if (message is Denm &&
            currentFocused is Denm &&
            currentFocused.stationID == message.stationID &&
            currentFocused.messageID == message.messageID) {

            // If DENM too far, remove
            if(newDistance > AUTO_NOTIFICATION_MAX_DIST) {
                focusedDistance = null
                removeFocused(message, true)
            }
            else {
                focusedDistance = newDistance
                setFocused(message)
            }
            return
        }
        else if (message is Mapem &&
            currentFocused is Mapem &&
            currentFocused.intersectionID == message.intersectionID) {

            focusedDistance = newDistance

            // Check if intersection is not too far or visited and out of range
            if (newDistance > AUTO_NOTIFICATION_MAX_DIST ||
                (message.visited && newDistance >= AUTO_NOTIFICATION_INTERSECTION_REMOVE_VISITED_DIST)) {
                focusedDistance = null
                removeFocused(message, true)
            }
            else {
                // If close to intersection, set intersection as visited
                if(newDistance <= AUTO_NOTIFICATION_INTERSECTION_VISITED_DIST)
                    message.visited = true
                setFocused(message)
            }
            return
        }

        // If above cutoff, ignore
        if(newDistance > AUTO_NOTIFICATION_MAX_DIST) return

        // Decide if message should be focused
        if(message is Denm) {

            //Message previously cancelled by user
            if(message.autoNotifyCanceled) return

            // Nothing is currently displayed
            if (oldDistance == null) {
                // Play audio
                if(autoNotificationsAudioEnabled) {
                    if (context != null && mediaPlayer == null)
                        mediaPlayer = MediaPlayer.create(context, R.raw.notification_audio)

                    handler.post { mediaPlayer?.start() }
                }

                focusedDistance = newDistance
                setFocused(message)
            }

            // MAPEM is displayed and has priority
            else if (currentFocused is Mapem && mapemPriority) return

            // DENM is displayed and is closer than current
            else if (currentFocused is Denm && oldDistance < newDistance) return

            // Display
            else {
                // Play audio
                if(autoNotificationsAudioEnabled) {
                    if (context != null && mediaPlayer == null)
                        mediaPlayer = MediaPlayer.create(context, R.raw.notification_audio)

                    handler.post { mediaPlayer?.start() }
                }

                focusedDistance = newDistance
                setFocused(message)
            }
        }
        else if (message is Mapem) {
            //Message previously cancelled by user
            if(message.autoNotifyCanceled) return

            // MAPEM already visited
            if(message.visited && newDistance > AUTO_NOTIFICATION_INTERSECTION_REMOVE_VISITED_DIST) return

            // Nothing is currently displayed
            if(oldDistance == null) {
                // Play audio
                if(autoNotificationsAudioEnabled) {
                    if (context != null && mediaPlayer == null)
                        mediaPlayer = MediaPlayer.create(context, R.raw.notification_audio)

                    handler.post { mediaPlayer?.start() }
                }

                focusedDistance = newDistance
                setFocused(message)
            }

            // DENM is displayed and has priority
            else if(currentFocused is Denm && !mapemPriority) return

            // DENM is displayed and is closer than current
            else if(currentFocused is Mapem && oldDistance < newDistance) return

            // Display
            else {
                // Play audio
                if(autoNotificationsAudioEnabled) {
                    if (context != null && mediaPlayer == null)
                        mediaPlayer = MediaPlayer.create(context, R.raw.notification_audio)

                    handler.post { mediaPlayer?.start() }
                }

                focusedDistance = newDistance
                setFocused(message)
            }
        }
    }
}