package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import java.time.Instant

data class MonitoringStatusSnapshot(
    val lastCheckedAt: Instant? = null,
    val lastSuccessfulReadAt: Instant? = null,
) {
    fun after(result: MonitoringTickResult): MonitoringStatusSnapshot =
        when (result) {
            is MonitoringTickResult.Success -> {
                copy(
                    lastCheckedAt = result.checkedAt,
                    lastSuccessfulReadAt = result.snapshot.measuredAt,
                )
            }

            is MonitoringTickResult.Failure -> {
                copy(lastCheckedAt = result.checkedAt)
            }

            is MonitoringTickResult.Skipped -> {
                this
            }
        }
}
