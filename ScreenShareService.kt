package com.adda.chat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.adda.chat.call.CallManager

/** Visible foreground service required by Android while user-approved screen projection is active. */
class ScreenShareService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("আড্ডা — স্ক্রিন শেয়ার")
            .setContentText("আপনার স্ক্রিন অপর প্রান্তে শেয়ার হচ্ছে")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
            val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                (intent?.getParcelableExtra(EXTRA_DATA) as? Intent)
            }
            if (data == null || code != android.app.Activity.RESULT_OK) {
                stopSelf()
                return START_NOT_STICKY
            }
            // Start capture only after the projection foreground service is promoted.
            CallManager.startScreenShare(code, data)
            return START_NOT_STICKY
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "স্ক্রিন শেয়ার", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "adda_screen_share"
        private const val NOTIFICATION_ID = 9010
        private const val EXTRA_RESULT_CODE = "projection_result_code"
        private const val EXTRA_DATA = "projection_data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenShareService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            try { context.stopService(Intent(context, ScreenShareService::class.java)) } catch (_: Exception) {}
        }
    }
}
