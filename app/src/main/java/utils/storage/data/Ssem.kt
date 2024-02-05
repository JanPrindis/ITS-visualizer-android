package utils.storage.data

class Response(
    val requesterId: Int,
    val requesterStationId: Long,

    val requestId: Int,
    val requestSequenceNumber: Int,

    val role: Int,
    val subRole: Int,

    val inboundLane: Int,
    val outboundLane: Int,

    val approachInbound: Int,
    val approachOutbound: Int,

    val statusCode: Int,
    val statusString: String
) {
    companion object {
        val statusString: List<String> = listOf(
            "Unknown",
            "Requested",
            "Processing",
            "Watch Other Traffic",
            "Granted",
            "Denied",
            "Processing time exc.",
            "Service locked"
        )
        /* dsrc.signalStatusPackage.status
            - 0 unknown
            - 1 requested (received but control not changed)
            - 2 processing (request validated, changed timing sent by SPaT)
            - 3 watchOtherTraffic (for emergency vehicles, all signals are red until vehicle passes stop line, updated timing sent by SPaT)
            - 4 granted (signal will be green and not change, until vehicle passes stop line)
            - 5 denied
            - 6 maxPresence (max processing time exceeded)
            - 7 reserviceLocked (rejected because maximum number of request for the maneuver in given time frame has been exceeded)
         */
    }
}

class Ssem(
    messageID: Int,
    stationID: Long,
    val timestamp: Int,
    val sequenceNumber: Int,
    val intersectionId: Int,    // intersection ID
    val responses: MutableList<Response>

) : Message(
    messageID,
    stationID,
    stationType = 0,
    originPosition = null
) {
    override fun getProtocolName(): String {
        return "SSEM"
    }
}