package dev.worxbend.airgradient.presentation.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationError
import dev.worxbend.airgradient.presentation.dashboard.DashboardMonitoringActionState
import dev.worxbend.airgradient.presentation.dashboard.DashboardMonitoringSummary

@Composable
internal fun MonitoringStatusCard(
    summary: DashboardMonitoringSummary,
    actions: DashboardMonitoringActions,
) {
    val isAlwaysOn = summary.mode == MonitoringMode.AlwaysOnForegroundService

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardComponentDefaults.cardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonitoringStatusText(summary = summary)
            DashboardMonitoringActionMessage(actionState = summary.actionState)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isAlwaysOn) {
                    OutlinedButton(
                        onClick = actions.onStopMonitoring,
                        enabled = summary.actionState != DashboardMonitoringActionState.Stopping,
                    ) {
                        Text(text = "Stop monitoring")
                    }
                } else {
                    Button(
                        onClick = actions.onStartMonitoring,
                        enabled = summary.actionState != DashboardMonitoringActionState.Starting,
                    ) {
                        Text(text = "Start always-on")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitoringStatusText(summary: DashboardMonitoringSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Background monitoring",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary.mode.toDashboardMonitoringStatusText(summary.foregroundPollingIntervalSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = summary.mode.toDashboardMonitoringChipText(),
            style = MaterialTheme.typography.labelLarge,
            color =
                if (summary.mode.isEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun DashboardMonitoringActionMessage(actionState: DashboardMonitoringActionState) {
    val text =
        when (actionState) {
            DashboardMonitoringActionState.Idle -> null
            DashboardMonitoringActionState.Starting -> "Starting always-on monitoring..."
            DashboardMonitoringActionState.Started -> "Always-on monitoring started."
            DashboardMonitoringActionState.Stopping -> "Stopping monitoring..."
            DashboardMonitoringActionState.Stopped -> "Monitoring stopped."
            is DashboardMonitoringActionState.Rejected -> actionState.error.toDashboardMonitoringErrorMessage()
        }

    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (actionState is DashboardMonitoringActionState.Rejected) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

private fun MonitoringMode.toDashboardMonitoringStatusText(intervalSeconds: Int): String =
    when (this) {
        MonitoringMode.Off -> "Monitoring off"
        MonitoringMode.AlwaysOnForegroundService -> "Always-on checks every ${intervalSeconds.toIntervalText()}"
        MonitoringMode.BatteryFriendlyPeriodic -> "Battery-friendly background checks active"
    }

private fun MonitoringMode.toDashboardMonitoringChipText(): String =
    when (this) {
        MonitoringMode.Off -> "Off"
        MonitoringMode.AlwaysOnForegroundService -> "Always-on"
        MonitoringMode.BatteryFriendlyPeriodic -> "Periodic"
    }

private fun MonitoringPolicyValidationError.toDashboardMonitoringErrorMessage(): String =
    when (this) {
        MonitoringPolicyValidationError.MissingDeviceUrl -> {
            "Configure a device URL before starting monitoring."
        }

        MonitoringPolicyValidationError.MissingNotificationPermission -> {
            "Android notification permission is required for always-on monitoring."
        }
    }

private fun Int.toIntervalText(): String =
    if (this < SECONDS_PER_MINUTE) {
        "${this}s"
    } else {
        "${this / SECONDS_PER_MINUTE}m"
    }

private const val SECONDS_PER_MINUTE = 60
