package com.adda.chat.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Compose/AndroidView-এর Context থেকে আসল Activity খুঁজে বের করে (বিজ্ঞাপনের জন্য দরকার) */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
