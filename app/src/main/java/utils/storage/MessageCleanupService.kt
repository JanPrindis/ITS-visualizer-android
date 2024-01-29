package utils.storage

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class MessageCleanupService : Service() {
    private lateinit var timer: Timer
    private lateinit var sharedPreferences: SharedPreferences

    private var period = 10 // Seconds
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val timeValues = listOf(10, 30, 60, 180, 300, 600, 1800, -1)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)

        // Register listener for update call from settings
        val filter = IntentFilter("itsVisualizer.SETTINGS_UPDATED")
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)

        loadValues()
        initTimer()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            timer.cancel()
            loadValues()

            if(period != -1)
                initTimer()
        }
    }

    private fun initTimer() {
        timer = Timer()
        timer.scheduleAtFixedRate(object: TimerTask() {
            override fun run() {
                serviceScope.launch {
                    MessageStorage.clearUnusedItems()
                    Log.i("[Message Cleaner]", "Cleanup finished! ($period s)")
                }
            }
        }, 0, period * 5000L)
    }

    private fun loadValues() {
        val index = sharedPreferences.getInt("deletionTimeIndex", 2)

        period = if(index >= 0 && index < timeValues.size)
            timeValues[index]
        else
            60
    }

    override fun onDestroy() {
        super.onDestroy()

        timer.cancel()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }
}