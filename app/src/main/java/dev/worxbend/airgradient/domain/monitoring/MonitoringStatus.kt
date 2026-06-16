package dev.worxbend.airgradient.domain.monitoring

import java.time.Duration
import java.time.Instant

sealed interface MonitoringStatus {
    val mode: MonitoringMode

    data object Off : MonitoringStatus {
        override val mode: MonitoringMode = MonitoringMode.Off
    }

    data class Starting(
        override val mode: MonitoringMode,
        val startedAt: Instant,
    ) : MonitoringStatus

    data class Active(
        override val mode: MonitoringMode,
        val pollingInterval: Duration,
        val lastCheckedAt: Instant?,
        val lastSuccessfulReadAt: Instant?,
    ) : MonitoringStatus

    data class Stopped(
        val reason: MonitoringStopReason,
        val stoppedAt: Instant,
    ) : MonitoringStatus {
        override val mode: MonitoringMode = MonitoringMode.Off
    }
}

enum class MonitoringStopReason {
    UserRequested,
    MissingDeviceUrl,
    MissingNotificationPermission,
    InvalidConfiguration,
    ServiceDestroyed,
}
