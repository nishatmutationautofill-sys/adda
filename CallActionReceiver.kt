package com.adda.chat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adda.chat.call.CallManager
import com.adda.chat.util.Notifier

/** কল নোটিফিকেশনের "রিজেক্ট" বাটন — অ্যাপ না খুলেই কল কেটে দেয় */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Notifier.ACTION_DECLINE) CallManager.declineCall()
    }
}
