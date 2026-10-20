package com.adda.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adda.chat.data.Api
import com.adda.chat.data.Session
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray

private data class MicroPost(val id:String, val uid:String, val username:String, val text:String, val createdAt:String, val likes:Int, val liked:Boolean, val comments:List<String>)

@Composable
fun MicroblogScreen() {
    var draft by remember { mutableStateOf("") }
    var posts by remember { mutableStateOf(listOf<MicroPost>()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val me = Session.me?.uid.orEmpty()
    fun parse(arr: JSONArray): List<MicroPost> = (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val cs = o.optJSONArray("comments") ?: JSONArray()
        MicroPost(o.optString("id"), o.optString("uid"), o.optString("username","ব্যবহারকারী"),
            o.optString("text"), o.optString("createdAt"), o.optInt("likes"), o.optBoolean("liked"),
            (0 until cs.length()).mapNotNull { j -> cs.optJSONObject(j)?.let { "${it.optString("username")}: ${it.optString("text")}" } })
    }
    // Endpoint returns {posts:[...]} rather than a bare array.
    LaunchedEffect(Unit) {
        busy=true
        try { val o=Api.getObj("/api/microblog/posts"); posts=parse(o.optJSONArray("posts") ?: JSONArray()); error="" }
        catch(e:Exception) { error=e.message ?: "পোস্ট লোড হয়নি" }
        busy=false
    }
    Column(Modifier.fillMaxSize().background(Canvas).padding(16.dp)) {
        Text("মাইক্রোব্লগ", fontSize=24.sp, fontWeight=FontWeight.Bold, color=Ink)
        Text("ছোট পোস্ট, মতামত ও মন্তব্য শেয়ার করুন", color=Muted, fontSize=13.sp)
        Spacer(Modifier.height(12.dp))
        Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(value=draft, onValueChange={ if(it.length<=1000) draft=it }, modifier=Modifier.fillMaxWidth(),
                    placeholder={ Text("আপনার মনের কথা লিখুন…") }, minLines=3, maxLines=5)
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                    Text("${draft.length}/1000", color=Muted, fontSize=12.sp)
                    Button(enabled=draft.isNotBlank() && !busy, onClick={
                        scope.launch {
                            busy=true
                            try {
                                val o=Api.postObj("/api/microblog/posts", JSONObject().put("text", draft.trim()))
                                val list=posts.toMutableList()
                                list.add(0, MicroPost(o.optString("id"),o.optString("uid"),o.optString("username"),o.optString("text"),o.optString("createdAt"),0,false, emptyList()))
                                posts=list; draft=""; error=""
                            } catch(e:Exception) { error=e.message ?: "পোস্ট করা যায়নি" }
                            busy=false
                        }
                    }) { Icon(Icons.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text("পোস্ট করুন") }
                }
            }
        }
        if(error.isNotBlank()) Text(error, color=MaterialTheme.colorScheme.error, modifier=Modifier.padding(vertical=8.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(posts, key={it.id}) { p ->
                Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(18.dp), modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.username, color=Ink, fontWeight=FontWeight.Bold)
                                Text(p.createdAt.replace("T"," ").take(16), color=Muted, fontSize=11.sp)
                            }
                            if(p.uid==me) IconButton(onClick={
                                scope.launch { try { Api.request("DELETE","/api/microblog/posts/${p.id}"); posts=posts.filterNot{it.id==p.id} } catch(e:Exception){ error=e.message ?: "মুছতে পারেনি" } }
                            }) { Icon(Icons.Filled.Delete, contentDescription="পোস্ট মুছুন", tint=Muted) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(p.text, color=Ink, fontSize=16.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={
                                scope.launch { try {
                                    val r=Api.postObj("/api/microblog/posts/${p.id}/like", JSONObject())
                                    posts=posts.map { if(it.id==p.id) it.copy(liked=r.optBoolean("liked"), likes=r.optInt("likes")) else it }
                                } catch(e:Exception){ error=e.message ?: "রিঅ্যাকশন ব্যর্থ" } }
                            }) { Icon(Icons.Filled.Favorite, null, tint=if(p.liked) Color(0xFFE54865) else Muted); Spacer(Modifier.width(4.dp)); Text("পছন্দ ${p.likes}") }
                            var comment by remember(p.id) { mutableStateOf("") }
                            Spacer(Modifier.weight(1f))
                            OutlinedTextField(value=comment, onValueChange={if(it.length<=300) comment=it}, modifier=Modifier.weight(2f), placeholder={Text("মন্তব্য")}, singleLine=true)
                            IconButton(enabled=comment.isNotBlank(), onClick={
                                scope.launch { try {
                                    Api.postObj("/api/microblog/posts/${p.id}/comments", JSONObject().put("text",comment.trim()))
                                    comment=""; val o=Api.getObj("/api/microblog/posts"); posts=parse(o.optJSONArray("posts") ?: JSONArray()); error=""
                                } catch(e:Exception){ error=e.message ?: "মন্তব্য পাঠানো যায়নি" } }
                            }) { Icon(Icons.Filled.Send, null, tint=Accent) }
                        }
                        p.comments.takeLast(3).forEach { Text(it, color=Muted, fontSize=12.sp, modifier=Modifier.padding(top=4.dp)) }
                    }
                }
            }
        }
    }
}
