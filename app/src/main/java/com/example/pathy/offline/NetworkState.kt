package com.example.pathy.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the phone can reach the internet right now. Screens use it to say plainly that they are
 * showing what is saved on the phone, instead of a spinner that never ends or an error that blames
 * the person.
 */
object NetworkState {

    /** True when the active network has internet and the system has confirmed it really works. */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return hasInternet(
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        )
    }

    /** A network that is connected but has no real internet (a hotel Wi-Fi before login, a dead hotspot) counts as offline. */
    internal fun hasInternet(hasInternetCapability: Boolean, validated: Boolean): Boolean = hasInternetCapability && validated
}

/** The connection state as a value that updates by itself while the screen is shown. */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(NetworkState.isOnline(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                state.value = NetworkState.isOnline(context)
            }

            override fun onLost(network: Network) {
                state.value = NetworkState.isOnline(context)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                state.value = NetworkState.isOnline(context)
            }
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        state.value = NetworkState.isOnline(context)
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    return state
}
