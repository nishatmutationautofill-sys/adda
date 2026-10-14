package com.adda.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

    Box(Modifier.fillMaxSize()) {
        when {
            Session.me == null && Session.hasToken -> LoadingScreen()
            Session.me == null -> AuthScreen()
            Session.activeFriend != null -> ChatScreen(requestCallPermission)
            else -> HomeScreen()
        }
        if (CallManager.phase != CallManager.Phase.None) {
            CallScreen(requestCallPermission)
        }
    }
}
