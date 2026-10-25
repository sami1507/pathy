package com.example.pathy.walkietalkie

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi

enum class ConnectionType {
    INTERNET,      // WebRTC via internet - unlimited range
    LOCAL_P2P,     // Nearby Connections - up to 200m
    OFFLINE        // No connection available
}

data class ConnectionStatus(
    val type: ConnectionType,
    val estimatedRangeMeters: Int,
    val quality: ConnectionQuality
)

enum class ConnectionQuality {
    EXCELLENT,  // <50ms latency, strong signal
    GOOD,       // 50-150ms latency
    FAIR,       // 150-300ms latency
    POOR        // >300ms latency or weak signal
}

@RequiresApi(Build.VERSION_CODES.O)
class ConnectionModeManager(
    private val context: Context,
    private val onConnectionChanged: (ConnectionStatus) -> Unit
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    @Volatile private var currentMode = ConnectionType.OFFLINE
    @Volatile private var hasInternet = false
    @Volatile private var hasWifi = false
    @Volatile private var hasCellular = false
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkState()
        }
        
        override fun onLost(network: Network) {
            updateNetworkState()
        }
        
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateNetworkState()
        }
    }
    
    init {
        registerNetworkCallback()
        updateNetworkState()
    }
    
    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }
    
    private fun updateNetworkState() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        
        hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                     capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        
        hasWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        hasCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        
        determineConnectionMode()
    }
    
    private fun determineConnectionMode() {
        val newMode = when {
            hasInternet && (hasWifi || hasCellular) -> ConnectionType.INTERNET
            else -> ConnectionType.LOCAL_P2P
        }
        
        if (newMode != currentMode) {
            currentMode = newMode
            notifyConnectionChange()
        }
    }
    
    private fun notifyConnectionChange() {
        val status = ConnectionStatus(
            type = currentMode,
            estimatedRangeMeters = when (currentMode) {
                ConnectionType.INTERNET -> 999_999  // Unlimited (show as 999+ km)
                ConnectionType.LOCAL_P2P -> 200     // WiFi Direct maximum
                ConnectionType.OFFLINE -> 0
            },
            quality = estimateQuality()
        )
        onConnectionChanged(status)
    }
    
    private fun estimateQuality(): ConnectionQuality {
        return when {
            !hasInternet && currentMode == ConnectionType.LOCAL_P2P -> ConnectionQuality.GOOD
            hasWifi -> ConnectionQuality.EXCELLENT
            hasCellular -> ConnectionQuality.GOOD
            else -> ConnectionQuality.POOR
        }
    }
    
    fun getCurrentMode(): ConnectionType = currentMode
    
    fun isInternetAvailable(): Boolean = hasInternet
    
    fun canUseInternetMode(): Boolean = hasInternet
    
    fun forceMode(mode: ConnectionType) {
        if (mode == ConnectionType.INTERNET && !hasInternet) {
            // Cannot force internet mode without internet
            return
        }
        currentMode = mode
        notifyConnectionChange()
    }
    
    fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
    }
}
