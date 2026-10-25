package com.example.pathy.data

import android.content.Context
import android.content.Intent
import com.example.pathy.ui.screens.NavigationActivity
import com.google.firebase.auth.FirebaseAuth

fun openNavigationWithGroup(
    context: Context,
    groupId: String,
    fromGroups: Boolean,
    isLocalOnly: Boolean,
    lat: Double? = null,
    lng: Double? = null
) {
    val user = FirebaseAuth.getInstance().currentUser
    if (isLocalOnly && user != null) {
        val name = user.displayName ?: "משתמש"
        OfflineMembershipQueue(context).enqueue(
            PendingMember(
                groupId = groupId,
                uid = user.uid,
                name = name
            )
        )
    }

    val i = Intent(context, NavigationActivity::class.java).apply {
        putExtra("groupId", groupId)
        putExtra("fromGroups", fromGroups)
        putExtra("isLocalOnly", isLocalOnly)
        if (lat != null) putExtra("lat", lat)
        if (lng != null) putExtra("lng", lng)
    }
    context.startActivity(i)
}