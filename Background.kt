package com.adda.chat.util

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** ব্যাকগ্রাউন্ডে সবসময় চালু থাকার জন্য দরকারি সিস্টেম অনুমতিগুলো */
object Background {
    const val STEP_NONE = 0
    const val STEP_BATTERY = 1
    const val STEP_FULLSCREEN = 2

    fun batteryRestricted(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun needsFullScreenPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        return !ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    /** কোন ধাপ বাকি (প্রতিটি ধাপ একবারই জিজ্ঞেস করা হয়, বারবার বিরক্ত না করতে) */
    fun nextStep(ctx: Context): Int {
        val p = ctx.getSharedPreferences("adda", Context.MODE_PRIVATE)
        if (batteryRestricted(ctx) && !p.getBoolean("askedBattery", false)) return STEP_BATTERY
        if (needsFullScreenPermission(ctx) && !p.getBoolean("askedFullScreen", false)) return STEP_FULLSCREEN
        return STEP_NONE
    }

    fun markAsked(ctx: Context, step: Int) {
        val key = if (step == STEP_BATTERY) "askedBattery" else "askedFullScreen"
        ctx.getSharedPreferences("adda", Context.MODE_PRIVATE).edit().putBoolean(key, true).apply()
    }

    @SuppressLint("BatteryLife")
    fun openSettings(ctx: Context, step: Int) {
        val pkg = Uri.parse("package:${ctx.packageName}")
        try {
            when (step) {
                STEP_BATTERY -> ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                STEP_FULLSCREEN -> if (Build.VERSION.SDK_INT >= 34) ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (_: Exception) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }
}
