package com.adda.chat.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.content.Intent
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adda.chat.data.Api
import com.adda.chat.data.Session
import com.adda.chat.service.ConnectionService
import com.adda.chat.util.Notifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * অডিও/ভিডিও কল (WebRTC) — ওয়েব ভার্সনের সিগন্যালিং (webrtc:offer/answer/candidate/hangup) এর সাথে সামঞ্জস্যপূর্ণ।
 * নয়েজ ক্যান্সেলেশন: সফটওয়্যার AEC + NS + AGC + হাই-পাস ফিল্টার চালু।
 * ভিডিও: 480x360 @ 20fps, ~350kbps; অডিও ~32kbps — যেন কল স্মুথ চলে।
 */
object CallManager {
    enum class Phase { None, Outgoing, Incoming, Connecting, Connected }

    var phase by mutableStateOf(Phase.None)
    var callKind by mutableStateOf("audio")
    var peerUid by mutableStateOf("")
    var status by mutableStateOf("")
    var remoteVideo by mutableStateOf<VideoTrack?>(null)
    var localVideo by mutableStateOf<VideoTrack?>(null)
    var micMuted by mutableStateOf(false)
    var camOff by mutableStateOf(false)
    var speakerOn by mutableStateOf(false)
    var connectedAt by mutableStateOf(0L)

    private const val VIDEO_W = 480
    private const val VIDEO_H = 360
    private const val VIDEO_FPS = 20
    private const val VIDEO_MAX_BPS = 350_000
    private const val VIDEO_MAX_FPS = 24
    private const val AUDIO_MAX_BPS = 32_000

    private lateinit var ctx: Context
    val eglBase: EglBase by lazy { EglBase.create() }

    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    private var pendingOffer: JSONObject? = null
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var ringPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringbackTone: ToneGenerator? = null
    private var timeoutJob: Job? = null
    // সাময়িক নেটওয়ার্ক ড্রপ হলে সঙ্গে সঙ্গে কল না কেটে ১২ সেকেন্ড পুনঃসংযোগের সুযোগ দিন।
    private var iceDisconnectJob: Job? = null
    private const val ICE_DISCONNECT_GRACE_MS = 12_000L
    private const val OUTGOING_TIMEOUT_MS = 45_000L
    private const val INCOMING_TIMEOUT_MS = 50_000L
    private var prevAudioMode = AudioManager.MODE_NORMAL
    private var audioFocusRequest: AudioFocusRequest? = null

    // ICE সার্ভার (STUN + TURN) সার্ভার থেকে আনা হয়; না পেলে ডিফল্ট তালিকা
    private var cachedIceServers: List<PeerConnection.IceServer>? = null
    private var iceFetchedAt = 0L
    // অফার/অ্যানসার পাঠানোর আগে তৈরি হওয়া ICE candidate জমা রাখা হয় (নইলে অপর পক্ষ হারিয়ে ফেলে)
    private var signalReady = false
    private val outCandidates = mutableListOf<JSONObject>()

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    // ---------------------------------------------------------------- factory

