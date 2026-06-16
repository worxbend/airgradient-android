package dev.worxbend.airgradient.domain.monitoring

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import java.time.Instant

sealed interface MonitoringTickResult {
    val checkedAt: Instant

    data class Success(
        val snapshot: AirMeasureSnapshot,
        override val checkedAt: Instant,
    ) : MonitoringTickResult

    data class Failure(
        val error: AirGradientError,
        val consecutiveFailureCount: Int,
        override val checkedAt: Instant,
    ) : MonitoringTickResult

    data class Skipped(
        val reason: MonitoringTickSkipReason,
        override val checkedAt: Instant,
    ) : MonitoringTickResult
}

enum class MonitoringTickSkipReason {
    MonitoringOff,
    MissingDeviceUrl,
    MissingNotificationPermission,
    RequestAlreadyRunning,
}
