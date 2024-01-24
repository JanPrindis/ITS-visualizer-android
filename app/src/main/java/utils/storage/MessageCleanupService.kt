package utils.storage

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class MessageCleanupService : Service() {
    private lateinit var timer: Timer
    // TODO: Allow user to change time period
    private val period = 10 * 1000L // 10 seconds
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        timer = Timer()
        timer.scheduleAtFixedRate(object: TimerTask() {
            override fun run() {
                serviceScope.launch {
                    MessageStorage.clearUnusedItems()
                    Log.i("[Message Cleaner]", "Cleanup finished!")
                }
            }
        }, 0, period)
    }

    override fun onDestroy() {
        super.onDestroy()

        timer.cancel()
    }
}