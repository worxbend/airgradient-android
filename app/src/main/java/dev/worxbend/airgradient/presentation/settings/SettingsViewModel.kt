package dev.worxbend.airgradient.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.worxbend.airgradient.core.dispatchers.AppDispatchers
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.repository.SaveDeviceUrlResult
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizationResult
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizer
import dev.worxbend.airgradient.domain.usecase.ObserveSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.SaveDeviceUrlUseCase
import dev.worxbend.airgradient.domain.usecase.SaveNotificationsEnabledUseCase
import dev.worxbend.airgradient.domain.usecase.SaveRefreshIntervalUseCase
import dev.worxbend.airgradient.domain.usecase.SaveThemeModeUseCase
import dev.worxbend.airgradient.domain.usecase.TestDeviceConnectionResult
import dev.worxbend.airgradient.domain.usecase.TestDeviceConnectionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val observeSettings: ObserveSettingsUseCase,
    private val saveDeviceUrlUseCase: SaveDeviceUrlUseCase,
    private val saveRefreshInterval: SaveRefreshIntervalUseCase,
    private val saveNotificationsEnabled: SaveNotificationsEnabledUseCase,
    private val saveThemeMode: SaveThemeModeUseCase,
    private val testDeviceConnection: TestDeviceConnectionUseCase,
    private val dispatchers: AppDispatchers = AppDispatchers.production,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    private var isDeviceUrlDirty = false

    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatchers.io) {
            observeSettings().collect(::applySettings)
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
            when (val result = saveDeviceUrlUseCase(_uiState.value.deviceUrlInput)) {
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
                when (val result = testDeviceConnection(input)) {
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
            saveRefreshInterval(clampedSeconds)
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
            saveNotificationsEnabled(enabled)
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
            saveNotificationsEnabled(false)
        }
    }

    fun onThemeModeSelected(themeMode: AppThemeMode) {
        _uiState.update { state -> state.copy(themeMode = themeMode) }
        viewModelScope.launch(dispatchers.io) {
            saveThemeMode(themeMode)
        }
    }

    private fun applySettings(settings: AppSettings) {
        _uiState.update { state ->
            state.copy(
                deviceUrlInput = if (isDeviceUrlDirty) state.deviceUrlInput else settings.serverUrl.orEmpty(),
                deviceUrlPreview =
                    if (isDeviceUrlDirty) {
                        state.deviceUrlPreview
                    } else {
                        settings.serverUrl.toDeviceUrlPreview()
                    },
                refreshIntervalSeconds = settings.refreshIntervalSeconds,
                notificationsEnabled = settings.notificationsEnabled,
                notificationPermissionDenied =
                    if (settings.notificationsEnabled) {
                        false
                    } else {
                        state.notificationPermissionDenied
                    },
                themeMode = settings.themeMode,
            )
        }
    }

    private fun String?.toDeviceUrlPreview(): DeviceUrlPreview =
        when (val result = DeviceUrlNormalizer.normalize(orEmpty())) {
            DeviceUrlNormalizationResult.Unconfigured -> DeviceUrlPreview.Empty
            DeviceUrlNormalizationResult.Invalid -> DeviceUrlPreview.Invalid
            is DeviceUrlNormalizationResult.Normalized -> DeviceUrlPreview.Valid(result.value)
        }
}
