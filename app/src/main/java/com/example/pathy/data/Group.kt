package com.example.pathy.data

import com.google.firebase.firestore.Exclude

data class Group(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val createdBy: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "planned",
    val isPublic: Boolean = true,
    val lat: Double? = null,
    val lng: Double? = null,
    val isSynced: Boolean = false
) {
    @Transient
    @Exclude
    var distanceFromUser: Float? = null

    @Exclude
    var isLocalOnly: Boolean = false

    @Exclude
    var localMembers: MutableSet<String> = mutableSetOf()

    @Exclude
    var comms: CommsState = CommsState()

    @get:Exclude
    val nearbyServiceId: String
        get() = "com.example.pathy.group.$id"
}

enum class TransportPreference { CLOUD_FIRST, P2P_FIRST, P2P_ONLY }

enum class ActiveTransport { NONE, CLOUD, P2P }

data class CommsState(
    var preference: TransportPreference = TransportPreference.CLOUD_FIRST,
    var active: ActiveTransport = ActiveTransport.NONE,
    var webrtcRoomId: String? = null,
    var lastOnlineMillis: Long = 0L,
    var lastP2pMillis: Long = 0L
)
