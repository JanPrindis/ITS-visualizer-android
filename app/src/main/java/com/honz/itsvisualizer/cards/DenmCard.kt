package com.honz.itsvisualizer.cards

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.honz.itsvisualizer.R
import utils.storage.data.Denm
import java.text.DateFormat.getDateTimeInstance
import java.util.Date

class DenmCard(private val denm: Denm) : Fragment() {

    private var initialized = false

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
        updateValues(denm)

        return view
    }

    fun updateValues(denm: Denm) {

        if(!initialized) return

        "DENM from station ID: ${denm.originatingStationID}".also { title.text = it }

        val drawable = when(denm.causeCode) {
            0 -> R.drawable.denm_general_big
            1 -> R.drawable.denm_traffic_big
            2 -> R.drawable.denm_accident_big
            3 -> R.drawable.denm_roadwork_big
            6 -> R.drawable.denm_weather_big
            9 -> R.drawable.denm_road_condition_big
            10 -> R.drawable.denm_road_obstacle_big
            11 -> R.drawable.denm_road_animal_big
            12 -> R.drawable.denm_road_human_big
            14 -> R.drawable.denm_car_big
            15 -> R.drawable.denm_emergency_big
            17 -> R.drawable.denm_weather_big
            19 -> R.drawable.denm_weather_big
            26 -> R.drawable.denm_car_big
            27 -> R.drawable.denm_traffic_big
            91 -> R.drawable.denm_breakdown_big
            92 -> R.drawable.denm_accident_big
            93 -> R.drawable.denm_human_problem_big
            94 -> R.drawable.denm_car_big
            95 -> R.drawable.denm_emergency_big
            96 -> R.drawable.denm_dangerous_curve_big
            97 -> R.drawable.denm_car_big
            98 -> R.drawable.denm_car_big
            99 -> R.drawable.denm_general_big
            else -> R.drawable.denm_general_big
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
    }
}