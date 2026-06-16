package dev.worxbend.airgradient.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.worxbend.airgradient.core.dispatchers.AppDispatchers
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationError
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import dev.worxbend.airgradient.domain.notifications.NotificationSeverity
import dev.worxbend.airgradient.domain.repository.SaveDeviceUrlResult
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizationResult
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizer
import dev.worxbend.airgradient.domain.usecase.TestDeviceConnectionResult
import dev.worxbend.airgradient.service.MonitoringServiceController
import dev.worxbend.airgradient.service.MonitoringServiceControllerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
class SettingsViewModel(
    private val useCases: SettingsUseCases,
    private val monitoringServiceController: MonitoringServiceController,
    private val dispatchers: AppDispatchers = AppDispatchers.production,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    private var isDeviceUrlDirty = false

    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatchers.io) {
            useCases.observeSettings().collect { settings ->
                _uiState.update { state -> state.applySettings(settings, isDeviceUrlDirty) }
            }
        }
        viewModelScope.launch(dispatchers.io) {
            useCases.observeMonitoringSettings().collect { settings ->
                _uiState.update { state -> state.applyMonitoringSettings(settings) }
            }
        }
    }

    fun onDeviceUrlChanged(input: String) {
        isDeviceUrlDirty = true
        _uiState.update { state ->
            state.copy(
                deviceUrlInput = input,
                deviceUrlPreview = input.toDeviceUrlPreview(),
                saveState = DeviceUrlSaveState.Idle,
                connectionTestState = ConnectionTestState.Idle,
            )
        }
    }

    fun saveDeviceUrl() {
        viewModelScope.launch(dispatchers.io) {
            when (val result = useCases.saveDeviceUrl(_uiState.value.deviceUrlInput)) {
                SaveDeviceUrlResult.Invalid -> {
                    _uiState.update { state -> state.copy(saveState = DeviceUrlSaveState.Invalid) }
                }

                is SaveDeviceUrlResult.Saved -> {
                    isDeviceUrlDirty = false
                    _uiState.update { state ->
                        state.copy(
                            deviceUrlInput = result.serverUrl.orEmpty(),
                            deviceUrlPreview = result.serverUrl.toDeviceUrlPreview(),
                            saveState = DeviceUrlSaveState.Saved(result.serverUrl),
                        )
                    }
                }
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch(dispatchers.io) {
            val input = _uiState.value.deviceUrlInput
            _uiState.update { state -> state.copy(connectionTestState = ConnectionTestState.Testing) }

            val testState =
                when (val result = useCases.testDeviceConnection(input)) {
                    TestDeviceConnectionResult.InvalidUrl -> {
                        ConnectionTestState.InvalidInput
                    }

                    is TestDeviceConnectionResult.Success -> {
                        ConnectionTestState.Success(result.normalizedUrl)
                    }

                    is TestDeviceConnectionResult.Failure -> {
                        ConnectionTestState.Failure(
                            error = result.error,
                            message = SettingsPresentationFormatter.connectionFailureMessage(result.error),
                        )
                    }
                }

            _uiState.update { state -> state.copy(connectionTestState = testState) }
        }
    }

    fun onRefreshIntervalSelected(seconds: Int) {
        val clampedSeconds = AppSettings.clampRefreshInterval(seconds)
        _uiState.update { state -> state.copy(refreshIntervalSeconds = clampedSeconds) }
        viewModelScope.launch(dispatchers.io) {
            useCases.saveRefreshInterval(clampedSeconds)
        }
    }

    fun onNotificationsEnabledChanged(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                notificationsEnabled = enabled,
                notificationPermissionDenied = false,
            )
        }
        viewModelScope.launch(dispatchers.io) {
            useCases.saveNotificationsEnabled(enabled)
        }
    }

    fun onMinimumNotificationSeveritySelected(severity: NotificationSeverity) {
        _uiState.update { state -> state.copy(minimumNotificationSeverity = severity) }
        viewModelScope.launch(dispatchers.io) {
            useCases.saveMinimumNotificationSeverity(severity)
        }
    }

    fun onNotifyOnRecoveryChanged(enabled: Boolean) {
        _uiState.update { state -> state.copy(notifyOnRecovery = enabled) }
        viewModelScope.launch(dispatchers.io) {
            useCases.saveNotifyOnRecovery(enabled)
        }
    }

    fun onNotifyOnDeviceUnreachableChanged(enabled: Boolean) {
        _uiState.update { state -> state.copy(notifyOnDeviceUnreachable = enabled) }
        viewModelScope.launch(dispatchers.io) {
            useCases.saveNotifyOnDeviceUnreachable(enabled)
        }
    }

    fun onNotificationPermissionDenied() {
        _uiState.update { state ->
            state.copy(
                notificationsEnabled = false,
                notificationPermissionDenied = true,
            )
        }
        viewModelScope.launch(dispatchers.io) {
            useCases.saveNotificationsEnabled(false)
        }
    }

    fun onThemeModeSelected(themeMode: AppThemeMode) {
        _uiState.update { state -> state.copy(themeMode = themeMode) }
        viewModelScope.launch(dispatchers.io) {
            useCases.saveThemeMode(themeMode)
        }
    }

    fun onForegroundPollingIntervalSelected(seconds: Int) {
        val supportedSeconds = seconds.coerceAtLeast(MonitoringSettings.MIN_FOREGROUND_POLLING_INTERVAL_SECONDS)
        _uiState.update { state -> state.copy(foregroundPollingIntervalSeconds = supportedSeconds) }
        viewModelScope.launch(dispatchers.io) {
            useCases.saveForegroundPollingInterval(supportedSeconds)
        }
    }

    fun onPeriodicBackgroundIntervalSelected(minutes: Int) {
        val supportedMinutes = minutes.coerceAtLeast(MonitoringSettings.MIN_PERIODIC_BACKGROUND_INTERVAL_MINUTES)
        _uiState.update { state -> state.copy(periodicBackgroundIntervalMinutes = supportedMinutes) }
        viewModelScope.launch(dispatchers.io) {
            useCases.savePeriodicBackgroundInterval(supportedMinutes)
            if (_uiState.value.monitoringMode == MonitoringMode.BatteryFriendlyPeriodic) {
                monitoringServiceController.startBatteryFriendlyMonitoring()
            }
        }
    }

    fun onAlwaysOnMonitoringEnabledChanged(enabled: Boolean) {
        if (enabled) {
            _uiState.update { state -> state.copy(monitoringActionState = MonitoringActionState.Starting) }
            viewModelScope.launch(dispatchers.io) {
                val actionState =
                    when (val result = monitoringServiceController.startAlwaysOnMonitoring()) {
                        MonitoringServiceControllerResult.Started -> MonitoringActionState.Started
                        MonitoringServiceControllerResult.Stopped -> MonitoringActionState.Stopped
                        is MonitoringServiceControllerResult.Rejected -> MonitoringActionState.Rejected(result.error)
                    }
                _uiState.update { state -> state.copy(monitoringActionState = actionState) }
            }
        } else {
            _uiState.update { state -> state.copy(monitoringActionState = MonitoringActionState.Stopping) }
            viewModelScope.launch(dispatchers.io) {
                monitoringServiceController.stopMonitoring()
                _uiState.update { state -> state.copy(monitoringActionState = MonitoringActionState.Stopped) }
            }
        }
    }

    fun onBatteryFriendlyMonitoringEnabled() {
        _uiState.update { state -> state.copy(monitoringActionState = MonitoringActionState.Starting) }
        viewModelScope.launch(dispatchers.io) {
            val actionState =
                when (val result = monitoringServiceController.startBatteryFriendlyMonitoring()) {
                    MonitoringServiceControllerResult.Started -> MonitoringActionState.Started
                    MonitoringServiceControllerResult.Stopped -> MonitoringActionState.Stopped
                    is MonitoringServiceControllerResult.Rejected -> MonitoringActionState.Rejected(result.error)
                }
            _uiState.update { state -> state.copy(monitoringActionState = actionState) }
        }
    }

    fun onMonitoringPermissionDenied() {
        _uiState.update { state ->
            state.copy(
                monitoringActionState =
                    MonitoringActionState.Rejected(
                        MonitoringPolicyValidationError.MissingNotificationPermission,
                    ),
            )
        }
    }
}

