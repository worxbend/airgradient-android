package dev.worxbend.airgradient.presentation.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.onNotificationsEnabledChanged(true)
            } else {
                viewModel.onNotificationPermissionDenied()
            }
        }
    val monitoringPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.onAlwaysOnMonitoringEnabledChanged(true)
            } else {
                viewModel.onMonitoringPermissionDenied()
            }
        }

    SettingsScreen(
        state = state.value,
        onNavigateBack = onNavigateBack,
        actions =
            SettingsScreenActions(
                onDeviceUrlChanged = viewModel::onDeviceUrlChanged,
                onSaveDeviceUrl = viewModel::saveDeviceUrl,
                onTestConnection = viewModel::testConnection,
                onRefreshIntervalSelected = viewModel::onRefreshIntervalSelected,
                onNotificationsEnabledChanged = { enabled ->
                    if (!enabled || hasNotificationPermission(context)) {
                        viewModel.onNotificationsEnabledChanged(enabled)
                    } else {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onMinimumNotificationSeveritySelected = viewModel::onMinimumNotificationSeveritySelected,
                onNotifyOnRecoveryChanged = viewModel::onNotifyOnRecoveryChanged,
                onNotifyOnDeviceUnreachableChanged = viewModel::onNotifyOnDeviceUnreachableChanged,
                onThemeModeSelected = viewModel::onThemeModeSelected,
                onForegroundPollingIntervalSelected = viewModel::onForegroundPollingIntervalSelected,
                onPeriodicBackgroundIntervalSelected = viewModel::onPeriodicBackgroundIntervalSelected,
                onAdaptivePollingEnabledChanged = viewModel::onAdaptivePollingEnabledChanged,
                onStartAlwaysOnMonitoring = {
                    if (hasNotificationPermission(context)) {
                        viewModel.onAlwaysOnMonitoringEnabledChanged(true)
                    } else {
                        monitoringPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onStartBatteryFriendlyMonitoring = viewModel::onBatteryFriendlyMonitoringEnabled,
                onStopMonitoring = { viewModel.onAlwaysOnMonitoringEnabledChanged(false) },
            ),
    )
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
