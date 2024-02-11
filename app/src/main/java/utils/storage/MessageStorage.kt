package utils.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import utils.visualization.VisualizerInstance

/**
 * This data here is just for UI testing, it is not real data and does not make sense in the real world
 * It is added to storage and displayed after pressing the "testFab" on MapFragment
 */
object TestingData {
    val testCam = Cam(
        0,
        0,
        7,
        Position(49.8359823761241, 18.158047008184024, 0.0),
        22.1f,
        313.0f,
        mutableListOf(
            Position(-834.960539393137, 1335.4244686780703, 0.0),
            Position(-2525.7605471296074, -214.57672119140625, 0.0),
            Position(-1055.2874236680054, 2843.14155582166, 0.0),
            Position(311.39652833189757, 1046.0615157725783, 0.0),
            Position(6694.976860970314, 15985.965728617657, 0.0)),
        3.1f,
        1.8f,
        9,
        VehicleLights(
            lowBeamHeadLightsOn = true,
            highBeamHeadLightsOn = true,
            leftTurnSignalOn = true,
            rightTurnSignalOn = true,
            daytimeRunningLightsOn = false,
            reverseLightsOn = false,
            fogLightsOn = false,
            parkingLightsOn = false
        ),
        0.0
        )

    val testDenm = Denm(
        1,
        11,
        7,
        Position(49.83548541583945, 18.158451453741396, 0.0),
        0, 0, 0, 0,
        false,
        94, 1,
        mutableListOf(
            mutableListOf(
                Position(2387.385274147391, -1823.9021301269531, 0.0),
                Position(3131.2627951507466, -3433.227539026973, 0.0)
            )
        )
    )

    val testSrem = Srem(
        2,
        0,
        0f,
        0,
        mutableListOf(Request(0, 0, 0, 2, 0, 0, 0)),
        0,
        9, 0,
        "Test",
        "Test route"
    )

    val testSsem = Ssem(
        3,
        2,
        0,
        1,
        0,
        mutableListOf(Response(0, 0, 0, 0, 9, 0,0, 0,0, 0, 4, "Granted"))
    )

    val testMapem = Mapem(
        4, 3,
        Position(49.83560478453323, 18.158636526163423, 0.0),
        1, "Test Intersection",
        0f,
        mutableListOf(
            Lane(
                0, "Vehicle",
                0, null, null, directionIngressPath = true, directionEgressPath = false,
                nodes = mutableListOf(
                    Node(2601, 1678, 0),
                    Node(3889, 1591, 0)
                ),
                connectingLanes = mutableListOf(
                    ConnectingLane(0, 1, 0,
                        maneuverStraightAllowed = false,
                        maneuverLeftAllowed = true,
                        maneuverRightAllowed = false
                    )
                )
            ),
            Lane(
                0, "Vehicle",
                1, null, null, directionIngressPath = true, directionEgressPath = false,
                nodes = mutableListOf(
                    Node(2611, 1678, 0),
                    Node(3895, 1591, 0)
                ),
                connectingLanes = mutableListOf(
                    ConnectingLane(1, 2, 1,
                        maneuverStraightAllowed = true,
                        maneuverLeftAllowed = false,
                        maneuverRightAllowed = false
                    )
                )
            ),
            Lane(
                0, "Vehicle",
                2, null, null, directionIngressPath = true, directionEgressPath = false,
                nodes = mutableListOf(
                    Node(2621, 1678, 0),
                    Node(3899, 1591, 0)
                ),
                connectingLanes = mutableListOf(
                    ConnectingLane(2, 3, 2,
                        maneuverStraightAllowed = false,
                        maneuverLeftAllowed = false,
                        maneuverRightAllowed = true
                    )
                )
            )
        )
    )

    val testSpatem = Spatem(
        5, 3,
        mutableListOf(
            SPATEMIntersection(
                1, 0, 0, "Test Intersection",
                mutableListOf(
                    MovementState(0, "",
                        mutableListOf(MovementEvent(3, 0, 0, 300, 600, null))
                    ),
                    MovementState(1, "",
                        mutableListOf(MovementEvent(4, 0, 0, 300, 600, null))
                    ),
                    MovementState(2, "",
                        mutableListOf(MovementEvent(5, 0, 0, 300, 600, null))
                    )
                )
            )
        )
    )
}

object MessageStorage {
    private val mutex = Mutex()

    private val denmList: MutableList<Denm> = mutableListOf()
    private val camList: MutableList<Cam> = mutableListOf()
    private val spatemList: MutableList<Spatem> = mutableListOf()
    private val mapemList: MutableList<Mapem> = mutableListOf()
    private val sremList: MutableList<Srem> = mutableListOf()
    private val ssemList: MutableList<Ssem> = mutableListOf()

    /**
     * Will either add new DENM message to storage or update existing one.
     */
    suspend fun add(data: Denm) {
        mutex.withLock {
            val existingDenm = denmList.find { it.stationID == data.stationID && it.sequenceNumber == data.sequenceNumber }

            if (existingDenm != null) {
                val index = denmList.indexOf(existingDenm)

                if(data.termination) {
                    VisualizerInstance.visualizer?.removeDenm(denmList[index])
                    denmList.removeAt(index)
                    return
                }
                denmList[index].update(data)
            } else {
                denmList.add(data)
                data.calculatePathHistory()
                VisualizerInstance.visualizer?.drawDenm(data)
            }
        }
    }

    /**
     * Will either add new CAM message to storage or update existing one.
     */
    suspend fun add(data: Cam) {
        mutex.withLock {
            val existingCam = camList.find { it.stationID == data.stationID }

            if (existingCam != null) {
                existingCam.update(data)
                VisualizerInstance.visualizer?.drawCam(existingCam)
            } else {
                data.calculatePathOffset()
                camList.add(data)
                VisualizerInstance.visualizer?.drawCam(data)
            }
        }
    }

