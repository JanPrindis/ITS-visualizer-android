package com.honz.itsvisualizer.cards

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.honz.itsvisualizer.R
import utils.storage.data.Maneuver
import utils.storage.data.Mapem
import utils.storage.data.MovementEvent
import utils.storage.data.MovementState
import utils.storage.data.SignalGroupCompact
import kotlin.math.abs

class MapemCard(private val mapem: Mapem, private val signalGroup: Int) : Fragment() {

    private lateinit var title: TextView

    // Left column
    private lateinit var iconLeft: ImageView
    private lateinit var directionLeft: ImageView

    private lateinit var detailsWrapperLeft: LinearLayout
    private lateinit var stateLeft: TextView
    private lateinit var minWrapperLeft: LinearLayout
    private lateinit var minLeft: TextView
    private lateinit var maxWrapperLeft: LinearLayout
    private lateinit var maxLeft: TextView
    private lateinit var likelyWrapperLeft: LinearLayout
    private lateinit var likelyLeft: TextView
    private lateinit var confidenceWrapperLeft: LinearLayout
    private lateinit var confidenceLeft: TextView

    // Center column
    private lateinit var iconCenter: ImageView
    private lateinit var directionCenter: ImageView

    private lateinit var detailsWrapperCenter: LinearLayout
    private lateinit var stateCenter: TextView
    private lateinit var minWrapperCenter: LinearLayout
    private lateinit var minCenter: TextView
    private lateinit var maxWrapperCenter: LinearLayout
    private lateinit var maxCenter: TextView
    private lateinit var likelyWrapperCenter: LinearLayout
    private lateinit var likelyCenter: TextView
    private lateinit var confidenceWrapperCenter: LinearLayout
    private lateinit var confidenceCenter: TextView

    // Right column
    private lateinit var iconRight: ImageView
    private lateinit var directionRight: ImageView

