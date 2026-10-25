package com.example.pathy.walkietalkie

import com.example.pathy.R
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Hybrid walkie-talkie manager that intelligently switches between:
 * - Internet mode (WebRTC): Unlimited range when internet available
 * - Local P2P mode (Nearby Connections): Up to 200m without internet
 * 
 * Automatically detects best connection and switches seamlessly
 */
@RequiresApi(Build.VERSION_CODES.O)
class HybridGroupConnectionManager(
    private val context: Context,
    private val userName: String,
    val groupId: String,
    private val isHost: Boolean,
    private val onUserConnected: (String) -> Unit,
    private val onUserDisconnected: (String) -> Unit,
    private val onSpeakerChanged: (String?) -> Unit,
    private val onConnectionStatusChanged: (ConnectionStatus) -> Unit = {},
    private val onError: (Throwable) -> Unit = {},
) {
    private val TAG = "HybridGroupManager"
    
    // Connection mode manager
    private val connectionModeManager = ConnectionModeManager(context) { status ->
        onConnectionStatusChanged(status)
        handleConnectionModeChange(status.type)
    }
    
    // Local P2P transport (Nearby Connections)
    private var localTransport: GroupConnectionManager? = null
    
    // Internet transport (WebRTC)
    private var webrtcTransport: WebRTCAudioTransport? = null
    
    // Current active mode
    @Volatile private var currentMode: ConnectionType = ConnectionType.OFFLINE
    @Volatile private var isActive = false
    @Volatile private var micEnabled = false
    
    // Connected peers (unified from both transports)
    private val connectedPeers = mutableSetOf<String>()
    
    fun start() {
        if (isActive) return
        isActive = true
        
        Log.d(TAG, "Starting hybrid manager for group: $groupId")
        
        // Start with best available mode
        val initialMode = if (connectionModeManager.isInternetAvailable()) {
            ConnectionType.INTERNET
        } else {
            ConnectionType.LOCAL_P2P
        }
        
        startTransport(initialMode)
    }
    
    private fun handleConnectionModeChange(newMode: ConnectionType) {
        if (!isActive) return
        if (newMode == currentMode) return
        
        Log.d(TAG, "Connection mode changed: $currentMode -> $newMode")
        
        // Switch to new mode
        when (newMode) {
            ConnectionType.INTERNET -> {
                // Upgrade to internet mode
                switchToInternetMode()
            }
            ConnectionType.LOCAL_P2P -> {
                // Fallback to local P2P mode
                switchToLocalMode()
            }
            ConnectionType.OFFLINE -> {
                // Lost all connectivity
                onError(Exception(context.getString(R.string.walkie_connection_lost)))
            }
        }
    }
    
    private fun startTransport(mode: ConnectionType) {
        currentMode = mode
        
        when (mode) {
            ConnectionType.INTERNET -> startInternetMode()
            ConnectionType.LOCAL_P2P -> startLocalMode()
            ConnectionType.OFFLINE -> {
                onError(Exception(context.getString(R.string.walkie_no_connection_available)))
            }
        }
    }
    
    private fun startInternetMode() {
        Log.d(TAG, "Starting INTERNET mode (WebRTC) - unlimited range")
        
        try {
            webrtcTransport = WebRTCAudioTransport(
                context = context,
                groupId = groupId,
                userName = userName,
                onUserJoined = { name ->
                    if (connectedPeers.add(name)) {
                        onUserConnected(name)
                    }
                },
                onUserLeft = { name ->
                    if (connectedPeers.remove(name)) {
                        onUserDisconnected(name)
                    }
                },
                onError = onError
            ).apply {
                start()
                if (micEnabled) {
                    setMicEnabled(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start internet mode", e)
            onError(e)
            // Fallback to local mode
            switchToLocalMode()
        }
    }
    
    private fun startLocalMode() {
        Log.d(TAG, "Starting LOCAL P2P mode (Nearby) - up to 200m range")
        
        try {
            localTransport = GroupConnectionManager(
                context = context,
                userName = userName,
                groupId = groupId,
                isHost = isHost,
                onUserConnected = { name ->
                    if (connectedPeers.add(name)) {
                        onUserConnected(name)
                    }
                },
                onUserDisconnected = { name ->
                    if (connectedPeers.remove(name)) {
                        onUserDisconnected(name)
                    }
                },
                onSpeakerChanged = onSpeakerChanged,
                onError = onError
            ).apply {
                start()
                if (micEnabled) {
                    setMicEnabled(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local mode", e)
            onError(e)
        }
    }
    
    private fun switchToInternetMode() {
        if (currentMode == ConnectionType.INTERNET) return
        
        Log.d(TAG, "Switching to INTERNET mode")
        
        // Save current mic state
        val wasMicEnabled = micEnabled
        
        // Stop local transport
        localTransport?.stop()
        localTransport = null
        
        // Start internet transport
        currentMode = ConnectionType.INTERNET
        startInternetMode()
        
        // Restore mic state
        if (wasMicEnabled) {
            setMicEnabled(true)
        }
    }
    
    private fun switchToLocalMode() {
        if (currentMode == ConnectionType.LOCAL_P2P) return
        
        Log.d(TAG, "Switching to LOCAL P2P mode")
        
        // Save current mic state
        val wasMicEnabled = micEnabled
        
        // Stop internet transport
        webrtcTransport?.stop()
        webrtcTransport = null
        
        // Start local transport
        currentMode = ConnectionType.LOCAL_P2P
        startLocalMode()
        
        // Restore mic state
        if (wasMicEnabled) {
            setMicEnabled(true)
        }
    }
    
    fun setMicEnabled(enabled: Boolean): Boolean {
        micEnabled = enabled
        
        return when (currentMode) {
            ConnectionType.INTERNET -> {
                webrtcTransport?.setMicEnabled(enabled)
                true
            }
            ConnectionType.LOCAL_P2P -> {
                localTransport?.setMicEnabled(enabled) ?: false
            }
            ConnectionType.OFFLINE -> false
        }
    }
    
    fun isMicEnabled(): Boolean = micEnabled
    
    fun getCurrentMode(): ConnectionType = currentMode
    
    fun getConnectionStatus(): ConnectionStatus {
        return ConnectionStatus(
            type = currentMode,
            estimatedRangeMeters = when (currentMode) {
                ConnectionType.INTERNET -> 999_999
                ConnectionType.LOCAL_P2P -> 200
                ConnectionType.OFFLINE -> 0
            },
            quality = ConnectionQuality.GOOD
        )
    }
    
    fun getPeersSnapshot(): List<String> = connectedPeers.toList()
    
    fun stop() {
        if (!isActive) return
        isActive = false
        
        Log.d(TAG, "Stopping hybrid manager")
        
        // Stop both transports
        localTransport?.stop()
        webrtcTransport?.stop()
        
        localTransport = null
        webrtcTransport = null
        
        connectedPeers.clear()
        connectionModeManager.cleanup()
        
        micEnabled = false
    }
}
