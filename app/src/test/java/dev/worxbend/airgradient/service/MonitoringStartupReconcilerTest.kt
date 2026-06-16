package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationError
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class MonitoringStartupReconcilerTest {
    @Test
    fun `off mode does not start or stop monitoring`() =
        runTest {
            val controller = RecordingMonitoringServiceController()
            val reconciler =
                reconciler(
                    monitoringSettings = MonitoringSettings.default.copy(mode = MonitoringMode.Off),
                    controller = controller,
                )

            val result = reconciler.reconcile()

            assertEquals(MonitoringStartupReconciliationResult.MonitoringOff, result)
            assertEquals(emptyList<String>(), controller.actions)
        }

    @Test
    fun `always-on mode restarts foreground monitoring`() =
        runTest {
            val controller = RecordingMonitoringServiceController()
            val reconciler =
                reconciler(
                    monitoringSettings =
                        MonitoringSettings.default.copy(mode = MonitoringMode.AlwaysOnForegroundService),
                    controller = controller,
                )

            val result = reconciler.reconcile()

            assertEquals(
                MonitoringStartupReconciliationResult.Restored(MonitoringMode.AlwaysOnForegroundService),
                result,
            )
            assertEquals(listOf("startAlwaysOn"), controller.actions)
        }

    @Test
    fun `battery-friendly mode reschedules periodic monitoring`() =
        runTest {
            val controller = RecordingMonitoringServiceController()
            val reconciler =
                reconciler(
                    monitoringSettings =
                        MonitoringSettings.default.copy(mode = MonitoringMode.BatteryFriendlyPeriodic),
                    controller = controller,
                )

            val result = reconciler.reconcile()

            assertEquals(
                MonitoringStartupReconciliationResult.Restored(MonitoringMode.BatteryFriendlyPeriodic),
                result,
            )
            assertEquals(listOf("startBatteryFriendly"), controller.actions)
        }

    @Test
    fun `rejected active mode is stopped and reported as disabled`() =
        runTest {
            val controller =
                RecordingMonitoringServiceController(
                    alwaysOnResult =
                        MonitoringServiceControllerResult.Rejected(
                            MonitoringPolicyValidationError.MissingNotificationPermission,
                        ),
                )
            val reconciler =
                reconciler(
                    monitoringSettings =
                        MonitoringSettings.default.copy(mode = MonitoringMode.AlwaysOnForegroundService),
                    controller = controller,
                )

            val result = reconciler.reconcile()

            assertEquals(
                MonitoringStartupReconciliationResult.RejectedAndDisabled(
                    mode = MonitoringMode.AlwaysOnForegroundService,
                    error = MonitoringPolicyValidationError.MissingNotificationPermission,
                ),
                result,
            )
            assertEquals(listOf("startAlwaysOn", "stop"), controller.actions)
        }

    private fun reconciler(
        monitoringSettings: MonitoringSettings,
        controller: RecordingMonitoringServiceController = RecordingMonitoringServiceController(),
    ): MonitoringStartupReconciler =
        MonitoringStartupReconciler(
            monitoringSettingsRepository = FakeMonitoringSettingsRepository(monitoringSettings),
            monitoringServiceController = controller,
        )

    private class FakeMonitoringSettingsRepository(
        initialSettings: MonitoringSettings,
    ) : MonitoringSettingsRepository {
        private val state = MutableStateFlow(initialSettings)

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

        override suspend fun updateAdaptivePollingEnabled(enabled: Boolean) {
            state.value = state.value.copy(adaptivePollingEnabled = enabled)
        }
    }

    private class RecordingMonitoringServiceController(
        private val alwaysOnResult: MonitoringServiceControllerResult =
            MonitoringServiceControllerResult.Started,
        private val batteryFriendlyResult: MonitoringServiceControllerResult =
            MonitoringServiceControllerResult.Started,
    ) : MonitoringServiceController {
        val actions = mutableListOf<String>()

        override suspend fun startAlwaysOnMonitoring(): MonitoringServiceControllerResult {
            actions += "startAlwaysOn"
            return alwaysOnResult
        }

        override suspend fun startBatteryFriendlyMonitoring(): MonitoringServiceControllerResult {
            actions += "startBatteryFriendly"
            return batteryFriendlyResult
        }

        override suspend fun stopMonitoring(): MonitoringServiceControllerResult.Stopped {
            actions += "stop"
            return MonitoringServiceControllerResult.Stopped
        }

        override fun refreshNow() {
            actions += "refresh"
        }
    }
}
