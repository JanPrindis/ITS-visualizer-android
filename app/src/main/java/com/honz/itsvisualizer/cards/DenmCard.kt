package com.honz.itsvisualizer.cards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.honz.itsvisualizer.R
import utils.storage.data.Denm
import java.text.DateFormat.getDateTimeInstance
import java.util.Date

class DenmCard : DetailCard {
    private var denmData: Denm? = null
    private var initialized = false

    constructor() : super()
    constructor(denm: Denm) : super() {
        denmData = denm
    }

    private lateinit var title: TextView
    private lateinit var icon: ImageView
    private lateinit var causeCode: TextView
    private lateinit var subCauseCode: TextView
    private lateinit var detectionTime: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_denm_card, container, false)

        title = view.findViewById(R.id.denm_title)
        icon = view.findViewById(R.id.denm_icon)
        causeCode = view.findViewById(R.id.denm_cause_code)
        subCauseCode = view.findViewById(R.id.denm_sub_cause_code)
        detectionTime = view.findViewById(R.id.denm_detection_time)

        initialized = true

        denmData?.let { updateValues(it) } ?: setToNoData()
        return view
    }

    fun updateValues(denm: Denm) {
        denmData = denm
        if(!initialized) return

        "DENM from station ID: ${denm.originatingStationID}".also { title.text = it }

        subCauseCode.visibility = View.VISIBLE
        detectionTime.visibility = View.VISIBLE

        val drawable = when(denm.causeCode) {
            0 -> R.drawable.denm_big_general
            1 -> {
                when (denm.subCauseCode) {
                    2,3,4 -> R.drawable.denm_big_traffic_inc
                    5 -> R.drawable.denm_big_traffic_stationary
                    6,7,8 -> R.drawable.denm_big_traffic_dec
                    else -> R.drawable.denm_big_traffic_inc
                }
            }
            2 -> R.drawable.denm_big_accident
            3 -> R.drawable.denm_big_roadwork
            6 -> R.drawable.denm_big_weather_adhesion
            9 -> R.drawable.denm_big_hazard_surface
            10 -> R.drawable.denm_big_hazard_obstacle
            11 -> R.drawable.denm_big_hazard_animal
            12 -> R.drawable.denm_big_hazard_human
            14 -> R.drawable.denm_big_wrong_way
            15 -> R.drawable.denm_big_recovery
            17 -> R.drawable.denm_big_weather_extreme
            18 -> R.drawable.denm_big_weather_visibility
            19 -> R.drawable.denm_big_weather_rain
            26 -> R.drawable.denm_big_vehicle_warning
            27 -> R.drawable.denm_big_traffic_stationary
            91 -> {
                when (denm.subCauseCode) {
                    1 -> R.drawable.denm_big_breakdown_fuel
                    2 -> R.drawable.denm_big_breakdown_battery
                    3,4 -> R.drawable.denm_big_breakdown_engine
                    5 -> R.drawable.denm_big_breakdown_temp
                    6 -> R.drawable.denm_big_breakdown_brake
                    7 -> R.drawable.denm_big_breakdown_steering
                    8 -> R.drawable.denm_big_breakdown_tyre
                    else -> R.drawable.denm_big_breakdown
                }
            }
            92 -> R.drawable.denm_big_accident
            93 -> R.drawable.denm_big_human_problem
            94 -> {
                when (denm.subCauseCode) {
                    1 -> R.drawable.denm_big_stationary_human_problem
                    2 -> R.drawable.denm_big_stationary_breakdown
                    3 -> R.drawable.denm_big_stationary_crash
                    4 -> R.drawable.denm_big_stationary_public_stop
                    5 -> R.drawable.denm_big_stationary_dangerous_goods
                    else -> R.drawable.denm_big_stationary
                }
            }
            95 -> R.drawable.denm_big_recovery
            96 -> {
                when (denm.subCauseCode) {
                    1 -> R.drawable.denm_big_dangerous_curve_left
                    2 -> R.drawable.denm_big_dangerous_curve_right
                    3,4 -> R.drawable.denm_big_dangerous_curve_multi_left
                    5 -> R.drawable.denm_big_dangerous_curve_multi_right
                    else -> R.drawable.denm_big_dangerous_curve_left
                }
            }
            97 -> {
                when (denm.subCauseCode) {
                    1 -> R.drawable.denm_big_longitudinal_collision_risk
                    2,4 -> R.drawable.denm_big_pedestrian_collision_risk
                    3 -> R.drawable.denm_big_lateral_collision_risk
                    else -> R.drawable.denm_big_collision_risk
                }
            }
            98 -> R.drawable.denm_big_signal_violation
            99 -> {
                when (denm.subCauseCode) {
                    1 -> R.drawable.denm_big_danger_emergency_brake
                    2,5,7 -> R.drawable.denm_big_danger_aeb
                    3 -> R.drawable.denm_big_danger_esp
                    4 -> R.drawable.denm_big_danger_abs
                    6 -> R.drawable.denm_big_danger_brake
                    else -> R.drawable.denm_big_vehicle_warning
                }
            }
            else -> R.drawable.denm_big_general
        }
        icon.setImageResource(drawable)

        causeCode.text = denm.getCauseCodeDescription()
        val subCauseCodeVal = denm.getSubCauseCodeDescription()
        if(subCauseCodeVal.isEmpty()) {
            subCauseCode.visibility = View.GONE
        }
        else {
            subCauseCode.text = subCauseCodeVal
            subCauseCode.visibility = View.VISIBLE
        }

        // Because the date is calculated in milliseconds since 01.01.2004
        val epoch2004 = 1072911600000L
        val date = Date(denm.detectionTime + epoch2004)
        "Detection time: ${getDateTimeInstance().format(date)}".also { detectionTime.text = it }

        this.view?.invalidate()
    }

    private fun setToNoData() {
        icon.setImageResource(R.drawable.denm_big_no_data)
        causeCode.text = getText(R.string.no_data_yet)

        subCauseCode.visibility = View.GONE
        detectionTime.visibility = View.INVISIBLE // Invisible to keep layout spacing

        if (title.text == getText(R.string.title_placeholder))
            title.text = getText(R.string.no_data)
    }
}