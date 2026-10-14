package com.adda.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.adda.chat.data.Session
import com.adda.chat.ui.AddaTheme
import com.adda.chat.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AddaTheme { AppRoot() }
        }
    }

    override fun onStart() {
        super.onStart()
        Session.appInForeground = true
    }

    override fun onStop() {
        Session.appInForeground = false
        super.onStop()
    }
}
