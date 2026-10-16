package com.adda.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adda.chat.data.Session

@Composable
fun AuthScreen() {
    var signup by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = !Session.busy && username.isNotBlank() && password.isNotEmpty()

    Box(Modifier.fillMaxSize().background(BubbleBrush)) {
        // সাজসজ্জার ভাসমান ইমোজি ও রঙিন বৃত্ত
        Box(Modifier.size(220.dp).offset(x = (-70).dp, y = (-60).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
        Box(Modifier.align(Alignment.TopEnd).size(160.dp).offset(x = 50.dp, y = 120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(92.dp).shadow(20.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("আ", fontSize = 52.sp, fontWeight = FontWeight.Black, color = Accent)
            }
            Spacer(Modifier.height(16.dp))
            Text("আড্ডা", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("বন্ধুদের সাথে জমিয়ে আড্ডা 🔥", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(26.dp))

            Surface(shape = RoundedCornerShape(32.dp), color = Color.White, shadowElevation = 16.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Them).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ModeTab("লগইন", !signup, Modifier.weight(1f)) { signup = false }
                        ModeTab("সাইন আপ", signup, Modifier.weight(1f)) { signup = true }
                    }
                    Spacer(Modifier.height(18.dp))
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Line,
                        focusedContainerColor = Color(0xFFF3F8FE), unfocusedContainerColor = Color(0xFFF3F8FE),
                        focusedLabelColor = Accent
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("ইউজারনেম") },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("পাসওয়ার্ড") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(20.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (Session.authError.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(Session.authError, color = Danger, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(50))
                            .background(if (canSubmit) BubbleBrush else SolidColor(Line))
                            .clickable(enabled = canSubmit) { Session.authenticate(username, password, signup) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (Session.busy) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (signup) "অ্যাকাউন্ট খুলুন 🚀" else "লগইন করুন",
                                fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                                color = if (canSubmit) Color.White else Muted
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(Session.connectionLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(if (selected) BubbleBrush else SolidColor(Color.Transparent))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.ExtraBold, color = if (selected) Color.White else Muted)
    }
}

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(BubbleBrush).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(Session.connectionLabel, color = Color.White)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { Session.logout() }) { Text("লগআউট", color = Color.White) }
    }
}
