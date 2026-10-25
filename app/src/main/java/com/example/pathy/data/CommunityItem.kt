package com.example.pathy.data

import com.example.pathy.CommunityEvent

sealed class CommunityItem {
    data class PostItem(val post: Post) : CommunityItem()
    data class EventItem(val event: CommunityEvent) : CommunityItem()
}