    private fun ensureFactory(): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(ctx).createInitializationOptions()
        )
        // সফটওয়্যার AEC/NS ব্যবহার — সব ডিভাইসে সমান মানের নয়েজ ক্যান্সেলেশনের জন্য
        val adm = JavaAudioDeviceModule.builder(ctx)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .createAudioDeviceModule()
        val f = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f
        return f
    }

    private fun defaultIceServers(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
    )

    /** সার্ভার থেকে STUN/TURN তালিকা আনে (মোবাইল ডেটায় কল সংযোগের জন্য TURN জরুরি) */
    private suspend fun loadIceServers(): List<PeerConnection.IceServer> {
        val cached = cachedIceServers
        if (cached != null && SystemClock.elapsedRealtime() - iceFetchedAt < 10 * 60_000L) return cached
        return try {
            val obj = withTimeout(4_000) { Api.getObj("/api/webrtc/ice") }
            val arr = obj.optJSONArray("iceServers")
            val list = mutableListOf<PeerConnection.IceServer>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val urls = mutableListOf<String>()
                    val u = o.opt("urls")
                    if (u is org.json.JSONArray) { for (k in 0 until u.length()) urls.add(u.optString(k)) }
                    else if (u != null) urls.add(u.toString())
                    val clean = urls.filter { it.isNotBlank() }
                    if (clean.isEmpty()) continue
                    val b = PeerConnection.IceServer.builder(clean)
                    val user = o.optString("username")
                    val cred = o.optString("credential")
                    if (user.isNotEmpty()) b.setUsername(user)
                    if (cred.isNotEmpty()) b.setPassword(cred)
                    list.add(b.createIceServer())
                }
            }
            val result = if (list.isEmpty()) defaultIceServers() else list
            cachedIceServers = result
            iceFetchedAt = SystemClock.elapsedRealtime()
            result
        } catch (_: Exception) {
            cached ?: defaultIceServers()
        }
    }

    private fun createPeerAndMedia(kind: String, servers: List<PeerConnection.IceServer>): PeerConnection {
        val f = ensureFactory()
        val cfg = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }
        val p = f.createPeerConnection(cfg, observer) ?: throw IllegalStateException("পিয়ার কানেকশন তৈরি হয়নি")
        pc = p

        // অডিও (নয়েজ ক্যান্সেলেশন সহ)
        val audioConstraints = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        val aSrc = f.createAudioSource(audioConstraints)
        val aTrack = f.createAudioTrack("audio0", aSrc)
        audioSource = aSrc
        audioTrack = aTrack
        p.addTrack(aTrack, listOf("stream0"))

        // ভিডিও (কম রেজোলিউশন)
        if (kind == "video") {
            val enumerator = Camera2Enumerator(ctx)
            val names = enumerator.deviceNames
            val camName = names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull()
                ?: throw IllegalStateException("ক্যামেরা পাওয়া যায়নি")
            val cap = enumerator.createCapturer(camName, null) ?: throw IllegalStateException("ক্যামেরা চালু করা যায়নি")
            val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            val vSrc = f.createVideoSource(cap.isScreencast)
            cap.initialize(helper, ctx, vSrc.capturerObserver)
            cap.startCapture(VIDEO_W, VIDEO_H, VIDEO_FPS)
            val vTrack = f.createVideoTrack("video0", vSrc)
            capturer = cap
            surfaceHelper = helper
            videoSource = vSrc
            videoTrack = vTrack
            p.addTrack(vTrack, listOf("stream0"))
            localVideo = vTrack
        }
        return p
    }

    private fun tuneSenders() {
        val p = pc ?: return
        for (sender in p.senders) {
            try {
                val track = sender.track() ?: continue
                val params = sender.parameters
                if (params.encodings.isEmpty()) continue
                val enc = params.encodings[0]
                if (track.kind() == "video") {
                    enc.maxBitrateBps = VIDEO_MAX_BPS
                    enc.maxFramerate = VIDEO_MAX_FPS
                } else {
                    enc.maxBitrateBps = AUDIO_MAX_BPS
                }
                sender.setParameters(params)
            } catch (_: Exception) {
            }
        }
    }

    // ---------------------------------------------------------------- observer

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Session.scope.launch {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        iceDisconnectJob?.cancel()
                        iceDisconnectJob = null
                        markConnected()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        // মোবাইল নেটওয়ার্কে ক্ষণস্থায়ী packet loss বা Wi-Fi handoff-এ
                        // ICE সাময়িক DISCONNECTED হতে পারে; সঙ্গে সঙ্গে কল শেষ করবেন না।
                        iceDisconnectJob?.cancel()
                        iceDisconnectJob = Session.scope.launch {
                            delay(ICE_DISCONNECT_GRACE_MS)
                            if (phase != Phase.None &&
                                pc?.iceConnectionState() == PeerConnection.IceConnectionState.DISCONNECTED
                            ) {
                                Session.toast("নেটওয়ার্ক সংযোগ ফিরছে না। কল শেষ করা হচ্ছে; TURN সার্ভার লাগতে পারে।")
                                endCall(true)
                            }
                        }
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        iceDisconnectJob?.cancel()
                        iceDisconnectJob = null
                        if (phase != Phase.None) {
                            Session.toast("কল সংযোগ ব্যর্থ। Google STUN যথেষ্ট না হলে TURN সার্ভার প্রয়োজন হতে পারে।")
                            endCall(true)
                        }
                    }
                    else -> {}
                }
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate == null) return
            val payload = JSONObject()
                .put("candidate", candidate.sdp)
                .put("sdpMid", candidate.sdpMid)
                .put("sdpMLineIndex", candidate.sdpMLineIndex)
            Session.scope.launch {
                if (peerUid.isEmpty()) return@launch
                // অফার/অ্যানসার পাঠানোর আগে হলে জমা রাখি; পরে একসাথে পাঠানো হবে
                if (!signalReady) outCandidates.add(payload)
                else Session.emit("webrtc:candidate", JSONObject().put("to", peerUid).put("candidate", payload))
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}

        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track()
            if (track is VideoTrack) Session.scope.launch { remoteVideo = track }
        }
    }

    private fun flushOutCandidates() {
        signalReady = true
        val to = peerUid
        if (to.isEmpty()) { outCandidates.clear(); return }
        for (c in outCandidates.toList()) {
            Session.emit("webrtc:candidate", JSONObject().put("to", to).put("candidate", c))
        }
        outCandidates.clear()
    }

    private fun markConnected() {
        if (phase == Phase.None || phase == Phase.Connected) return
        stopRingback()
        cancelTimeout()
        phase = Phase.Connected
        connectedAt = SystemClock.elapsedRealtime()
        status = "কল সংযুক্ত হয়েছে"
        tuneSenders()
    }

    // ---------------------------------------------------------------- outgoing

    fun startCall(kind: String, peer: String) {
        if (phase != Phase.None) {
            Session.toast("একটি কল ইতিমধ্যে চলছে")
            return
        }
        if (!Session.socketConnected) {
            Session.toast("সার্ভারের সঙ্গে সংযোগ নেই")
            return
        }
        Session.scope.launch {
            try {
                callKind = if (kind == "video") "video" else "audio"
                peerUid = peer
                phase = Phase.Outgoing
                status = "কল করা হচ্ছে: ${Session.displayName(peer)}"
                resetControls()
                startAudioMode()
                startRingback()
                startOutgoingTimeout()
                signalReady = false
                outCandidates.clear()
                ConnectionService.setCallState(ctx, true, callKind == "video")
                val servers = loadIceServers()
                if (phase == Phase.None) return@launch
                val p = createPeerAndMedia(callKind, servers)
                val offer = p.createOfferSuspend()
                p.setLocalSuspend(offer)
                tuneSenders()
                Session.emit(
                    "webrtc:offer",
                    JSONObject().put("to", peer).put("offer", sdpJson(offer)).put("kind", callKind)
                )
                flushOutCandidates()
            } catch (e: Exception) {
                Session.toast("কল শুরু করা যায়নি: ${e.message}")
                endCall(false)
            }
        }
    }

    // ---------------------------------------------------------------- incoming

    private fun handleOffer(from: String, data: JSONObject) {
        if (Session.me?.friends?.contains(from) != true) return
        // একই কলারের অফার আবার এলে (সার্ভার সকেট রিকানেক্টে অফার রিপ্লে করে) ব্যস্ত বলবেন না — উপেক্ষা করুন
        if (phase != Phase.None && from == peerUid) {
            if (phase == Phase.Incoming && data.optJSONObject("offer") != null) pendingOffer = data
            return
        }
        if (phase != Phase.None) {
            Session.emit("webrtc:hangup", JSONObject().put("to", from).put("reason", "busy"))
            return
        }
        if (data.optJSONObject("offer") == null) return
        pendingOffer = data
        pendingCandidates.clear()
        peerUid = from
        callKind = if (data.optString("kind") == "video") "video" else "audio"
        phase = Phase.Incoming
        resetControls()
        status = if (callKind == "video") "ভিডিও কল আসছে" else "অডিও কল আসছে"
        startRingtone()
        startIncomingTimeout()
        if (!Session.appInForeground) {
            Notifier.incomingCall(ctx, Session.displayName(from), callKind == "video")
        }
    }

    fun acceptCall() {
        if (phase != Phase.Incoming) return
        val offerData = pendingOffer ?: return
        stopRingtone()
        cancelTimeout()
        Notifier.cancelCall(ctx)
        ConnectionService.setCallState(ctx, true, callKind == "video")
        Session.scope.launch {
            try {
                phase = Phase.Connecting
                status = "${Session.displayName(peerUid)}-এর কল গ্রহণ করা হচ্ছে"
                Session.openConversation(peerUid)
                startAudioMode()
                signalReady = false
                outCandidates.clear()
                val servers = loadIceServers()
                if (phase == Phase.None) return@launch
                val p = createPeerAndMedia(callKind, servers)
                val offer = offerData.getJSONObject("offer")
                p.setRemoteSuspend(SessionDescription(SessionDescription.Type.OFFER, offer.getString("sdp")))
                flushCandidates()
                val answer = p.createAnswerSuspend()
                p.setLocalSuspend(answer)
                tuneSenders()
                Session.emit(
                    "webrtc:answer",
                    JSONObject().put("to", peerUid).put("answer", sdpJson(answer))
                )
                flushOutCandidates()
                pendingOffer = null
                status = "কল সংযুক্ত হচ্ছে…"
            } catch (e: Exception) {
                Session.toast("কল গ্রহণ করা যায়নি: ${e.message}")
                endCall(true)
            }
        }
    }

    fun declineCall() {
        if (phase != Phase.Incoming) return
        Session.emit("webrtc:hangup", JSONObject().put("to", peerUid).put("reason", "declined"))
        endCall(false)
    }

    // ---------------------------------------------------------------- signaling in

    fun onSignal(event: String, data: JSONObject) {
        val from = data.optString("from")
        if (from.isEmpty()) return
        when (event) {
            "offer" -> handleOffer(from, data)
            "answer" -> {
                if (phase == Phase.None || from != peerUid) return
                val answer = data.optJSONObject("answer") ?: return
                stopRingback()
                cancelTimeout()
                Session.scope.launch {
                    try {
                        pc?.setRemoteSuspend(SessionDescription(SessionDescription.Type.ANSWER, answer.getString("sdp")))
                        flushCandidates()
                        if (phase == Phase.Outgoing) {
                            phase = Phase.Connecting
                            status = "অপর পক্ষ কল গ্রহণ করেছেন; সংযোগ হচ্ছে…"
                        }
                    } catch (e: Exception) {
                        Session.toast("কল সংযোগে সমস্যা: ${e.message}")
                    }
                }
            }
            "candidate" -> {
                if (phase == Phase.None || from != peerUid) return
                val c = data.optJSONObject("candidate") ?: return
                val sdp = c.optString("candidate")
                if (sdp.isEmpty()) return
                val mid = if (c.isNull("sdpMid")) null else c.optString("sdpMid")
                val ice = IceCandidate(mid, c.optInt("sdpMLineIndex", 0), sdp)
                val p = pc
                if (p != null && p.remoteDescription != null) p.addIceCandidate(ice) else pendingCandidates.add(ice)
            }
            "hangup" -> {
                if (phase == Phase.None || from != peerUid) return
                if (phase == Phase.Incoming) {
                    Notifier.missedCall(ctx, Session.displayName(from), callKind == "video")
                }
                val msg = when (data.optString("reason")) {
                    "declined" -> "কলটি রিসিভ করা হয়নি (কল রিজেক্ট করা হয়েছে)"
                    "busy" -> "অপর পক্ষ ব্যস্ত আছেন"
                    else -> "কল শেষ হয়েছে"
                }
                endCall(false)
                Session.toast(msg)
            }
        }
    }

    private fun flushCandidates() {
        val p = pc ?: return
        for (c in pendingCandidates.toList()) {
            try { p.addIceCandidate(c) } catch (_: Exception) {}
        }
        pendingCandidates.clear()
    }

    // ---------------------------------------------------------------- screen sharing

    var screenSharing by mutableStateOf(false)
        private set

    /** Android-এর user-consent result পাওয়ার পর বর্তমান ভিডিও ট্র্যাককে স্ক্রিন ক্যাপচারে বদলায়। */
    fun startScreenShare(resultCode: Int, projectionData: Intent) {
        if (phase == Phase.None || pc == null || resultCode != android.app.Activity.RESULT_OK) return
        if (screenSharing) return
        try {
            val f = ensureFactory()
            val p = pc ?: return
            val oldCapturer = capturer
            val oldSource = videoSource
            val oldTrack = videoTrack
            val oldHelper = surfaceHelper

            try { oldCapturer?.stopCapture() } catch (_: Exception) {}
            try { oldCapturer?.dispose() } catch (_: Exception) {}

            val helper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext)
            val source = f.createVideoSource(true)
            val screenCapturer = ScreenCapturerAndroid(projectionData, object : android.media.projection.MediaProjection.Callback() {
                override fun onStop() {
                    screenSharing = false
                    try { com.adda.chat.service.ScreenShareService.stop(ctx) } catch (_: Exception) {}
                }
            })
            screenCapturer.initialize(helper, ctx, source.capturerObserver)
            screenCapturer.startCapture(720, 1280, 15)
            val track = f.createVideoTrack("screen0", source)
            val sender = p.senders.firstOrNull { it.track()?.kind() == "video" }
            if (sender == null || !sender.setTrack(track, false)) {
                try { screenCapturer.stopCapture() } catch (_: Exception) {}
                screenCapturer.dispose()
                track.dispose(); source.dispose(); helper.dispose()
                throw IllegalStateException("ভিডিও কলের স্ক্রিন শেয়ার ট্র্যাক তৈরি হয়নি")
            }
            capturer = screenCapturer
            videoSource = source
            videoTrack = track
            surfaceHelper = helper
            localVideo = track
            camOff = false
            screenSharing = true
            try { oldTrack?.dispose() } catch (_: Exception) {}
            try { oldSource?.dispose() } catch (_: Exception) {}
            try { oldHelper?.dispose() } catch (_: Exception) {}
            tuneSenders()
            Session.toast("স্ক্রিন শেয়ার চালু হয়েছে")
        } catch (e: Exception) {
            Session.toast("স্ক্রিন শেয়ার চালু করা যায়নি: ${e.localizedMessage ?: "অজানা সমস্যা"}")
        }
    }

    // ---------------------------------------------------------------- controls

    fun toggleMute() {
        micMuted = !micMuted
        audioTrack?.setEnabled(!micMuted)
    }

    fun toggleCamera() {
        camOff = !camOff
        videoTrack?.setEnabled(!camOff)
    }

    fun switchCamera() {
        (capturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun toggleSpeaker() {
        speakerOn = !speakerOn
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = speakerOn
    }

    private fun resetControls() {
        micMuted = false
        camOff = false
        connectedAt = 0L
    }

    // ---------------------------------------------------------------- teardown

    fun endCall(notifyPeer: Boolean) {
        if (notifyPeer && phase != Phase.None && peerUid.isNotEmpty()) {
            Session.emit("webrtc:hangup", JSONObject().put("to", peerUid))
        }
        val wasOutgoing = phase == Phase.Outgoing
        stopRingtone()
        stopRingback()
        cancelTimeout()
        iceDisconnectJob?.cancel()
        iceDisconnectJob = null
        Notifier.cancelCall(ctx)
        stopAudioMode()
        ConnectionService.setCallState(ctx, false, false)
        try { com.adda.chat.service.ScreenShareService.stop(ctx) } catch (_: Exception) {}
        if (phase != Phase.None) playEndTone(wasOutgoing)

        val oldPc = pc
        val oldCapturer = capturer
        screenSharing = false
        val oldVideoTrack = videoTrack
        val oldVideoSource = videoSource
        val oldHelper = surfaceHelper
        val oldAudioTrack = audioTrack
        val oldAudioSource = audioSource

        pc = null
        capturer = null
        videoTrack = null
        videoSource = null
        surfaceHelper = null
        audioTrack = null
        audioSource = null
        pendingOffer = null
        pendingCandidates.clear()
        outCandidates.clear()
        signalReady = false

        remoteVideo = null
        localVideo = null
        phase = Phase.None
        peerUid = ""
        status = ""
        connectedAt = 0L

        // ভারী কাজ ব্যাকগ্রাউন্ডে (ক্যামেরা বন্ধ করতে একটু সময় লাগে)
        Thread {
            try { oldCapturer?.stopCapture() } catch (_: Exception) {}
            try { oldPc?.close() } catch (_: Exception) {}
            try { oldCapturer?.dispose() } catch (_: Exception) {}
            try { oldVideoTrack?.dispose() } catch (_: Exception) {}
            try { oldVideoSource?.dispose() } catch (_: Exception) {}
            try { oldHelper?.dispose() } catch (_: Exception) {}
            try { oldAudioTrack?.dispose() } catch (_: Exception) {}
            try { oldAudioSource?.dispose() } catch (_: Exception) {}
            try { oldPc?.dispose() } catch (_: Exception) {}
        }.start()
    }

    // ---------------------------------------------------------------- audio routing / ringtone

    private fun startAudioMode() {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prevAudioMode = am.mode
        requestAudioFocus(am)
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        speakerOn = callKind == "video"
        am.isSpeakerphoneOn = speakerOn
    }

    private fun stopAudioMode() {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = false
        am.mode = prevAudioMode
        speakerOn = false
        abandonAudioFocus(am)
    }

    private fun requestAudioFocus(am: AudioManager) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                am.requestAudioFocus(req)
                audioFocusRequest = req
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonAudioFocus(am: AudioManager) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
    }

    // ---- ইনকামিং রিংটোন (লুপ) + ভাইব্রেশন ----
    @Suppress("DEPRECATION")
    private fun startRingtone() {
        stopRingtone()
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mode = am.ringerMode
        if (mode == AudioManager.RINGER_MODE_SILENT) return
        if (mode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(ctx, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                ringPlayer = mp
            } catch (_: Exception) {
                ringPlayer = null
            }
        }
        // নরমাল ও ভাইব্রেট মোড — দুটোতেই ভাইব্রেশন
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val pattern = longArrayOf(0, 900, 900)
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    v.vibrate(pattern, 0)
                }
                vibrator = v
            }
        } catch (_: Exception) {
        }
    }

    private fun stopRingtone() {
        try { ringPlayer?.stop() } catch (_: Exception) {}
        try { ringPlayer?.release() } catch (_: Exception) {}
        ringPlayer = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
    }

    // ---- আউটগোয়িং ডায়ালিং/রিংব্যাক টোন (কানের স্পিকারে, ফোনকলের মতো) ----
    private fun startRingback() {
        stopRingback()
        try {
            val t = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
            t.startTone(ToneGenerator.TONE_SUP_RINGTONE)
            ringbackTone = t
        } catch (_: Exception) {
            ringbackTone = null
        }
    }

    private fun stopRingback() {
        try { ringbackTone?.stopTone() } catch (_: Exception) {}
        try { ringbackTone?.release() } catch (_: Exception) {}
        ringbackTone = null
    }

    /** কল শেষের ছোট বিপ; কেউ ধরেনি/রিজেক্ট করলে busy টোন */
    private fun playEndTone(wasOutgoing: Boolean) {
        try {
            val t = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 60)
            t.startTone(if (wasOutgoing) ToneGenerator.TONE_SUP_BUSY else ToneGenerator.TONE_PROP_PROMPT, 400)
            Session.scope.launch {
                delay(700)
                try { t.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        }
    }

    // ---- কেউ না ধরলে স্বয়ংক্রিয়ভাবে কল শেষ ----
    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun startOutgoingTimeout() {
        cancelTimeout()
        timeoutJob = Session.scope.launch {
            delay(OUTGOING_TIMEOUT_MS)
            if (phase == Phase.Outgoing) {
                Session.toast("${Session.displayName(peerUid)} কল ধরেননি")
                endCall(true)
            }
        }
    }

    private fun startIncomingTimeout() {
        cancelTimeout()
        timeoutJob = Session.scope.launch {
            delay(INCOMING_TIMEOUT_MS)
            if (phase == Phase.Incoming) {
                Notifier.missedCall(ctx, Session.displayName(peerUid), callKind == "video")
                endCall(false)
            }
        }
    }

    private fun sdpJson(d: SessionDescription): JSONObject =
        JSONObject().put("type", d.type.canonicalForm()).put("sdp", d.description)
}

// -------------------------------------------------------------------- SDP suspend helpers

private open class SdpAdapter : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}

private suspend fun PeerConnection.createOfferSuspend(): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) cont.resume(desc) else cont.resumeWithException(IllegalStateException("offer তৈরি হয়নি"))
            }
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "offer তৈরি ব্যর্থ"))
            }
        }, MediaConstraints())
    }

private suspend fun PeerConnection.createAnswerSuspend(): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createAnswer(object : SdpAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) cont.resume(desc) else cont.resumeWithException(IllegalStateException("answer তৈরি হয়নি"))
            }
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "answer তৈরি ব্যর্থ"))
            }
        }, MediaConstraints())
    }

private suspend fun PeerConnection.setLocalSuspend(desc: SessionDescription) =
    suspendCancellableCoroutine<Unit> { cont ->
        setLocalDescription(object : SdpAdapter() {
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "local description ব্যর্থ"))
            }
        }, desc)
    }

private suspend fun PeerConnection.setRemoteSuspend(desc: SessionDescription) =
    suspendCancellableCoroutine<Unit> { cont ->
        setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "remote description ব্যর্থ"))
            }
        }, desc)
    }
