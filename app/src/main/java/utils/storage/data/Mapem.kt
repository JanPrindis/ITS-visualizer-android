package utils.storage.data

import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

enum class Maneuver(value: Int) {
    LEFT(0),
    LEFT_STRAIGHT(1),
    STRAIGHT(2),
    RIGHT_STRAIGHT(3),
    RIGHT(4),
    LEFT_RIGHT(5),
    ALL(6),
    UNKNOWN(7)
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

    // Visualization
    internal var calculatedLaneOffset: List<Position> = mutableListOf()
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
    internal val currentIconIDs: MutableList<Long> = mutableListOf(),
    internal var visualizerSignalGroupID: Int? = null,
    internal var visited: Boolean = false

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

        calculateLaneOffsets()
    }

    private fun calculateLaneOffsets() {
        val refPos = originPosition ?: return

        for(lane in lanes) {
            lane.calculatedLaneOffset = calculatePathHistory(refPos, nodesToPositions(lane.nodes))
        }
    }

    private fun nodesToPositions(nodes: List<Node>) : List<Position> {
        val positions = mutableListOf<Position>()
        for(node in nodes) {
            positions.add(Position(node.y.toDouble(), node.x.toDouble(), 0.0))
        }

        return positions
    }

    companion object LaneTypes{
        val laneTypes: List<String> = listOf(
            "Vehicle",
            "CrossWalk",
            "BikeLane",
            "Sidewalk",
            "Median",
            "Striping",
            "TrackedVehicle",
            "Parking")
    }

    override fun getProtocolName(): String {
        return "MAPEM"
    }
}