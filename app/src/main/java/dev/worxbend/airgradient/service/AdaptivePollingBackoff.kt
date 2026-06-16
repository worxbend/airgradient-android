package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import dev.worxbend.airgradient.domain.sensors.SensorThresholds
import java.time.Duration

/**
 * Increases the effective poll delay after several consecutive good readings or repeated failures,
 * so the foreground service does fewer wakeups when air quality is stable.
 *
 * Backoff is purely in-memory and resets when the service starts a new monitoring loop.
 */
class AdaptivePollingBackoff {
    private var consecutiveGoodCount = 0
    private var consecutiveFailureCount = 0

    fun updateAndGetDelay(
        tickResult: MonitoringTickResult,
        configuredInterval: Duration,
    ): Duration =
        when (tickResult) {
            is MonitoringTickResult.Success -> {
                val status = SensorThresholds.overallStatus(tickResult.snapshot)
                if (status == SensorStatus.GOOD) {
                    consecutiveGoodCount++
                    consecutiveFailureCount = 0
                    backedOffInterval(consecutiveGoodCount, GOOD_BACKOFF_THRESHOLD, configuredInterval)
                } else {
                    consecutiveGoodCount = 0
                    consecutiveFailureCount = 0
                    configuredInterval
                }
            }

            is MonitoringTickResult.Failure -> {
                consecutiveGoodCount = 0
                consecutiveFailureCount++
                backedOffInterval(consecutiveFailureCount, FAILURE_BACKOFF_THRESHOLD, configuredInterval)
            }

            is MonitoringTickResult.Skipped -> {
                configuredInterval
            }
        }

    fun reset() {
        consecutiveGoodCount = 0
        consecutiveFailureCount = 0
    }

    private fun backedOffInterval(
        count: Int,
        threshold: Int,
        baseInterval: Duration,
    ): Duration {
        if (count < threshold) return baseInterval
        val steps = count - threshold
        var result = baseInterval
        repeat(steps + 1) {
            val doubled = result.multipliedBy(2)
            result = if (doubled < MAX_BACKOFF) doubled else MAX_BACKOFF
        }
        return result
    }

    companion object {
        internal const val GOOD_BACKOFF_THRESHOLD = 3
        internal const val FAILURE_BACKOFF_THRESHOLD = 3
        val MAX_BACKOFF: Duration = Duration.ofMinutes(15)
    }
}
