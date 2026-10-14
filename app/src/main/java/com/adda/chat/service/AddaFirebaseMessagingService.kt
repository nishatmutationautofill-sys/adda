package com.adda.chat.service

import com.adda.chat.data.Session
import com.adda.chat.util.Notifier
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Foreground push handler. When the app is backgrounded, FCM notification payloads
 * are displayed by Android automatically; tapping them opens the app.
 */
class AddaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Session.registerPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"].orEmpty()
        val title = message.notification?.title ?: data["title"] ?: "আড্ডা"
        val body = message.notification?.body ?: data["body"] ?: "আপনার নতুন আপডেট এসেছে"

        // Avoid duplicating the live Socket.IO message notification while app is open.
        if (Session.appInForeground) return

        when (type) {
            "message", "friend_request" -> {
                val sender = data["senderName"]?.takeIf { it.isNotBlank() } ?: title
                val key = data["senderUid"]?.hashCode() ?: title.hashCode()
                Notifier.message(applicationContext, sender, body, key)
            }
            else -> Notifier.message(applicationContext, title, body, title.hashCode())
        }
    }
}
