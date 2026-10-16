package com.adda.chat

import android.app.Application
import com.adda.chat.call.CallManager
import com.adda.chat.data.Session

class AddaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CallManager.init(this)
        Session.init(this)
    }
}
