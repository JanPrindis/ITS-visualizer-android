package com.honz.itsvisualizer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MapFragment : Fragment() {

    private lateinit var connectionToggleFab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("[MAP FRAGMENT]", "onCreate()")

        // Signals from SocketService
        val stateFilter = IntentFilter("itsVisualizer.SERVICE_STATE")
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(stateReceiver, stateFilter)

        // TODO: Init map
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        connectionToggleFab = view.findViewById(R.id.connectionToggleFab)
        connectionToggleFab.setOnClickListener { toggleConnection() }

        // Update FAB icon based on current state
        val intent = Intent("itsVisualizer.SOCKET_SERVICE_STATE_REQUEST")
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)

        return view
    }

    /**
     * Gets boolean representing current state of socket to update FAB image
     */
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            when(intent?.getBooleanExtra("socketState", true)) {
                true -> connectionToggleFab.setImageResource(R.drawable.wifi)
                false -> connectionToggleFab.setImageResource(R.drawable.wifi_off)
                null -> {}
            }
        }
    }

    private fun toggleConnection() {
        val intent = Intent("itsVisualizer.TOGGLE_SOCKET_SERVICE")
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(stateReceiver)

    }
}