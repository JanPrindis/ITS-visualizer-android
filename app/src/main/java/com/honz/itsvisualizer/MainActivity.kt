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
import com.google.android.material.navigationrail.NavigationRailView
import utils.socket.SocketService
import utils.storage.MessageCleanupService

class MainActivity : AppCompatActivity() {

    // Navigation
    private lateinit var navigationRail: NavigationRailView

    // Services
    private lateinit var socketService: SocketService
    private lateinit var cleanerService: Intent

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

        navigationRail = findViewById(R.id.navigation_rail)

        // Fragments
        if (savedInstanceState == null) {
            val mapFragment = MapFragment()
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.container, mapFragment, "MapFragment")
            fragmentTransaction.addToBackStack("MapFragment")
            fragmentTransaction.commit()
        }

        // Navigation
        navigationRail.setOnItemSelectedListener { item ->
            fragmentSwitch(item.itemId)
        }

        // Services
        cleanerService = Intent(this, MessageCleanupService::class.java)

        // Start the services
        startService(cleanerService)
        startService(Intent(this, SocketService::class.java))

        // Bind to SocketService
        bindService(Intent(this, SocketService::class.java), socketServiceConnection, Context.BIND_AUTO_CREATE)

    }

    private fun fragmentSwitch(fragmentID: Int): Boolean {
        val fragmentTransaction = supportFragmentManager.beginTransaction()

        val fragmentTag = when(fragmentID) {
            R.id.rail_menu_map -> "MapFragment"
            R.id.rail_menu_settings -> "SettingsFragment"
            else -> return false
        }

        var fragment = supportFragmentManager.findFragmentByTag(fragmentTag)

        if (fragment == null) {
            fragment = when(fragmentID) {
                R.id.rail_menu_map -> MapFragment()
                R.id.rail_menu_settings -> SettingsFragment()
                else -> return false
            }
        }

        fragmentTransaction.replace(R.id.container, fragment, fragmentTag)
        fragmentTransaction.commit()

        return true
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
