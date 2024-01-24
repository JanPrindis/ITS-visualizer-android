package com.honz.itsvisualizer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.google.android.material.floatingactionbutton.FloatingActionButton
import utils.socket.SocketService
import utils.storage.MessageCleanupService

class MainActivity : AppCompatActivity() {
    private lateinit var socketService: SocketService
    private lateinit var cleanerService: Intent

    private lateinit var socketToggleFab: FloatingActionButton

    private var socketServiceBound = false

    private val socketServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SocketService.SocketServiceBinder
            socketService = binder.getService()
            socketServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            socketServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Services
        cleanerService = Intent(this, MessageCleanupService::class.java)

        // Start the services
        startService(cleanerService)
        startService(Intent(this, SocketService::class.java))

        // Bind to SocketService
        bindService(Intent(this, SocketService::class.java), socketServiceConnection, Context.BIND_AUTO_CREATE)

        // FAB TEST
        socketToggleFab = findViewById(R.id.socketToggleFab)
        socketToggleFab.setOnClickListener {
            Log.i("[FAB]", "CLICKED")

            if(!socketService.attemptConnection) {
                socketService.startConnection()
                socketToggleFab.setImageResource(R.drawable.wifi)
            }
            else {
                socketService.stopConnection()
                socketToggleFab.setImageResource(R.drawable.wifi_off)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unbind from the service
        if (socketServiceBound) {
            unbindService(socketServiceConnection)
            socketServiceBound = false
        }
    }
}
