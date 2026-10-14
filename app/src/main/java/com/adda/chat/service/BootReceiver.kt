package com.adda.chat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adda.chat.data.Session

/** ফোন চালু হলে বা অ্যাপ আপডেটের পর লগইন থাকলে সংযোগ সার্ভিস আবার শুরু করে */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // AddaApp.onCreate আগেই Session.init করেছে, তাই টোকেন পড়া আছে
        if (Session.hasToken) ConnectionService.start(context)
    }
}
