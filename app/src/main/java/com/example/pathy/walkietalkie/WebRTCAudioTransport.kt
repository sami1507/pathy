package com.example.pathy.walkietalkie

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.util.concurrent.ConcurrentHashMap

/**
 * Walkie-talkie voice over the internet (WebRTC), for when the group is not within Nearby range.
 * Firebase Realtime Database is only the switchboard: it carries the "who is here" list and the
 * connection set-up messages. The voice itself goes straight between phones, encrypted.
 *
 * What changed from the first version, and why:
 *  - People are identified by their account id, not by the name they type. The database rules now
 *    check that a message really comes from the account it names, so nobody can answer as someone else.
 *  - Only one side of each pair makes the offer (the smaller id). Before, both sides offered at once,
 *    the offers collided and the call never connected.
 *  - Connection candidates that arrive before the offer are kept and applied afterwards.
 *  - The microphone starts muted. It used to transmit as soon as a peer joined, with the button "off".
 *  - A short network drop no longer ends the call: only a failed connection is closed.
 */
@RequiresApi(Build.VERSION_CODES.O)
class WebRTCAudioTransport(
    private val context: Context,
    private val groupId: String,
    private val userName: String,
    private val onUserJoined: (String) -> Unit,
    private val onUserLeft: (String) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val tag = "WebRTCAudioTransport"

    private val myUid: String? = FirebaseAuth.getInstance().currentUser?.uid
    private val groupRef = FirebaseDatabase.getInstance().getReference("walkie_groups/$groupId")
    private val usersRef = groupRef.child("users")
    private val signalingRef = groupRef.child("signaling")

    private var factory: PeerConnectionFactory? = null
    private var localSource: AudioSource? = null
    private var localTrack: AudioTrack? = null

    private val connections = ConcurrentHashMap<String, PeerConnection>()
    private val names = ConcurrentHashMap<String, String>()
    private val remoteReady = ConcurrentHashMap.newKeySet<String>()
    private val pendingCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()

    private var usersListener: ChildEventListener? = null
    private var inboxListener: ChildEventListener? = null
    @Volatile private var isActive = false

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    init {
        initialize()
    }

    private fun initialize() {
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(null, false, false))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(null))
                .createPeerConnectionFactory()

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            }
            localSource = factory?.createAudioSource(constraints)
            localTrack = factory?.createAudioTrack("pathy_audio", localSource)
            // Push-to-talk: nothing leaves the phone until the person turns the microphone on.
            localTrack?.setEnabled(false)
        } catch (e: Exception) {
            Log.e(tag, "Could not start the voice engine", e)
            onError(e)
        }
    }

    fun start() {
        val uid = myUid
        if (uid == null) {
            onError(IllegalStateException("Sign in to talk over the internet"))
            return
        }
        if (isActive) return
        isActive = true

        usersRef.child(uid).setValue(mapOf("name" to userName, "timestamp" to ServerValue.TIMESTAMP))
        usersRef.child(uid).onDisconnect().removeValue()

        usersListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val peerUid = snapshot.key ?: return
                if (peerUid == uid) return
                val peerName = snapshot.child("name").getValue(String::class.java).orEmpty().ifBlank { peerUid.take(6) }
                if (names.put(peerUid, peerName) == null) onUserJoined(peerName)
                // One side offers, the other answers: the smaller account id offers.
                if (uid < peerUid && !connections.containsKey(peerUid)) openConnection(peerUid, makeOffer = true)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val peerUid = snapshot.key ?: return
                closeConnection(peerUid)
                names.remove(peerUid)?.let(onUserLeft)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) = onError(error.toException())
        }.also { usersRef.addChildEventListener(it) }

        inboxListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    handleMessage(snapshot)
                } catch (e: Exception) {
                    Log.w(tag, "Ignored a bad signaling message", e)
                } finally {
                    snapshot.ref.removeValue()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) = onError(error.toException())
        }.also { signalingRef.child(uid).addChildEventListener(it) }
    }

    // ---- connections ---------------------------------------------------------------------------

    private fun openConnection(peerUid: String, makeOffer: Boolean) {
        val f = factory ?: return
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { send(peerUid, mapOf(
                    "type" to "ice",
                    "sdpMid" to it.sdpMid,
                    "sdpMLineIndex" to it.sdpMLineIndex,
                    "candidate" to it.sdp
                )) }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                // DISCONNECTED is often a passing network change and heals by itself; only FAILED is final.
                if (state == PeerConnection.IceConnectionState.FAILED) {
                    closeConnection(peerUid)
                    val me = myUid
                    if (isActive && me != null && me < peerUid && names.containsKey(peerUid)) openConnection(peerUid, makeOffer = true)
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        }
        val pc = f.createPeerConnection(config, observer) ?: run {
            onError(IllegalStateException("Could not create a voice connection"))
            return
        }
        localTrack?.let { pc.addTrack(it, listOf("pathy_stream")) }
        connections[peerUid] = pc
        if (makeOffer) createOffer(peerUid, pc)
    }

    private fun createOffer(peerUid: String, pc: PeerConnection) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        pc.createOffer(object : SimpleSdpObserver("create offer") {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(object : SimpleSdpObserver("set local offer") {
                    override fun onSetSuccess() = send(peerUid, mapOf("type" to "offer", "sdp" to sdp.description))
                }, sdp)
            }
        }, constraints)
    }

    private fun handleMessage(snapshot: DataSnapshot) {
        val type = snapshot.child("type").getValue(String::class.java) ?: return
        val from = snapshot.child("from").getValue(String::class.java) ?: return
        if (from == myUid) return
        when (type) {
            "offer" -> {
                val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                val pc = connections[from] ?: run { openConnection(from, makeOffer = false); connections[from] } ?: return
                pc.setRemoteDescription(object : SimpleSdpObserver("set remote offer") {
                    override fun onSetSuccess() {
                        markRemoteReady(from, pc)
                        answer(from, pc)
                    }
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
            "answer" -> {
                val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                val pc = connections[from] ?: return
                pc.setRemoteDescription(object : SimpleSdpObserver("set remote answer") {
                    override fun onSetSuccess() = markRemoteReady(from, pc)
                }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "ice" -> {
                val mid = snapshot.child("sdpMid").getValue(String::class.java) ?: return
                val index = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: return
                val line = snapshot.child("candidate").getValue(String::class.java) ?: return
                val candidate = IceCandidate(mid, index, line)
                val pc = connections[from]
                if (pc != null && remoteReady.contains(from)) pc.addIceCandidate(candidate)
                else pendingCandidates.getOrPut(from) { java.util.Collections.synchronizedList(ArrayList()) }.add(candidate)
            }
        }
    }

    /** Candidates can arrive before the offer or answer; they are applied once the other side is known. */
    private fun markRemoteReady(peerUid: String, pc: PeerConnection) {
        remoteReady.add(peerUid)
        pendingCandidates.remove(peerUid)?.forEach { pc.addIceCandidate(it) }
    }

    private fun answer(peerUid: String, pc: PeerConnection) {
        pc.createAnswer(object : SimpleSdpObserver("create answer") {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(object : SimpleSdpObserver("set local answer") {
                    override fun onSetSuccess() = send(peerUid, mapOf("type" to "answer", "sdp" to sdp.description))
                }, sdp)
            }
        }, MediaConstraints())
    }

    private fun send(toUid: String, body: Map<String, Any?>) {
        val me = myUid ?: return
        signalingRef.child(toUid).push().setValue(body + mapOf("from" to me, "timestamp" to ServerValue.TIMESTAMP))
    }

    private fun closeConnection(peerUid: String) {
        connections.remove(peerUid)?.close()
        remoteReady.remove(peerUid)
        pendingCandidates.remove(peerUid)
    }

    fun setMicEnabled(enabled: Boolean) {
        localTrack?.setEnabled(enabled)
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        val uid = myUid
        usersListener?.let { usersRef.removeEventListener(it) }
        if (uid != null) {
            inboxListener?.let { signalingRef.child(uid).removeEventListener(it) }
            usersRef.child(uid).removeValue()
            signalingRef.child(uid).removeValue()
        }
        connections.keys.toList().forEach { closeConnection(it) }
        names.clear()
        localTrack?.dispose()
        localSource?.dispose()
        factory?.dispose()
        localTrack = null
        localSource = null
        factory = null
    }

    /** An observer that logs failures; each call overrides only what it needs. */
    private open inner class SimpleSdpObserver(private val what: String) : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.w(tag, "$what failed: $error") }
        override fun onSetFailure(error: String?) { Log.w(tag, "$what failed: $error") }
    }
}
