package com.example.pathy.data


data class Report(
    val type: ReportType,
    val category: String,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    /** Reporter's uid. Required by the Firestore rules: a report is only accepted from its author. */
    val userId: String? = null
)

enum class ReportType {
    HAZARD, POINT_OF_INTEREST
}
