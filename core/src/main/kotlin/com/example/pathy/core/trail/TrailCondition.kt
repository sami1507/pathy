package com.example.pathy.core.trail

/** What a hiker found on the trail today. */
enum class ConditionStatus { OPEN, MUDDY, BLOCKED, CLOSED, NOT_FOUND }

/** How worried to be: green, yellow, red. */
enum class ConditionLevel { GOOD, CAUTION, PROBLEM }

data class ConditionReport(val status: ConditionStatus, val atMs: Long, val userId: String)

/** What the recent reports add up to. */
data class ConditionSummary(
    val latest: ConditionStatus,
    val level: ConditionLevel,
    /** When the newest report was made. */
    val atMs: Long,
    /** How many of the recent reports say the same kind of thing (same colour) as the newest. */
    val agreeing: Int,
    val total: Int
)

/**
 * Turns hikers' reports into one line. Map data cannot know that a tree fell last night or that a gate
 * was locked this week; people on the trail can. Only recent reports count, because a trail that was
 * flooded in spring is not flooded in autumn.
 */
object TrailCondition {
    const val FRESH_DAYS = 45L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun levelOf(status: ConditionStatus): ConditionLevel = when (status) {
        ConditionStatus.OPEN -> ConditionLevel.GOOD
        ConditionStatus.MUDDY -> ConditionLevel.CAUTION
        ConditionStatus.BLOCKED, ConditionStatus.CLOSED, ConditionStatus.NOT_FOUND -> ConditionLevel.PROBLEM
    }

    /** One report per person counts (their newest); null when nobody reported lately. */
    fun summarize(reports: List<ConditionReport>, nowMs: Long): ConditionSummary? {
        val recent = reports
            .filter { it.atMs <= nowMs + DAY_MS && nowMs - it.atMs <= FRESH_DAYS * DAY_MS }
            .groupBy { it.userId }
            .map { (_, mine) -> mine.maxBy { it.atMs } }
        if (recent.isEmpty()) return null
        val newest = recent.maxBy { it.atMs }
        val level = levelOf(newest.status)
        return ConditionSummary(
            latest = newest.status,
            level = level,
            atMs = newest.atMs,
            agreeing = recent.count { levelOf(it.status) == level },
            total = recent.size
        )
    }
}
