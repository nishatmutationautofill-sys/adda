package com.adda.chat.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.adda.chat.MainActivity
import com.adda.chat.service.CallActionReceiver

object Notifier {
    private const val CH_SERVICE = "service"
    private const val CH_MESSAGES = "messages"
    // পুরনো "calls" চ্যানেলে ডিফল্ট শব্দ সেট করা ছিল (চ্যানেল পরে বদলানো যায় না), তাই নতুন আইডি
    private const val CH_CALLS = "calls_v2"
    private const val CH_MISSED = "missed"

    const val ID_SERVICE = 9000
    const val ID_CALL = 9001
    const val ID_MISSED = 9002

    const val ACTION_ACCEPT = "com.adda.chat.ACTION_ACCEPT_CALL"
    const val ACTION_DECLINE = "com.adda.chat.ACTION_DECLINE_CALL"
    const val ACTION_SHOW_CALL = "com.adda.chat.ACTION_SHOW_CALL"

    fun init(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel("calls")
            nm.createNotificationChannel(
                NotificationChannel(CH_SERVICE, "সংযোগ (ব্যাকগ্রাউন্ড)", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "অ্যাপ বন্ধ থাকলেও মেসেজ ও কল পেতে সংযোগ চালু রাখে"
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(NotificationChannel(CH_MESSAGES, "মেসেজ", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(
                NotificationChannel(CH_CALLS, "ইনকামিং কল", NotificationManager.IMPORTANCE_HIGH).apply {
                    // রিংটোন ও ভাইব্রেশন CallManager নিজে বাজায়, তাই চ্যানেলে শব্দ নেই
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
            nm.createNotificationChannel(NotificationChannel(CH_MISSED, "মিসড কল", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** সবসময় চালু থাকা সার্ভিসের স্থায়ী নোটিফিকেশন */
    fun service(ctx: Context, connected: Boolean, inCall: Boolean): Notification =
        NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("আড্ডা")
            .setContentText(
                when {
                    inCall -> "কল চলছে — ফিরে যেতে ট্যাপ করুন"
                    connected -> "সংযুক্ত — মেসেজ ও কল গ্রহণের জন্য প্রস্তুত"
                    else -> "সংযোগ হচ্ছে…"
                }
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(ctx))
            .build()

    @SuppressLint("MissingPermission")
    fun message(ctx: Context, from: String, text: String, key: Int) {
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(from)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(key, n)
    }

    /** ফুল-স্ক্রিন ইনকামিং কল (লক স্ক্রিনেও দেখায়) + রিসিভ/রিজেক্ট বাটন */
    @SuppressLint("MissingPermission")
    fun incomingCall(ctx: Context, from: String, video: Boolean) {
        if (!canNotify(ctx)) return
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val showIntent = Intent(ctx, MainActivity::class.java)
            .setAction(ACTION_SHOW_CALL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val fullScreen = PendingIntent.getActivity(ctx, 2, showIntent, flags)

        val acceptIntent = Intent(ctx, MainActivity::class.java)
            .setAction(ACTION_ACCEPT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val accept = PendingIntent.getActivity(ctx, 1, acceptIntent, flags)

        val declineIntent = Intent(ctx, CallActionReceiver::class.java).setAction(ACTION_DECLINE)
        val decline = PendingIntent.getBroadcast(ctx, 3, declineIntent, flags)

        val caller = Person.Builder().setName(from).setImportant(true).build()
        val n = NotificationCompat.Builder(ctx, CH_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(from)
            .setContentText(if (video) "ভিডিও কল আসছে" else "অডিও কল আসছে")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(55_000)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, accept).setIsVideo(video))
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_CALL, n)
    }

    fun cancelCall(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_CALL)
    }

    @SuppressLint("MissingPermission")
    fun missedCall(ctx: Context, from: String, video: Boolean) {
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_MISSED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle("মিসড কল")
            .setContentText(if (video) "$from — ভিডিও কল" else "$from — অডিও কল")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_MISSED, n)
    }
}
