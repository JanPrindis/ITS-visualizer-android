package utils.storage

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import utils.storage.data.Cam
import utils.storage.data.Denm
import utils.storage.data.Mapem
import utils.storage.data.Request
import utils.storage.data.Spatem
import utils.storage.data.Srem
import utils.storage.data.Ssem

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
            val existingDenm = denmList.find { it.stationID == data.stationID }

            if (existingDenm != null) {
                val index = denmList.indexOf(existingDenm)
                denmList[index] = data
                denmList[index].modified = true
            } else {
                denmList.add(data)
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
            } else {
                camList.add(data)
                data.draw()
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
                    matchingMapem.draw()
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
                val index = mapemList.indexOf(existingMapem)
                mapemList[index].modified = true
            } else {
                mapemList.add(data)
                data.prepareForDraw()
                data.draw()
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
                            Log.i(
                                "[SREM UPDATE]",
                                "Station [${data.stationID}] updated REQUEST to: [${Request.requestTypeString[data.requests[0].requestType]}(${data.requests[0].requestType})]"
                            )
                            sremList.remove(srem)
                            data.modified = true
                            sremList.add(data)
                            tryMatchCam(data.stationID)
                            return
                        }
                    }
                }
            }

            Log.i(
                "[NEW SREM]",
                "Station [${data.stationID}] made REQUEST: [${Request.requestTypeString[data.requests[0].requestType]}(${data.requests[0].requestType})]"
            )
            sremList.add(data)

            tryMatchCam(data.stationID)
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
                    Log.i(
                        "[SSEM STORAGE]",
                        "Possible updated response received, overwriting old..."
                    )
                    ssemList.remove(ssem)
                    data.modified = true
                    ssemList.add(data)
                    replaced = true
                    break
                }
            }

            if (!replaced) {
                Log.i("[SSEM STORAGE]", "Possible updated response received, overwriting old...")
                ssemList.add(data)
            }

            // Try find matching request
            for (srem in sremList) {
                for (request in srem.requests) {
                    if (data.intersectionId == request.id) {
                        for (response in data.responses) {
                            if (response.requestId == request.requestId) {
                                Log.i(
                                    "[REQUEST-FOUND]",
                                    "Intersection [${data.intersectionId}], Request ID [${response.requestId}], for CAM [${response.requesterStationId}], changed STATE to: [${response.statusString}(${response.statusCode})]"
                                )
                                srem.modified = true
                                tryMatchCam(response.requesterStationId)
                                return
                            }
                        }
                    }
                }
            }

            for (response in data.responses) {
                Log.i(
                    "[REQUEST NOT FOUND]",
                    "Intersection [${data.intersectionId}], Request ID [${response.requestId}], for CAM [${response.requesterStationId}], changed STATE to: [${response.statusString}(${response.statusCode})]"
                )
                tryMatchCam(response.requesterStationId)
            }
        }
    }

    /**
     * Tries to find if CAM details are available in storage based on 'stationID' parameter.
     */
    private suspend fun tryMatchCam(stationID: Long) {
        val found = camList.find { it.stationID == stationID }

        if(found != null) {
            Log.i("[CAM FOUND]", "Cam [${found.stationID}] is in storage!")
        }
        else {
            Log.i("[CAM NOT FOUND]", "Cam is NOT in storage!")
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
                else
                    denmIterator.remove()
            }

            // CAM
            val camIterator = camList.iterator()
            while (camIterator.hasNext()) {
                val cam = camIterator.next()
                if (cam.modified)
                    cam.modified = false
                else {
                    cam.remove()
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
                    mapem.remove()
                    mapemIterator.remove()
                }
            }

            // SREM
            val sremIterator = sremList.iterator()
            while (sremIterator.hasNext()) {
                val srem = sremIterator.next()
                if (srem.modified)
                    srem.modified = false
                else
                    sremIterator.remove()
            }

            // SSEM
            val ssemIterator = ssemList.iterator()
            while (ssemIterator.hasNext()) {
                val ssem = ssemIterator.next()
                if (ssem.modified)
                    ssem.modified = false
                else
                    ssemIterator.remove()
            }
        }
    }

    suspend fun drawAll() {
        mutex.withLock {
            denmList.forEach { it.draw() }
            camList.forEach { it.draw() }
            mapemList.forEach { it.draw() }
        }
    }

    /**
     * Clears the entire Message storage
     */
    suspend fun clearStorage() {
        mutex.withLock {
            denmList.clear()
            camList.clear()
            spatemList.clear()
            mapemList.clear()
            sremList.clear()
            ssemList.clear()
        }
    }
}