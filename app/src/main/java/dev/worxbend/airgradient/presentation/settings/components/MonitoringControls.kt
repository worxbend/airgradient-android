package dev.worxbend.airgradient.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import dev.worxbend.airgradient.presentation.settings.MonitoringActionState

@Composable
fun MonitoringControls(
    mode: MonitoringMode,
    foregroundPollingIntervalSeconds: Int,
    actionState: MonitoringActionState,
    onForegroundPollingIntervalSelected: (Int) -> Unit,
    onStartAlwaysOnMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAlwaysOn = mode == MonitoringMode.AlwaysOnForegroundService
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonitoringStatusRow(mode = mode)
        Text(
            text =
                "Always-on mode keeps a persistent notification visible and checks your device " +
                    "on the selected interval. This may increase battery use.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FOREGROUND_MONITORING_INTERVAL_OPTIONS.forEach { seconds ->
                FilterChip(
                    selected = foregroundPollingIntervalSeconds == seconds,
                    onClick = { onForegroundPollingIntervalSelected(seconds) },
                    label = { Text(text = seconds.toMonitoringIntervalLabel()) },
                )
            }
        }
        MonitoringActionMessage(actionState = actionState)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isAlwaysOn) {
                OutlinedButton(
                    onClick = onStopMonitoring,
                    enabled = actionState != MonitoringActionState.Stopping,
                ) {
                    Text(text = "Stop monitoring")
                }
            } else {
                Button(
                    onClick = onStartAlwaysOnMonitoring,
                    enabled = actionState != MonitoringActionState.Starting,
                ) {
                    Text(text = "Start always-on")
                }
            }
        }
    }
}

@Composable
private fun MonitoringStatusRow(mode: MonitoringMode) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Background monitoring",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = mode.toStatusText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AssistChip(
            onClick = {},
            label = { Text(text = mode.toChipLabel()) },
        )
    }
}

@Composable
private fun MonitoringActionMessage(actionState: MonitoringActionState) {
    val text =
        when (actionState) {
            MonitoringActionState.Idle -> null
            MonitoringActionState.Starting -> "Starting always-on monitoring..."
            MonitoringActionState.Started -> "Always-on monitoring started."
            MonitoringActionState.Stopping -> "Stopping monitoring..."
            MonitoringActionState.Stopped -> "Monitoring stopped."
            is MonitoringActionState.Rejected -> actionState.error.toMonitoringErrorMessage()
        }

    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (actionState is MonitoringActionState.Rejected) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

private fun MonitoringMode.toStatusText(): String =
    when (this) {
        MonitoringMode.Off -> "Monitoring off"
        MonitoringMode.AlwaysOnForegroundService -> "Always-on monitoring active"
        MonitoringMode.BatteryFriendlyPeriodic -> "Battery-friendly monitoring active"
    }

private fun MonitoringMode.toChipLabel(): String =
    when (this) {
        MonitoringMode.Off -> "Off"
        MonitoringMode.AlwaysOnForegroundService -> "Always-on"
        MonitoringMode.BatteryFriendlyPeriodic -> "Periodic"
    }

private fun MonitoringPolicyValidationError.toMonitoringErrorMessage(): String =
    when (this) {
        MonitoringPolicyValidationError.MissingDeviceUrl -> {
            "Configure a device URL before starting monitoring."
        }

        MonitoringPolicyValidationError.MissingNotificationPermission -> {
            "Android notification permission is required for always-on monitoring."
        }
    }

private fun Int.toMonitoringIntervalLabel(): String =
    if (this < SECONDS_PER_MINUTE) {
        "${this}s"
    } else {
        "${this / SECONDS_PER_MINUTE}m"
    }

private const val SECONDS_PER_MINUTE = 60
private val FOREGROUND_MONITORING_INTERVAL_OPTIONS = listOf(30, 60, 120, 300)
