package utils.storage.data

import com.honz.itsvisualizer.R
import utils.storage.data.MovementEvent.Companion.StateColor
import utils.visualization.VisualizerInstance
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

enum class Maneuver {
    LEFT,
    LEFT_STRAIGHT,
    STRAIGHT,
    RIGHT_STRAIGHT,
    RIGHT,
    LEFT_RIGHT,
    ALL,
    UNKNOWN
}

data class SignalGroupCompact (
    val id: Long,
    val signalGroup: Int,
    val maneuversAllowed: Maneuver,
    val position: Position,
    val angle: Double
)

data class SignalGroupLaneInfo(
    var laneId: Long,
    var nodes: List<Node>,
    var straight: Boolean = false,
    var left: Boolean = false,
    var right: Boolean = false
)

data class Node(
    val x: Int,
    val y: Int,
    val delta: Int
)

data class ConnectingLane(
    val laneNumber: Int,
    val connectionID: Int,
    val signalGroup: Int,

    val maneuverStraightAllowed: Boolean,
    val maneuverLeftAllowed: Boolean,
    val maneuverRightAllowed: Boolean
)

data class Lane(
    val type: Int,
    val strType: String,
    val laneID: Long,
    val ingressApproach: Int?,
    val egressApproach: Int?,

    val directionIngressPath: Boolean,
    val directionEgressPath: Boolean,

    val nodes: MutableList<Node>,
    val connectingLanes: MutableList<ConnectingLane>,
)

