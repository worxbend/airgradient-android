package dev.worxbend.airgradient.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
@OptIn(ExperimentalLayoutApi::class)
fun MonitoringControls(
    mode: MonitoringMode,
    foregroundPollingIntervalSeconds: Int,
    periodicBackgroundIntervalMinutes: Int,
    actionState: MonitoringActionState,
    actions: MonitoringControlActions,
    modifier: Modifier = Modifier,
) {
    val isAlwaysOn = mode == MonitoringMode.AlwaysOnForegroundService
    val isBatteryFriendly = mode == MonitoringMode.BatteryFriendlyPeriodic
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
        MonitoringIntervalChips(
            label = "Always-on interval",
            options = FOREGROUND_MONITORING_INTERVAL_OPTIONS,
            selected = foregroundPollingIntervalSeconds,
            onSelected = actions.onForegroundPollingIntervalSelected,
            valueLabel = Int::toSecondsIntervalLabel,
        )
        Text(
            text =
                "Battery-friendly mode uses Android background scheduling. Checks are not real-time " +
                    "and may run every 15 minutes or later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MonitoringIntervalChips(
            label = "Battery-friendly interval",
            options = PERIODIC_MONITORING_INTERVAL_OPTIONS,
            selected = periodicBackgroundIntervalMinutes,
            onSelected = actions.onPeriodicBackgroundIntervalSelected,
            valueLabel = Int::toMinutesIntervalLabel,
        )
        MonitoringActionMessage(actionState = actionState)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isAlwaysOn || isBatteryFriendly) {
                OutlinedButton(
                    onClick = actions.onStopMonitoring,
                    enabled = actionState != MonitoringActionState.Stopping,
                ) {
                    Text(text = "Stop monitoring")
                }
            } else {
                Button(
                    onClick = actions.onStartAlwaysOnMonitoring,
                    enabled = actionState != MonitoringActionState.Starting,
                ) {
                    Text(text = "Start always-on")
                }
                OutlinedButton(
                    onClick = actions.onStartBatteryFriendlyMonitoring,
                    enabled = actionState != MonitoringActionState.Starting,
                ) {
                    Text(text = "Start battery-friendly")
                }
            }
        }
    }
}

data class MonitoringControlActions(
    val onForegroundPollingIntervalSelected: (Int) -> Unit,
    val onPeriodicBackgroundIntervalSelected: (Int) -> Unit,
    val onStartAlwaysOnMonitoring: () -> Unit,
    val onStartBatteryFriendlyMonitoring: () -> Unit,
    val onStopMonitoring: () -> Unit,
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MonitoringIntervalChips(
    label: String,
    options: List<Int>,
    selected: Int,
    onSelected: (Int) -> Unit,
    valueLabel: (Int) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(text = valueLabel(value)) },
                )
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
            MonitoringActionState.Starting -> "Starting monitoring..."
            MonitoringActionState.Started -> "Monitoring started."
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

private fun Int.toSecondsIntervalLabel(): String =
    if (this < SECONDS_PER_MINUTE) {
        "${this}s"
    } else {
        "${this / SECONDS_PER_MINUTE}m"
    }

private fun Int.toMinutesIntervalLabel(): String =
    when {
        this < MINUTES_PER_HOUR -> "${this}m"
        this % MINUTES_PER_HOUR == 0 -> "${this / MINUTES_PER_HOUR}h"
        else -> "${this}m"
    }

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private val FOREGROUND_MONITORING_INTERVAL_OPTIONS = listOf(30, 60, 120, 300)
private val PERIODIC_MONITORING_INTERVAL_OPTIONS = listOf(15, 30, 60)
