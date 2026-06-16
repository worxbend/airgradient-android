package dev.worxbend.airgradient.presentation.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.domain.model.SensorMetric
import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.presentation.dashboard.DashboardError
import dev.worxbend.airgradient.presentation.dashboard.DashboardMonitoringSummary

@Composable
internal fun RefreshStatusBar(
    fetchStatusLabel: String,
    refreshIntervalSeconds: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardComponentDefaults.cardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = fetchStatusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${refreshIntervalSeconds}s",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun WarningPanel(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardComponentDefaults.cardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = DashboardComponentDefaults.warning.copy(alpha = 0.16f),
            ),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
internal fun EmptyConfigurationPanel(onConfigureDevice: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Card(
            shape = DashboardComponentDefaults.cardShape,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                ),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EmptyStateBadge()
                Text(
                    text = "Configure a local AirGradient device",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Enter the local device URL to load indoor air readings from /measures/current.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onConfigureDevice) {
                    Text(text = "Configure device")
                }
            }
        }
    }
}

@Composable
private fun EmptyStateBadge() {
    Box(
        modifier =
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(DashboardComponentDefaults.good.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "AQ",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DashboardComponentDefaults.good,
        )
    }
}

@Composable
internal fun LoadingDashboard() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingCard(height = 230.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadingCard(
                height = 132.dp,
                modifier = Modifier.weight(1f),
            )
            LoadingCard(
                height = 132.dp,
                modifier = Modifier.weight(1f),
            )
        }
        LoadingCard(height = 148.dp)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LoadingCard(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height),
        shape = DashboardComponentDefaults.cardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingBar(widthFraction = 0.36f)
            LoadingBar(widthFraction = 0.64f)
            LoadingBar(widthFraction = 0.48f)
        }
    }
}

@Composable
private fun LoadingBar(widthFraction: Float) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
    )
}

@Composable
internal fun ErrorDashboard(
    error: DashboardError,
    lastUpdatedLabel: String?,
    metrics: List<SensorMetric>,
    monitoringSummary: DashboardMonitoringSummary,
    monitoringActions: DashboardMonitoringActions,
    onRetry: () -> Unit,
    onConfigureDevice: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ErrorPanel(
            error = error,
            lastUpdatedLabel = lastUpdatedLabel,
            onRetry = onRetry,
            onConfigureDevice = onConfigureDevice,
        )
        if (metrics.isNotEmpty()) {
            DashboardContent(
                content =
                    DashboardContentModel(
                        metrics = metrics,
                        overallStatus = SensorStatus.UNKNOWN,
                        lastUpdatedLabel = lastUpdatedLabel ?: "Last update unavailable",
                        fetchStatusLabel = error.message,
                        refreshIntervalSeconds = 30,
                        monitoringSummary = monitoringSummary,
                        isRefreshing = false,
                    ),
                monitoringActions = monitoringActions,
            )
        } else {
            MonitoringStatusCard(
                summary = monitoringSummary,
                actions = monitoringActions,
            )
        }
    }
}

@Composable
private fun ErrorPanel(
    error: DashboardError,
    lastUpdatedLabel: String?,
    onRetry: () -> Unit,
    onConfigureDevice: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardComponentDefaults.cardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.86f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorPanelText(error = error, lastUpdatedLabel = lastUpdatedLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRetry) {
                    Text(text = "Retry")
                }
                OutlinedButton(onClick = onConfigureDevice) {
                    Text(text = "Settings")
                }
            }
        }
    }
}

@Composable
private fun ErrorPanelText(
    error: DashboardError,
    lastUpdatedLabel: String?,
) {
    Text(
        text = error.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onErrorContainer,
    )
    Text(
        text = error.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
    )
    if (lastUpdatedLabel != null) {
        Text(
            text = lastUpdatedLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
