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

@Composable
internal fun DashboardContent(
    metrics: List<SensorMetric>,
    overallStatus: SensorStatus,
    lastUpdatedLabel: String,
    fetchStatusLabel: String,
    refreshIntervalSeconds: Int,
    isRefreshing: Boolean,
    warningMessage: String? = null,
) {
    val aqiMetric = metrics.firstMetric(SensorMetricKind.AQI)
    val comfortMetrics =
        listOfNotNull(
            metrics.firstMetric(SensorMetricKind.TEMPERATURE),
            metrics.firstMetric(SensorMetricKind.HUMIDITY),
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
        ).mapNotNull(metrics::firstMetric)

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
            if (warningMessage != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WarningPanel(message = warningMessage)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                AqiHeroCard(
                    metric = aqiMetric,
                    overallStatus = overallStatus,
                    lastUpdatedLabel = lastUpdatedLabel,
                    isRefreshing = isRefreshing,
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
                    fetchStatusLabel = fetchStatusLabel,
                    refreshIntervalSeconds = refreshIntervalSeconds,
                )
            }
        }
    }
}

private fun List<SensorMetric>.firstMetric(kind: SensorMetricKind): SensorMetric? = firstOrNull { it.kind == kind }
