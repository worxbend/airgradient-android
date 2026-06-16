package dev.worxbend.airgradient.presentation.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.domain.model.SensorMetric
import dev.worxbend.airgradient.domain.model.SensorMetricKind
import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.presentation.dashboard.DashboardMonitoringSummary

@Composable
internal fun DashboardContent(
    content: DashboardContentModel,
    monitoringActions: DashboardMonitoringActions,
) {
    val heroMetric = content.metrics.heroMetric(content.overallStatus)
    val comfortMetrics =
        listOfNotNull(
            content.metrics.firstMetric(SensorMetricKind.TEMPERATURE),
            content.metrics.firstMetric(SensorMetricKind.HUMIDITY),
        )
    val pollutantMetrics =
        listOf(
            SensorMetricKind.CO2,
            SensorMetricKind.PM25,
            SensorMetricKind.PM01,
            SensorMetricKind.PM10,
            SensorMetricKind.PM003_COUNT,
            SensorMetricKind.TVOC,
            SensorMetricKind.NOX,
        ).mapNotNull(content.metrics::firstMetric)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridColumns =
            if (maxWidth < DashboardComponentDefaults.compactWidth) {
                DashboardComponentDefaults.COMPACT_GRID_COLUMNS
            } else {
                DashboardComponentDefaults.EXPANDED_GRID_COLUMNS
            }
        val contentPadding = if (maxWidth < DashboardComponentDefaults.compactWidth) 16.dp else 24.dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = contentPadding,
                    top = 12.dp,
                    end = contentPadding,
                    bottom = 24.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (content.warningMessage != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WarningPanel(message = content.warningMessage)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                AqiHeroCard(
                    metric = heroMetric,
                    overallStatus = content.overallStatus,
                    lastUpdatedLabel = content.lastUpdatedLabel,
                    isRefreshing = content.isRefreshing,
                )
            }
            items(
                items = comfortMetrics,
                key = { it.kind.name },
                span = { GridItemSpan(if (gridColumns == DashboardComponentDefaults.COMPACT_GRID_COLUMNS) 1 else 2) },
            ) { metric ->
                ComfortMetricCard(metric = metric)
            }
            items(
                items = pollutantMetrics,
                key = { it.kind.name },
            ) { metric ->
                SensorMetricCard(metric = metric)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                RefreshStatusBar(
                    fetchStatusLabel = content.fetchStatusLabel,
                    refreshIntervalSeconds = content.refreshIntervalSeconds,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                MonitoringStatusCard(
                    summary = content.monitoringSummary,
                    actions = monitoringActions,
                )
            }
        }
    }
}

internal data class DashboardContentModel(
    val metrics: List<SensorMetric>,
    val overallStatus: SensorStatus,
    val lastUpdatedLabel: String,
    val fetchStatusLabel: String,
    val refreshIntervalSeconds: Int,
    val monitoringSummary: DashboardMonitoringSummary,
    val isRefreshing: Boolean,
    val warningMessage: String? = null,
)

private fun List<SensorMetric>.firstMetric(kind: SensorMetricKind): SensorMetric? = firstOrNull { it.kind == kind }

private fun List<SensorMetric>.heroMetric(overallStatus: SensorStatus): SensorMetric? {
    val aqiMetric = firstMetric(SensorMetricKind.AQI)
    if (overallStatus.severity <= SensorStatus.GOOD.severity) return aqiMetric ?: firstOrNull()

    return firstOrNull { metric -> metric.status == overallStatus } ?: aqiMetric ?: firstOrNull()
}
