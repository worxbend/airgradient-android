package dev.worxbend.airgradient.presentation.dashboard

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.SensorMetric
import dev.worxbend.airgradient.domain.model.SensorStatus

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
        val isRefreshing: Boolean,
    ) : DashboardUiState

    data class Error(
        val reason: DashboardError,
        val lastKnownSnapshot: AirMeasureSnapshot?,
        val metrics: List<SensorMetric>,
        val lastUpdatedLabel: String?,
    ) : DashboardUiState
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
