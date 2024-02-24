package utils.storage

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.honz.itsvisualizer.StatusColor
import org.json.JSONObject
import utils.storage.data.Cam
import utils.storage.data.ConnectingLane
import utils.storage.data.Denm
import utils.storage.data.Lane
import utils.storage.data.Mapem
import utils.storage.data.MovementEvent
import utils.storage.data.MovementState
import utils.storage.data.Node
import utils.storage.data.Position
import utils.storage.data.Request
import utils.storage.data.Response
import utils.storage.data.SPATEMIntersection
import utils.storage.data.Spatem
import utils.storage.data.Srem
import utils.storage.data.Ssem
import utils.storage.data.VehicleLights
import java.text.DateFormat
import java.util.Calendar

class MessageParser(private val context: Context) {
    /**
     * Attempts to parse an ITS message
     * Supported protocols are: DENM, CAM, SPATEM, MAPEM, SREM, SSEM
     */
    suspend fun parseJson(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val itsPduHeader = json
                .optJSONObject("_source")
                ?.optJSONObject("layers")
                ?.optJSONObject("its")
                ?.optJSONObject("its.ItsPduHeader_element")

            val stationID = itsPduHeader?.getString("its.stationID")
            var protocol = ""

            when(itsPduHeader?.getString("its.messageID")) {
                "1" ->{
                    protocol = "DENM"
                    parseDENM(json)
                }
                "2" -> {
                    protocol = "CAM"
                    parseCAM(json)
                }
                "4" -> {
                    protocol = "SPATEM"
                    parseSPATEM(json)
                }
                "5" -> {
                    protocol = "MAPEM"
                    parseMAPEM(json)
                }
                "9" -> {
                    protocol = "SREM"
                    parseSREM(json)
                }
                "10" -> {
                    protocol = "SSEM"
                    parseSSEM(json)
                }
                null -> return
            }

            if(protocol.isNotEmpty()) {
                val currentTime = Calendar.getInstance().time
                val timeString = DateFormat.getTimeInstance().format(currentTime)

                sendNotification("[$timeString] $protocol from: $stationID")
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendNotification(text: String) {
        val statusIntent = Intent("itsVisualizer.SET_STATUS")
        statusIntent.putExtra("statusImg", StatusColor.GREEN.value)
        statusIntent.putExtra("statusStr", text)
        LocalBroadcastManager.getInstance(context).sendBroadcast(statusIntent)
    }

    private suspend fun parseDENM(json: JSONObject) {
        val its = json
            .getJSONObject("_source")
            .getJSONObject("layers")
            .getJSONObject("its")

        // ITS PDU Header
        val itsHeader = its.getJSONObject("its.ItsPduHeader_element")

        val stationID = itsHeader.getString("its.stationID").toLong()
        val messageID = itsHeader.getString("its.messageID").toInt()

        // DENM
        val denmElement = its
            .getJSONObject("denm.DecentralizedEnvironmentalNotificationMessage_element")

        val managementElement = denmElement
            .getJSONObject("denm.management_element")

        val originatingStationID = managementElement
            .getJSONObject("denm.actionID_element")
            .getString("its.originatingStationID").toLong()

        val sequenceNumber = managementElement
            .getJSONObject("denm.actionID_element")
            .getString("its.sequenceNumber").toInt()

        // DENM time
        val detectionTime = managementElement.getString("denm.detectionTime").toLong()
        val referenceTime = managementElement.getString("denm.referenceTime").toLong()
        val termination = managementElement.optString("denm.termination") == "1"

        // ITS position
        val positionElement = managementElement
            .getJSONObject("denm.eventPosition_element")

        val lat = positionElement.getString("its.latitude").toDouble() / 10000000.0
        val lon = positionElement.getString("its.longitude").toDouble() / 10000000.0
        val altitude = positionElement
            .getJSONObject("its.altitude_element")
            .getString("its.altitudeValue").toDouble() / 100.0

        // DENM type
        val stationType = managementElement.getString("denm.stationType").toInt()

        // Situation element
        val eventTypeElement = denmElement
            .getJSONObject("denm.situation_element")
            .getJSONObject("denm.eventType_element")

        val causeCode = eventTypeElement
            .getString("its.causeCode").toInt()

        val subCauseCode = eventTypeElement
            .getString("its.subCauseCode").toInt()

        // Location element
        val locationElement = denmElement
            .getJSONObject("denm.location_element")

        // Path history
        val tracesCount = locationElement
            .getString("denm.traces").toInt()
        val tracesTree = locationElement
            .getJSONObject("denm.traces_tree")

        val pathHistory: MutableList<MutableList<Position>> = mutableListOf()

        for (i in 0..<tracesCount) {
            val trace = tracesTree
                .getJSONObject("Item $i")

            val waypointCount = trace
                .getString("its.PathHistory").toInt()
            val waypointsTree = trace
                .getJSONObject("its.PathHistory_tree")

            val waypoints: MutableList<Position> = mutableListOf()

            for(j in 0..<waypointCount) {
                val waypoint = waypointsTree
                    .getJSONObject("Item $j")
                    .getJSONObject("its.PathPoint_element")
                    .getJSONObject("its.pathPosition_element")

                val deltaLat = waypoint
                    .getString("its.deltaLatitude").toDouble()
                val deltaLon = waypoint
                    .getString("its.deltaLongitude").toDouble()
                val deltaAlt = waypoint
                    .getString("its.deltaAltitude").toDouble()

                waypoints.add(Position(deltaLat, deltaLon, deltaAlt))
            }
            pathHistory.add(waypoints)
        }

        MessageStorage.add(
            Denm(
                messageID,
                stationID,
                stationType,
                Position(lat,lon,altitude),
                originatingStationID,
                sequenceNumber,
                detectionTime,
                referenceTime,
                termination,
                causeCode,
                subCauseCode,
                pathHistory
            )
        )
    }

    private suspend fun parseCAM(json: JSONObject) {
        val layers = json
            .getJSONObject("_source")
            .getJSONObject("layers")

        val timeEpoch = layers
            .getJSONObject("frame")
            .getString("frame.time_epoch").toDouble()

        val its = layers
            .getJSONObject("its")

        // ITS PDU Header
        val itsHeader = its.getJSONObject("its.ItsPduHeader_element")

        val stationID = itsHeader.getString("its.stationID").toLong()
        val messageID = itsHeader.getString("its.messageID").toInt()

        // CAM
        val camParametersElement = its
            .optJSONObject("cam.CoopAwareness_element")
            ?.optJSONObject("cam.camParameters_element") ?: return // If it does not contain base element, then its useless

        // Low frequency container
        val basicVehicleContainerLowFrequencyElement = camParametersElement
            .optJSONObject("cam.lowFrequencyContainer_tree")
            ?.optJSONObject("cam.basicVehicleContainerLowFrequency_element")

        val pathHistory: MutableList<Position> = mutableListOf()
        var vehicleRole: Int? = null
        var vehicleLights: VehicleLights? = null

        if(basicVehicleContainerLowFrequencyElement != null) {

            val pathRecordCount = basicVehicleContainerLowFrequencyElement
                .optString("cam.pathHistory", "0").toInt()

            val pathRecords = basicVehicleContainerLowFrequencyElement
                .optJSONObject("cam.pathHistory_tree")

            // Get path history
            for (i in 0..<pathRecordCount) {

                if(pathRecords == null) break

                val pathData = pathRecords
                    .getJSONObject("Item $i")
                    .getJSONObject("its.PathPoint_element")
                    .getJSONObject("its.pathPosition_element")

                val deltaLat = pathData.getString("its.deltaLatitude").toDouble()
                val deltaLon = pathData.getString("its.deltaLongitude").toDouble()
                val deltaAlt = pathData.getString("its.deltaLongitude").toDouble()

                pathHistory.add(Position(deltaLat, deltaLon, deltaAlt))
            }

            // Vehicle exterior features
            val exteriorLightsTree = basicVehicleContainerLowFrequencyElement
                .getJSONObject("cam.exteriorLights_tree")

            val lowBeamHeadlightsOn = exteriorLightsTree
                .getString("its.ExteriorLights.lowBeamHeadlightsOn") == "1"
            val highBeamHeadlightsOn = exteriorLightsTree
                .getString("its.ExteriorLights.highBeamHeadlightsOn") == "1"
            val leftTurnSignalOn = exteriorLightsTree
                .getString("its.ExteriorLights.leftTurnSignalOn") == "1"
            val rightTurnSignalOn = exteriorLightsTree
                .getString("its.ExteriorLights.rightTurnSignalOn") == "1"
            val daytimeRunningLightsOn = exteriorLightsTree
                .getString("its.ExteriorLights.daytimeRunningLightsOn") == "1"
            val reverseLightsOn = exteriorLightsTree
                .getString("its.ExteriorLights.reverseLightOn") == "1"
            val fogLightsOn = exteriorLightsTree
                .getString("its.ExteriorLights.fogLightOn") == "1"
            val parkingLightsOn = exteriorLightsTree
                .getString("its.ExteriorLights.parkingLightsOn") == "1"

            vehicleLights = VehicleLights(
                lowBeamHeadlightsOn,
                highBeamHeadlightsOn,
                leftTurnSignalOn,
                rightTurnSignalOn,
                daytimeRunningLightsOn,
                reverseLightsOn,
                fogLightsOn,
                parkingLightsOn
            )

            // Vehicle role
            vehicleRole = basicVehicleContainerLowFrequencyElement
                .getString("cam.vehicleRole").toInt()
        }

        // High frequency container
        val basicVehicleContainerHighFrequencyElement = camParametersElement
            .optJSONObject("cam.highFrequencyContainer_tree")
            ?.optJSONObject("cam.basicVehicleContainerHighFrequency_element")

        val vehicleLengthElement = basicVehicleContainerHighFrequencyElement
            ?.getJSONObject("cam.vehicleLength_element")

        val vehicleLength = vehicleLengthElement
            ?.getString("its.vehicleLengthValue")?.toFloat()?.div(10.0f)
        val vehicleWidth = basicVehicleContainerHighFrequencyElement
            ?.getString("cam.vehicleWidth")?.toFloat()?.div(10.0f)

        val vehicleHeading: Float? = (basicVehicleContainerHighFrequencyElement
            ?.getJSONObject("cam.heading_element")
            ?.optString("its.headingValue")?.toFloatOrNull())?.div(10.0f)

        val vehicleSpeed: Float? = (basicVehicleContainerHighFrequencyElement
            ?.getJSONObject("cam.speed_element")
            ?.optString("its.speedValue")?.toFloatOrNull())?.div(100.0f)?.times(3.6f)

        // Basic Frequency container
        val basicContainerElement = camParametersElement
            .optJSONObject("cam.basicContainer_element")

        val referencePositionElement = basicContainerElement
            ?.getJSONObject("cam.referencePosition_element")

        val lat = referencePositionElement
            ?.getString("its.latitude")?.toDoubleOrNull()?.div(10000000.0)
        val lon = referencePositionElement
            ?.getString("its.longitude")?.toDoubleOrNull()?.div(10000000.0)
        val alt = referencePositionElement
            ?.getJSONObject("its.altitude_element")
            ?.getString("its.altitudeValue")?.toDoubleOrNull()?.div(100.0)

        val stationType = basicContainerElement
            ?.getString("cam.stationType")?.toIntOrNull()

        val position = if (lat == null || lon == null || alt == null) {
            null
        } else {
            Position(lat, lon, alt)
        }

        MessageStorage.add(
            Cam(
                messageID,
                stationID,
                stationType,
                position,
                vehicleSpeed,
                vehicleHeading,
                pathHistory,
                vehicleLength,
                vehicleWidth,
                vehicleRole,
                vehicleLights,
                timeEpoch
            )
        )
    }

    private suspend fun parseSPATEM(json: JSONObject) {
        val layers = json
            .getJSONObject("_source")
            .getJSONObject("layers")

        val its = layers
            .getJSONObject("its")

        // ITS PDU Header
        val itsHeader = its.getJSONObject("its.ItsPduHeader_element")

        val stationID = itsHeader.getString("its.stationID").toLong()
        val messageID = itsHeader.getString("its.messageID").toInt()

        // SPATEM
        val spatElement = its
            .getJSONObject("dsrc.SPAT_element")

        val intersectionCount = spatElement
            .getString("dsrc.intersections").toInt()

        val intersectionTree = spatElement
            .getJSONObject("dsrc.intersections_tree")

        val intersections: MutableList<SPATEMIntersection> = mutableListOf()

        for (i in 0..<intersectionCount) {
            val intersectionElement = intersectionTree
                .getJSONObject("Item $i")
                .getJSONObject("dsrc.IntersectionState_element")

            val name = intersectionElement
                .optString("dsrc.name")
            val timestamp = intersectionElement
                .getString("dsrc.timeStamp").toInt()
            val moy = intersectionElement
                .getString("dsrc.moy").toInt()

            val idElement = intersectionElement
                .getJSONObject("dsrc.id_element")
            val id = idElement
                .getString("dsrc.id").toInt()

            val statesCount = intersectionElement
                .getString("dsrc.states").toInt()
            val statesTree = intersectionElement
                .getJSONObject("dsrc.states_tree")

            val movementStates: MutableList<MovementState> = mutableListOf()

            for (j in 0..<statesCount) {
                val movementStateElement = statesTree
                    .getJSONObject("Item $j")
                    .getJSONObject("dsrc.MovementState_element")

                val movementName = movementStateElement
                    .optString("dsrc.movementName")

                val signalGroup = movementStateElement
                    .getString("dsrc.signalGroup").toInt()
                val stateTimeSpeed = movementStateElement
                    .getString("dsrc.state_time_speed").toInt()

                val stateTimeSpeedTree = movementStateElement
                    .getJSONObject("dsrc.state_time_speed_tree")

                val movementEvents: MutableList<MovementEvent> = mutableListOf()

                for (k in 0..<stateTimeSpeed) {
                    val movementEventElement = stateTimeSpeedTree
                        .getJSONObject("Item $k")
                        .getJSONObject("dsrc.MovementEvent_element")

                    val eventState = movementEventElement
                        .getString("dsrc.eventState").toInt()

                    val timingElement = movementEventElement
                        .optJSONObject("dsrc.timing_element")

                    val startTime = timingElement
                        ?.getString("dsrc.startTime")?.toInt()
                    val minEndTime = timingElement
                        ?.getString("dsrc.minEndTime")?.toInt()
                    val maxEndTime = timingElement
                        ?.getString("dsrc.maxEndTime")?.toInt()
                    val likelyTime = timingElement
                        ?.getString("dsrc.likelyTime")?.toInt()
                    val confidence = timingElement
                        ?.getString("dsrc.confidence")?.toInt()

                    movementEvents.add(
                        MovementEvent(
                            eventState,
                            startTime,
                            minEndTime,
                            maxEndTime,
                            likelyTime,
                            confidence
                        )
                    )
                }
                movementStates.add(
                    MovementState(signalGroup, movementName, movementEvents)
                )
            }
            intersections.add(
                SPATEMIntersection(id, timestamp, moy, name, movementStates)
            )
        }
        MessageStorage.add(
            Spatem(
                messageID,
                stationID,
                intersections
            )
        )
    }

    private suspend fun parseMAPEM(json: JSONObject) {
        val layers = json
            .getJSONObject("_source")
            .getJSONObject("layers")

        val its = layers
            .getJSONObject("its")

        // ITS PDU Header
        val itsHeader = its.getJSONObject("its.ItsPduHeader_element")

        val stationID = itsHeader.getString("its.stationID").toLong()
        val messageID = itsHeader.getString("its.messageID").toInt()

        // MAPEM
        val mapDataElement = its
            .getJSONObject("dsrc.MapData_element")

        val intersectionsCount = mapDataElement
            .getString("dsrc.intersections").toInt()

        val intersectionsTree = mapDataElement
            .getJSONObject("dsrc.intersections_tree")

        for (i in 0..<intersectionsCount) {
            val intersectionGeometryElement = intersectionsTree
                .getJSONObject("Item 0")
                .getJSONObject("dsrc.IntersectionGeometry_element")

            val refPointElement = intersectionGeometryElement
                .getJSONObject("dsrc.refPoint_element")

            val laneSetTree = intersectionGeometryElement
                .getJSONObject("dsrc.laneSet_tree")
            val laneCount = intersectionGeometryElement
                .getString("dsrc.laneSet").toInt()

            val lanes: MutableList<Lane> = mutableListOf()

            for (j in 0..<laneCount) {

                val genericLaneElement = laneSetTree
                    .getJSONObject("Item $j")
                    .getJSONObject("dsrc.GenericLane_element")

                // Lane nodes
                val nodeListTree = genericLaneElement
                    .getJSONObject("dsrc.nodeList_tree")

                val nodesTree = nodeListTree
                    .getJSONObject("dsrc.nodes_tree")
                val nodeCount = nodeListTree
                    .getString("dsrc.nodes").toInt()

                val nodes: MutableList<Node> = mutableListOf()

                for (k in 0..<nodeCount) {

                    val nodeXYElement = nodesTree
                        .getJSONObject("Item $k")
                        .getJSONObject("dsrc.NodeXY_element")

                    val deltaTree = nodeXYElement
                        .getJSONObject("dsrc.delta_tree")

                    val delta = nodeXYElement
                        .getString("dsrc.delta").toInt()

                    //what
                    val nodeXYNumElement = deltaTree
                        .getJSONObject("dsrc.node_XY" + (delta + 1) + "_element")

                    val x = nodeXYNumElement
                        .getString("dsrc.x").toInt()
                    val y = nodeXYNumElement
                        .getString("dsrc.y").toInt()

                    nodes.add(Node(x, y, delta))
                }

                val laneAttributesElement = genericLaneElement
                    .getJSONObject("dsrc.laneAttributes_element")

                val laneType = laneAttributesElement
                    .getString("dsrc.laneType").toInt()
                val laneID = genericLaneElement
                    .getString("dsrc.laneID").toLong()
                val ingressApproach = genericLaneElement
                    .optString("dsrc.ingressApproach").toIntOrNull()
                val egressApproach = genericLaneElement
                    .optString("dsrc.egressApproach").toIntOrNull()

                val directionalUseTree = laneAttributesElement
                    .getJSONObject("dsrc.directionalUse_tree")
                val laneDirectionIngressPath = directionalUseTree
                    .getString("dsrc.LaneDirection.ingressPath") == "1"
                val laneDirectionEgressPath = directionalUseTree
                    .getString("dsrc.LaneDirection.egressPath") == "1"

                // Connecting lanes
                val connectsTo = genericLaneElement
                    .optString("dsrc.connectsTo").toIntOrNull()
                val connectsToTree = genericLaneElement
                    .optJSONObject("dsrc.connectsTo_tree")

                val connectingLanes: MutableList<ConnectingLane> = mutableListOf()

                val count = connectsTo ?: 0

                for (k in 0..<count) {
                    val connectionElement = connectsToTree!!
                        .getJSONObject("Item $k")
                        .getJSONObject("dsrc.Connection_element")

                    val connectingLaneElement = connectionElement
                        .getJSONObject("dsrc.connectingLane_element")

                    val lane = connectingLaneElement
                        .getString("dsrc.lane").toInt()
                    val signalGroup = connectionElement
                        .getString("dsrc.signalGroup").toInt()
                    val connectionID = connectionElement
                        .getString("dsrc.connectionID").toInt()

                    val maneuverTree = connectingLaneElement
                        .getJSONObject("dsrc.maneuver_tree")
                    val maneuverStraightAllowed = maneuverTree
                        .getString("dsrc.AllowedManeuvers.maneuverStraightAllowed") == "1"
                    val maneuverLeftAllowed = maneuverTree
                        .getString("dsrc.AllowedManeuvers.maneuverLeftAllowed") == "1"
                    val maneuverRightAllowed = maneuverTree
                        .getString("dsrc.AllowedManeuvers.maneuverRightAllowed") == "1"

                    connectingLanes.add(
                        ConnectingLane(
                            lane,
                            connectionID,
                            signalGroup,
                            maneuverStraightAllowed,
                            maneuverLeftAllowed,
                            maneuverRightAllowed
                        )
                    )
                }
                lanes.add(
                    Lane(
                        laneType,
                        Mapem.laneTypes[laneType],
                        laneID,
                        ingressApproach,
                        egressApproach,
                        laneDirectionIngressPath,
                        laneDirectionEgressPath,
                        nodes,
                        connectingLanes
                    )
                )
            }

            val lat = refPointElement
                .getString("dsrc.lat").toDouble() /10000000.0
            val lon = refPointElement
                .getString("dsrc.long").toDouble() /10000000.0
            val alt = refPointElement
                .optString("dsrc.position3D.elevation").toDoubleOrNull()?.div(10.0)

            val laneWidth = intersectionGeometryElement
                .getString("dsrc.laneWidth").toFloat() / 100.0f
            val name = intersectionGeometryElement
                .optString("dsrc.name", "Unknown")

            val id = intersectionGeometryElement
                .getJSONObject("dsrc.id_element")
                .getString("dsrc.id").toLong()

            MessageStorage.add(
                Mapem(
                    messageID,
                    stationID,
                    Position(lat, lon, alt ?: 0.0),
                    id,
                    name,
                    laneWidth,
                    lanes
                )
            )
        }
    }

    private suspend fun parseSREM(json: JSONObject) {
        val layers = json
            .getJSONObject("_source")
            .getJSONObject("layers")

        val its = layers
            .getJSONObject("its")

        // ITS PDU Header
        val itsHeader = its.getJSONObject("its.ItsPduHeader_element")

        val stationID = itsHeader.getString("its.stationID").toLong()
        val messageID = itsHeader.getString("its.messageID").toInt()

        // SREM
        val signalRequestMessageElement = its
            .getJSONObject("dsrc.SignalRequestMessage_element")
        val timestamp = signalRequestMessageElement
            .getString("dsrc.timeStamp").toFloat()
        val sequenceNumber = signalRequestMessageElement
            .getString("dsrc.sequenceNumber").toInt()

        val requestCount = signalRequestMessageElement
            .getString("dsrc.requests").toInt()
        val requestTree = signalRequestMessageElement
            .getJSONObject("dsrc.requests_tree")

        val requests: MutableList<Request> = mutableListOf()

        for (i in 0..<requestCount) {

            val requestElement = requestTree
                .getJSONObject("Item $i")
                .getJSONObject("dsrc.SignalRequestPackage_element")
                .getJSONObject("dsrc.request_element")

            val idElement = requestElement
                .getJSONObject("dsrc.id_element")

            val id = idElement
                .getString("dsrc.id").toInt()
            val requestID = requestElement
                .getString("dsrc.requestID").toInt()
            val requestType = requestElement
                .getString("dsrc.requestType").toInt()

            val inboundLane = requestElement
                .getString("dsrc.inBoundLane").toInt()
            val outboundLane = requestElement
                .getString("dsrc.outBoundLane").toInt()

            val approachInbound = requestElement
                .getJSONObject("dsrc.inBoundLane_tree")
                .getString("dsrc.approach").toInt()

            val approachOutbound = requestElement
                .getJSONObject("dsrc.outBoundLane_tree")
                .getString("dsrc.approach").toInt()

            requests.add(
                Request(
                    id,
                    requestID,
                    requestType,
                    inboundLane,
                    outboundLane,
                    approachInbound,
                    approachOutbound
                )
            )
        }

        val requestorElement = signalRequestMessageElement
            .getJSONObject("dsrc.requestor_element")

        val id = requestorElement
            .getJSONObject("dsrc.id_tree")
            .getString("dsrc.stationID").toLong()

        val typeElement = requestorElement
            .getJSONObject("dsrc.type_element")
        val role = typeElement
            .getString("dsrc.role").toInt()
        val subRole = typeElement
            .getString("dsrc.subrole").toInt()

        val name = requestorElement
            .getString("dsrc.name")
        val routeName = requestorElement
            .getString("dsrc.routeName")

        MessageStorage.add(
            Srem(
                messageID,
                stationID,
                timestamp,
                sequenceNumber,
                requests,
                id,
                role,
                subRole,
                name,
                routeName
            )
        )
    }

    private suspend fun parseSSEM(json: JSONObject) {
        val layers = json
            .getJSONObject("_source")
            .getJSONObject("layers")

        val its = layers
            .getJSONObject("its")

        // ITS PDU Header
        val itsHeader = its.getJSONObject("its.ItsPduHeader_element")

        val stationID = itsHeader.getString("its.stationID").toLong()
        val messageID = itsHeader.getString("its.messageID").toInt()

        // SSEM
        val signalStatusMessageElement = its
            .getJSONObject("dsrc.SignalStatusMessage_element")

        val timestamp = signalStatusMessageElement
            .getString("dsrc.timeStamp").toInt()
        val sequenceNumber = signalStatusMessageElement
            .getString("dsrc.sequenceNumber").toInt()

        val statusTree = signalStatusMessageElement
            .getJSONObject("dsrc.signalStatusMessage.status_tree")

        val signalStatusElement = statusTree
            .getJSONObject("Item 0")
            .getJSONObject("dsrc.SignalStatus_element")

        val id = signalStatusElement
            .getJSONObject("dsrc.id_element")
            .getString("dsrc.id").toInt()   //intersection ID?

        val signalCount = signalStatusElement
            .getString("dsrc.sigStatus").toInt()
        val signalTree = signalStatusElement
            .getJSONObject("dsrc.sigStatus_tree")

        val responses: MutableList<Response> = mutableListOf()

        for (i in 0..<signalCount) {

            val signalStatusPackage = signalTree
                .getJSONObject("Item $i")
                .getJSONObject("dsrc.SignalStatusPackage_element")

            val requesterElement = signalStatusPackage
                .getJSONObject("dsrc.requester_element")

            // Vehicle id?
            val requesterID = requesterElement
                .getString("dsrc.id").toInt()
            val requesterStationID = requesterElement
                .getJSONObject("dsrc.id_tree")
                .getString("dsrc.stationID").toLong()

            val requestID = requesterElement
                .getString("dsrc.request").toInt()
            val requesterSequenceNumber = requesterElement
                .getString("dsrc.sequenceNumber").toInt()

            // Vehicle role and subrole
            val typeData = requesterElement
                .getJSONObject("dsrc.typeData_element")
            val role = typeData
                .getString("dsrc.role").toInt()
            val subrole = typeData
                .getString("dsrc.subrole").toInt()

            val inboundLane = signalStatusPackage
                .getString("dsrc.inboundOn").toInt()
            val outboundLane = signalStatusPackage
                .getString("dsrc.outboundOn").toInt()

            val approachInbound = signalStatusPackage
                .getJSONObject("dsrc.inboundOn_tree")
                .getString("dsrc.approach").toInt()
            val approachOutbound = signalStatusPackage
                .getJSONObject("dsrc.outboundOn_tree")
                .getString("dsrc.approach").toInt()

            // Result code
            val status = signalStatusPackage
                .getString("dsrc.signalStatusPackage.status").toInt()

            responses.add(
                Response(
                    requesterID,
                    requesterStationID,
                    requestID,
                    requesterSequenceNumber,
                    role,
                    subrole,
                    inboundLane,
                    outboundLane,
                    approachInbound,
                    approachOutbound,
                    status,
                    Response.statusString[status]
                )
            )
        }

        MessageStorage.add(
            Ssem(
                messageID,
                stationID,
                timestamp,
                sequenceNumber,
                id,
                responses
            )
        )
    }
}