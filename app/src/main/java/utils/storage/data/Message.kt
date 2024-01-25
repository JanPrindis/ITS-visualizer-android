package utils.storage.data

data class Position(
    var lat: Double,
    var lon: Double,
    var alt: Double
)

open class Message(
    var messageID: Int,
    var stationID: Long,
    var stationType: Int?,
    var originPosition: Position?
) {
    var modified: Boolean = true  // Determines if Message is no longer receiving updates and is to be deleted

    open fun getProtocolName(): String {
        return ""
    }
}