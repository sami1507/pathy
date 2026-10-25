package com.example.pathy.core.track

/**
 * Elevation gain and loss from noisy altitude readings.
 *
 * Adding up every positive difference between consecutive readings (what the old recorder did)
 * counts every metre of sensor noise as climbing, and phone GPS altitude is only good to about
 * 5-10 m, so a flat walk looks like a mountain. This works in two steps:
 *  1. a short median-then-mean smoothing removes spikes;
 *  2. a dead band: a rise or fall only counts once it exceeds [thresholdM], measured from the last
 *     turning point, so wobble smaller than the threshold never adds up.
 */
class ElevationAccumulator(private val thresholdM: Double = 4.0, private val window: Int = 7) {
    private val recent = ArrayDeque<Double>()

    var gainM = 0.0
        private set
    var lossM = 0.0
        private set
    var minM: Double? = null
        private set
    var maxM: Double? = null
        private set
    /** The smoothed altitude of the latest reading. */
    var currentM: Double? = null
        private set

    private var anchor: Double? = null
    private var trend = 0 // 0 undecided, 1 rising, -1 falling
    private var extreme = 0.0

    fun add(altitudeM: Double) {
        recent.addLast(altitudeM)
        if (recent.size > window) recent.removeFirst()
        val smoothed = smooth()
        currentM = smoothed
        minM = minM?.let { minOf(it, smoothed) } ?: smoothed
        maxM = maxM?.let { maxOf(it, smoothed) } ?: smoothed

        val start = anchor
        if (start == null) {
            anchor = smoothed
            extreme = smoothed
            return
        }
        when (trend) {
            0 -> when {
                smoothed >= start + thresholdM -> {
                    trend = 1; gainM += smoothed - start; extreme = smoothed
                }
                smoothed <= start - thresholdM -> {
                    trend = -1; lossM += start - smoothed; extreme = smoothed
                }
            }
            1 -> when {
                smoothed > extreme -> {
                    gainM += smoothed - extreme; extreme = smoothed
                }
                smoothed <= extreme - thresholdM -> {
                    trend = -1; lossM += extreme - smoothed; extreme = smoothed
                }
            }
            else -> when {
                smoothed < extreme -> {
                    lossM += extreme - smoothed; extreme = smoothed
                }
                smoothed >= extreme + thresholdM -> {
                    trend = 1; gainM += smoothed - extreme; extreme = smoothed
                }
            }
        }
    }

    /** Median of the window, then mean of the values within 3 m of that median. */
    private fun smooth(): Double {
        if (recent.size < 3) return recent.average()
        val sorted = recent.sorted()
        val median = sorted[sorted.size / 2]
        val close = recent.filter { kotlin.math.abs(it - median) <= 3.0 }
        return if (close.isEmpty()) median else close.average()
    }

    companion object {
        /** Convenience for a finished track. Missing altitudes are skipped. */
        fun of(altitudes: List<Double?>, thresholdM: Double = 4.0): ElevationAccumulator {
            val acc = ElevationAccumulator(thresholdM)
            altitudes.forEach { if (it != null) acc.add(it) }
            return acc
        }
    }
}
