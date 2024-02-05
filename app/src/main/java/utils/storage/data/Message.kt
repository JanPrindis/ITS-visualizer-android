package utils.storage.data

import java.math.BigDecimal
import java.math.RoundingMode

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

    companion object {
        fun calculatePathHistory(refPosition: Position, offsets: List<Position>): List<Position> {

            val path: MutableList<Position> = mutableListOf()
            var originPosition = refPosition

            for (offset in offsets) {
                val latBigDecimal = BigDecimal(originPosition.lat).add(
                    BigDecimal(offset.lat).divide(
                        BigDecimal(10000000.0), 7, RoundingMode.HALF_UP))
                val lonBigDecimal = BigDecimal(originPosition.lon).add(
                    BigDecimal(offset.lon).divide(
                        BigDecimal(10000000.0), 7, RoundingMode.HALF_UP))
                val altDouble = originPosition.alt + (offset.alt / 100.0)
                path.add(Position(latBigDecimal.toDouble(), lonBigDecimal.toDouble(), altDouble))

                originPosition = path.last()
            }

            return path
        }
    }
}