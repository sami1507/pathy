package com.example.pathy.data.moderation

/**
 * Two-step moderation: fast local word rules first, then the server-side ML check.
 * When the server check is unavailable (offline, quota, outage), the local rules are the
 * only gate, and content is not blocked just because the network failed.
 */
object ModerationPipeline {

    suspend fun moderate(text: String): ModerationDecision {
        when (LocalModeration.check(text)) {
            ModerationDecision.BLOCKED -> return ModerationDecision.BLOCKED
            ModerationDecision.NEEDS_EDIT -> return ModerationDecision.NEEDS_EDIT
            else -> {}
        }
        return RemoteModeration.check(text) ?: ModerationDecision.OK
    }
}
