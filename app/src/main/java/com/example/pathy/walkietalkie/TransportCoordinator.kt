package com.example.pathy.walkietalkie

import com.example.pathy.R
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TransportCoordinator {
    private var manager: HybridGroupConnectionManager? = null
    private var currentConnectionStatus: ConnectionStatus? = null

    var isActive: Boolean = false
        private set
    var isMicEnabled: Boolean = false
        private set

    private var appContext: Context? = null
    private var currentSpeaker: String? = null
    private var onSpeakerChangedCb: (String?) -> Unit = {}
    private var onErrorCb: (Throwable) -> Unit = {}
    private val _peersFlow = MutableStateFlow<List<String>>(emptyList())
    val peersFlow: StateFlow<List<String>> = _peersFlow.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.O)
    fun start(
        context: Context,
        userName: String,
        groupId: String,
        isHost: Boolean,
        onSpeakerChanged: (String?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        appContext = context.applicationContext
        onSpeakerChangedCb = { spk ->
            currentSpeaker = spk
            onSpeakerChanged(spk)
        }
        onErrorCb = onError

        if (manager == null) {
            manager = HybridGroupConnectionManager(
                context = context,
                userName = userName,
                groupId = groupId.trim(),
                isHost = isHost,
                onUserConnected = { name -> addPeer(name) },
                onUserDisconnected = { name -> removePeer(name) },
                onSpeakerChanged = { name -> onSpeakerChangedCb(name) },
                // A change of connection mode is not an error. It used to be reported as one, which made the
                // screen believe the call had ended just as it started; the screen reads the status itself.
                onConnectionStatusChanged = { status -> currentConnectionStatus = status },
                onError = { t -> onErrorCb(t) }
            ).also {
                it.start()
                _peersFlow.value = it.getPeersSnapshot()
            }
        } else {
            pushSnapshotToUi()
        }

        isActive = true
        isMicEnabled = manager?.isMicEnabled() == true
        // Keeps the call alive with the screen off or the app in the background.
        WalkieTalkieService.start(context)
    }

    private fun addPeer(name: String) {
        if (name.isBlank()) return
        val cur = _peersFlow.value
        if (!cur.contains(name)) _peersFlow.value = (cur + name).distinct()
    }

    private fun removePeer(name: String) {
        if (name.isBlank()) return
        _peersFlow.value = _peersFlow.value.filterNot { it == name }
    }

    private fun pushSnapshotToUi() {
        val m = manager ?: return
        _peersFlow.value = m.getPeersSnapshot()
        onSpeakerChangedCb(currentSpeaker)
        isMicEnabled = m.isMicEnabled()
    }

    fun getCurrentSpeaker(): String? = currentSpeaker
    
    fun getConnectionStatus(): ConnectionStatus? = currentConnectionStatus
    
    fun getCurrentMode(): ConnectionType? = manager?.getCurrentMode()

    fun setMicEnabled(enabled: Boolean) {
        val ok = manager?.setMicEnabled(enabled) ?: false
        isMicEnabled = ok && enabled
        if (!enabled) currentSpeaker = null
    }

    fun stop() {
        appContext?.let { WalkieTalkieService.stop(it) }
        manager?.stop()
        manager = null
        isActive = false
        isMicEnabled = false
        currentSpeaker = null
        _peersFlow.value = emptyList()
    }
}
