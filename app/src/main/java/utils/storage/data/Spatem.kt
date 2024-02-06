package utils.storage.data

class MovementEvent(
    val eventState: Int,
    val startTime: Int?,
    val minEndTime: Int?,
    val maxEndTime: Int?,
    val likelyTime: Int?,
    val confidence: Int?
) {
    companion object {
        val eventStateString: List<String> = listOf(
            // Unlit / dark
            "Unavailable",  //e.g. power outage
            "Dark",         // e.g. outside of operating hours

            // Reds
            "Stop-Then-Proceed",
            "Stop-And-Remain",

            // Red and Amber
            "Pre-Movement",

            // Greens
            "Permissive-Movement-Allowed",
            "Protected-Movement-Allowed",

            // Yellows / Amber
            "Permissive-clearance",
            "Protected-clearance",
            "Caution-Conflicting-Traffic",
        )

        enum class StateColor {
            RED,
            AMBER,
            RED_AMBER,
            GREEN,
            OFF,
            UNKNOWN;

            fun toStringValue(): String {
                return when (this) {
                    RED -> "Red"
                    RED_AMBER -> "Red and amber"
                    AMBER -> "Amber"
                    GREEN -> "Green"
                    OFF -> "Off"
                    UNKNOWN -> "Unknown"
                }
            }
        }
    }

    fun getStateColor(): StateColor {
        return when (eventState) {
            in 0..1 -> StateColor.OFF
            in 2..3 -> StateColor.RED
            4 -> StateColor.RED_AMBER
            in 5..6 -> StateColor.GREEN
            in 7..9 -> StateColor.AMBER
            else -> StateColor.UNKNOWN
        }
    }

    fun getStateString(): String {
        return if(eventState in eventStateString.indices)
            eventStateString[eventState]
        else ""
    }
}

data class MovementState(
    val signalGroup: Int,
    val movementName: String,
    val movementEvents: MutableList<MovementEvent>
)

data class SPATEMIntersection(
    val id: Int,
    val timeStamp: Int,
    val moy: Int,
    val name: String,
    val movementStates: MutableList<MovementState>
)

class Spatem(
    messageID: Int,
    stationID: Long,
    val intersections: MutableList<SPATEMIntersection>

) : Message(
    messageID,
    stationID,
    stationType = 0,
    originPosition = null
) {
    override fun getProtocolName(): String {
        return "SPATEM"
    }
}