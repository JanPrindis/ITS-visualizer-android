package utils.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import utils.storage.data.Cam
import utils.storage.data.Denm
import utils.storage.data.Mapem
import utils.storage.data.Spatem
import utils.storage.data.Srem
import utils.storage.data.Ssem
import utils.visualization.VisualizerInstance

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