class Mapem(
    messageID: Int,
    stationID: Long,
    originPosition: Position,

    val intersectionID: Long,
    val intersectionName: String,
    val laneWidth: Float,
    val lanes: MutableList<Lane>,

    // Visualization
    var latestSpatem: SPATEMIntersection? = null,
    val signalGroups: MutableList<SignalGroupCompact> = mutableListOf(),
    private val currentIconIDs: MutableList<Long> = mutableListOf()

) : Message(
    messageID,
    stationID,
    stationType = 0,
    originPosition
) {
    /**
     * Gets list of ingress lanes that are for vehicles
     */
    private fun getAllIngressVehicleLanes() : List<Lane> {
        val ingressVehicleLanes: MutableList<Lane> = mutableListOf()

        for(lane in lanes) {
            if(lane.directionIngressPath && lane.type == 0)
                ingressVehicleLanes.add(lane)
        }

        return ingressVehicleLanes
    }

    /**
     * Calculates the position of the first Node item, representing stop line
     */
    private fun calculateSignalGroupPositionOffset(nodes: List<Node>, refPosition: Position): Position {
        if(nodes.isEmpty()) return refPosition
        val latOffset = nodes.first().y / 10000000.0
        val lonOffset = nodes.first().x / 10000000.0

        return Position(refPosition.lat + latOffset, refPosition.lon + lonOffset, refPosition.alt)
    }

    /**
     * Calculates angle of 2 points to North
     */
    private fun calculateSignalGroupAngle(nodes: List<Node>, refPosition: Position): Double {
        if(nodes.size < 2) return 999.0

        val latOffsetTo = nodes[0].y / 10000000.0
        val lonOffsetTo = nodes[0].x / 10000000.0

        val latOffsetFrom = nodes[1].y / 10000000.0
        val lonOffsetFrom = nodes[1].x / 10000000.0

        val latTo = refPosition.lat + latOffsetTo
        val lonTo = refPosition.lon + lonOffsetTo

        val latFrom = refPosition.lat + latOffsetFrom
        val lonFrom = refPosition.lon + lonOffsetFrom

        val northVector = Pair(0.0, 1.0)
        val pathVector = Pair(lonTo - lonFrom, latTo - latFrom)

        val dotProduct = northVector.first * pathVector.first + northVector.second * pathVector.second

        val magnitudeNorth = sqrt(northVector.first.pow(2) + northVector.second.pow(2))
        val magnitudePath = sqrt(pathVector.first.pow(2) + pathVector.second.pow(2))

        val cosTheta = dotProduct / (magnitudeNorth * magnitudePath)
        val angleRad = acos(cosTheta)

        var angleDeg = Math.toDegrees(angleRad)
        angleDeg = (angleDeg + 360) % 360

        if (lonTo < lonFrom) {
            angleDeg = (360 - angleDeg) % 360
        }

        return angleDeg
    }

    /**
     * Finds all lanes under the same signalGroup and groups them together in a easy to read format
     */
    fun prepareForDraw() {
        val lanes = getAllIngressVehicleLanes()
        val signalMap: MutableMap<Int, SignalGroupLaneInfo> = mutableMapOf()

        // Separate all connecting lane elements
        for(lane in lanes) {
            for(connectingLane in lane.connectingLanes) {
                if (!signalMap.containsKey(connectingLane.signalGroup)) {
                    signalMap[connectingLane.signalGroup] = SignalGroupLaneInfo(lane.laneID, lane.nodes)
                }
                if (connectingLane.maneuverLeftAllowed) {
                    signalMap[connectingLane.signalGroup]?.left = true
                }
                if (connectingLane.maneuverRightAllowed) {
                    signalMap[connectingLane.signalGroup]?.right = true
                }
                if (connectingLane.maneuverStraightAllowed) {
                    signalMap[connectingLane.signalGroup]?.straight = true
                }
            }
        }

        // Intersection position
        val refPos: Position = originPosition ?: return

        // Transform SignalGroupCompact for easier drawing
        for (signal in signalMap) {
            signalGroups.add(SignalGroupCompact(
                id = "${intersectionID}${signal.value.laneId}".toLong(),
                signalGroup = signal.key,
                position = calculateSignalGroupPositionOffset(signal.value.nodes, refPos),
                angle = calculateSignalGroupAngle(signal.value.nodes, refPos),
                maneuversAllowed =
                    if(signal.value.left && signal.value.right && signal.value.straight) Maneuver.ALL
                    else if(signal.value.left && signal.value.right) Maneuver.LEFT_RIGHT
                    else if(signal.value.left && signal.value.straight) Maneuver.LEFT_STRAIGHT
                    else if(signal.value.right && signal.value.straight) Maneuver.RIGHT_STRAIGHT
                    else if(signal.value.straight) Maneuver.STRAIGHT
                    else Maneuver.UNKNOWN
            ))
        }
    }

    override fun draw() {
        for (signal in signalGroups) {
            val spatem = latestSpatem?.movementStates?.find { it.signalGroup == signal.signalGroup }

            val signalIcon = when (signal.maneuversAllowed) {
                Maneuver.LEFT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    StateColor.RED -> R.drawable.traffic_light_red_left_icon
                    StateColor.AMBER -> R.drawable.traffic_light_yellow_left_icon
                    StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_left_icon
                    StateColor.GREEN -> R.drawable.traffic_light_green_left_icon
                    else -> R.drawable.traffic_light_blank_left_icon
                }
                Maneuver.LEFT_STRAIGHT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    StateColor.RED -> R.drawable.traffic_light_red_straight_left_icon
                    StateColor.AMBER -> R.drawable.traffic_light_yellow_straight_left_icon
                    StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_straight_left_icon
                    StateColor.GREEN -> R.drawable.traffic_light_green_straight_left_icon
                    else -> R.drawable.traffic_light_blank_straight_left_icon
                }
                Maneuver.STRAIGHT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    StateColor.RED -> R.drawable.traffic_light_red_straight_icon
                    StateColor.AMBER -> R.drawable.traffic_light_yellow_straight_icon
                    StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_straight_icon
                    StateColor.GREEN -> R.drawable.traffic_light_green_straight_icon
                    else -> R.drawable.traffic_light_blank_straight_icon
                }
                Maneuver.RIGHT_STRAIGHT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    StateColor.RED -> R.drawable.traffic_light_red_straight_right_icon
                    StateColor.AMBER -> R.drawable.traffic_light_yellow_straight_right_icon
                    StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_straight_right_icon
                    StateColor.GREEN -> R.drawable.traffic_light_green_straight_right_icon
                    else -> R.drawable.traffic_light_blank_straight_right_icon
                }
                Maneuver.RIGHT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    StateColor.RED -> R.drawable.traffic_light_red_right_icon
                    StateColor.AMBER -> R.drawable.traffic_light_yellow_right_icon
                    StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_right_icon
                    StateColor.GREEN -> R.drawable.traffic_light_green_right_icon
                    else -> R.drawable.traffic_light_blank_right_icon
                }
                Maneuver.LEFT_RIGHT -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    StateColor.RED -> R.drawable.traffic_light_red_left_right_icon
                    StateColor.AMBER -> R.drawable.traffic_light_yellow_left_right_icon
                    StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_left_right_icon
                    StateColor.GREEN -> R.drawable.traffic_light_green_left_right_icon
                    else -> R.drawable.traffic_light_blank_left_right_icon
                }
                else -> when(spatem?.movementEvents?.first()?.getStateColor()) {
                    StateColor.RED -> R.drawable.traffic_light_red_icon
                    StateColor.AMBER -> R.drawable.traffic_light_yellow_icon
                    StateColor.RED_AMBER -> R.drawable.traffic_light_red_yellow_icon
                    StateColor.GREEN -> R.drawable.traffic_light_green_icon
                    else -> R.drawable.traffic_light_blank_icon
                }
            }
            currentIconIDs.add(signal.id)
            VisualizerInstance.visualizer?.drawPoint(signal.id, signal.position.lat, signal.position.lon, signalIcon)
        }
    }

    override fun remove() {
        for (id in currentIconIDs)
            VisualizerInstance.visualizer?.removePoint(id)

        currentIconIDs.clear()
    }

    companion object LaneTypes{
        val laneTypes: List<String> = listOf(
            "Vehicle",
            "CrossWalk",
            "BikeLane",
            "Unknown",
            "Unknown",
            "Unknown",
            "TrackedVehicle",
            "Parking")
    }

    override fun getProtocolName(): String {
        return "MAPEM"
    }
}