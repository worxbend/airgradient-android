package dev.worxbend.airgradient.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.SensorMeasurementUnit
import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.domain.sensors.SensorMetricFactory
import dev.worxbend.airgradient.presentation.theme.AirGradientTheme
import java.time.Instant

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DashboardScreenUnconfiguredPreview() {
    AirGradientTheme(dynamicColor = false) {
        DashboardScreen(
            state = DashboardUiState.Unconfigured,
            onRefresh = {},
            onOpenSettings = {},
            onConfigureDevice = {},
            onStartMonitoring = {},
            onStopMonitoring = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DashboardScreenContentPreview() {
    AirGradientTheme(dynamicColor = false) {
        DashboardScreen(
            state = sampleContentState(),
            onRefresh = {},
            onOpenSettings = {},
            onConfigureDevice = {},
            onStartMonitoring = {},
            onStopMonitoring = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 720)
@Composable
private fun DashboardScreenWidePreview() {
    AirGradientTheme(dynamicColor = false) {
        DashboardScreen(
            state = sampleContentState(status = SensorStatus.WARNING),
            onRefresh = {},
            onOpenSettings = {},
            onConfigureDevice = {},
            onStartMonitoring = {},
            onStopMonitoring = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DashboardScreenErrorPreview() {
    AirGradientTheme(dynamicColor = false) {
        DashboardScreen(
            state =
                DashboardUiState.Error(
                    reason =
                        DashboardError(
                            cause = AirGradientError.Timeout,
                            title = "Connection timed out",
                            message = "The device did not respond before the request timed out.",
                        ),
                    lastKnownSnapshot = null,
                    metrics = emptyList(),
                    lastUpdatedLabel = null,
                ),
            onRefresh = {},
            onOpenSettings = {},
            onConfigureDevice = {},
            onStartMonitoring = {},
            onStopMonitoring = {},
        )
    }
}

private fun sampleContentState(status: SensorStatus = SensorStatus.GOOD): DashboardUiState.Content {
    val snapshot =
        when (status) {
            SensorStatus.CRITICAL -> {
                previewSnapshot.copy(
                    aqi = 162,
                    pm25 = 62.0,
                    co2 = 1860.0,
                    tvoc = 710.0,
                )
            }

            SensorStatus.WARNING -> {
                previewSnapshot.copy(
                    aqi = 118,
                    pm25 = 42.0,
                    co2 = 1320.0,
                )
            }

            else -> {
                previewSnapshot
            }
        }
    val previous = previewSnapshot.copy(aqi = 36, pm25 = 9.0, co2 = 610.0)

    return DashboardUiState.Content(
        snapshot = snapshot,
        metrics = SensorMetricFactory.createMetrics(snapshot, previous),
        overallStatus = status,
        lastUpdatedLabel = "Last updated 2026-06-16T00:12:00Z",
        fetchStatusLabel = "Latest measurements loaded.",
        refreshIntervalSeconds = 30,
        isRefreshing = false,
    )
}

private val previewSnapshot =
    AirMeasureSnapshot(
        aqi = 42,
        pm003Count = 442.0,
        pm01 = 3.0,
        pm25 = 7.0,
        pm10 = 8.0,
        co2 = 447.0,
        tvoc = 100.0,
        tvocUnit = SensorMeasurementUnit.INDEX,
        nox = 1.0,
        noxUnit = SensorMeasurementUnit.INDEX,
        temperatureCelsius = 24.5,
        humidityPercent = 49.0,
        measuredAt = Instant.parse("2026-06-16T00:12:00Z"),
    )
