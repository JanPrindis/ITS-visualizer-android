package utils.socket

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.honz.itsvisualizer.StatusColor
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

    private var socket: Socket? = null
    private var ipAddress: String? = null
    private var port = -1

    private var attemptConnection  = true
    private var attemptCount = 0

    private lateinit var sharedPreferences: SharedPreferences
    private val parser = MessageParser(this)

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopConnection()
            loadValues()
            startConnection()
        }
    }

    private val socketReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(attemptConnection)
                stopConnection()
            else
                startConnection()

            // Return current state
            sendCurrentState()
        }
    }

    private val stateRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            sendCurrentState()
        }
    }

    private fun sendCurrentState() {
        val returnIntent = Intent("itsVisualizer.SERVICE_STATE")
        returnIntent.putExtra("socketState", attemptConnection)
        LocalBroadcastManager.getInstance(this@SocketService).sendBroadcast(returnIntent)
    }

    private fun loadValues() {
        ipAddress = sharedPreferences.getString("ipAddress", null)
        port = sharedPreferences.getInt("serverPort", -1)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)

        // Register listener for update call from settings, and socket control call
        val settingsFilter = IntentFilter("itsVisualizer.SETTINGS_UPDATED")
        val socketFilter = IntentFilter("itsVisualizer.TOGGLE_SOCKET_SERVICE")
        val socketReqFilter = IntentFilter("itsVisualizer.SOCKET_SERVICE_STATE_REQUEST")
        LocalBroadcastManager.getInstance(this).registerReceiver(settingsReceiver, settingsFilter)
        LocalBroadcastManager.getInstance(this).registerReceiver(socketReceiver, socketFilter)
        LocalBroadcastManager.getInstance(this).registerReceiver(stateRequestReceiver, socketReqFilter)

        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            loadValues()
            connectToSocket()
        }
    }

    /**
     * Will attempt to connect to TCP socket.
     * If the connection fails, it will try to reconnect unless 'attemptConnection' is set to *false*.
     */
    private fun connectToSocket() {
        while(serviceJob?.isActive == true) {
            if (!attemptConnection) continue
            if (ipAddress.isNullOrEmpty() || port == -1) {
                // IP or Port is not set
                continue
            }

            // Wait before retrying connection
            when(attemptCount) {
                0 -> {}
                in 1..5 -> Thread.sleep(1000)
                in 6..10 -> Thread.sleep(5000)
                else -> Thread.sleep(10000)
            }

            Log.i("[Socket Service]", "Attempting to connect to $ipAddress:$port")
            sendNotification(StatusColor.YELLOW, "Attempting to connect to $ipAddress:$port")

            try {
                val socket = Socket()
                val socketAddress = InetSocketAddress(ipAddress, port)

                socket.connect(socketAddress, 1000)

                // When connected successfully, reset attempt counter
                attemptCount = 0

                Log.i("[Socket Service]", "Connected!")
                sendNotification(StatusColor.GREEN, "Connected to $ipAddress:$port!")

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val buffer = StringBuilder()

                var jsonObjectMayEnd = false

                while (serviceJob?.isActive == true) {
                    try {
                        if (!attemptConnection) {
                            socket.close()
                            buffer.clear()
                            break
                        }
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

                if(attemptConnection)
                    sendNotification(StatusColor.RED, "Connection failed!")
                else
                    sendNotification(StatusColor.RED, "Disconnected")

                socket?.close()
                attemptCount++
            }
        }
    }

    private fun sendNotification(icon: StatusColor, text: String) {
        val statusIntent = Intent("itsVisualizer.SET_STATUS")
        statusIntent.putExtra("statusImg", icon.value)
        statusIntent.putExtra("statusStr", text)
        LocalBroadcastManager.getInstance(this@SocketService).sendBroadcast(statusIntent)
    }

    private fun stopConnection() {
        attemptConnection = false
        attemptCount = 0

        socket?.close()
        sendNotification(StatusColor.RED, "Disconnected")
    }

    private fun startConnection() {
        attemptConnection = true
    }

    private suspend fun processData(data: String) {
        parser.parseJson(data)
    }

    override fun onDestroy() {
        super.onDestroy()

        serviceJob?.cancel()
        socket?.close()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(settingsReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(socketReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateRequestReceiver)
    }
}