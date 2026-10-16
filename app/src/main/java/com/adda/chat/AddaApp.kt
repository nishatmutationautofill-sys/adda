package com.adda.chat

import android.app.Application
import com.adda.chat.call.CallManager
import com.adda.chat.data.Session
import com.startapp.sdk.adsbase.StartAppSDK

class AddaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // টেস্ট মোড: true থাকলে Start.io টেস্ট বিজ্ঞাপন দেখায় (অ্যাপ/অ্যাকাউন্ট অনুমোদন ছাড়াই)।
        // ⚠ লাইভে দেওয়ার আগে অবশ্যই false করুন, নইলে আসল আয় হবে না।
        // ম্যানিফেস্টের পাশাপাশি কোডেও SDK চালু করা (Start.io App ID)
        StartAppSDK.init(this, START_IO_APP_ID, false)
        StartAppSDK.setTestAdsEnabled(ADS_TEST_MODE)
        CallManager.init(this)
        Session.init(this)
    }
}

private const val ADS_TEST_MODE = true
private const val START_IO_APP_ID = "209645230"
