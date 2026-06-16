package dev.worxbend.airgradient.presentation.dashboard.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.R
import dev.worxbend.airgradient.domain.model.SensorMetricKind

@Composable
internal fun BoxScope.MetricIconWatermark(
    kind: SensorMetricKind,
    modifier: Modifier = Modifier,
    size: Dp = 92.dp,
    alignment: Alignment = Alignment.BottomEnd,
    alpha: Float = 0.12f,
) {
    Image(
        painter = painterResource(id = kind.metricIconRes()),
        contentDescription = null,
        modifier =
            modifier
                .align(alignment)
                .offset(x = 12.dp, y = 12.dp)
                .size(size)
                .alpha(alpha),
        colorFilter = ColorFilter.tint(kind.metricIconColor()),
    )
}

@DrawableRes
private fun SensorMetricKind.metricIconRes(): Int =
    when (this) {
        SensorMetricKind.AQI -> R.drawable.ic_metric_aqi

        SensorMetricKind.CO2 -> R.drawable.ic_metric_co2

        SensorMetricKind.PM25,
        SensorMetricKind.PM01,
        SensorMetricKind.PM10,
        -> R.drawable.ic_metric_particles

        SensorMetricKind.PM003_COUNT -> R.drawable.ic_metric_pm_count

        SensorMetricKind.TVOC -> R.drawable.ic_metric_tvoc

        SensorMetricKind.NOX -> R.drawable.ic_metric_nox

        SensorMetricKind.TEMPERATURE -> R.drawable.ic_metric_temperature

        SensorMetricKind.HUMIDITY -> R.drawable.ic_metric_humidity
    }

@Composable
private fun SensorMetricKind.metricIconColor(): Color =
    when (this) {
        SensorMetricKind.AQI -> DashboardComponentDefaults.good

        SensorMetricKind.CO2 -> MaterialTheme.colorScheme.primary

        SensorMetricKind.PM25,
        SensorMetricKind.PM01,
        SensorMetricKind.PM10,
        SensorMetricKind.PM003_COUNT,
        -> DashboardComponentDefaults.moderate

        SensorMetricKind.TVOC -> DashboardComponentDefaults.warning

        SensorMetricKind.NOX -> DashboardComponentDefaults.critical

        SensorMetricKind.TEMPERATURE -> MaterialTheme.colorScheme.tertiary

        SensorMetricKind.HUMIDITY -> MaterialTheme.colorScheme.secondary
    }