    private lateinit var detailsWrapperRight: LinearLayout
    private lateinit var stateRight: TextView
    private lateinit var minWrapperRight: LinearLayout
    private lateinit var minRight: TextView
    private lateinit var maxWrapperRight: LinearLayout
    private lateinit var maxRight: TextView
    private lateinit var likelyWrapperRight: LinearLayout
    private lateinit var likelyRight: TextView
    private lateinit var confidenceWrapperRight: LinearLayout
    private lateinit var confidenceRight: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_mapem_card, container, false)

        title = view.findViewById(R.id.mapem_title)

        // Left
        iconLeft = view.findViewById(R.id.mapem_left_icon)
        directionLeft = view.findViewById(R.id.mapem_left_direction)

        detailsWrapperLeft = view.findViewById(R.id.mapem_left_details)
        stateLeft = view.findViewById(R.id.mapem_left_state)
        minWrapperLeft = view.findViewById(R.id.mapem_left_min_wrapper)
        minLeft = view.findViewById(R.id.mapem_left_min_value)
        maxWrapperLeft = view.findViewById(R.id.mapem_left_max_wrapper)
        maxLeft = view.findViewById(R.id.mapem_left_max_value)
        likelyWrapperLeft = view.findViewById(R.id.mapem_left_likely_wrapper)
        likelyLeft = view.findViewById(R.id.mapem_left_likely_value)
        confidenceWrapperLeft = view.findViewById(R.id.mapem_left_confidence_wrapper)
        confidenceLeft = view.findViewById(R.id.mapem_left_confidence_value)

        // Center
        iconCenter = view.findViewById(R.id.mapem_center_icon)
        directionCenter = view.findViewById(R.id.mapem_center_direction)

        detailsWrapperCenter = view.findViewById(R.id.mapem_center_details)
        stateCenter = view.findViewById(R.id.mapem_center_state)
        minWrapperCenter = view.findViewById(R.id.mapem_center_min_wrapper)
        minCenter = view.findViewById(R.id.mapem_center_min_value)
        maxWrapperCenter = view.findViewById(R.id.mapem_center_max_wrapper)
        maxCenter = view.findViewById(R.id.mapem_center_max_value)
        likelyWrapperCenter = view.findViewById(R.id.mapem_center_likely_wrapper)
        likelyCenter = view.findViewById(R.id.mapem_center_likely_value)
        confidenceWrapperCenter = view.findViewById(R.id.mapem_center_confidence_wrapper)
        confidenceCenter = view.findViewById(R.id.mapem_center_confidence_value)

        // Right
        iconRight = view.findViewById(R.id.mapem_right_icon)
        directionRight = view.findViewById(R.id.mapem_right_direction)

        detailsWrapperRight = view.findViewById(R.id.mapem_right_details)
        stateRight = view.findViewById(R.id.mapem_right_state)
        minWrapperRight = view.findViewById(R.id.mapem_right_min_wrapper)
        minRight = view.findViewById(R.id.mapem_right_min_value)
        maxWrapperRight = view.findViewById(R.id.mapem_right_max_wrapper)
        maxRight = view.findViewById(R.id.mapem_right_max_value)
        likelyWrapperRight = view.findViewById(R.id.mapem_right_likely_wrapper)
        likelyRight = view.findViewById(R.id.mapem_right_likely_value)
        confidenceWrapperRight = view.findViewById(R.id.mapem_right_confidence_wrapper)
        confidenceRight = view.findViewById(R.id.mapem_right_confidence_value)

        updateValues(mapem, signalGroup)

        return view
    }

    private fun updateValues(mapem: Mapem, signalGroup: Int) {
        // This should never fail, it is protected in Visualizer class, but just to be sure
        val base = mapem.signalGroups.find { it.signalGroup == signalGroup } ?: mapem.signalGroups.first()
        val filteredSignalGroups = findGroupsWithSimilarAngle(base, mapem.signalGroups)

        // Title
        val intersectionName = mapem.intersectionName
        if(intersectionName.isEmpty())
            "Intersection ID: ${mapem.intersectionID}".also { title.text = it }
        else
            title.text = intersectionName

        val firstGroup = filteredSignalGroups.getOrNull(0)
        val secondGroup = filteredSignalGroups.getOrNull(1)
        val thirdGroup = filteredSignalGroups.getOrNull(2)

        // Left column
        if(firstGroup == null) {
            iconLeft.visibility = View.GONE
            directionLeft.visibility = View.GONE
            detailsWrapperLeft.visibility = View.GONE
        }
        else {
            iconLeft.visibility = View.VISIBLE
            directionLeft.visibility = View.VISIBLE
            detailsWrapperLeft.visibility = View.VISIBLE

            val spatem = mapem.latestSpatem?.movementStates?.find { it.signalGroup == firstGroup.signalGroup }
            val movementEvent = spatem?.movementEvents?.first()

            // Icon
            iconLeft.setImageResource(getIconDrawable(spatem))

            // Direction
            directionLeft.setImageResource(getDirectionDrawable(firstGroup))


            // State
            if(movementEvent == null) {
                "No data yet".also { stateLeft.text = it }
            }
            else if(movementEvent.getStateString().isEmpty()) {
                "No data".also { stateLeft.text = it }
            }
            else {
                stateLeft.text = movementEvent.getStateString()
            }

            val startTime = movementEvent?.startTime

            // Min time
            if(startTime == null || movementEvent.minEndTime == null)
                minWrapperLeft.visibility = View.GONE
            else {
                minWrapperLeft.visibility = View.VISIBLE
                minLeft.text = convertTimeFormatToString(movementEvent.minEndTime - startTime)
            }

            // Max time
            if(startTime == null || movementEvent.maxEndTime == null)
                maxWrapperLeft.visibility = View.GONE
            else {
                maxWrapperLeft.visibility = View.VISIBLE
                maxLeft.text = convertTimeFormatToString(movementEvent.maxEndTime - startTime)
            }

            // Likely time
            if(startTime == null || movementEvent.likelyTime == null)
                likelyWrapperLeft.visibility = View.GONE
            else {
                likelyWrapperLeft.visibility = View.VISIBLE
                likelyLeft.text = convertTimeFormatToString(movementEvent.likelyTime - startTime)
            }

            // Confidence
            if(movementEvent?.confidence == null)
                confidenceWrapperLeft.visibility = View.GONE
            else {
                confidenceWrapperLeft.visibility = View.VISIBLE
                "${movementEvent.confidence}%".also { confidenceLeft.text = it }
            }
        }

        // Center column
        if(secondGroup == null) {
            iconCenter.visibility = View.GONE
            directionCenter.visibility = View.GONE
            detailsWrapperCenter.visibility = View.GONE
        }
        else {
            iconCenter.visibility = View.VISIBLE
            directionCenter.visibility = View.VISIBLE
            detailsWrapperCenter.visibility = View.VISIBLE

            val spatem = mapem.latestSpatem?.movementStates?.find { it.signalGroup == secondGroup.signalGroup }
            val movementEvent = spatem?.movementEvents?.first()

            // Icon
            iconCenter.setImageResource(getIconDrawable(spatem))

            // Direction
            directionCenter.setImageResource(getDirectionDrawable(secondGroup))


            // State
            if(movementEvent == null) {
                "No data yet".also { stateCenter.text = it }
            }
            else if(movementEvent.getStateString().isEmpty()) {
                "No data".also { stateCenter.text = it }
            }
            else {
                stateCenter.text = movementEvent.getStateString()
            }

            val startTime = movementEvent?.startTime

            // Min time
            if(startTime == null || movementEvent.minEndTime == null)
                minWrapperCenter.visibility = View.GONE
            else {
                minWrapperCenter.visibility = View.VISIBLE
                minCenter.text = convertTimeFormatToString(movementEvent.minEndTime - startTime)
            }

            // Max time
            if(startTime == null || movementEvent.maxEndTime == null)
                maxWrapperCenter.visibility = View.GONE
            else {
                maxWrapperCenter.visibility = View.VISIBLE
                maxCenter.text = convertTimeFormatToString(movementEvent.maxEndTime - startTime)
            }

            // Likely time
            if(startTime == null || movementEvent.likelyTime == null)
                likelyWrapperCenter.visibility = View.GONE
            else {
                likelyWrapperCenter.visibility = View.VISIBLE
                likelyCenter.text = convertTimeFormatToString(movementEvent.likelyTime - startTime)
            }

            // Confidence
            if(movementEvent?.confidence == null)
                confidenceWrapperCenter.visibility = View.GONE
            else {
                confidenceWrapperCenter.visibility = View.VISIBLE
                "${movementEvent.confidence}%".also { confidenceCenter.text = it }
            }
        }

        // Right column
        if(thirdGroup == null) {
            iconRight.visibility = View.GONE
            directionRight.visibility = View.GONE
            detailsWrapperRight.visibility = View.GONE
        } else {
            iconRight.visibility = View.VISIBLE
            directionRight.visibility = View.VISIBLE
            detailsWrapperRight.visibility = View.VISIBLE

            val spatem = mapem.latestSpatem?.movementStates?.find { it.signalGroup == thirdGroup.signalGroup }
            val movementEvent = spatem?.movementEvents?.first()

            // Icon
            iconRight.setImageResource(getIconDrawable(spatem))

            // Direction
            directionRight.setImageResource(getDirectionDrawable(thirdGroup))


            // State
            if(movementEvent == null) {
                "No data yet".also { stateRight.text = it }
            }
            else if(movementEvent.getStateString().isEmpty()) {
                "No data".also { stateRight.text = it }
            }
            else {
                stateRight.text = movementEvent.getStateString()
            }

            val startTime = movementEvent?.startTime

            // Min time
            if(startTime == null || movementEvent.minEndTime == null)
                minWrapperRight.visibility = View.GONE
            else {
                minWrapperRight.visibility = View.VISIBLE
                minRight.text = convertTimeFormatToString(movementEvent.minEndTime - startTime)
            }

            // Max time
            if(startTime == null || movementEvent.maxEndTime == null)
                maxWrapperRight.visibility = View.GONE
            else {
                maxWrapperRight.visibility = View.VISIBLE
                maxRight.text = convertTimeFormatToString(movementEvent.maxEndTime - startTime)
            }

            // Likely time
            if(startTime == null || movementEvent.likelyTime == null)
                likelyWrapperRight.visibility = View.GONE
            else {
                likelyWrapperRight.visibility = View.VISIBLE
                likelyRight.text = convertTimeFormatToString(movementEvent.likelyTime - startTime)
            }

            // Confidence
            if(movementEvent?.confidence == null)
                confidenceWrapperRight.visibility = View.GONE
            else {
                confidenceWrapperRight.visibility = View.VISIBLE
                "${movementEvent.confidence}%".also { confidenceRight.text = it }
            }
        }
    }

    private fun convertTimeFormatToString(timeVal: Int): String {
        val seconds = timeVal / 10
        val tenths = timeVal % 10
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60

        return String.format("%02d:%02d.%d", minutes, remainingSeconds, tenths)
    }

    private fun findGroupsWithSimilarAngle(baseGroup: SignalGroupCompact, signalGroups: List<SignalGroupCompact>) : List<SignalGroupCompact> {
        val maxAngleOffset = 30.0
        val result = mutableListOf<SignalGroupCompact>()
        result.add(baseGroup)

        for (group in signalGroups) {
            if(group == baseGroup) continue
            val angleOffset = group.angle - baseGroup.angle

            if(abs(angleOffset) <= maxAngleOffset)
                result.add(group)
        }

        return result.sortedBy { it.maneuversAllowed }
    }

    private fun getIconDrawable(state: MovementState?) : Int {
        return when(state?.movementEvents?.first()?.getStateColor()) {
            MovementEvent.Companion.StateColor.RED -> R.drawable.signal_big_red
            MovementEvent.Companion.StateColor.AMBER -> R.drawable.signal_big_yellow
            MovementEvent.Companion.StateColor.RED_AMBER -> R.drawable.signal_big_red_yellow
            MovementEvent.Companion.StateColor.GREEN -> R.drawable.signal_big_green
            else -> R.drawable.signal_big_off
        }
    }

    private fun getDirectionDrawable(group: SignalGroupCompact) : Int {
        return when(group.maneuversAllowed) {
            Maneuver.LEFT -> R.drawable.direction_left
            Maneuver.LEFT_STRAIGHT -> R.drawable.direction_forward_left
            Maneuver.STRAIGHT -> R.drawable.direction_forward
            Maneuver.RIGHT_STRAIGHT -> R.drawable.direction_forward_right
            Maneuver.RIGHT -> R.drawable.direction_right
            Maneuver.LEFT_RIGHT -> R.drawable.direction_left_right
            else -> R.drawable.direction_none
        }
    }
}