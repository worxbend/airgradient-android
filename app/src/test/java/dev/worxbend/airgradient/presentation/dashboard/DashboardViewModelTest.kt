package dev.worxbend.airgradient.presentation.dashboard

import androidx.lifecycle.viewModelScope
import dev.worxbend.airgradient.core.dispatchers.AppDispatchers
import dev.worxbend.airgradient.core.time.ClockProvider
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.model.SensorMetricKind
import dev.worxbend.airgradient.domain.notifications.AirQualityAlert
import dev.worxbend.airgradient.domain.notifications.AirQualityAlertKind
import dev.worxbend.airgradient.domain.notifications.AirQualityAlertNotifier
import dev.worxbend.airgradient.domain.notifications.AirQualityAlertPolicy
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import dev.worxbend.airgradient.domain.repository.AirGradientRepository
import dev.worxbend.airgradient.domain.repository.SaveDeviceUrlResult
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import dev.worxbend.airgradient.domain.usecase.GetCurrentMeasurementUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.RefreshDashboardUseCase
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
    fun `notifications are sent after consecutive degraded readings when enabled`() =
        runTest(mainDispatcherRule.dispatcher) {
            val notifier = RecordingAirQualityAlertNotifier()
            val repository =
                FakeAirGradientRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                AirGradientFetchResult.Success(firstSnapshot.copy(co2 = 1_000.0)),
                                AirGradientFetchResult.Success(firstSnapshot.copy(co2 = 1_000.0)),
                            ),
                        ),
                )
            val viewModel =
                viewModel(
                    settings = configuredSettings.copy(notificationsEnabled = true),
                    repository = repository,
                    alertNotifier = notifier,
                )
            runCurrent()

            assertTrue(notifier.alerts.isEmpty())

            viewModel.refresh()
            runCurrent()

            assertEquals(1, notifier.alerts.size)
            assertEquals(AirQualityAlertKind.CO2, notifier.alerts.single().kind)
            viewModel.viewModelScope.cancel()
        }

    private fun viewModel(
        settings: AppSettings = AppSettings.default,
        repository: FakeAirGradientRepository,
        alertNotifier: AirQualityAlertNotifier = RecordingAirQualityAlertNotifier(),
    ): DashboardViewModel {
        val dispatcher = mainDispatcherRule.dispatcher
        return DashboardViewModel(
            observeSettings = ObserveSettingsUseCase(FakeSettingsRepository(settings)),
            refreshDashboard = RefreshDashboardUseCase(GetCurrentMeasurementUseCase(repository)),
            alertPolicy = AirQualityAlertPolicy(),
            alertNotifier = alertNotifier,
            clockProvider = ClockProvider { Instant.parse("2026-06-16T00:00:00Z") },
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

        override suspend fun saveThemeMode(themeMode: AppThemeMode) {
            settingsState.value = settingsState.value.copy(themeMode = themeMode)
        }
    }

    private class RecordingAirQualityAlertNotifier : AirQualityAlertNotifier {
        val alerts = mutableListOf<AirQualityAlert>()

        override fun showAlert(alert: AirQualityAlert) {
            alerts += alert
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
