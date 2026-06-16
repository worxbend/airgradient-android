package dev.worxbend.airgradient.presentation.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.domain.model.SensorMetric
import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.presentation.dashboard.label

@Composable
internal fun AqiHeroCard(
    metric: SensorMetric?,
    overallStatus: SensorStatus,
    lastUpdatedLabel: String,
    isRefreshing: Boolean,
) {
    val statusColor = overallStatus.statusColor()
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
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(heroBrush)
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
            text = if (isRefreshing) "Refreshing" else "Live local reading",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun StatusPill(status: SensorStatus) {
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(status.statusColor().copy(alpha = 0.16f))
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
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(status.statusColor()),
    )
}
