package com.adda.chat.data

import android.content.Context
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC DataChannel transport for online chat peers (শুধু টেক্সট)। ফাইল/ছবি/ভিডিও এখন সার্ভারের মাধ্যমে যায়।
 */
object P2pChatManager {
    private data class Peer(val pc: PeerConnection, var channel: DataChannel? = null, val pending: MutableList<IceCandidate> = mutableListOf())
    private var context: Context? = null
    private var factory: PeerConnectionFactory? = null
    private val peers = ConcurrentHashMap<String, Peer>()
    var onText: ((String, String, String, Long) -> Unit)? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
        if (factory != null) return
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext).createInitializationOptions())
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    fun isOpen(uid: String): Boolean = peers[uid.uppercase()]?.channel?.state() == DataChannel.State.OPEN

    fun connect(uidRaw: String) {
        val uid = uidRaw.uppercase()
        // Deterministic initiator prevents simultaneous-offer glare.
        val self = Session.me?.uid?.uppercase().orEmpty()
        if (uid.isBlank() || self.isBlank() || self > uid || peers.containsKey(uid)) return
        val peer = createPeer(uid) ?: return
        val dc = peer.pc.createDataChannel("adda-chat", DataChannel.Init())
        peer.channel = dc
        observeChannel(uid, dc)
        peer.pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peer.pc.setLocalDescription(object : SdpObserver by NoopSdpObserver {
                    override fun onSetSuccess() { Session.emit("p2p:offer", JSONObject().put("to", uid).put("sdp", desc.description).put("type", desc.type.canonicalForm())) }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    fun onSignal(event: String, data: JSONObject) {
        val uid = data.optString("from").uppercase()
        if (uid.isBlank()) return
        when (event) {
            "offer" -> {
                close(uid)
                val p = createPeer(uid) ?: return
                val desc = SessionDescription(SessionDescription.Type.OFFER, data.optString("sdp"))
                p.pc.setRemoteDescription(object : SdpObserver by NoopSdpObserver {
                    override fun onSetSuccess() {
                        synchronized(p.pending) { p.pending.forEach { p.pc.addIceCandidate(it) }; p.pending.clear() }
                        p.pc.createAnswer(object : SdpObserver {
                            override fun onCreateSuccess(answer: SessionDescription?) {
                                if (answer == null) return
                                p.pc.setLocalDescription(object : SdpObserver by NoopSdpObserver {
                                    override fun onSetSuccess() { Session.emit("p2p:answer", JSONObject().put("to", uid).put("sdp", answer.description).put("type", answer.type.canonicalForm())) }
                                }, answer)
                            }
                            override fun onSetSuccess() {}
                            override fun onCreateFailure(error: String?) {}
                            override fun onSetFailure(error: String?) {}
                        }, MediaConstraints())
                    }
                }, desc)
            }
            "answer" -> peers[uid]?.let { p ->
                p.pc.setRemoteDescription(NoopSdpObserver, SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp")))
                synchronized(p.pending) { p.pending.forEach { p.pc.addIceCandidate(it) }; p.pending.clear() }
            }
            "candidate" -> {
                val c = IceCandidate(data.optString("sdpMid"), data.optInt("sdpMLineIndex"), data.optString("candidate"))
                val p = peers[uid]
                if (p != null && p.pc.remoteDescription != null) p.pc.addIceCandidate(c) else if (p != null) synchronized(p.pending) { p.pending.add(c) }
            }
            "close" -> close(uid)
        }
    }

    fun sendText(uidRaw: String, text: String): Boolean {
        val uid = uidRaw.uppercase()
        val dc = peers[uid]?.channel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        val msg = JSONObject().put("kind", "text").put("id", UUID.randomUUID().toString()).put("text", text).put("date", System.currentTimeMillis())
        return try { dc.send(DataChannel.Buffer(ByteBuffer.wrap(msg.toString().toByteArray(Charsets.UTF_8)), false)) } catch (_: Exception) { false }
    }

    private fun createPeer(uid: String): Peer? {
        val f = factory ?: return null
        val config = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        ))
        val pc = f.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) { candidate?.let { Session.emit("p2p:candidate", JSONObject().put("to", uid).put("sdpMid", it.sdpMid).put("sdpMLineIndex", it.sdpMLineIndex).put("candidate", it.sdp)) } }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) { if (channel != null) { peers[uid]?.channel = channel; observeChannel(uid, channel) } }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
        }) ?: return null
        val p = Peer(pc)
        peers[uid] = p
        return p
    }

    private fun observeChannel(uid: String, dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                try {
                    if (buffer.binary) return // ফাইল এখন সার্ভারের মাধ্যমে যায়; P2P-তে শুধু টেক্সট
                    val bytes = ByteArray(buffer.data.remaining()); buffer.data.get(bytes)
                    val o = JSONObject(String(bytes, Charsets.UTF_8))
                    if (o.optString("kind") == "text") onText?.invoke(uid, o.optString("id"), o.optString("text"), o.optLong("date", System.currentTimeMillis()))
                } catch (_: Exception) {}
            }
        })
    }

    fun close(uidRaw: String) { val p = peers.remove(uidRaw.uppercase()) ?: return; try { p.channel?.unregisterObserver(); p.channel?.close(); p.channel?.dispose(); p.pc.close(); p.pc.dispose() } catch (_: Exception) {} }
    fun closeAll() { peers.keys.toList().forEach(::close) }
}

private object NoopSdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
