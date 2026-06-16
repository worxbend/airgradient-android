package dev.worxbend.airgradient.presentation.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.domain.model.SensorMetric
import dev.worxbend.airgradient.domain.model.TrendDirection

@Composable
internal fun ComfortMetricCard(metric: SensorMetric) {
    MetricCard(
        metric = metric,
        minHeight = 132.dp,
        valueTextStyle = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
internal fun SensorMetricCard(metric: SensorMetric) {
    MetricCard(
        metric = metric,
        minHeight = 148.dp,
        valueTextStyle = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun MetricCard(
    metric: SensorMetric,
    minHeight: Dp,
    valueTextStyle: TextStyle,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(minHeight),
        shape = DashboardComponentDefaults.cardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MetricIconWatermark(kind = metric.kind)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricHeader(metric = metric)
                MetricReading(metric = metric, valueTextStyle = valueTextStyle)
                TrendLabel(metric = metric)
            }
        }
    }
}

@Composable
private fun MetricHeader(metric: SensorMetric) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = metric.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StatusDot(status = metric.status)
    }
}

@Composable
private fun MetricReading(
    metric: SensorMetric,
    valueTextStyle: TextStyle,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = metric.valueLabel,
            style = valueTextStyle,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = metric.interpretation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TrendLabel(metric: SensorMetric?) {
    val trend = metric?.trend
    val label =
        when (trend?.direction) {
            TrendDirection.UP -> "Up ${trend.deltaLabel}"

            TrendDirection.DOWN -> "Down ${trend.deltaLabel}"

            TrendDirection.STABLE -> "Stable ${trend.deltaLabel}"

            TrendDirection.UNKNOWN,
            null,
            -> "No trend"
        }

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
