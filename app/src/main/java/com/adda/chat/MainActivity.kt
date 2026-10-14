package com.adda.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.adda.chat.call.CallManager
import com.adda.chat.data.Session
import com.adda.chat.service.ConnectionService
import com.adda.chat.ui.AddaTheme
import com.adda.chat.ui.AppRoot
import com.adda.chat.util.Notifier

class MainActivity : ComponentActivity() {

    private var acceptAfterPermission = false

    private val callPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (!acceptAfterPermission) return@registerForActivityResult
            acceptAfterPermission = false
            if (result.values.all { it }) CallManager.acceptCall()
            else Session.toast("কলের জন্য মাইক্রোফোন/ক্যামেরার অনুমতি দিন")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android app windows are protected from system screenshots and screen recording.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            AddaTheme { AppRoot() }
        }
        handleCallIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallIntent(intent)
    }

    /** নোটিফিকেশনের "রিসিভ" বাটন চাপলে সরাসরি কল গ্রহণ */
    private fun handleCallIntent(intent: Intent?) {
        if (intent?.action != Notifier.ACTION_ACCEPT) return
        intent.action = null
        if (CallManager.phase != CallManager.Phase.Incoming) return
        val needed = if (CallManager.callKind == "video")
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
        val ok = needed.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (ok) CallManager.acceptCall()
        else {
            acceptAfterPermission = true
            callPermLauncher.launch(needed)
        }
    }

    override fun onStart() {
        super.onStart()
        Session.appInForeground = true
        Session.startTick++
        // অ্যাপ খুললেই (ফোরগ্রাউন্ডে) ব্যাকগ্রাউন্ড সংযোগ সার্ভিস নিশ্চিত করি
        if (Session.hasToken) ConnectionService.start(this)
    }

    override fun onStop() {
        Session.appInForeground = false
        super.onStop()
    }
}
