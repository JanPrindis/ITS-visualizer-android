package utils.storage.data

import utils.visualization.VisualizerInstance

data class CauseCode(val description: String, val subCauseCodes: Map<Int, String>)

class Denm(
    messageID: Int,
    stationID: Long,
    stationType: Int,
    originPosition: Position,
    var originatingStationID: Long,
    var sequenceNumber: Int,
    var detectionTime: Long,
    var referenceTime: Long,
    var termination: Boolean,
    var causeCode: Int,
    var subCauseCode: Int,
    var pathHistory: MutableList<MutableList<Position>>,

    internal val calculatedPathHistory: MutableList<MutableList<Position>> = mutableListOf()

) : Message(
    messageID,
    stationID,
    stationType,
    originPosition
) {
    override fun getProtocolName(): String {
        return "DENM"
    }

    fun getCauseCodeDescription(): String {
        return causeCodeStringMap[causeCode]?.description ?: "Unknown"
    }

    fun getSubCauseCodeDescription(): String {
        return causeCodeStringMap[causeCode]?.subCauseCodes?.get(subCauseCode) ?: ""
    }

    fun update(other: Denm) {
        messageID = other.messageID
        stationType = other.stationType
        originPosition = other.originPosition
        originatingStationID = other.originatingStationID
        detectionTime = other.detectionTime
        referenceTime = other.referenceTime
        termination = other.termination
        causeCode = other.causeCode
        subCauseCode = other.subCauseCode

        if(pathHistory != other.pathHistory) {
            pathHistory = other.pathHistory
            calculatePathHistory()
            VisualizerInstance.visualizer?.drawDenm(this)
        }

        modified = true
    }

    fun calculatePathHistory() {
        val refPos = originPosition ?: return
        calculatedPathHistory.clear()

        for (pathList in pathHistory) {
            calculatedPathHistory.add(
                calculatePathHistory(refPos, pathList).toMutableList()
            )
        }
    }

    companion object {
        val causeCodeStringMap: Map<Int, CauseCode> = mapOf(
            0 to CauseCode("No specific information", emptyMap()),
            1 to CauseCode("Traffic condition", mapOf(
                2 to "Traffic jam slowly increasing",
                3 to "Traffic jam increasing",
                4 to "Traffic jam strongly increasing",
                5 to "Traffic stationary",
                6 to "Traffic jam slightly decreasing",
                7 to "Traffic jam decreasing",
                8 to "Traffic jam strongly decreasing"
            )),
            2 to CauseCode("Accident", emptyMap()),
            3 to CauseCode("Roadworks", mapOf(
                4 to "Short-term stationary roadWorks",
                5 to "Street cleaning",
                6 to "Winter service"
            )),
            6 to CauseCode("Adverse Weather Condition - Adhesion", emptyMap()),
            9 to CauseCode("Hazardous Location - Surface Condition", emptyMap()),
            10 to CauseCode("Hazardous Location - Obstacle On The Road", emptyMap()),
            11 to CauseCode("Hazardous Location - Animal On The Road", emptyMap()),
            12 to CauseCode("Human Presence On The Road", emptyMap()),
            14 to CauseCode("Wrong Way Driving", mapOf(
                1 to "Vehicle driving in the wrong lane",
                2 to "Vehicle driving in the wrong driving direction"
            )),
            15 to CauseCode("Rescue And Recovery Work In Progress", emptyMap()),
            17 to CauseCode("Adverse Weather Condition - Extreme Weather Condition", emptyMap()),
            19 to CauseCode("Adverse Weather Condition - Precipitation", emptyMap()),
            26 to CauseCode("Slow Vehicle", emptyMap()),
            27 to CauseCode("Dangerous End Of Queue", emptyMap()),
            91 to CauseCode("Vehicle Breakdown", mapOf(
                1 to "Lack of fuel",
                2 to "Lack of battery",
                3 to "Engine problem",
                4 to "Transmission problem",
                5 to "Engine cooling problem",
                6 to "Braking system problem",
                7 to "Steering problem",
                8 to "Tyre puncture"
            )),
            92 to CauseCode("Post Crash", mapOf(
                1 to "Accident without e-Call triggered",
                2 to "Accident with e-Call manually triggered",
                3 to "Accident with e-Call automatically triggered",
                4 to "Accident with e-Call triggered without possible access to a cell network"
            )),
            93 to CauseCode("Human Problem", mapOf(
                1 to "Hypoglycemia problem",
                2 to "Heart problem"
            )),
            94 to CauseCode("Stationary Vehicle", mapOf(
                1 to "Human Problem",
                2 to "Vehicle breakdown",
                3 to "Post crash",
                4 to "On public transport stop",
                5 to "Carrying dangerous goods"
            )),
            95 to CauseCode("Emergency Vehicle Approaching", mapOf(
                1 to "Emergency vehicle approaching",
                2 to "Prioritized vehicle approaching"
            )),
            96 to CauseCode("Hazardous Location - Dangerous Curve", mapOf(
                1 to "Dangerous left turn curve",
                2 to "Dangerous right turn curve",
                3 to "Multiple curves starting with unknown turning direction",
                4 to "Multiple curves starting with left turn",
                5 to "Multiple curves starting with right turn"
            )),
            97 to CauseCode("Collision Risk", mapOf(
                1 to "Longitudinal collision risk",
                2 to "Crossing collision risk",
                3 to "Lateral collision risk",
                4 to "Collision risk involving vulnerable road user"
            )),
            98 to CauseCode("Signal Violation", mapOf(
                1 to "Stop sign violation",
                2 to "Traffic light violation",
                3 to "Turning regulation violation"
            )),
            99 to CauseCode("Dangerous Situation", mapOf(
                1 to "Emergency electronic brake lights",
                2 to "Pre-crash system activated",
                3 to "ESP (Electronic Stability Program) activated",
                4 to "ABS (Anti-lock braking system) activated",
                5 to "AEB (Automatic Emergency Braking) activated",
                6 to "Brake warning activated",
                7 to "Collision risk warning activated"
            ))
        )
    }

}