package dev.worxbend.airgradient.worker

import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickSkipReason
import dev.worxbend.airgradient.domain.notifications.NotificationSeverity
import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository
import dev.worxbend.airgradient.domain.repository.SaveDeviceUrlResult
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import dev.worxbend.airgradient.service.MonitoringTickRunner
import dev.worxbend.airgradient.service.PeriodicMonitoringScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class BatteryFriendlyMonitoringCheckRunnerTest {
    @Test
    fun `inactive monitoring mode cancels periodic work without running a tick`() =
        runTest {
            val scheduler = RecordingPeriodicMonitoringScheduler()
            val tickRunner = RecordingMonitoringTickRunner()
            val runner =
                runner(
                    monitoringSettings =
                        MonitoringSettings.default.copy(mode = MonitoringMode.Off),
                    scheduler = scheduler,
                    tickRunner = tickRunner,
                )

            val result = runner.runCheck()

            assertEquals(BatteryFriendlyMonitoringCheckResult.CancelledInactiveMode, result)
            assertEquals(listOf("cancel"), scheduler.actions)
            assertEquals(emptyList<AppSettings>(), tickRunner.receivedSettings)
        }

    @Test
    fun `missing device URL disables monitoring and cancels periodic work`() =
        runTest {
            val monitoringRepository =
                FakeMonitoringSettingsRepository(
                    MonitoringSettings.default.copy(mode = MonitoringMode.BatteryFriendlyPeriodic),
                )
            val scheduler = RecordingPeriodicMonitoringScheduler()
            val tickRunner = RecordingMonitoringTickRunner()
            val runner =
                runner(
                    appSettings = settings(serverUrl = null),
                    monitoringRepository = monitoringRepository,
                    scheduler = scheduler,
                    tickRunner = tickRunner,
                )

            val result = runner.runCheck()

            assertEquals(BatteryFriendlyMonitoringCheckResult.DisabledMissingDeviceUrl, result)
            assertEquals(MonitoringMode.Off, monitoringRepository.state.value.mode)
            assertEquals(listOf("cancel"), scheduler.actions)
            assertEquals(emptyList<AppSettings>(), tickRunner.receivedSettings)
        }

    @Test
    fun `configured battery-friendly mode runs one monitoring tick`() =
        runTest {
            val appSettings = settings(serverUrl = "http://192.168.1.201")
            val tickResult =
                MonitoringTickResult.Skipped(
                    reason = MonitoringTickSkipReason.RequestAlreadyRunning,
                    checkedAt = now,
                )
            val scheduler = RecordingPeriodicMonitoringScheduler()
            val tickRunner = RecordingMonitoringTickRunner(tickResult)
            val runner =
                runner(
                    appSettings = appSettings,
                    monitoringSettings =
                        MonitoringSettings.default.copy(mode = MonitoringMode.BatteryFriendlyPeriodic),
                    scheduler = scheduler,
                    tickRunner = tickRunner,
                )

            val result = runner.runCheck()

            assertEquals(BatteryFriendlyMonitoringCheckResult.Checked(tickResult), result)
            assertEquals(listOf(appSettings), tickRunner.receivedSettings)
            assertEquals(emptyList<String>(), scheduler.actions)
        }

    private fun runner(
        appSettings: AppSettings = settings(),
        monitoringSettings: MonitoringSettings = MonitoringSettings.default,
        monitoringRepository: FakeMonitoringSettingsRepository =
            FakeMonitoringSettingsRepository(monitoringSettings),
        scheduler: RecordingPeriodicMonitoringScheduler = RecordingPeriodicMonitoringScheduler(),
        tickRunner: RecordingMonitoringTickRunner = RecordingMonitoringTickRunner(),
    ): BatteryFriendlyMonitoringCheckRunner =
        BatteryFriendlyMonitoringCheckRunner(
            settingsRepository = FakeSettingsRepository(appSettings),
            monitoringSettingsRepository = monitoringRepository,
            periodicMonitoringScheduler = scheduler,
            monitoringTickRunner = tickRunner,
        )

    private fun settings(serverUrl: String? = "http://192.168.1.201"): AppSettings =
        AppSettings(
            serverUrl = serverUrl,
            refreshIntervalSeconds = 30,
            notificationsEnabled = true,
            themeMode = AppThemeMode.SYSTEM,
        )

    private class FakeSettingsRepository(
        initialSettings: AppSettings,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initialSettings)

        override val settings: Flow<AppSettings> = state

        override suspend fun saveDeviceUrl(input: String): SaveDeviceUrlResult = SaveDeviceUrlResult.Saved(input)

        override suspend fun saveRefreshIntervalSeconds(seconds: Int) {
            state.value = state.value.copy(refreshIntervalSeconds = seconds)
        }

        override suspend fun saveNotificationsEnabled(enabled: Boolean) {
            state.value = state.value.copy(notificationsEnabled = enabled)
        }

        override suspend fun saveMinimumNotificationSeverity(severity: NotificationSeverity) {
            state.value = state.value.copy(minimumNotificationSeverity = severity)
        }

        override suspend fun saveNotifyOnRecovery(enabled: Boolean) {
            state.value = state.value.copy(notifyOnRecovery = enabled)
        }

        override suspend fun saveNotifyOnDeviceUnreachable(enabled: Boolean) {
            state.value = state.value.copy(notifyOnDeviceUnreachable = enabled)
        }

        override suspend fun saveThemeMode(themeMode: AppThemeMode) {
            state.value = state.value.copy(themeMode = themeMode)
        }
    }

    private class FakeMonitoringSettingsRepository(
        initialSettings: MonitoringSettings,
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

    private class RecordingPeriodicMonitoringScheduler : PeriodicMonitoringScheduler {
        val actions = mutableListOf<String>()

        override fun schedulePeriodicMonitoring(interval: Duration) {
            actions += "schedule:$interval"
        }

        override fun cancelPeriodicMonitoring() {
            actions += "cancel"
        }
    }

    private class RecordingMonitoringTickRunner(
        private val result: MonitoringTickResult =
            MonitoringTickResult.Skipped(
                reason = MonitoringTickSkipReason.MonitoringOff,
                checkedAt = now,
            ),
    ) : MonitoringTickRunner {
        val receivedSettings = mutableListOf<AppSettings>()

        override suspend fun runOneTick(settings: AppSettings): MonitoringTickResult {
            receivedSettings += settings
            return result
        }
    }

    private companion object {
        val now: Instant = Instant.parse("2026-06-16T08:00:00Z")
    }
}
