package com.example.pathy.walkietalkie
import com.example.pathy.R
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import java.math.BigInteger
import java.security.MessageDigest

@RequiresApi(Build.VERSION_CODES.O)
class GroupConnectionManager(
    private val context: Context,
    private val userName: String,
    val groupId: String,
    private val isHost: Boolean,
    private val onUserConnected: (String) -> Unit,
    private val onUserDisconnected: (String) -> Unit,
    private val onSpeakerChanged: (String?) -> Unit = {},
    private val onError: (Throwable) -> Unit = {},
) {
    @Volatile private var strategyIndex = 0
    @Volatile private var advStartInFlight = false
    @Volatile private var discStartInFlight = false
    @Volatile private var lastRestartMs = 0L
    @Volatile private var isAdvertising = false
    @Volatile private var isDiscovering = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val strategies = arrayOf(
        Strategy.P2P_CLUSTER,
        Strategy.P2P_STAR,
        Strategy.P2P_POINT_TO_POINT
    )
    private val currentStrategy get() = strategies[strategyIndex]
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = makeServiceId(groupId)
    private val localSpeakerName = userName
    private val lastSeenByName = mutableMapOf<String, Long>()
    private var presenceJob: Job? = null
    private val peers = linkedSetOf<String>()
    private var emptySinceMs: Long? = null
    private val sendersByEndpoint = mutableMapOf<String, AudioSender>()
    private val endpointNameById = mutableMapOf<String, String>()
    private val audioReceiver = AudioReceiver(context).apply {
        onControlMessage = { type, name ->
            when (type) {
                "SPEAKER_ON"  -> onSpeakerChanged(name)
                "SPEAKER_OFF" -> onSpeakerChanged(null)
                "PING", "INTRO" -> if (!name.isNullOrBlank()) {
                    val isNew = !lastSeenByName.containsKey(name)
                    lastSeenByName[name] = now()
                    if (isNew && name != localSpeakerName) {
                        peers.add(name)
                        onUserConnected(name)
                    }
                }
            }
        }
    }
    private object PttLock {
        @Volatile var holderName: String? = null
        @Synchronized fun tryAcquire(requester: String, isHost: Boolean): Boolean {
            if (holderName == null || isHost) {
                holderName = requester
                return true
            }
            return holderName == requester
        }
        @Synchronized fun release(requester: String) {
            if (holderName == requester) holderName = null
        }
    }
    @Volatile private var micEnabled: Boolean = false
    fun isMicEnabled(): Boolean = micEnabled

    private fun now() = SystemClock.elapsedRealtime()

    private fun makeServiceId(groupId: String): String {
        val norm = groupId.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256").digest(norm.toByteArray())
        val base36 = BigInteger(1, digest).toString(36)
        return "pt-${base36.take(17)}"
    }

    private fun sendControlToAll(type: String, name: String?) {
        val bytes = "CTRL|$type|${name ?: ""}".toByteArray(Charsets.UTF_8)
        val payload = Payload.fromBytes(bytes)
        sendersByEndpoint.keys.forEach { id ->
            runCatching { connectionsClient.sendPayload(id, payload) }
        }
    }

    private fun sendControlTo(endpointId: String, type: String, name: String?) {
        val bytes = "CTRL|$type|${name ?: ""}".toByteArray(Charsets.UTF_8)
        runCatching { connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes)) }
    }

    private fun startPresence() {
        if (presenceJob != null) return
        presenceJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                sendControlToAll("PING", localSpeakerName)

                val cutoff = now() - 15_000
                val expired = lastSeenByName.filterValues { it < cutoff }.keys.toList()
                for (name in expired) {
                    if (name == localSpeakerName) continue
                    lastSeenByName.remove(name)
                    if (peers.remove(name)) onUserDisconnected(name)
                }


                if (peers.isEmpty()) {
                    val since = emptySinceMs ?: now().also { emptySinceMs = it }
                    if (now() - since >= 20_000L) {
                        restartWithFallback()
                        emptySinceMs = now()
                    }
                } else {
                    emptySinceMs = null
                }

                delay(3_000)
            }
        }
    }

    private fun stopPresence() {
        presenceJob?.cancel()
        presenceJob = null
        lastSeenByName.clear()
    }

    fun getPeersSnapshot(): List<String> = synchronized(peers) {
        peers.filter { it != localSpeakerName }.toList()
    }

    private val connCb = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            endpointNameById[endpointId] = info.endpointName
            connectionsClient.acceptConnection(endpointId, audioReceiver)
            sendControlTo(endpointId, "INTRO", localSpeakerName)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                sendersByEndpoint[endpointId] = AudioSender(context, connectionsClient, endpointId)
                val peerName = endpointNameById[endpointId] ?: endpointId
                if (peers.add(peerName)) {
                    lastSeenByName[peerName] = now()
                    onUserConnected(peerName)
                }
                sendControlTo(endpointId, "INTRO", localSpeakerName)

                if (micEnabled && PttLock.holderName == localSpeakerName) {
                    try { sendersByEndpoint[endpointId]?.startSending() }
                    catch (t: Throwable) {}
                    sendControlTo(endpointId, "SPEAKER_ON", localSpeakerName)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            sendersByEndpoint.remove(endpointId)?.stopSending()
            val name = endpointNameById.remove(endpointId)
            if (name != null) {
                peers.remove(name)
                lastSeenByName.remove(name)
            }
            onUserDisconnected(name ?: endpointId)
            if (name != null && PttLock.holderName == name) {
                PttLock.release(name)
                onSpeakerChanged(null)
            }
        }
    }

    fun start() {
        startBoth()
        startPresence()
        scope.launch(Dispatchers.Default) {
            delay(5_000)
            if (peers.isEmpty()) {
                restartWithFallback()
            }
        }
    }

    fun stop() {
        stopPresence()
        if (isAdvertising) runCatching { connectionsClient.stopAdvertising() }
        if (isDiscovering) runCatching { connectionsClient.stopDiscovery() }
        isAdvertising = false
        isDiscovering = false

        runCatching { connectionsClient.stopAllEndpoints() }
        sendersByEndpoint.values.forEach { it.stopSending() }
        sendersByEndpoint.clear()
        endpointNameById.clear()
        audioReceiver.stop()

        if (micEnabled) {
            micEnabled = false
            PttLock.release(localSpeakerName)
            onSpeakerChanged(null)
        }
    }

    private fun startBoth() {
        startAdvertisingInternal()
        startDiscoveryInternal()
    }

    private fun startAdvertisingInternal() {
        if (isAdvertising || advStartInFlight) { return }
        advStartInFlight = true
        runCatching { connectionsClient.stopAdvertising() }

        connectionsClient.startAdvertising(
            userName, serviceId, connCb,
            AdvertisingOptions.Builder().setStrategy(currentStrategy).build()
        )
            .addOnSuccessListener { isAdvertising = true }
            .addOnFailureListener { e ->
                val code = (e as? ApiException)?.statusCode
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                    isAdvertising = true
                    return@addOnFailureListener
                }
                onError(e)
                scope.launch { delay(300); startAdvertisingInternal() } // נסיון עדין, בלי לופים הדוקים
            }
            .addOnCompleteListener { advStartInFlight = false }
    }

    private fun startDiscoveryInternal() {
        if (isDiscovering || discStartInFlight) {
            return
        }
        discStartInFlight = true
        runCatching { connectionsClient.stopDiscovery() }

        connectionsClient.startDiscovery(
            serviceId,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    if (endpointNameById.containsKey(endpointId)) return
                    connectionsClient.requestConnection(userName, endpointId, connCb)
                }
                override fun onEndpointLost(endpointId: String) {}
            },
            DiscoveryOptions.Builder().setStrategy(currentStrategy).build()
        )
            .addOnSuccessListener { isDiscovering = true }
            .addOnFailureListener { e ->
                val code = (e as? ApiException)?.statusCode
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                    isDiscovering = true
                    return@addOnFailureListener
                }
                onError(e)
                scope.launch { delay(300); startDiscoveryInternal() }
            }
            .addOnCompleteListener { discStartInFlight = false }
    }

    private fun restartWithFallback() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRestartMs < 10_000) { return }
        lastRestartMs = now
        runCatching { connectionsClient.stopAdvertising() }
        runCatching { connectionsClient.stopDiscovery() }
        isAdvertising = false
        isDiscovering = false
        scope.launch {
            delay(300)
            startAdvertisingInternal()
            startDiscoveryInternal()
        }
    }

    fun setMicEnabled(enabled: Boolean): Boolean {
        return try {
            if (enabled) {
                if (micEnabled) return true
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    onError(SecurityException("RECORD_AUDIO permission missing"))
                    return false
                }

                // Try acquire with a short backoff to reduce collisions in the field
                var ok = PttLock.tryAcquire(localSpeakerName, isHost)
                if (!ok) {
                    val start = now()
                    var attempts = 0
                    while (!ok && attempts < 5 && now() - start < 350L) {
                        try { Thread.sleep(60) } catch (_: Throwable) {}
                        ok = PttLock.tryAcquire(localSpeakerName, isHost)
                        attempts++
                    }
                }
                if (!ok) {
                    val holder = PttLock.holderName ?: context.getString(R.string.walkie_another_user)
                    onError(IllegalStateException(context.getString(R.string.walkie_mic_busy, holder)))
                    return false
                }

                sendersByEndpoint.values.forEach { sender ->
                    try { sender.startSending() } catch (t: Throwable) {}
                }
                micEnabled = true
                onSpeakerChanged(localSpeakerName)
                sendControlToAll("SPEAKER_ON", localSpeakerName)
                true
            } else {
                if (!micEnabled) return true
                sendersByEndpoint.values.forEach { it.stopSending() }
                micEnabled = false
                PttLock.release(localSpeakerName)
                onSpeakerChanged(null)
                sendControlToAll("SPEAKER_OFF", localSpeakerName)
                true
            }
        } catch (t: Throwable) {
            onError(t)
            false
        }
    }
}
