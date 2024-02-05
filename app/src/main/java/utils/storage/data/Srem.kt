package utils.storage.data

class Request(
    val id: Int,            // Intersection ID
    val requestId: Int,     // The requestID uniquely links the request to the corresponding status from the intersection (SSM).
    val requestType: Int,
    val inboundLane: Int,   // index within intersection
    val outboundLane: Int,
    val approachInbound: Int,
    val approachOutbound: Int
) {
    companion object {
        val requestTypeString: List<String> = listOf(
            "Request reserved",
            "Request",
            "Request update",
            "Cancellation"
        )
    }

    fun getRequestTypeString(): String {
        return if (requestType in requestTypeString.indices) {
            requestTypeString[requestType]
        } else {
            "Unknown"
        }
    }
}

class Srem(
    messageID: Int,
    stationID: Long,
    val timestamp: Float,
    val sequenceNumber: Int,
    val requests: MutableList<Request>,
    val requestorId: Long,
    val requestorRole: Int,
    val requestorSubRole: Int,
    val requestorName: String,
    val routeName: String,

    internal var latestSsem: Ssem? = null

) : Message(
    messageID,
    stationID,
    stationType = 0,
    originPosition = null
) {
    override fun getProtocolName(): String {
        return "SREM"
    }
}