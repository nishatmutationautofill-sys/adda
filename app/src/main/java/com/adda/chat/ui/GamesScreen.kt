package com.adda.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
import java.util.UUID
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adda.chat.data.Api
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class GameRow(val rank: Int, val username: String, val score: Int)

@Composable
fun GamesScreen() {
    var selectedGame by remember { mutableStateOf("tap-rush") }
    var score by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(30) }
    var playing by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(0) }
    var leaderboard by remember { mutableStateOf<List<GameRow>>(emptyList()) }
    var status by remember { mutableStateOf("গেম বেছে নিয়ে খেলা শুরু করুন।") }
    var showClaim by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf("bkash") }
    var phone by remember { mutableStateOf("") }
    var claimStatus by remember { mutableStateOf("") }
    var coinBalance by remember { mutableIntStateOf(0) }
    var videosRemaining by remember { mutableIntStateOf(10) }
    var rewardedLoading by remember { mutableStateOf(false) }
    var rewardMessage by remember { mutableStateOf("ভিডিও সম্পূর্ণ করলে ১০ কয়েন পাবেন।") }
    var showRedeem by remember { mutableStateOf(false) }
    var redeemOperator by remember { mutableStateOf("grameenphone") }
    var redeemAmount by remember { mutableIntStateOf(10) }
    var redeemPhone by remember { mutableStateOf("") }
    var redeemStatus by remember { mutableStateOf("") }
    var redeemBusy by remember { mutableStateOf(false) }
    var redeemRequestKey by remember { mutableStateOf(UUID.randomUUID().toString()) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    suspend fun refreshWallet() {
        try {
            val w = Api.getObj("/api/rewards/wallet")
            coinBalance = w.optInt("balance", 0)
            videosRemaining = w.optInt("remainingToday", 10)
        } catch (_: Exception) { /* show a useful message when the user tries the action */ }
    }

    LaunchedEffect(Unit) { refreshWallet() }

    fun watchRewardedVideo() {
        if (rewardedLoading) return
        if (activity == null) { rewardMessage = "এই স্ক্রিনে বিজ্ঞাপন চালু করা যাচ্ছে না।"; return }
        rewardedLoading = true
        rewardMessage = "বিজ্ঞাপন প্রস্তুত করা হচ্ছে…"
        scope.launch {
            var sessionId = ""
            try {
                val session = Api.postObj("/api/rewards/video/start", JSONObject())
                sessionId = session.optString("sessionId")
                if (sessionId.isBlank()) throw IllegalStateException("ভিডিও সেশন তৈরি হয়নি")
                val ad = StartAppAd(activity)
                val activeSession = sessionId
                ad.setVideoListener(object : VideoListener {
                    override fun onVideoCompleted() {
                        scope.launch {
                            try {
                                val result = Api.postObj("/api/rewards/video/complete", JSONObject().put("sessionId", activeSession))
                                rewardMessage = result.optString("message", "কয়েন যোগ হয়েছে")
                                refreshWallet()
                            } catch (e: Exception) {
                                rewardMessage = "ভিডিও শেষ হয়েছে, কিন্তু কয়েন নিশ্চিত হয়নি। নেটওয়ার্ক ঠিক করে আবার ব্যালেন্স দেখুন।"
                            } finally { rewardedLoading = false }
                        }
                    }
                })
                ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                    override fun onReceiveAd(adInfo: Ad) {
                        activity.runOnUiThread {
                            rewardMessage = "ভিডিও চলছে… সম্পূর্ণ দেখুন।"
                            ad.showAd()
                            // If the user closes the ad early and no completion callback arrives,
                            // release the UI lock after a safety timeout without granting coins.
                            scope.launch {
                                delay(90_000)
                                if (rewardedLoading) {
                                    try { Api.postObj("/api/rewards/video/fail", JSONObject().put("sessionId", activeSession)) } catch (_: Exception) {}
                                    rewardedLoading = false
                                    rewardMessage = "ভিডিও সম্পূর্ণ হয়নি বা সেশন শেষ হয়েছে। কয়েন দেওয়া হয়নি; আবার চেষ্টা করতে পারেন."
                                    refreshWallet()
                                }
                            }
                        }
                    }
                    override fun onFailedToReceiveAd(adInfo: Ad?) {
                        scope.launch {
                            try { Api.postObj("/api/rewards/video/fail", JSONObject().put("sessionId", activeSession)) } catch (_: Exception) {}
                            rewardMessage = "এখন বিজ্ঞাপন পাওয়া যাচ্ছে না। কিছুক্ষণ পর আবার চেষ্টা করুন।" + (adInfo?.errorMessage?.takeIf { it.isNotBlank() }?.let { "\n(কারণ: $it)" } ?: "")
                            refreshWallet()
                            rewardedLoading = false
                        }
                    }
                })
            } catch (e: Exception) {
                if (sessionId.isNotBlank()) try { Api.postObj("/api/rewards/video/fail", JSONObject().put("sessionId", sessionId)) } catch (_: Exception) {}
                rewardMessage = e.message ?: "বিজ্ঞাপন চালু করা যায়নি। সার্ভার সংযোগ পরীক্ষা করুন।"
                refreshWallet()
                rewardedLoading = false
            }
        }
    }

    fun refreshBoard() {
        scope.launch {
            try {
                val obj = Api.getObj("/api/games/leaderboard?gameId=$selectedGame")
                val arr = obj.optJSONArray("leaderboard")
                leaderboard = (0 until (arr?.length() ?: 0)).map { i ->
                    val row = arr!!.getJSONObject(i)
                    GameRow(row.optInt("rank"), row.optString("username", "Player"), row.optInt("score"))
                }
            } catch (e: Exception) { status = e.message ?: "লিডারবোর্ড লোড করা যায়নি" }
        }
    }

    LaunchedEffect(selectedGame) { refreshBoard() }
    LaunchedEffect(playing) {
        if (playing) {
            while (seconds > 0 && playing) { delay(1000); seconds-- }
            if (seconds <= 0 && playing) {
                playing = false
                best = maxOf(best, score)
                try {
                    Api.postObj("/api/games/score", JSONObject().put("gameId", selectedGame).put("score", score).put("durationSeconds", 30))
                    status = "স্কোর সার্ভারে জমা হয়েছে। যোগ্যতা যাচাই করে Claim Reward ব্যবহার করুন।"
                    refreshBoard()
                } catch (e: Exception) { status = "স্কোর জমা হয়নি: ${e.message ?: "সার্ভার সমস্যা"}" }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Canvas).statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)) {
            Icon(Icons.Filled.Gamepad, null, tint = Color(0xFF6957D5), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Adda Games", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text("খেলুন • স্কোর করুন • রিওয়ার্ডের আবেদন করুন", fontSize = 12.sp, color = Muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("tap-rush" to "Tap Rush", "math-sprint" to "Math Sprint", "memory-match" to "Memory Match").forEach { (id, label) ->
                Surface(color = if (selectedGame == id) Color(0xFF6957D5) else Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).clickable(enabled = !playing) { selectedGame = id; score = 0; seconds = 30 }) {
                    Text(label, color = if (selectedGame == id) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when (selectedGame) { "math-sprint" -> "Math Sprint"; "memory-match" -> "Memory Match"; else -> "Tap Rush" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(if (playing) "সময় বাকি: $seconds সেকেন্ড" else "৩০ সেকেন্ডে যত বেশি সম্ভব পয়েন্ট অর্জন করুন", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Text(score.toString(), fontSize = 52.sp, fontWeight = FontWeight.Black, color = Color(0xFF6957D5), modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = {
                    if (!playing) { score = 0; seconds = 30; playing = true; status = "খেলা চলছে…" }
                    else score += when (selectedGame) { "math-sprint" -> 2; "memory-match" -> 3; else -> 1 }
                }, enabled = playing || seconds == 30, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text(if (playing) when (selectedGame) { "math-sprint" -> "সঠিক উত্তর! (+2)"; "memory-match" -> "কার্ড মেলান! (+3)"; else -> "এখানে ট্যাপ করুন! (+1)" } else "খেলা শুরু করুন", fontWeight = FontWeight.Bold)
                }
                Text("সেরা স্কোর: $best", color = Muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0EDFF)), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Redeem, null, tint = Color(0xFF6957D5), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Adda Rewards", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Ink)
                        Text("ব্যালেন্স: $coinBalance কয়েন • আজ বাকি: $videosRemaining/10 ভিডিও", fontSize = 12.sp, color = Muted)
                    }
                }
                Button(onClick = { watchRewardedVideo() }, enabled = !rewardedLoading && videosRemaining > 0, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(if (rewardedLoading) "বিজ্ঞাপন প্রস্তুত হচ্ছে…" else if (videosRemaining <= 0) "আজকের ভিডিও সীমা শেষ" else "▶ ভিডিও দেখুন • +১০ কয়েন", fontWeight = FontWeight.Bold)
                }
                Text(rewardMessage, fontSize = 12.sp, color = Muted)
                OutlinedButton(onClick = { showRedeem = true; redeemStatus = "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.Redeem, null); Spacer(Modifier.width(6.dp)); Text("কয়েন দিয়ে ফ্লেক্সিলোড রিডিম")
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refreshBoard() }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.EmojiEvents, null); Spacer(Modifier.width(5.dp)); Text("দৈনিক হাইস্কোর") }
            Button(onClick = { showClaim = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Redeem, null); Spacer(Modifier.width(5.dp)); Text("Claim Reward") }
        }
        Text(status, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        Text("আজকের লিডারবোর্ড", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(bottom = 6.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(leaderboard) { row ->
                Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("#${row.rank}", color = Color(0xFF6957D5), fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(42.dp))
                        Text(row.username, color = Ink, modifier = Modifier.weight(1f))
                        Text(row.score.toString(), color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (leaderboard.isEmpty()) item { Text("আজ এখনো কোনো স্কোর নেই। প্রথম খেলোয়াড় আপনিই হতে পারেন!", color = Muted, fontSize = 13.sp) }
        }
    }

    if (showClaim) AlertDialog(onDismissRequest = { showClaim = false }, title = { Text("Claim Reward") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("নির্দিষ্ট স্কোর অর্জন করলে আবেদন জমা দিতে পারবেন। রিওয়ার্ডের পরিমাণ এখনো নির্ধারিত নয়; আবেদন যাচাইয়ের পর ম্যানুয়ালি ব্যবস্থা নেওয়া হবে.", fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = method == "bkash", onClick = { method = "bkash" }); Text("বিকাশ")
                Spacer(Modifier.width(8.dp)); RadioButton(selected = method == "flexiload", onClick = { method = "flexiload" }); Text("Flexiload")
            }
            OutlinedTextField(value = phone, onValueChange = { phone = it.filter(Char::isDigit).take(14) }, label = { Text("মোবাইল নম্বর") }, singleLine = true)
            if (claimStatus.isNotBlank()) Text(claimStatus, color = Muted, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(onClick = {
        scope.launch {
            try {
                val res = Api.postObj("/api/games/claim", JSONObject().put("gameId", selectedGame).put("method", method).put("accountNumber", phone))
                claimStatus = res.optString("message", "আবেদন জমা হয়েছে; যাচাই অপেক্ষমাণ।")
            } catch (e: Exception) { claimStatus = e.message ?: "আবেদন জমা দেওয়া যায়নি" }
        }
    }) { Text("আবেদন জমা দিন") } }, dismissButton = { TextButton(onClick = { showClaim = false }) { Text("বন্ধ করুন") } })

    if (showRedeem) AlertDialog(
        onDismissRequest = { if (!redeemBusy) showRedeem = false },
        title = { Text("কয়েন দিয়ে ফ্লেক্সিলোড") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("বর্তমান ব্যালেন্স: $coinBalance কয়েন", fontWeight = FontWeight.Bold)
                Text("রিডিম রেট: প্রতি ৳১০ = ১,০০০ কয়েন (সার্ভার কনফিগে পরিবর্তনযোগ্য)", fontSize = 12.sp)
                Text("মোবাইল অপারেটর")
                listOf("grameenphone" to "Grameenphone", "robi" to "Robi", "banglalink" to "Banglalink", "teletalk" to "Teletalk").forEach { (id, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = redeemOperator == id, onClick = { redeemOperator = id })
                        Text(label)
                    }
                }
                Text("রিচার্জের পরিমাণ")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(10, 20, 50, 100).forEach { amount ->
                        FilterChip(selected = redeemAmount == amount, onClick = { redeemAmount = amount }, label = { Text("৳$amount") })
                    }
                }
                OutlinedTextField(value = redeemPhone, onValueChange = { redeemPhone = it.filter(Char::isDigit).take(11) }, label = { Text("মোবাইল নম্বর") }, singleLine = true)
                Text("অনুরোধটি অ্যাডমিন যাচাই করে ম্যানুয়ালি রিচার্জ করবেন। বাতিল হলে কয়েন ফেরত দেওয়া হবে।", fontSize = 11.sp, color = Muted)
                if (redeemStatus.isNotBlank()) Text(redeemStatus, color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = !redeemBusy, onClick = {
                scope.launch {
                    redeemBusy = true
                    try {
                        val result = Api.postObj("/api/rewards/redeem", JSONObject().put("operator", redeemOperator).put("amount", redeemAmount).put("phone", redeemPhone).put("requestKey", redeemRequestKey))
                        redeemStatus = result.optString("message", "রিডিম আবেদন জমা হয়েছে।")
                        redeemRequestKey = UUID.randomUUID().toString()
                        refreshWallet()
                    } catch (e: Exception) { redeemStatus = e.message ?: "রিডিম আবেদন করা যায়নি" }
                    finally { redeemBusy = false }
                }
            }) { Text(if (redeemBusy) "জমা হচ্ছে…" else "রিডিম আবেদন করুন") }
        },
        dismissButton = { TextButton(onClick = { if (!redeemBusy) showRedeem = false }) { Text("বন্ধ করুন") } }
    )

}