    /**
     * Will either add new SPATEM message to storage or update existing one.
     */
    suspend fun add(data: Spatem) {
        mutex.withLock {
            val existingSpatem = spatemList.find { it.stationID == data.stationID }

            if (existingSpatem != null) {
                val index = spatemList.indexOf(existingSpatem)
                spatemList[index] = data
                spatemList[index].modified = true
            } else {
                spatemList.add(data)
            }

            for (intersection in data.intersections) {
                val matchingMapem = mapemList.find { it.intersectionID == intersection.id.toLong() }

                if(matchingMapem != null) {
                    matchingMapem.latestSpatem = intersection
                    matchingMapem.modified = true
                    VisualizerInstance.visualizer?.drawMapem(matchingMapem)
                }
            }
        }
    }

    /**
     * Will either add new MAPEM message to storage or update existing one.
     */
    suspend fun add(data: Mapem) {
        mutex.withLock {
            val existingMapem = mapemList.find { it.intersectionID == data.intersectionID }

            if (existingMapem != null) {
                // Not redrawing, because not expecting sudden change in intersection geometry
                val index = mapemList.indexOf(existingMapem)
                mapemList[index].modified = true
            } else {
                mapemList.add(data)
                data.prepareForDraw()
                VisualizerInstance.visualizer?.drawMapem(data)
            }
        }
    }

    /**
     * Will either add new SREM message to storage or update existing one.
     */
    suspend fun add(data: Srem) {
        mutex.withLock {
            for (srem in sremList) {
                if (srem.stationID == data.stationID) {
                    for (existingRequest in srem.requests) {
                        if (existingRequest.id == data.requests.first().id) {
                            sremList.remove(srem)
                            data.modified = true
                            sremList.add(data)
                            camList.find { it.stationID == data.stationID }?.latestSrem = data
                            return
                        }
                    }
                }
            }
            sremList.add(data)
            camList.find { it.stationID == data.stationID }?.latestSrem = data
        }
    }

    /**
     * Will either add new SSEM message to storage or update existing one.
     * Also will try to find matching SREM (request).
     */
    suspend fun add(data: Ssem) {

        mutex.withLock {
            var replaced = false

            for (ssem in ssemList) {
                if (ssem.intersectionId == data.intersectionId) {
                    ssemList.remove(ssem)
                    data.modified = true
                    ssemList.add(data)
                    replaced = true
                    break
                }
            }

            if (!replaced) {
                ssemList.add(data)
            }

            // Try find matching request
            for (srem in sremList) {
                for (request in srem.requests) {
                    if (data.intersectionId == request.id) {
                        for (response in data.responses) {
                            if (response.requestId == request.requestId) {
                                srem.latestSsem = data
                                srem.modified = true
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * This function goes through all lists and checks the 'modified' attribute.
     * If an item was modified since the last check, it will be kept, and the 'modified'
     * attribute will be reset. If it was not modified, it is considered old and is deleted.
     */
    suspend fun clearUnusedItems() {
        mutex.withLock {
            // DENM
            val denmIterator = denmList.iterator()
            while (denmIterator.hasNext()) {
                val denm = denmIterator.next()
                if (denm.modified)
                    denm.modified = false
                else {
                    VisualizerInstance.visualizer?.removeDenm(denm)
                    denmIterator.remove()
                }
            }

            // CAM
            val camIterator = camList.iterator()
            while (camIterator.hasNext()) {
                val cam = camIterator.next()
                if (cam.modified)
                    cam.modified = false
                else {
                    VisualizerInstance.visualizer?.removeCam(cam)
                    camIterator.remove()
                }
            }

            // SPATEM
            val spatemIterator = spatemList.iterator()
            while (spatemIterator.hasNext()) {
                val spatem = spatemIterator.next()
                if (spatem.modified)
                    spatem.modified = false
                else
                    spatemIterator.remove()
            }

            // MAPEM
            val mapemIterator = mapemList.iterator()
            while (mapemIterator.hasNext()) {
                val mapem = mapemIterator.next()
                if (mapem.modified)
                    mapem.modified = false
                else {
                    VisualizerInstance.visualizer?.removeMapem(mapem)
                    mapemIterator.remove()
                }
            }

            // SREM
            val sremIterator = sremList.iterator()
            while (sremIterator.hasNext()) {
                val srem = sremIterator.next()
                if (srem.modified)
                    srem.modified = false
                else {
                    camList.find { it.latestSrem == srem }?.latestSrem = null
                    sremIterator.remove()
                }
            }

            // SSEM
            val ssemIterator = ssemList.iterator()
            while (ssemIterator.hasNext()) {
                val ssem = ssemIterator.next()
                if (ssem.modified)
                    ssem.modified = false
                else {
                    sremList.find { it.latestSsem == ssem }?.latestSsem = null
                    ssemIterator.remove()
                }
            }
        }
    }

    /**
     * Calls draw for all drawable elements
     */
    suspend fun drawAll() {
        mutex.withLock {
            denmList.forEach { VisualizerInstance.visualizer?.drawDenm(it) }
            camList.forEach { VisualizerInstance.visualizer?.drawCam(it) }
            mapemList.forEach {  VisualizerInstance.visualizer?.drawMapem(it) }
        }
    }

    /**
     * Clears the entire Message storage
     */
    suspend fun clearStorage() {
        mutex.withLock {
            VisualizerInstance.visualizer?.removeAllMarkers()

            denmList.clear()
            camList.clear()
            spatemList.clear()
            mapemList.clear()
            sremList.clear()
            ssemList.clear()
        }
    }
}