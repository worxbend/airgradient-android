package dev.worxbend.airgradient.presentation.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.domain.model.SensorMetric
import dev.worxbend.airgradient.domain.model.SensorMetricKind
import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.presentation.dashboard.label

@Composable
internal fun AqiHeroCard(
    metric: SensorMetric?,
    overallStatus: SensorStatus,
    lastUpdatedLabel: String,
    isRefreshing: Boolean,
) {
    val statusColor =
        animateColorAsState(
            targetValue = overallStatus.statusColor(),
            animationSpec = tween(durationMillis = STATUS_COLOR_ANIMATION_MILLIS),
            label = "AQI status color",
        ).value
    val heroBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    statusColor.copy(alpha = 0.22f),
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.82f),
                ),
        )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardComponentDefaults.cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(heroBrush),
        ) {
            HeroBackgroundGraphics(statusColor = statusColor)
            MetricIconWatermark(
                kind = metric?.kind ?: SensorMetricKind.AQI,
                size = 190.dp,
                alpha = 0.09f,
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeroHeader(
                    overallStatus = overallStatus,
                    lastUpdatedLabel = lastUpdatedLabel,
                )
                HeroReading(metric = metric)
                HeroFooter(
                    metric = metric,
                    isRefreshing = isRefreshing,
                )
                if (isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun HeroBackgroundGraphics(statusColor: Color) {
    val secondaryColor = MaterialTheme.colorScheme.secondary
    Canvas(modifier = Modifier.fillMaxSize()) {
        val flowOne =
            Path().apply {
                moveTo(size.width * 0.04f, size.height * 0.78f)
                cubicTo(
                    size.width * 0.28f,
                    size.height * 0.54f,
                    size.width * 0.52f,
                    size.height * 0.92f,
                    size.width * 0.96f,
                    size.height * 0.55f,
                )
            }
        val flowTwo =
            Path().apply {
                moveTo(size.width * 0.18f, size.height * 0.18f)
                cubicTo(
                    size.width * 0.40f,
                    size.height * 0.06f,
                    size.width * 0.64f,
                    size.height * 0.25f,
                    size.width * 0.88f,
                    size.height * 0.14f,
                )
            }

        drawPath(
            path = flowOne,
            color = statusColor.copy(alpha = 0.18f),
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = flowTwo,
            color = secondaryColor.copy(alpha = 0.16f),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )

        repeat(5) { index ->
            val left = size.width * (0.58f + index * 0.075f)
            val top = size.height * (0.22f + (index % 2) * 0.1f)
            drawRoundRect(
                color = statusColor.copy(alpha = 0.10f),
                topLeft = Offset(left, top),
                size = Size(width = 26.dp.toPx(), height = 5.dp.toPx()),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun HeroHeader(
    overallStatus: SensorStatus,
    lastUpdatedLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(status = overallStatus)
        Text(
            text = lastUpdatedLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroReading(metric: SensorMetric?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = metric?.displayName ?: "AQI",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = metric?.valueLabel ?: "--",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = metric?.interpretation ?: "No reading",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroFooter(
    metric: SensorMetric?,
    isRefreshing: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrendLabel(metric = metric)
        Text(
            text = if (isRefreshing) "Refreshing" else "Online",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun StatusPill(status: SensorStatus) {
    val statusColor =
        animateColorAsState(
            targetValue = status.statusColor(),
            animationSpec = tween(durationMillis = STATUS_COLOR_ANIMATION_MILLIS),
            label = "Status pill color",
        ).value
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.16f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status = status)
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
internal fun StatusDot(status: SensorStatus) {
    val statusColor =
        animateColorAsState(
            targetValue = status.statusColor(),
            animationSpec = tween(durationMillis = STATUS_COLOR_ANIMATION_MILLIS),
            label = "Status dot color",
        ).value
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor),
    )
}

private const val STATUS_COLOR_ANIMATION_MILLIS = 450