private fun SettingsUiState.applySettings(
    settings: AppSettings,
    isDeviceUrlDirty: Boolean,
): SettingsUiState =
    copy(
        deviceUrlInput = if (isDeviceUrlDirty) deviceUrlInput else settings.serverUrl.orEmpty(),
        deviceUrlPreview =
            if (isDeviceUrlDirty) {
                deviceUrlPreview
            } else {
                settings.serverUrl.toDeviceUrlPreview()
            },
        refreshIntervalSeconds = settings.refreshIntervalSeconds,
        notificationsEnabled = settings.notificationsEnabled,
        minimumNotificationSeverity = settings.minimumNotificationSeverity,
        notifyOnRecovery = settings.notifyOnRecovery,
        notifyOnDeviceUnreachable = settings.notifyOnDeviceUnreachable,
        notificationPermissionDenied =
            if (settings.notificationsEnabled) {
                false
            } else {
                notificationPermissionDenied
            },
        themeMode = settings.themeMode,
    )

private fun SettingsUiState.applyMonitoringSettings(settings: MonitoringSettings): SettingsUiState =
    copy(
        monitoringMode = settings.mode,
        foregroundPollingIntervalSeconds = settings.foregroundPollingIntervalSeconds,
        periodicBackgroundIntervalMinutes = settings.periodicBackgroundIntervalMinutes,
        monitoringActionState =
            if (settings.mode != MonitoringMode.Off &&
                monitoringActionState == MonitoringActionState.Starting
            ) {
                MonitoringActionState.Started
            } else {
                monitoringActionState
            },
    )

private fun String?.toDeviceUrlPreview(): DeviceUrlPreview =
    when (val result = DeviceUrlNormalizer.normalize(orEmpty())) {
        DeviceUrlNormalizationResult.Unconfigured -> DeviceUrlPreview.Empty
        DeviceUrlNormalizationResult.Invalid -> DeviceUrlPreview.Invalid
        is DeviceUrlNormalizationResult.Normalized -> DeviceUrlPreview.Valid(result.value)
    }
