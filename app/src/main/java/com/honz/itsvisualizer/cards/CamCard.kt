package com.honz.itsvisualizer.cards

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.honz.itsvisualizer.R
import utils.storage.data.Cam

class CamCard(private val cam: Cam) : Fragment() {

    lateinit var title: TextView
    lateinit var icon: ImageView
    lateinit var leftBlinker: TextView
    lateinit var rightBlinker: TextView
    lateinit var lowBeams: TextView
    lateinit var highBeams: TextView
    lateinit var speed: TextView
    lateinit var heading: TextView
    lateinit var dimensions: TextView
    lateinit var role: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_cam_card, container, false)

        title = view.findViewById(R.id.cam_title)
        icon = view.findViewById(R.id.cam_car_icon)
        leftBlinker = view.findViewById(R.id.left_blinker_text)
        rightBlinker = view.findViewById(R.id.right_blinker_text)
        lowBeams = view.findViewById(R.id.low_beams_text)
        highBeams = view.findViewById(R.id.high_beam_lights)
        speed = view.findViewById(R.id.cam_speed_value)
        heading = view.findViewById(R.id.cam_heading_value)
        dimensions = view.findViewById(R.id.cam_dimensions_value)
        role = view.findViewById(R.id.cam_vehicle_role)

        updateValues(cam)

        return view
    }

    fun updateValues(cam: Cam) {
        "CAM, station ID: ${cam.stationID}".also { title.text = it }
        icon.rotation = cam.heading ?: 0f
        leftBlinker.text = cam.vehicleLights?.leftTurnSignalOn.toString()
        rightBlinker.text = cam.vehicleLights?.rightTurnSignalOn.toString()
        lowBeams.text = cam.vehicleLights?.lowBeamHeadLightsOn.toString()
        highBeams.text = cam.vehicleLights?.highBeamHeadLightsOn.toString()
        "${cam.speed ?: "Unknown"} Km/h".also { speed.text = it }
        heading.text = (cam.heading ?: "Unknown").toString()
        "${cam.vehicleWidth ?: "Unknown"} x ${cam.vehicleLength ?: "Unknown"}".also { dimensions.text = it }
        role.text = cam.getRoleString()
    }
}