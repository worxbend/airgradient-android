package dev.worxbend.airgradient.presentation.dashboard

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.SensorMetric
import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationError
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings

sealed interface DashboardUiState {
    data object Unconfigured : DashboardUiState

    data object Loading : DashboardUiState

    data class Content(
        val snapshot: AirMeasureSnapshot,
        val metrics: List<SensorMetric>,
        val overallStatus: SensorStatus,
        val lastUpdatedLabel: String,
        val fetchStatusLabel: String,
        val refreshIntervalSeconds: Int,
        val monitoringSummary: DashboardMonitoringSummary = DashboardMonitoringSummary(),
        val isRefreshing: Boolean,
    ) : DashboardUiState

    data class ContentWithWarning(
        val snapshot: AirMeasureSnapshot,
        val metrics: List<SensorMetric>,
        val overallStatus: SensorStatus,
        val warning: DashboardWarning,
        val lastUpdatedLabel: String,
        val fetchStatusLabel: String,
        val refreshIntervalSeconds: Int,
        val monitoringSummary: DashboardMonitoringSummary = DashboardMonitoringSummary(),
        val isRefreshing: Boolean,
    ) : DashboardUiState

    data class Error(
        val reason: DashboardError,
        val lastKnownSnapshot: AirMeasureSnapshot?,
        val metrics: List<SensorMetric>,
        val lastUpdatedLabel: String?,
        val monitoringSummary: DashboardMonitoringSummary = DashboardMonitoringSummary(),
    ) : DashboardUiState
}

data class DashboardMonitoringSummary(
    val mode: MonitoringMode = MonitoringMode.Off,
    val foregroundPollingIntervalSeconds: Int =
        MonitoringSettings.DEFAULT_FOREGROUND_POLLING_INTERVAL_SECONDS,
    val periodicBackgroundIntervalMinutes: Int =
        MonitoringSettings.DEFAULT_PERIODIC_BACKGROUND_INTERVAL_MINUTES,
    val lastBackgroundCheckLabel: String? = null,
    val lastSuccessfulBackgroundReadLabel: String? = null,
    val actionState: DashboardMonitoringActionState = DashboardMonitoringActionState.Idle,
)

sealed interface DashboardMonitoringActionState {
    data object Idle : DashboardMonitoringActionState

    data object Starting : DashboardMonitoringActionState

    data object Started : DashboardMonitoringActionState

    data object Stopping : DashboardMonitoringActionState

    data object Stopped : DashboardMonitoringActionState

    data class Rejected(
        val error: MonitoringPolicyValidationError,
    ) : DashboardMonitoringActionState
}

data class DashboardWarning(
    val cause: AirGradientError,
    val message: String,
)

data class DashboardError(
    val cause: AirGradientError,
    val title: String,
    val message: String,
)
