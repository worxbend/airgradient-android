package dev.worxbend.airgradient.presentation.settings

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationError
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import dev.worxbend.airgradient.domain.notifications.NotificationSeverity

data class SettingsUiState(
    val deviceUrlInput: String = "",
    val deviceUrlPreview: DeviceUrlPreview = DeviceUrlPreview.Empty,
    val refreshIntervalSeconds: Int = AppSettings.DEFAULT_REFRESH_INTERVAL_SECONDS,
    val notificationsEnabled: Boolean = false,
    val notificationPermissionDenied: Boolean = false,
    val minimumNotificationSeverity: NotificationSeverity = NotificationSeverity.Warning,
    val notifyOnRecovery: Boolean = true,
    val notifyOnDeviceUnreachable: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val monitoringMode: MonitoringMode = MonitoringMode.Off,
    val foregroundPollingIntervalSeconds: Int =
        MonitoringSettings.DEFAULT_FOREGROUND_POLLING_INTERVAL_SECONDS,
    val periodicBackgroundIntervalMinutes: Int =
        MonitoringSettings.DEFAULT_PERIODIC_BACKGROUND_INTERVAL_MINUTES,
    val monitoringDiagnostics: SettingsMonitoringDiagnostics = SettingsMonitoringDiagnostics(),
    val monitoringActionState: MonitoringActionState = MonitoringActionState.Idle,
    val saveState: DeviceUrlSaveState = DeviceUrlSaveState.Idle,
    val connectionTestState: ConnectionTestState = ConnectionTestState.Idle,
)

data class SettingsMonitoringDiagnostics(
    val lastBackgroundCheckLabel: String? = null,
    val lastSuccessfulReadLabel: String? = null,
    val lastFailureLabel: String? = null,
    val consecutiveFailureCount: Int = 0,
)

sealed interface MonitoringActionState {
    data object Idle : MonitoringActionState

    data object Starting : MonitoringActionState

    data object Started : MonitoringActionState

    data object Stopping : MonitoringActionState

    data object Stopped : MonitoringActionState

    data class Rejected(
        val error: MonitoringPolicyValidationError,
    ) : MonitoringActionState
}

sealed interface DeviceUrlPreview {
    data object Empty : DeviceUrlPreview

    data class Valid(
        val normalizedUrl: String,
    ) : DeviceUrlPreview

    data object Invalid : DeviceUrlPreview
}

sealed interface DeviceUrlSaveState {
    data object Idle : DeviceUrlSaveState

    data class Saved(
        val normalizedUrl: String?,
    ) : DeviceUrlSaveState

    data object Invalid : DeviceUrlSaveState
}

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState

    data object Testing : ConnectionTestState

    data class Success(
        val normalizedUrl: String,
    ) : ConnectionTestState

    data class Failure(
        val error: AirGradientError,
        val message: String,
    ) : ConnectionTestState

    data object InvalidInput : ConnectionTestState
}
