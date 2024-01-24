package utils.socket

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import utils.storage.MessageParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException

class SocketService : Service() {

    private var serviceJob: Job? = null

    private lateinit var socket: Socket
    private var ipAddress: String? = null
    private var port = -1

    var attemptConnection  = true
    private var attemptCount = 0

    private val parser = MessageParser()

    inner class SocketServiceBinder: Binder() {
        fun getService(): SocketService = this@SocketService
    }
    private val binder = SocketServiceBinder()

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: Let user choose ipAddress and port number
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            ipAddress = "10.0.2.2"  // localhost from Emulator
            port = 12345
            connectToSocket()
        }
        return START_STICKY
    }

    /**
     * Will attempt to connect to TCP socket.
     * If the connection fails, it will try to reconnect unless 'attemptConnection' is set to *false*.
     */
    private fun connectToSocket() {
        while(true) {
            if (!attemptConnection) continue
            if (ipAddress.isNullOrEmpty() || port == -1) {
                // IP or Port is not set
                // TODO: Handle if port or IP is not set
                return
            }

            // Wait before retrying connection
            when(attemptCount) {
                0 -> {}
                in 1..5 -> Thread.sleep(1000)
                in 6..10 -> Thread.sleep(5000)
                else -> Thread.sleep(10000)
            }

            Log.i("[Socket Service]", "Attempting to connect...")

            try {
                val socket = Socket()
                val socketAddress = InetSocketAddress(ipAddress, port)

                socket.connect(socketAddress, 1000)

                // When connected successfully, reset attempt counter
                attemptCount = 0

                Log.i("[Socket Service]", "Connected!")

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val buffer = StringBuilder()

                var jsonObjectMayEnd = false

                while (serviceJob?.isActive == true) {
                    try {
                        val line = reader.readLine() ?: break

                        if (jsonObjectMayEnd) {
                            if (line.trim() == "{") {
                                jsonObjectMayEnd = false

                                if (buffer.startsWith('[')) {
                                    buffer.deleteCharAt(0)
                                }

                                buffer.deleteCharAt(buffer.lastIndex)
                                runBlocking {
                                    launch(Dispatchers.IO) {
                                        processData(buffer.toString())
                                    }.join()
                                }
                                buffer.clear()
                            }
                        }

                        buffer.append(line)

                        if (line.trim() == "},") {
                            jsonObjectMayEnd = true
                            continue
                        }
                    } catch (socketException: SocketException) {
                        throw socketException
                    } catch (e: Exception) {
                        e.printStackTrace()
                        connectToSocket()
                    }
                }

            } catch (e: Exception) {
                Log.e("[Socket Service]", "Connection failed!")

                if (::socket.isInitialized && !socket.isClosed) {
                    socket.close()
                }

                attemptCount++
            }
        }
    }

    fun stopConnection() {
        attemptConnection = false
        attemptCount = 0
    }

    fun startConnection() {
        attemptConnection = true
    }

    private suspend fun processData(data: String) {
        parser.parseJson(data)
    }

    override fun onDestroy() {
        super.onDestroy()
        socket.close()
    }
}