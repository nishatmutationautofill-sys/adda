package com.adda.chat.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.adda.chat.data.Session
import com.adda.chat.util.Notifier
import kotlinx.coroutines.launch

/**
 * অ্যাপ বন্ধ করলেও (রিসেন্ট থেকে সরালেও) প্রসেস বাঁচিয়ে রাখে, যাতে Socket.IO সংযোগ চালু থাকে
 * এবং মেসেজ/কল সাথে সাথে পৌঁছায়। একটি স্থায়ী (কমে-ভরা) নোটিফিকেশন দেখায় — Android এ এটা বাধ্যতামূলক।
 */
class ConnectionService : Service() {

    private var inCall = false
    private var callVideo = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        Notifier.init(applicationContext)
        acquireWakeLock()
        registerNetworkWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra(EXTRA_IN_CALL)) {
            inCall = intent.getBooleanExtra(EXTRA_IN_CALL, false)
            callVideo = intent.getBooleanExtra(EXTRA_VIDEO, false)
        }
        promote()
        // লগইন করা না থাকলে সার্ভিসের দরকার নেই (startForeground আগে ডাকা হয়েছে, তাই ক্র্যাশ হবে না)
        if (!Session.hasToken) {
            stopSelf()
            return START_NOT_STICKY
        }
        Session.scope.launch { Session.ensureConnected() }
        return START_STICKY
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun promote() {
        val notification = Notifier.service(this, Session.socketConnected, inCall)
        var full = 0
        var base = 0
        if (Build.VERSION.SDK_INT >= 34) {
            base = ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            full = base
        }
        if (inCall && Build.VERSION.SDK_INT >= 30) {
            if (granted(Manifest.permission.RECORD_AUDIO)) full = full or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (callVideo && granted(Manifest.permission.CAMERA)) full = full or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        try {
            ServiceCompat.startForeground(this, Notifier.ID_SERVICE, notification, full)
        } catch (e: Exception) {
            // মাইক/ক্যামেরা টাইপ ব্যাকগ্রাউন্ড থেকে শুরু করা না গেলে সাধারণ টাইপে ফিরে যাই
            try {
                ServiceCompat.startForeground(this, Notifier.ID_SERVICE, notification, base)
            } catch (_: Exception) {
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "adda:connection").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun registerNetworkWatcher() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // নেট ফিরলেই সাথে সাথে সকেট আবার যুক্ত করি
                    Session.scope.launch { Session.ensureConnected() }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        running = false
        try {
            netCallback?.let {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {
        }
        netCallback = null
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_IN_CALL = "inCall"
        private const val EXTRA_VIDEO = "video"

        @Volatile var running = false
            private set

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ConnectionService::class.java))
            } catch (_: Exception) {
                // ব্যাকগ্রাউন্ড থেকে শুরু করার অনুমতি না থাকলে পরে অ্যাপ খুললে চালু হবে
            }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, ConnectionService::class.java)) } catch (_: Exception) {}
        }

        /** কল শুরু/শেষে সার্ভিসের ধরন (মাইক/ক্যামেরা) বদলায় যাতে ব্যাকগ্রাউন্ডেও কল চলে */
        fun setCallState(ctx: Context, active: Boolean, video: Boolean) {
            // কল শেষের সময় সার্ভিস চালু না থাকলে/লগআউট হলে নতুন করে শুরু করার দরকার নেই
            if (!active && (!running || !Session.hasToken)) return
            try {
                val i = Intent(ctx, ConnectionService::class.java)
                    .putExtra(EXTRA_IN_CALL, active)
                    .putExtra(EXTRA_VIDEO, video)
                ContextCompat.startForegroundService(ctx, i)
            } catch (_: Exception) {
            }
        }

        /** সংযোগের অবস্থা বদলালে নোটিফিকেশনের লেখা হালনাগাদ */
        @Suppress("MissingPermission")
        fun refreshNotification(ctx: Context, inCall: Boolean = false) {
            if (!running) return
            try {
                val nm = NotificationManagerCompat.from(ctx)
                if (nm.areNotificationsEnabled()) {
                    nm.notify(Notifier.ID_SERVICE, Notifier.service(ctx, Session.socketConnected, inCall))
                }
            } catch (_: Exception) {
            }
        }
    }
}
