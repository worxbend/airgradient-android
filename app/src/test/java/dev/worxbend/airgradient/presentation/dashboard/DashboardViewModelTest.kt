package dev.worxbend.airgradient.presentation.dashboard

import androidx.lifecycle.viewModelScope
import dev.worxbend.airgradient.core.dispatchers.AppDispatchers
import dev.worxbend.airgradient.core.time.ClockProvider
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.model.SensorMetricKind
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringRuntimeState
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import dev.worxbend.airgradient.domain.notifications.NotificationDecisionEngine
import dev.worxbend.airgradient.domain.notifications.NotificationMessage
import dev.worxbend.airgradient.domain.notifications.NotificationMessageDispatcher
import dev.worxbend.airgradient.domain.notifications.NotificationSeverity
import dev.worxbend.airgradient.domain.notifications.NotificationState
import dev.worxbend.airgradient.domain.notifications.NotificationType
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import dev.worxbend.airgradient.domain.repository.AirGradientRepository
import dev.worxbend.airgradient.domain.repository.MonitoringRuntimeStateRepository
import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository
import dev.worxbend.airgradient.domain.repository.NotificationStateRepository
import dev.worxbend.airgradient.domain.repository.SaveDeviceUrlResult
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import dev.worxbend.airgradient.domain.usecase.GetCurrentMeasurementUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveMonitoringRuntimeStateUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveMonitoringSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.RefreshDashboardUseCase
import dev.worxbend.airgradient.service.MonitoringServiceController
import dev.worxbend.airgradient.service.MonitoringServiceControllerResult
import dev.worxbend.airgradient.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import kotlin.math.max

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `unconfigured settings emit unconfigured state without fetching`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAirGradientRepository()
            val viewModel = viewModel(repository = repository)

            runCurrent()

            assertEquals(DashboardUiState.Unconfigured, viewModel.uiState.value)
            assertEquals(0, repository.calls)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `configured settings load dashboard content`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeAirGradientRepository(
                    results = ArrayDeque(listOf(AirGradientFetchResult.Success(firstSnapshot))),
                )
            val viewModel =
                viewModel(
                    settings = configuredSettings,
                    repository = repository,
                )

            runCurrent()

            val state = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(firstSnapshot, state.snapshot)
            assertEquals("Latest measurements loaded.", state.fetchStatusLabel)
            assertEquals("Last updated 2026-06-16T00:00:00Z", state.lastUpdatedLabel)
            assertEquals(10, state.metrics.size)
            assertTrue(state.metrics.any { it.kind == SensorMetricKind.PM25 && it.valueLabel == "7 ug/m3" })
            assertEquals(1, repository.calls)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `refresh failure keeps last successful content with warning`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeAirGradientRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                AirGradientFetchResult.Success(firstSnapshot),
                                AirGradientFetchResult.Failure(AirGradientError.Timeout),
                            ),
                        ),
                )
            val viewModel = viewModel(settings = configuredSettings, repository = repository)
            runCurrent()

            viewModel.refresh()
            runCurrent()

            val state = viewModel.uiState.value as DashboardUiState.ContentWithWarning
            assertEquals(firstSnapshot, state.snapshot)
            assertEquals(AirGradientError.Timeout, state.warning.cause)
            assertEquals("Fetch failed: Request timed out.", state.fetchStatusLabel)
            assertEquals(2, repository.calls)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `manual refresh does not overlap an in flight request`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeAirGradientRepository(
                    delayMillis = 100,
                    results = ArrayDeque(listOf(AirGradientFetchResult.Success(firstSnapshot))),
                )
            val viewModel = viewModel(settings = configuredSettings, repository = repository)
            runCurrent()

            viewModel.refresh()
            viewModel.refresh()
            runCurrent()

            assertEquals(1, repository.calls)
            assertEquals(1, repository.maxConcurrentCalls)

            testScheduler.advanceTimeBy(100)
            runCurrent()

            assertTrue(viewModel.uiState.value is DashboardUiState.Content)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `auto refresh uses configured interval`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeAirGradientRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                AirGradientFetchResult.Success(firstSnapshot),
                                AirGradientFetchResult.Success(secondSnapshot),
                            ),
                        ),
                )
            val viewModel =
                viewModel(
                    settings = configuredSettings.copy(refreshIntervalSeconds = 5),
                    repository = repository,
                )
            runCurrent()

            testScheduler.advanceTimeBy(5_000)
            runCurrent()

            val state = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(secondSnapshot, state.snapshot)
            assertEquals(2, repository.calls)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `notifications are dispatched through persisted decision state when enabled`() =
        runTest(mainDispatcherRule.dispatcher) {
            val notificationStateRepository = FakeNotificationStateRepository()
            val dispatcher = RecordingNotificationMessageDispatcher()
            val repository =
                FakeAirGradientRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                AirGradientFetchResult.Success(firstSnapshot.copy(co2 = 1_300.0)),
                            ),
                        ),
                )
            val viewModel =
                viewModel(
                    settings = configuredSettings.copy(notificationsEnabled = true),
                    repository = repository,
                    notificationStateRepository = notificationStateRepository,
                    notificationMessageDispatcher = dispatcher,
                )
            runCurrent()

            assertEquals(1, dispatcher.messages.size)
            assertEquals(NotificationType.AirQualityDegraded, dispatcher.messages.single().type)
            assertEquals("co2", notificationStateRepository.state.lastDominantMetricKey)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `minimum notification severity suppresses dashboard warning alerts`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dispatcher = RecordingNotificationMessageDispatcher()
            val repository =
                FakeAirGradientRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                AirGradientFetchResult.Success(firstSnapshot.copy(co2 = 1_300.0)),
                            ),
                        ),
                )
            val viewModel =
                viewModel(
                    settings =
                        configuredSettings.copy(
                            notificationsEnabled = true,
                            minimumNotificationSeverity = NotificationSeverity.Critical,
                        ),
                    repository = repository,
                    notificationMessageDispatcher = dispatcher,
                )
            runCurrent()

            assertEquals(emptyList<NotificationMessage>(), dispatcher.messages)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `refresh failure dispatches stale data notification when last reading is old`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dispatcher = RecordingNotificationMessageDispatcher()
            val repository =
                FakeAirGradientRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                AirGradientFetchResult.Success(firstSnapshot),
                                AirGradientFetchResult.Failure(AirGradientError.Timeout),
                            ),
                        ),
                )
            val viewModel =
                viewModel(
                    settings = configuredSettings.copy(notificationsEnabled = true),
                    repository = repository,
                    notificationMessageDispatcher = dispatcher,
                    now = Instant.parse("2026-06-16T00:11:00Z"),
                )
            runCurrent()

            viewModel.refresh()
            runCurrent()

            assertEquals(listOf(NotificationType.StaleData), dispatcher.messages.map { it.type })
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `configured dashboard includes monitoring summary`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    settings = configuredSettings,
                    monitoringSettings =
                        MonitoringSettings.default.copy(
                            mode = MonitoringMode.AlwaysOnForegroundService,
                            foregroundPollingIntervalSeconds = 60,
                        ),
                    repository =
                        FakeAirGradientRepository(
                            results = ArrayDeque(listOf(AirGradientFetchResult.Success(firstSnapshot))),
                        ),
                )
            runCurrent()

            val state = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(MonitoringMode.AlwaysOnForegroundService, state.monitoringSummary.mode)
            assertEquals(60, state.monitoringSummary.foregroundPollingIntervalSeconds)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `configured dashboard includes monitoring runtime timestamps`() =
        runTest(mainDispatcherRule.dispatcher) {
            val runtimeState =
                MonitoringRuntimeState.default.copy(
                    lastCheckedAt = Instant.parse("2026-06-16T00:10:00Z"),
                    lastSuccessfulMeasurementAt = Instant.parse("2026-06-16T00:09:58Z"),
                )
            val viewModel =
                viewModel(
                    settings = configuredSettings,
                    monitoringRuntimeRepository = FakeMonitoringRuntimeStateRepository(runtimeState),
                    repository =
                        FakeAirGradientRepository(
                            results = ArrayDeque(listOf(AirGradientFetchResult.Success(firstSnapshot))),
                        ),
                )
            runCurrent()

            val state = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(
                "Last background check 2026-06-16T00:10:00Z",
                state.monitoringSummary.lastBackgroundCheckLabel,
            )
            assertEquals(
                "Last successful reading 2026-06-16T00:09:58Z",
                state.monitoringSummary.lastSuccessfulBackgroundReadLabel,
            )
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `dashboard monitoring actions delegate to controller`() =
        runTest(mainDispatcherRule.dispatcher) {
            val monitoringRepository = FakeMonitoringSettingsRepository(MonitoringSettings.default)
            val controller = RecordingMonitoringServiceController(monitoringRepository)
            val viewModel =
                viewModel(
                    settings = configuredSettings,
                    monitoringRepository = monitoringRepository,
                    repository =
                        FakeAirGradientRepository(
                            results = ArrayDeque(listOf(AirGradientFetchResult.Success(firstSnapshot))),
                        ),
                    monitoringServiceController = controller,
                )
            runCurrent()

            viewModel.startAlwaysOnMonitoring()
            runCurrent()

            var state = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(1, controller.startCalls)
            assertEquals(MonitoringMode.AlwaysOnForegroundService, state.monitoringSummary.mode)

            viewModel.stopMonitoring()
            runCurrent()

            state = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(1, controller.stopCalls)
            assertEquals(MonitoringMode.Off, state.monitoringSummary.mode)
            viewModel.viewModelScope.cancel()
        }

    @Suppress("LongParameterList")
    private fun viewModel(
        settings: AppSettings = AppSettings.default,
        monitoringSettings: MonitoringSettings = MonitoringSettings.default,
        monitoringRepository: FakeMonitoringSettingsRepository =
            FakeMonitoringSettingsRepository(monitoringSettings),
        monitoringRuntimeRepository: MonitoringRuntimeStateRepository =
            FakeMonitoringRuntimeStateRepository(MonitoringRuntimeState.default),
        repository: FakeAirGradientRepository,
        monitoringServiceController: MonitoringServiceController =
            RecordingMonitoringServiceController(
                monitoringRepository,
            ),
        notificationStateRepository: NotificationStateRepository = FakeNotificationStateRepository(),
        notificationMessageDispatcher: NotificationMessageDispatcher = RecordingNotificationMessageDispatcher(),
        now: Instant = Instant.parse("2026-06-16T00:00:00Z"),
    ): DashboardViewModel {
        val dispatcher = mainDispatcherRule.dispatcher
        return DashboardViewModel(
            observeSettings = ObserveSettingsUseCase(FakeSettingsRepository(settings)),
            refreshDashboard = RefreshDashboardUseCase(GetCurrentMeasurementUseCase(repository)),
            monitoringDependencies =
                DashboardMonitoringDependencies(
                    observeMonitoringSettings = ObserveMonitoringSettingsUseCase(monitoringRepository),
                    observeMonitoringRuntimeState =
                        ObserveMonitoringRuntimeStateUseCase(monitoringRuntimeRepository),
                    monitoringServiceController = monitoringServiceController,
                ),
            notificationDependencies =
                DashboardNotificationDependencies(
                    notificationStateRepository = notificationStateRepository,
                    notificationDecisionEngine = NotificationDecisionEngine(),
                    notificationMessageDispatcher = notificationMessageDispatcher,
                ),
            clockProvider = ClockProvider { now },
            dispatchers =
                AppDispatchers(
                    io = dispatcher,
                    default = dispatcher,
                    main = dispatcher,
                ),
        )
    }

    private class FakeSettingsRepository(
        initialSettings: AppSettings,
    ) : SettingsRepository {
        private val settingsState = MutableStateFlow(initialSettings)

        override val settings: Flow<AppSettings> = settingsState

        override suspend fun saveDeviceUrl(input: String): SaveDeviceUrlResult = SaveDeviceUrlResult.Saved(input)

        override suspend fun saveRefreshIntervalSeconds(seconds: Int) {
            settingsState.value = settingsState.value.copy(refreshIntervalSeconds = seconds)
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
    }

    private class FakeMonitoringSettingsRepository(
        initialSettings: MonitoringSettings,
    ) : MonitoringSettingsRepository {
        private val settingsState = MutableStateFlow(initialSettings)

        override fun observeMonitoringSettings(): Flow<MonitoringSettings> = settingsState

        override suspend fun getMonitoringSettings(): MonitoringSettings = settingsState.value

        override suspend fun updateMonitoringMode(mode: MonitoringMode) {
            settingsState.value = settingsState.value.copy(mode = mode)
        }

        override suspend fun updateForegroundPollingInterval(interval: Duration) {
            settingsState.value =
                settingsState.value.copy(
                    foregroundPollingIntervalSeconds =
                        MonitoringSettings.requireSupportedForegroundInterval(interval),
                )
        }

        override suspend fun updatePeriodicBackgroundInterval(interval: Duration) {
            settingsState.value =
                settingsState.value.copy(
                    periodicBackgroundIntervalMinutes =
                        MonitoringSettings.requireSupportedPeriodicInterval(interval),
                )
        }

        override suspend fun updateAdaptivePollingEnabled(enabled: Boolean) {
            settingsState.value = settingsState.value.copy(adaptivePollingEnabled = enabled)
        }
    }

    private class FakeMonitoringRuntimeStateRepository(
        initialState: MonitoringRuntimeState,
    ) : MonitoringRuntimeStateRepository {
        private val state = MutableStateFlow(initialState)

        override fun observeMonitoringRuntimeState(): Flow<MonitoringRuntimeState> = state

        override suspend fun getMonitoringRuntimeState(): MonitoringRuntimeState = state.value

        override suspend fun recordTickResult(result: MonitoringTickResult) {
            state.value =
                when (result) {
                    is MonitoringTickResult.Success -> {
                        state.value.copy(
                            lastCheckedAt = result.checkedAt,
                            lastSuccessfulCheckAt = result.checkedAt,
                            lastSuccessfulMeasurementAt = result.snapshot.measuredAt,
                            lastFailureAt = null,
                            consecutiveFailureCount = 0,
                        )
                    }

                    is MonitoringTickResult.Failure -> {
                        state.value.copy(
                            lastCheckedAt = result.checkedAt,
                            lastFailureAt = result.checkedAt,
                            consecutiveFailureCount = result.consecutiveFailureCount,
                        )
                    }

                    is MonitoringTickResult.Skipped -> {
                        state.value
                    }
                }
        }

        override suspend fun clearMonitoringRuntimeState() {
            state.value = MonitoringRuntimeState.default
        }
    }

    private class RecordingMonitoringServiceController(
        private val monitoringRepository: MonitoringSettingsRepository,
    ) : MonitoringServiceController {
        var startCalls: Int = 0
            private set
        var stopCalls: Int = 0
            private set

        override suspend fun startAlwaysOnMonitoring(): MonitoringServiceControllerResult {
            startCalls += 1
            monitoringRepository.updateMonitoringMode(MonitoringMode.AlwaysOnForegroundService)
            return MonitoringServiceControllerResult.Started
        }

        override suspend fun startBatteryFriendlyMonitoring(): MonitoringServiceControllerResult {
            monitoringRepository.updateMonitoringMode(MonitoringMode.BatteryFriendlyPeriodic)
            return MonitoringServiceControllerResult.Started
        }

        override suspend fun stopMonitoring(): MonitoringServiceControllerResult.Stopped {
            stopCalls += 1
            monitoringRepository.updateMonitoringMode(MonitoringMode.Off)
            return MonitoringServiceControllerResult.Stopped
        }

        override fun refreshNow() = Unit
    }

    private class FakeNotificationStateRepository(
        initialState: NotificationState = NotificationState.default,
    ) : NotificationStateRepository {
        var state: NotificationState = initialState
            private set

        override fun observeNotificationState(): Flow<NotificationState> = MutableStateFlow(state)

        override suspend fun getNotificationState(): NotificationState = state

        override suspend fun saveNotificationState(state: NotificationState) {
            this.state = state
        }

        override suspend fun updateNotificationState(transform: (NotificationState) -> NotificationState) {
            state = transform(state)
        }

        override suspend fun clearNotificationState() {
            state = NotificationState.default
        }
    }

    private class RecordingNotificationMessageDispatcher : NotificationMessageDispatcher {
        val messages = mutableListOf<NotificationMessage>()

        override fun show(message: NotificationMessage) {
            messages += message
        }
    }

    private class FakeAirGradientRepository(
        private val delayMillis: Long = 0,
        private val results: ArrayDeque<AirGradientFetchResult> = ArrayDeque(),
    ) : AirGradientRepository {
        var calls: Int = 0
            private set
        var maxConcurrentCalls: Int = 0
            private set

        private var activeCalls: Int = 0

        override suspend fun fetchCurrentMeasurement(serverUrl: String?): AirGradientFetchResult {
            calls += 1
            activeCalls += 1
            maxConcurrentCalls = max(maxConcurrentCalls, activeCalls)

            return try {
                if (delayMillis > 0) {
                    delay(delayMillis)
                }
                results.removeFirstOrNull() ?: AirGradientFetchResult.Success(firstSnapshot)
            } finally {
                activeCalls -= 1
            }
        }
    }

    private companion object {
        val configuredSettings =
            AppSettings.default.copy(
                serverUrl = "http://192.168.1.201",
                refreshIntervalSeconds = 30,
            )

        val firstSnapshot =
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

        val secondSnapshot =
            firstSnapshot.copy(
                aqi = 45,
                pm25 = 11.0,
                measuredAt = Instant.parse("2026-06-16T00:00:05Z"),
            )
    }
}
