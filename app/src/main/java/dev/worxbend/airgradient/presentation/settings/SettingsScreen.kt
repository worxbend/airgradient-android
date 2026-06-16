package dev.worxbend.airgradient.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.notifications.NotificationSeverity
import dev.worxbend.airgradient.presentation.settings.components.DeviceUrlInput
import dev.worxbend.airgradient.presentation.settings.components.DeviceUrlInputActions
import dev.worxbend.airgradient.presentation.settings.components.MonitoringControlActions
import dev.worxbend.airgradient.presentation.settings.components.MonitoringControls
import dev.worxbend.airgradient.presentation.settings.components.RefreshIntervalSelector
import dev.worxbend.airgradient.presentation.settings.components.ThemeSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onNavigateBack: () -> Unit,
    actions: SettingsScreenActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(text = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )
        },
    ) { paddingValues ->
        SettingsContent(
            state = state,
            actions = actions,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(settingsBackgroundBrush())
                    .padding(paddingValues),
        )
    }
}

data class SettingsScreenActions(
    val onDeviceUrlChanged: (String) -> Unit,
    val onSaveDeviceUrl: () -> Unit,
    val onTestConnection: () -> Unit,
    val onRefreshIntervalSelected: (Int) -> Unit,
    val onNotificationsEnabledChanged: (Boolean) -> Unit,
    val onMinimumNotificationSeveritySelected: (NotificationSeverity) -> Unit,
    val onNotifyOnRecoveryChanged: (Boolean) -> Unit,
    val onNotifyOnDeviceUnreachableChanged: (Boolean) -> Unit,
    val onThemeModeSelected: (AppThemeMode) -> Unit,
    val onForegroundPollingIntervalSelected: (Int) -> Unit,
    val onPeriodicBackgroundIntervalSelected: (Int) -> Unit,
    val onStartAlwaysOnMonitoring: () -> Unit,
    val onStartBatteryFriendlyMonitoring: () -> Unit,
    val onStopMonitoring: () -> Unit,
)

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsScreenActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection(title = "Device") {
                DeviceUrlInput(
                    input = state.deviceUrlInput,
                    preview = state.deviceUrlPreview,
                    saveState = state.saveState,
                    connectionTestState = state.connectionTestState,
                    actions =
                        DeviceUrlInputActions(
                            onInputChanged = actions.onDeviceUrlChanged,
                            onSave = actions.onSaveDeviceUrl,
                            onTestConnection = actions.onTestConnection,
                        ),
                )
            }
        }
        item {
            SettingsSection(title = "Refresh") {
                RefreshIntervalSelector(
                    selectedSeconds = state.refreshIntervalSeconds,
                    onSelected = actions.onRefreshIntervalSelected,
                )
            }
        }
        item {
            SettingsSection(title = "Notifications") {
                NotificationsRow(
                    state = state,
                    actions =
                        NotificationPreferenceActions(
                            onEnabledChanged = actions.onNotificationsEnabledChanged,
                            onMinimumSeveritySelected = actions.onMinimumNotificationSeveritySelected,
                            onNotifyOnRecoveryChanged = actions.onNotifyOnRecoveryChanged,
                            onNotifyOnDeviceUnreachableChanged = actions.onNotifyOnDeviceUnreachableChanged,
                        ),
                )
            }
        }
        item {
            SettingsSection(title = "Monitoring") {
                MonitoringControls(
                    mode = state.monitoringMode,
                    foregroundPollingIntervalSeconds = state.foregroundPollingIntervalSeconds,
                    periodicBackgroundIntervalMinutes = state.periodicBackgroundIntervalMinutes,
                    actionState = state.monitoringActionState,
                    actions =
                        MonitoringControlActions(
                            onForegroundPollingIntervalSelected = actions.onForegroundPollingIntervalSelected,
                            onPeriodicBackgroundIntervalSelected = actions.onPeriodicBackgroundIntervalSelected,
                            onStartAlwaysOnMonitoring = actions.onStartAlwaysOnMonitoring,
                            onStartBatteryFriendlyMonitoring = actions.onStartBatteryFriendlyMonitoring,
                            onStopMonitoring = actions.onStopMonitoring,
                        ),
                )
            }
        }
        item {
            SettingsSection(title = "Appearance") {
                ThemeSelector(
                    selectedThemeMode = state.themeMode,
                    onSelected = actions.onThemeModeSelected,
                )
            }
        }
        item {
            AboutSection()
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NotificationsRow(
    state: SettingsUiState,
    actions: NotificationPreferenceActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AlertSwitchRow(
            title = "Air quality alerts",
            description =
                if (state.notificationPermissionDenied) {
                    "Android notification permission was denied. Alerts remain off."
                } else {
                    "Notify when readings stay degraded. Disabled by default."
                },
            checked = state.notificationsEnabled,
            isError = state.notificationPermissionDenied,
            onCheckedChange = actions.onEnabledChanged,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Minimum alert severity",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NotificationSeverity.entries.forEach { severity ->
                    FilterChip(
                        selected = state.minimumNotificationSeverity == severity,
                        onClick = { actions.onMinimumSeveritySelected(severity) },
                        label = { Text(text = severity.toDisplayLabel()) },
                    )
                }
            }
        }
        AlertSwitchRow(
            title = "Recovery alerts",
            description = "Notify after air quality returns below alert thresholds.",
            checked = state.notifyOnRecovery,
            onCheckedChange = actions.onNotifyOnRecoveryChanged,
        )
        AlertSwitchRow(
            title = "Device unreachable alerts",
            description = "Notify after repeated failed local-network checks.",
            checked = state.notifyOnDeviceUnreachable,
            onCheckedChange = actions.onNotifyOnDeviceUnreachableChanged,
        )
    }
}

private data class NotificationPreferenceActions(
    val onEnabledChanged: (Boolean) -> Unit,
    val onMinimumSeveritySelected: (NotificationSeverity) -> Unit,
    val onNotifyOnRecoveryChanged: (Boolean) -> Unit,
    val onNotifyOnDeviceUnreachableChanged: (Boolean) -> Unit,
)

@Composable
private fun AlertSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isError: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun NotificationSeverity.toDisplayLabel(): String =
    when (this) {
        NotificationSeverity.Warning -> "Warning"
        NotificationSeverity.Critical -> "Critical"
    }

@Composable
private fun AboutSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "This app reads AirGradient-compatible local devices at /measures/current.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistChip(
                onClick = {},
                label = { Text(text = "Data stays on this device") },
            )
        }
    }
}

@Composable
private fun settingsBackgroundBrush(): Brush =
    Brush.verticalGradient(
        colors =
            listOf(
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.07f),
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
            ),
    )
