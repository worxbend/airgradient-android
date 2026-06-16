package dev.worxbend.airgradient.presentation.settings

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode

data class SettingsUiState(
    val deviceUrlInput: String = "",
    val deviceUrlPreview: DeviceUrlPreview = DeviceUrlPreview.Empty,
    val refreshIntervalSeconds: Int = AppSettings.DEFAULT_REFRESH_INTERVAL_SECONDS,
    val notificationsEnabled: Boolean = false,
    val notificationPermissionDenied: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val saveState: DeviceUrlSaveState = DeviceUrlSaveState.Idle,
    val connectionTestState: ConnectionTestState = ConnectionTestState.Idle,
)

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
