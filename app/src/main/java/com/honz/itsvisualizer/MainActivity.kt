package com.honz.itsvisualizer

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.navigationrail.NavigationRailView
import utils.socket.SocketService
import utils.storage.MessageCleanupService

enum class StatusColor(val value: Int) {
    RED(0),
    YELLOW(1),
    GREEN(2);
}

class MainActivity : AppCompatActivity() {

    // Navigation
    private lateinit var navigationRail: NavigationRailView

    // Status bar
    private lateinit var statusBar: TextView
    private var doUpdate = true

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val statusImg = intent?.getIntExtra("statusImg", -1)
            val statusString = intent?.getStringExtra("statusStr")

            val drawable = when(statusImg) {
                StatusColor.RED.value -> {
                    AppCompatResources.getDrawable(this@MainActivity, R.drawable.circle_red)
                }
                StatusColor.YELLOW.value -> {
                    if(doUpdate)
                        AppCompatResources.getDrawable(this@MainActivity, R.drawable.circle_yellow)
                    else null
                }
                StatusColor.GREEN.value -> {
                    if(doUpdate)
                        AppCompatResources.getDrawable(this@MainActivity, R.drawable.circle_green)
                    else null
                }
                else -> null
            }

            if(drawable != null)
                statusBar.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
            statusBar.text = statusString
        }
    }

    private val socketStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            doUpdate = intent?.getBooleanExtra("socketState", false) ?: false
        }
    }

    // Services
    private var socketService: ComponentName? = null
    private var cleanerService: ComponentName? = null

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

        // StatusBar
        statusBar = findViewById(R.id.statusDescription)
    }

    override fun onStart() {
        super.onStart()

        // Signals to change status bar
        val statusFilter = IntentFilter("itsVisualizer.SET_STATUS")
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, statusFilter)

        // Signals from SocketService
        val stateFilter = IntentFilter("itsVisualizer.SERVICE_STATE")
        LocalBroadcastManager.getInstance(this).registerReceiver(socketStateReceiver, stateFilter)

        // Services
        socketService = startService(Intent(this, SocketService::class.java))
        cleanerService = startService(Intent(this, MessageCleanupService::class.java))
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

    override fun onStop() {
        super.onStop()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(socketStateReceiver)

        stopService(Intent(this, SocketService::class.java))
        stopService(Intent(this, MessageCleanupService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(socketStateReceiver)

        stopService(Intent(this, SocketService::class.java))
        stopService(Intent(this, MessageCleanupService::class.java))
    }
}
