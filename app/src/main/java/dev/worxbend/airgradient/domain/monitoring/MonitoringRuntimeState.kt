package dev.worxbend.airgradient.domain.monitoring

import java.time.Instant

data class MonitoringRuntimeState(
    val lastCheckedAt: Instant?,
    val lastSuccessfulCheckAt: Instant?,
    val lastSuccessfulMeasurementAt: Instant?,
    val lastFailureAt: Instant?,
    val consecutiveFailureCount: Int,
) {
    companion object {
        val default: MonitoringRuntimeState =
            MonitoringRuntimeState(
                lastCheckedAt = null,
                lastSuccessfulCheckAt = null,
                lastSuccessfulMeasurementAt = null,
                lastFailureAt = null,
                consecutiveFailureCount = 0,
            )
    }
}
