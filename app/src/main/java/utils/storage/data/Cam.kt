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

    internal var calculatedPathHistory: MutableList<Position> = mutableListOf(),
    internal var latestDenm: Denm? = null,
    internal var latestSrem: Srem? = null

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

        if(other.path != path) {
            other.path.let { path = it }
            calculatePathOffset()
        }

        other.vehicleLength?.let { vehicleLength = it }
        other.vehicleWidth?.let { vehicleWidth = it }
        other.vehicleRole?.let { vehicleRole = it }
        other.vehicleLights?.let { vehicleLights = it }

        timeEpoch = other.timeEpoch
        modified = true
    }

    fun calculatePathOffset() {
        val refPos = originPosition ?: return
        val pathOffset = path ?: return
        calculatedPathHistory = calculatePathHistory(refPos, pathOffset).toMutableList()
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
