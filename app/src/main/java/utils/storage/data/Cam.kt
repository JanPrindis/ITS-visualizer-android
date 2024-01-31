package utils.storage.data

import android.util.Log
import com.honz.itsvisualizer.R
import utils.visualization.VisualizerInstance

data class VehicleLights(
    val lowBeamHeadLightsOn: Boolean,
    val highBeamHeadLightsOn: Boolean,
    val leftTurnSignalOn: Boolean,
    val rightTurnSignalOn: Boolean,
    val daytimeRunningLightsOn: Boolean,
    val reverseLightsOn: Boolean,
    val fogLightsOn: Boolean,
    val parkingLightsOn: Boolean
)

class Cam(
    messageID: Int,
    stationID: Long,
    stationType: Int?,
    originPosition: Position?,
    var speed: Float?,
    var heading: Float?,
    var path: MutableList<Position>?,
    var vehicleLength: Float?,
    var vehicleWidth: Float?,
    var vehicleRole: Int?,
    var vehicleLights: VehicleLights?,
    var timeEpoch: Double, //last update

    // POSSIBLE UNUSED
    var lastSremUpdate: Int = 0,
    var isSrcAttention: Boolean = false

) : Message(
    messageID,
    stationID,
    stationType,
    originPosition
) {

    override fun getProtocolName(): String {
        return "CAM"
    }

    fun update(other: Cam) {
        super.originPosition = other.originPosition

        other.speed?.let { speed = it }
        other.heading?.let { heading = it }
        other.path.let { path = it }
        other.vehicleLength?.let { vehicleLength = it }
        other.vehicleWidth?.let { vehicleWidth = it }
        other.vehicleRole?.let { vehicleRole = it }
        other.vehicleLights?.let { vehicleLights = it }

        timeEpoch = other.timeEpoch
        modified = true

        draw()
    }

    override fun draw() {
        val visualizer = VisualizerInstance.visualizer ?: return
        val position = originPosition ?: return
        val drawable = when(stationType) {
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
        visualizer.drawPoint(stationID, position.lat, position.lon, drawable)
    }

    override fun remove() {
        val visualizer = VisualizerInstance.visualizer ?: return
        visualizer.removePoint(stationID)
    }

    fun getRoleString(): String {
        return when (vehicleRole) {
            0 -> "Default"
            1 -> "Public Transport"
            2 -> "Special Transport"
            3 -> "Dangerous Goods Transport"
            4 -> "Road Work Vehicle"
            5 -> "Rescue Vehicle"
            6 -> "Emergency Vehicle"
            7 -> "Safety Car (Police)"
            8 -> "Agricultural Vehicle"
            9 -> "Commercial Goods Transport"
            10 -> "Military Vehicle"
            11 -> "Road Operator Vehicle"
            12 -> "Taxi"
            else -> return "Unknown"
        }
    }
}
