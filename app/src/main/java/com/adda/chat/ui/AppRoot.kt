package com.adda.chat.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.adda.chat.call.CallManager
import com.adda.chat.data.Session
import com.adda.chat.util.Background

@Composable
fun AppRoot() {
    val ctx = LocalContext.current

    // নোটিফিকেশন পারমিশন (Android 13+)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // টোস্ট বার্তা
    val toast = Session.toastMsg
    LaunchedEffect(toast?.id) {
        toast?.let { Toast.makeText(ctx, it.text, Toast.LENGTH_SHORT).show() }
    }

    // কলের জন্য মাইক/ক্যামেরা পারমিশন
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = pendingAction
        pendingAction = null
        if (result.values.all { it }) action?.invoke()
        else Session.toast("কলের জন্য মাইক্রোফোন/ক্যামেরার অনুমতি দিন")
    }
    val requestCallPermission: (String, () -> Unit) -> Unit = { kind, action ->
        val needed = if (kind == "video") arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
        val allGranted = needed.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        if (allGranted) action() else {
            pendingAction = action
            permLauncher.launch(needed)
        }
    }

    // কল চলাকালে (বা আসার সময়) লক স্ক্রিনের ওপরে দেখানো ও স্ক্রিন জাগানো
    val activity = ctx as? Activity
    val callActive = CallManager.phase != CallManager.Phase.None
    LaunchedEffect(callActive) {
        activity ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 27) {
            activity.setShowWhenLocked(callActive)
            activity.setTurnScreenOn(callActive)
        } else {
            val f = android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (callActive) activity.window.addFlags(f) else activity.window.clearFlags(f)
        }
    }

    // ব্যাকগ্রাউন্ডে সবসময় চালু থাকার অনুমতি (ব্যাটারি অপ্টিমাইজেশন / ফুল-স্ক্রিন কল)
    var bgStep by remember { mutableStateOf(Background.STEP_NONE) }
    LaunchedEffect(Session.me != null, Session.startTick) {
        if (Session.me != null && bgStep == Background.STEP_NONE &&
            CallManager.phase == CallManager.Phase.None
        ) bgStep = Background.nextStep(ctx)
    }
    if (bgStep != Background.STEP_NONE) {
        val step = bgStep
        AlertDialog(
            onDismissRequest = { Background.markAsked(ctx, step); bgStep = Background.STEP_NONE },
            title = {
                Text(if (step == Background.STEP_BATTERY) "সবসময় কল ও মেসেজ পেতে" else "লক স্ক্রিনে কল দেখাতে")
            },
            text = {
                Text(
                    if (step == Background.STEP_BATTERY)
                        "অ্যাপ বন্ধ থাকলেও কল ও মেসেজ পেতে \"ব্যাটারি অপ্টিমাইজেশন\" থেকে আড্ডাকে বাদ দিন (পরের পর্দায় \"অনুমতি দিন\" চাপুন)।"
                    else
                        "ফোন লক থাকা অবস্থায় ইনকামিং কল পুরো স্ক্রিনে দেখাতে \"ফুল-স্ক্রিন নোটিফিকেশন\" চালু করুন।"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Background.markAsked(ctx, step)
                    bgStep = Background.STEP_NONE
                    Background.openSettings(ctx, step)
                }) { Text("অনুমতি দিন") }
            },
            dismissButton = {
                TextButton(onClick = { Background.markAsked(ctx, step); bgStep = Background.STEP_NONE }) { Text("পরে") }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        when {
            Session.me == null && Session.hasToken -> LoadingScreen()
            Session.me == null -> AuthScreen()
            Session.activeFriend != null -> ChatScreen(requestCallPermission)
            else -> MainShell()
        }
        if (CallManager.phase != CallManager.Phase.None) {
            CallScreen(requestCallPermission)
        }
    }
}
