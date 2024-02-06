package utils.visualization

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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

object VisualizerInstance {
    var visualizer: Visualizer? = null
}

class Visualizer(
    fragmentContext: Context,
    mapView: MapView,
    private val detailsCard: MaterialCardView,
    private val fragmentManager: FragmentManager
) {

    private val fragmentContextRef: WeakReference<Context> = WeakReference(fragmentContext)
    private val context: Context?
        get() = fragmentContextRef.get()

    private val annotationApi = mapView.annotations

    // Items will be drawn in the same order the managers were created (latest created -> on top)
    private val camLineAnnotationManager = annotationApi.createPolylineAnnotationManager()
    private val denmLineAnnotationManager = annotationApi.createPolylineAnnotationManager()
    private val mapemLineAnnotationManager = annotationApi.createPolylineAnnotationManager()

    private val camPointAnnotationManager = annotationApi.createPointAnnotationManager()
    private val denmPointAnnotationManager = annotationApi.createPointAnnotationManager()
    private val mapemPointAnnotationManager = annotationApi.createPointAnnotationManager()

    private val pointList: MutableMap<Long, PointAnnotation> = mutableMapOf()
    private val lineList: MutableMap<Long, PolylineAnnotation> = mutableMapOf()

    private var focused: Message? = null
    private val gson = Gson()

    var detailsCardOpened = false
    private var displayedDetailsCard: Fragment? = null
    private var lastSelectedSignalGroup: Int? = null

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
            val existingPoint = pointList[cam.stationID]

            val currentFocused = focused
            if (existingPoint != null) {
                if(currentFocused is Cam && currentFocused.stationID == cam.stationID) {
                    setFocused(cam)
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
                pointList[cam.stationID] = newPoint

                camPointAnnotationManager.iconPadding = 10.0
                camPointAnnotationManager.addClickListener(OnPointAnnotationClickListener {
                    val restoredCam = gson.fromJson(it.getData(), Cam::class.java)
                    setFocused(restoredCam)

                    true
                })
            }
        }
    }

    fun removeCam(cam: Cam) {
        if(focused == cam)
            removeFocused(cam, true)

        val point = pointList[cam.stationID] ?: return
        camPointAnnotationManager.delete(point)
        pointList.remove(cam.stationID)
    }

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

        val id = "${denm.stationID}${denm.sequenceNumber}".toLong()

        bitmapFromDrawableRes(icon)?.let { bitmap ->
            val existingPoint = pointList[id]

            val currentFocused = focused
            if (existingPoint != null) {
                if( currentFocused is Denm &&
                    currentFocused.stationID == denm.stationID &&
                    currentFocused.sequenceNumber == denm.sequenceNumber) {
                    setFocused(denm)
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
                    setFocused(restoredDenm)

                    true
                })
            }
        }
    }

    fun removeDenm(denm: Denm) {
        if(focused == denm)
            removeFocused(denm, true)

        val id = "${denm.stationID}${denm.sequenceNumber}".toLong()
        val point = pointList[id] ?: return
        denmPointAnnotationManager.delete(point)
        pointList.remove(id)
    }

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
                val existingPoint = pointList[signal.id]

                val currentFocused = focused
                if (existingPoint != null) {
                    if(currentFocused is Mapem && currentFocused.intersectionID == mapem.intersectionID) {
                        setFocused(mapem)
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
                    pointList[signal.id] = newPoint

                    mapemPointAnnotationManager.iconPadding = 10.0
                    mapemPointAnnotationManager.addClickListener(OnPointAnnotationClickListener {
                        val restoredMapem = gson.fromJson(it.getData(), Mapem::class.java)
                        lastSelectedSignalGroup = restoredMapem.visualizerSignalGroupID
                        setFocused(restoredMapem)

                        true
                    })
                }
            }
        }
    }

    fun removeMapem(mapem: Mapem) {
        if(focused == mapem)
            removeFocused(mapem, true)

        for (id in mapem.currentIconIDs) {
            val point = pointList[id] ?: return
            mapemPointAnnotationManager.delete(point)
            pointList.remove(id)
        }

        lastSelectedSignalGroup = null
        mapem.currentIconIDs.clear()
    }

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

    private fun setFocused(cam: Cam) {
        removeCurrentFocused(false)
        focused = cam

        val existingLine = lineList[cam.stationID]

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
            lineList[cam.stationID] = newLine
        }

        val transaction = fragmentManager.beginTransaction()
        val fragment = CamCard(cam)
        transaction.replace(R.id.detailsContainer, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        setDetailsCardState(DetailsCardState.OPEN)

        displayedDetailsCard = fragment
    }

    private fun removeFocused(cam: Cam, closeDetailsTab: Boolean) {
        val line = lineList[cam.stationID] ?: return
        camLineAnnotationManager.delete(line)
        lineList.remove(cam.stationID)

        focused = null
        if(closeDetailsTab) setDetailsCardState(DetailsCardState.CLOSE)
    }

    private fun setFocused(denm: Denm) {
        removeCurrentFocused(false)
        focused = denm

        val c = context ?: return
        val colorInt = ContextCompat.getColor(c, R.color.map_line_denm)
        val colorHexString = String.format("#%06X", 0xFFFFFF and colorInt)

        for(i in denm.calculatedPathHistory.indices) {

            val path = denm.calculatedPathHistory[i]
            val id = "${denm.stationID}${denm.sequenceNumber}${i}".toLong()
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

        val transaction = fragmentManager.beginTransaction()
        val fragment = DenmCard(denm)
        transaction.replace(R.id.detailsContainer, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        setDetailsCardState(DetailsCardState.OPEN)

        displayedDetailsCard = fragment
    }

    private fun removeFocused(denm: Denm, closeDetailsTab: Boolean) {

        for(i in denm.calculatedPathHistory.indices) {
            val id = "${denm.stationID}${denm.sequenceNumber}${i}".toLong()

            val line = lineList[id] ?: return
            denmLineAnnotationManager.delete(line)
            lineList.remove(id)
        }

        focused = null
        if(closeDetailsTab) setDetailsCardState(DetailsCardState.CLOSE)
    }

    private fun setFocused(mapem: Mapem) {

        val selected = lastSelectedSignalGroup ?: return
        if(mapem.signalGroups.find { it.signalGroup == lastSelectedSignalGroup } == null) return

        removeCurrentFocused(false)
        focused = mapem

        val transaction = fragmentManager.beginTransaction()
        val fragment = MapemCard(mapem, selected)
        transaction.replace(R.id.detailsContainer, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        setDetailsCardState(DetailsCardState.OPEN)

        displayedDetailsCard = fragment
    }

    private fun removeFocused(mapem: Mapem, closeDetailsTab: Boolean) {
        focused = null
        if(closeDetailsTab) {
            lastSelectedSignalGroup = null
            setDetailsCardState(DetailsCardState.CLOSE)
        }
    }

    fun removeCurrentFocused(closeDetailsTab: Boolean) {
        when(val message = focused) {
            is Cam -> removeFocused(message, closeDetailsTab)
            is Denm -> removeFocused(message, closeDetailsTab)
            is Mapem -> removeFocused(message, closeDetailsTab)
            null -> return
            else -> {
                focused = null
                setDetailsCardState(DetailsCardState.CLOSE)
            }
        }
    }

    enum class DetailsCardState {
        OPEN,
        CLOSE,
        TOGGLE
    }
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

        Handler(Looper.getMainLooper()).post {
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
                    val transaction = fragmentManager.beginTransaction()
                    displayedDetailsCard?.let { fragment -> transaction.remove(fragment) }
                    transaction.commit()
                }
                animator.start()
            }
        }
    }

    private fun positionListToPointList(positionList: List<Position>) : List<Point> {
        val points = mutableListOf<Point>()

        for(position in positionList) {
            points.add(positionToPoint(position))
        }

        return points
    }

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
}