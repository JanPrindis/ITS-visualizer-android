package com.honz.itsvisualizer.cards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.honz.itsvisualizer.R
import utils.storage.data.Cam

class CamCard(private val cam: Cam) : Fragment() {

    private var initialized = false

    private lateinit var title: TextView
    private lateinit var icon: ImageView
    private lateinit var leftBlinker: ImageView
    private lateinit var rightBlinker: ImageView
    private lateinit var lowBeams: ImageView
    private lateinit var highBeams: ImageView
    private lateinit var speed: TextView
    private lateinit var heading: TextView
    private lateinit var dimensions: TextView
    private lateinit var role: TextView
    private lateinit var latestDENM: TextView
    private lateinit var latestSREM: TextView
    private lateinit var latestSSEM: TextView
    private lateinit var speedWrapper: LinearLayout
    private lateinit var headingWrapper: LinearLayout
    private lateinit var dimensionsWrapper: LinearLayout
    private lateinit var roleWrapper: LinearLayout
    private lateinit var denmWrapper: LinearLayout
    private lateinit var sremWrapper: LinearLayout
    private lateinit var ssemWrapper: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_cam_card, container, false)

        title = view.findViewById(R.id.cam_title)
        icon = view.findViewById(R.id.cam_vehicle_icon)
        leftBlinker = view.findViewById(R.id.cam_left_signal)
        rightBlinker = view.findViewById(R.id.cam_right_signal)
        lowBeams = view.findViewById(R.id.cam_low_beams)
        highBeams = view.findViewById(R.id.cam_high_beams)
        speed = view.findViewById(R.id.cam_speed_value)
        heading = view.findViewById(R.id.cam_heading_value)
        dimensions = view.findViewById(R.id.cam_dimensions_value)
        role = view.findViewById(R.id.cam_vehicle_role)
        latestDENM = view.findViewById(R.id.cam_denm_message)
        latestSREM = view.findViewById(R.id.cam_srem)
        latestSSEM = view.findViewById(R.id.cam_ssem)

        speedWrapper = view.findViewById(R.id.cam_speed_wrarpper)
        headingWrapper = view.findViewById(R.id.cam_heading_wrapper)
        dimensionsWrapper = view.findViewById(R.id.cam_dimensions_wrapper)
        roleWrapper = view.findViewById(R.id.cam_role_wrapper)
        denmWrapper = view.findViewById(R.id.cam_denm_wrapper)
        sremWrapper = view.findViewById(R.id.cam_srem_wrapper)
        ssemWrapper = view.findViewById(R.id.cam_ssem_wrapper)

        initialized = true
        updateValues(cam)

        return view
    }

    fun updateValues(cam: Cam) {

        if(!initialized) return

        "CAM, station ID: ${cam.stationID}".also { title.text = it }

        var roleStr = cam.getRoleString()

        // Icon
        val drawable = when(cam.stationType) {
            5, 10 -> R.drawable.car_top
            6 -> R.drawable.bus_top
            7, 8 -> R.drawable.truck_top
            11 -> R.drawable.tram_top
            15 -> {
                leftBlinker.visibility = View.GONE
                rightBlinker.visibility = View.GONE
                lowBeams.visibility = View.GONE
                highBeams.visibility = View.GONE
                roleStr = "Road-Side Unit"
                R.drawable.road_side_unit_big
            }
            else -> R.drawable.unknown_top
        }
        icon.setImageResource(drawable)

        // Lights
        if(cam.vehicleLights?.leftTurnSignalOn == true)
            leftBlinker.setImageResource(R.drawable.cam_signal_left_on)
        else
            leftBlinker.setImageResource(R.drawable.cam_signal_left_off)

        if(cam.vehicleLights?.rightTurnSignalOn == true)
            rightBlinker.setImageResource(R.drawable.cam_signal_right_on)
        else
            rightBlinker.setImageResource(R.drawable.cam_signal_right_off)

        if(cam.vehicleLights?.daytimeRunningLightsOn == true || cam.vehicleLights?.lowBeamHeadLightsOn == true)
            lowBeams.setImageResource(R.drawable.cam_low_beam_on)
        else
            lowBeams.setImageResource(R.drawable.cam_low_beam_off)

        if(cam.vehicleLights?.highBeamHeadLightsOn == true)
            highBeams.setImageResource(R.drawable.cam_high_beam_on)
        else
            highBeams.setImageResource(R.drawable.cam_high_beam_off)

        val headingValue = cam.heading
        val speedValue = cam.speed
        val width = cam.vehicleWidth
        val length = cam.vehicleLength
        val denmMessage = cam.latestDenm
        val sremMessage = cam.latestSrem?.requests?.firstOrNull()
        val ssemMessage = cam.latestSrem?.latestSsem?.responses?.firstOrNull()

        // Speed
        if(speedValue != null) {
            "${speedValue.toInt()} Km/h".also { speed.text = it }
            speedWrapper.visibility = View.VISIBLE
        }
        else
            speedWrapper.visibility = View.GONE

        // Heading
        icon.rotation = headingValue ?: 0f

        if(headingValue != null) {
            heading.text = formatHeadingWithDirection(headingValue)
            headingWrapper.visibility = View.VISIBLE
        }
        else
            headingWrapper.visibility = View.GONE

        // Dimensions
        if(width != null && length != null) {
            "$width x $length m".also { dimensions.text = it }
            dimensionsWrapper.visibility = View.VISIBLE
        }
        else
            dimensionsWrapper.visibility = View.GONE

        // Role
        role.text = roleStr

        // DENM
        if(denmMessage != null) {
            latestDENM.text = denmMessage.getCauseCodeDescription()
            denmWrapper.visibility = View.VISIBLE
        }
        else
            denmWrapper.visibility = View.GONE

        // SREM
        if(sremMessage != null) {
            latestSREM.text = sremMessage.getRequestTypeString()
            sremWrapper.visibility = View.VISIBLE
        }
        else
            sremWrapper.visibility = View.GONE

        // SSEM
        if(ssemMessage != null) {
            latestSSEM.text = ssemMessage.statusString
            ssemWrapper.visibility = View.VISIBLE
        }
        else
            ssemWrapper.visibility = View.GONE
    }

    private fun formatHeadingWithDirection(headingValue: Float): String {
        val direction = when {
            headingValue >= 337.5f || headingValue < 22.5f -> "N"
            headingValue < 67.5f -> "NE"
            headingValue < 112.5f -> "E"
            headingValue < 157.5f -> "SE"
            headingValue < 202.5f -> "S"
            headingValue < 247.5f -> "SW"
            headingValue < 292.5f -> "W"
            else -> "NW"
        }

        return String.format("%.2f° (%s)", headingValue, direction)
    }
}