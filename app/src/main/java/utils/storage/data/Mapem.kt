package utils.storage.data

object MapemStorage {
    val mapemStorage: MutableList<Mapem> = mutableListOf()
}

data class SignalGroupInfo(
    val laneID: Long,
    val connectingLaneID: Long,
    val maneuver: String
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
    val signalGroupInfoPairList: MutableList<Pair<Int, MutableList<SignalGroupInfo>>> = mutableListOf()
)

class Mapem(
    messageID: Int,
    stationID: Long,
    originPosition: Position,

    val intersectionID: Long,
    val intersectionName: String,
    val laneWidth: Float,

    val lanes: MutableList<Lane>,
    val adjacentIngressLanes: MutableList<MutableList<Lane>> = mutableListOf(),
    val laneTypes: MutableList<String> = mutableListOf()

) : Message(
    messageID,
    stationID,
    stationType = 0,
    originPosition
) {
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