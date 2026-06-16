package dev.worxbend.airgradient.presentation.settings

import androidx.lifecycle.viewModelScope
import dev.worxbend.airgradient.core.dispatchers.AppDispatchers
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationError
import dev.worxbend.airgradient.domain.monitoring.MonitoringRuntimeState
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import dev.worxbend.airgradient.domain.notifications.NotificationSeverity
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import dev.worxbend.airgradient.domain.repository.AirGradientRepository
import dev.worxbend.airgradient.domain.repository.MonitoringRuntimeStateRepository
import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository
import dev.worxbend.airgradient.domain.repository.SaveDeviceUrlResult
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizationResult
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizer
import dev.worxbend.airgradient.domain.usecase.GetCurrentMeasurementUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveMonitoringRuntimeStateUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveMonitoringSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.SaveDeviceUrlUseCase
import dev.worxbend.airgradient.domain.usecase.SaveForegroundPollingIntervalUseCase
import dev.worxbend.airgradient.domain.usecase.SaveMinimumNotificationSeverityUseCase
import dev.worxbend.airgradient.domain.usecase.SaveNotificationsEnabledUseCase
import dev.worxbend.airgradient.domain.usecase.SaveNotifyOnDeviceUnreachableUseCase
import dev.worxbend.airgradient.domain.usecase.SaveNotifyOnRecoveryUseCase
import dev.worxbend.airgradient.domain.usecase.SavePeriodicBackgroundIntervalUseCase
import dev.worxbend.airgradient.domain.usecase.SaveRefreshIntervalUseCase
import dev.worxbend.airgradient.domain.usecase.SaveThemeModeUseCase
import dev.worxbend.airgradient.domain.usecase.TestDeviceConnectionUseCase
import dev.worxbend.airgradient.service.MonitoringServiceController
import dev.worxbend.airgradient.service.MonitoringServiceControllerResult
import dev.worxbend.airgradient.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads persisted settings into form state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settings =
                AppSettings.default.copy(
                    serverUrl = "http://192.168.1.201",
                    refreshIntervalSeconds = 60,
                    notificationsEnabled = true,
                    themeMode = AppThemeMode.DARK,
                )
            val viewModel = viewModel(settingsRepository = FakeSettingsRepository(settings))

            runCurrent()

            val state = viewModel.uiState.value
            assertEquals("http://192.168.1.201", state.deviceUrlInput)
            assertEquals(DeviceUrlPreview.Valid("http://192.168.1.201"), state.deviceUrlPreview)
            assertEquals(60, state.refreshIntervalSeconds)
            assertEquals(true, state.notificationsEnabled)
            assertEquals(AppThemeMode.DARK, state.themeMode)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `saving device url normalizes and persists bare host`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(settingsRepository = settingsRepository)
            runCurrent()

            viewModel.onDeviceUrlChanged("192.168.1.201")
            viewModel.saveDeviceUrl()
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals("http://192.168.1.201", settingsRepository.settingsState.value.serverUrl)
            assertEquals("http://192.168.1.201", state.deviceUrlInput)
            assertEquals(DeviceUrlSaveState.Saved("http://192.168.1.201"), state.saveState)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `saving invalid device url reports validation error without persisting`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = FakeSettingsRepository()
            val controller = FakeMonitoringServiceController()
            val viewModel =
                viewModel(
                    settingsRepository = settingsRepository,
                    monitoringServiceController = controller,
                )
            runCurrent()

            viewModel.onDeviceUrlChanged("ftp://airgradient.local")
            viewModel.saveDeviceUrl()
            runCurrent()

            assertEquals(null, settingsRepository.settingsState.value.serverUrl)
            assertEquals(DeviceUrlSaveState.Invalid, viewModel.uiState.value.saveState)
            assertEquals(emptyList<String>(), controller.actions)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `clearing device url stops monitoring immediately`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository =
                FakeSettingsRepository(
                    AppSettings.default.copy(serverUrl = "http://192.168.1.201"),
                )
            val controller = FakeMonitoringServiceController()
            val viewModel =
                viewModel(
                    settingsRepository = settingsRepository,
                    monitoringServiceController = controller,
                )
            runCurrent()

            viewModel.onDeviceUrlChanged("")
            viewModel.saveDeviceUrl()
            runCurrent()

            assertEquals(null, settingsRepository.settingsState.value.serverUrl)
            assertEquals(DeviceUrlSaveState.Saved(null), viewModel.uiState.value.saveState)
            assertEquals(MonitoringActionState.Stopped, viewModel.uiState.value.monitoringActionState)
            assertEquals(listOf("stop"), controller.actions)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `test connection uses normalized input and reports success`() =
        runTest(mainDispatcherRule.dispatcher) {
            val airRepository = FakeAirGradientRepository(AirGradientFetchResult.Success(sampleSnapshot))
            val viewModel = viewModel(airGradientRepository = airRepository)
            runCurrent()

            viewModel.onDeviceUrlChanged("192.168.1.201")
            viewModel.testConnection()
            runCurrent()

            assertEquals("http://192.168.1.201", airRepository.lastServerUrl)
            assertEquals(
                ConnectionTestState.Success("http://192.168.1.201"),
                viewModel.uiState.value.connectionTestState,
            )
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `test connection maps fetch failure to user facing state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val airRepository = FakeAirGradientRepository(AirGradientFetchResult.Failure(AirGradientError.Timeout))
            val viewModel = viewModel(airGradientRepository = airRepository)
            runCurrent()

            viewModel.onDeviceUrlChanged("http://192.168.1.201")
            viewModel.testConnection()
            runCurrent()

            val state = viewModel.uiState.value.connectionTestState as ConnectionTestState.Failure
            assertEquals(AirGradientError.Timeout, state.error)
            assertEquals("The device did not respond before the request timed out.", state.message)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `refresh interval notifications and theme changes persist immediately`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(settingsRepository = settingsRepository)
            runCurrent()

            viewModel.onRefreshIntervalSelected(5)
            viewModel.onNotificationsEnabledChanged(true)
            viewModel.onThemeModeSelected(AppThemeMode.LIGHT)
            runCurrent()

            val persisted = settingsRepository.settingsState.value
            assertEquals(5, persisted.refreshIntervalSeconds)
            assertTrue(persisted.notificationsEnabled)
            assertEquals(AppThemeMode.LIGHT, persisted.themeMode)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `notification policy preference changes persist immediately`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(settingsRepository = settingsRepository)
            runCurrent()

            viewModel.onMinimumNotificationSeveritySelected(NotificationSeverity.Critical)
            viewModel.onNotifyOnRecoveryChanged(false)
            viewModel.onNotifyOnDeviceUnreachableChanged(false)
            runCurrent()

            val persisted = settingsRepository.settingsState.value
            assertEquals(NotificationSeverity.Critical, persisted.minimumNotificationSeverity)
            assertEquals(false, persisted.notifyOnRecovery)
            assertEquals(false, persisted.notifyOnDeviceUnreachable)
            assertEquals(NotificationSeverity.Critical, viewModel.uiState.value.minimumNotificationSeverity)
            assertEquals(false, viewModel.uiState.value.notifyOnRecovery)
            assertEquals(false, viewModel.uiState.value.notifyOnDeviceUnreachable)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `loads persisted monitoring settings into form state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val monitoringRepository =
                FakeMonitoringSettingsRepository(
                    MonitoringSettings.default.copy(
                        mode = MonitoringMode.AlwaysOnForegroundService,
                        foregroundPollingIntervalSeconds = 120,
                        periodicBackgroundIntervalMinutes = 30,
                    ),
                )
            val viewModel = viewModel(monitoringSettingsRepository = monitoringRepository)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(MonitoringMode.AlwaysOnForegroundService, state.monitoringMode)
            assertEquals(120, state.foregroundPollingIntervalSeconds)
            assertEquals(30, state.periodicBackgroundIntervalMinutes)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `loads monitoring runtime diagnostics into form state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val runtimeRepository =
                FakeMonitoringRuntimeStateRepository(
                    MonitoringRuntimeState(
                        lastCheckedAt = Instant.parse("2026-06-16T12:00:00Z"),
                        lastSuccessfulCheckAt = Instant.parse("2026-06-16T11:45:00Z"),
                        lastSuccessfulMeasurementAt = Instant.parse("2026-06-16T11:44:58Z"),
                        lastFailureAt = Instant.parse("2026-06-16T12:00:00Z"),
                        consecutiveFailureCount = 2,
                    ),
                )
            val viewModel = viewModel(monitoringRuntimeStateRepository = runtimeRepository)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(
                "Last background check 2026-06-16T12:00:00Z",
                state.monitoringDiagnostics.lastBackgroundCheckLabel,
            )
            assertEquals(
                "Last successful reading 2026-06-16T11:44:58Z",
                state.monitoringDiagnostics.lastSuccessfulReadLabel,
            )
            assertEquals("Last failed check 2026-06-16T12:00:00Z", state.monitoringDiagnostics.lastFailureLabel)
            assertEquals(2, state.monitoringDiagnostics.consecutiveFailureCount)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `foreground polling interval selection persists supported value`() =
        runTest(mainDispatcherRule.dispatcher) {
            val monitoringRepository = FakeMonitoringSettingsRepository()
            val viewModel = viewModel(monitoringSettingsRepository = monitoringRepository)
            runCurrent()

            viewModel.onForegroundPollingIntervalSelected(60)
            runCurrent()

            assertEquals(60, monitoringRepository.state.value.foregroundPollingIntervalSeconds)
            assertEquals(60, viewModel.uiState.value.foregroundPollingIntervalSeconds)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `periodic background interval selection persists supported value`() =
        runTest(mainDispatcherRule.dispatcher) {
            val monitoringRepository = FakeMonitoringSettingsRepository()
            val viewModel = viewModel(monitoringSettingsRepository = monitoringRepository)
            runCurrent()

            viewModel.onPeriodicBackgroundIntervalSelected(30)
            runCurrent()

            assertEquals(30, monitoringRepository.state.value.periodicBackgroundIntervalMinutes)
            assertEquals(30, viewModel.uiState.value.periodicBackgroundIntervalMinutes)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `start always-on monitoring delegates to controller and reports success`() =
        runTest(mainDispatcherRule.dispatcher) {
            val controller = FakeMonitoringServiceController()
            val viewModel = viewModel(monitoringServiceController = controller)
            runCurrent()

            viewModel.onAlwaysOnMonitoringEnabledChanged(true)
            runCurrent()

            assertEquals(listOf("start"), controller.actions)
            assertEquals(MonitoringActionState.Started, viewModel.uiState.value.monitoringActionState)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `start battery-friendly monitoring delegates to controller and reports success`() =
        runTest(mainDispatcherRule.dispatcher) {
            val controller = FakeMonitoringServiceController()
            val viewModel = viewModel(monitoringServiceController = controller)
            runCurrent()

            viewModel.onBatteryFriendlyMonitoringEnabled()
            runCurrent()

            assertEquals(listOf("startPeriodic"), controller.actions)
            assertEquals(MonitoringActionState.Started, viewModel.uiState.value.monitoringActionState)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `start always-on monitoring surfaces controller validation error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val controller =
                FakeMonitoringServiceController(
                    startResult =
                        MonitoringServiceControllerResult.Rejected(
                            MonitoringPolicyValidationError.MissingDeviceUrl,
                        ),
                )
            val viewModel = viewModel(monitoringServiceController = controller)
            runCurrent()

            viewModel.onAlwaysOnMonitoringEnabledChanged(true)
            runCurrent()

            assertEquals(
                MonitoringActionState.Rejected(MonitoringPolicyValidationError.MissingDeviceUrl),
                viewModel.uiState.value.monitoringActionState,
            )
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `stop monitoring delegates to controller and reports stopped`() =
        runTest(mainDispatcherRule.dispatcher) {
            val controller = FakeMonitoringServiceController()
            val viewModel = viewModel(monitoringServiceController = controller)
            runCurrent()

            viewModel.onAlwaysOnMonitoringEnabledChanged(false)
            runCurrent()

            assertEquals(listOf("stop"), controller.actions)
            assertEquals(MonitoringActionState.Stopped, viewModel.uiState.value.monitoringActionState)
            viewModel.viewModelScope.cancel()
        }

    private fun viewModel(
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        monitoringSettingsRepository: FakeMonitoringSettingsRepository = FakeMonitoringSettingsRepository(),
        monitoringRuntimeStateRepository: FakeMonitoringRuntimeStateRepository =
            FakeMonitoringRuntimeStateRepository(),
        airGradientRepository: FakeAirGradientRepository = FakeAirGradientRepository(),
        monitoringServiceController: FakeMonitoringServiceController = FakeMonitoringServiceController(),
    ): SettingsViewModel {
        val dispatcher = mainDispatcherRule.dispatcher
        val getCurrentMeasurement = GetCurrentMeasurementUseCase(airGradientRepository)

        return SettingsViewModel(
            useCases =
                SettingsUseCases(
                    observeSettings = ObserveSettingsUseCase(settingsRepository),
                    observeMonitoringSettings = ObserveMonitoringSettingsUseCase(monitoringSettingsRepository),
                    observeMonitoringRuntimeState =
                        ObserveMonitoringRuntimeStateUseCase(monitoringRuntimeStateRepository),
                    saveDeviceUrl = SaveDeviceUrlUseCase(settingsRepository),
                    saveRefreshInterval = SaveRefreshIntervalUseCase(settingsRepository),
                    saveForegroundPollingInterval =
                        SaveForegroundPollingIntervalUseCase(monitoringSettingsRepository),
                    savePeriodicBackgroundInterval =
                        SavePeriodicBackgroundIntervalUseCase(monitoringSettingsRepository),
                    saveNotificationsEnabled = SaveNotificationsEnabledUseCase(settingsRepository),
                    saveMinimumNotificationSeverity = SaveMinimumNotificationSeverityUseCase(settingsRepository),
                    saveNotifyOnRecovery = SaveNotifyOnRecoveryUseCase(settingsRepository),
                    saveNotifyOnDeviceUnreachable = SaveNotifyOnDeviceUnreachableUseCase(settingsRepository),
                    saveThemeMode = SaveThemeModeUseCase(settingsRepository),
                    testDeviceConnection = TestDeviceConnectionUseCase(getCurrentMeasurement),
                ),
            monitoringServiceController = monitoringServiceController,
            dispatchers =
                AppDispatchers(
                    io = dispatcher,
                    default = dispatcher,
                    main = dispatcher,
                ),
        )
    }

    private class FakeSettingsRepository(
        initialSettings: AppSettings = AppSettings.default,
    ) : SettingsRepository {
        val settingsState = MutableStateFlow(initialSettings)
        override val settings: Flow<AppSettings> = settingsState

        override suspend fun saveDeviceUrl(input: String): SaveDeviceUrlResult =
            when (val result = DeviceUrlNormalizer.normalize(input)) {
                DeviceUrlNormalizationResult.Invalid -> SaveDeviceUrlResult.Invalid
                DeviceUrlNormalizationResult.Unconfigured -> saveServerUrl(null)
                is DeviceUrlNormalizationResult.Normalized -> saveServerUrl(result.value)
            }

        override suspend fun saveRefreshIntervalSeconds(seconds: Int) {
            settingsState.value =
                settingsState.value.copy(
                    refreshIntervalSeconds = AppSettings.clampRefreshInterval(seconds),
                )
        }

        override suspend fun saveNotificationsEnabled(enabled: Boolean) {
            settingsState.value = settingsState.value.copy(notificationsEnabled = enabled)
        }

        override suspend fun saveMinimumNotificationSeverity(severity: NotificationSeverity) {
            settingsState.value = settingsState.value.copy(minimumNotificationSeverity = severity)
        }

        override suspend fun saveNotifyOnRecovery(enabled: Boolean) {
            settingsState.value = settingsState.value.copy(notifyOnRecovery = enabled)
        }

        override suspend fun saveNotifyOnDeviceUnreachable(enabled: Boolean) {
            settingsState.value = settingsState.value.copy(notifyOnDeviceUnreachable = enabled)
        }

        override suspend fun saveThemeMode(themeMode: AppThemeMode) {
            settingsState.value = settingsState.value.copy(themeMode = themeMode)
        }

        private fun saveServerUrl(serverUrl: String?): SaveDeviceUrlResult.Saved {
            settingsState.value = settingsState.value.copy(serverUrl = serverUrl)
            return SaveDeviceUrlResult.Saved(serverUrl)
        }
    }

    private class FakeMonitoringSettingsRepository(
        initialSettings: MonitoringSettings = MonitoringSettings.default,
    ) : MonitoringSettingsRepository {
        val state = MutableStateFlow(initialSettings)

        override fun observeMonitoringSettings(): Flow<MonitoringSettings> = state

        override suspend fun getMonitoringSettings(): MonitoringSettings = state.value

        override suspend fun updateMonitoringMode(mode: MonitoringMode) {
            state.value = state.value.copy(mode = mode)
        }

        override suspend fun updateForegroundPollingInterval(interval: Duration) {
            state.value =
                state.value.copy(
                    foregroundPollingIntervalSeconds =
                        MonitoringSettings.requireSupportedForegroundInterval(interval),
                )
        }

        override suspend fun updatePeriodicBackgroundInterval(interval: Duration) {
            state.value =
                state.value.copy(
                    periodicBackgroundIntervalMinutes =
                        MonitoringSettings.requireSupportedPeriodicInterval(interval),
                )
        }
    }

    private class FakeMonitoringRuntimeStateRepository(
        initialState: MonitoringRuntimeState = MonitoringRuntimeState.default,
    ) : MonitoringRuntimeStateRepository {
        val state = MutableStateFlow(initialState)

        override fun observeMonitoringRuntimeState(): Flow<MonitoringRuntimeState> = state

        override suspend fun getMonitoringRuntimeState(): MonitoringRuntimeState = state.value

        override suspend fun recordTickResult(result: MonitoringTickResult) = Unit

        override suspend fun clearMonitoringRuntimeState() {
            state.value = MonitoringRuntimeState.default
        }
    }

    private class FakeMonitoringServiceController(
        private val startResult: MonitoringServiceControllerResult = MonitoringServiceControllerResult.Started,
    ) : MonitoringServiceController {
        val actions = mutableListOf<String>()

        override suspend fun startAlwaysOnMonitoring(): MonitoringServiceControllerResult {
            actions += "start"
            return startResult
        }

        override suspend fun startBatteryFriendlyMonitoring(): MonitoringServiceControllerResult {
            actions += "startPeriodic"
            return startResult
        }

        override suspend fun stopMonitoring(): MonitoringServiceControllerResult.Stopped {
            actions += "stop"
            return MonitoringServiceControllerResult.Stopped
        }

        override fun refreshNow() {
            actions += "refresh"
        }
    }

    private class FakeAirGradientRepository(
        private val result: AirGradientFetchResult = AirGradientFetchResult.Success(sampleSnapshot),
    ) : AirGradientRepository {
        var lastServerUrl: String? = null
            private set

        override suspend fun fetchCurrentMeasurement(serverUrl: String?): AirGradientFetchResult {
            lastServerUrl = serverUrl
            return result
        }
    }

    private companion object {
        val sampleSnapshot =
            AirMeasureSnapshot(
                aqi = 29,
                pm003Count = 442.0,
                pm01 = 3.0,
                pm25 = 7.0,
                pm10 = 8.0,
                co2 = 447.0,
                tvoc = 100.0,
                nox = 1.0,
                temperatureCelsius = 24.47,
                humidityPercent = 49.0,
                measuredAt = Instant.parse("2026-06-16T00:00:00Z"),
            )
    }
}
