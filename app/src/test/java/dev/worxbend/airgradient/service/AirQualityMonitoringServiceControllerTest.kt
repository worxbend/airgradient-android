package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationError
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import dev.worxbend.airgradient.domain.notifications.NotificationSeverity
import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository
import dev.worxbend.airgradient.domain.repository.SaveDeviceUrlResult
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class AirQualityMonitoringServiceControllerTest {
    @Test
    fun `start rejects missing device URL`() =
        runTest {
            val gateway = RecordingMonitoringServiceGateway()
            val controller =
                controller(
                    settingsRepository = FakeSettingsRepository(settings(serverUrl = null)),
                    serviceGateway = gateway,
                )

            val result = controller.startAlwaysOnMonitoring()

            assertEquals(
                MonitoringServiceControllerResult.Rejected(MonitoringPolicyValidationError.MissingDeviceUrl),
                result,
            )
            assertEquals(emptyList<String>(), gateway.actions)
        }

    @Test
    fun `start rejects missing notification permission`() =
        runTest {
            val gateway = RecordingMonitoringServiceGateway()
            val controller =
                controller(
                    permissionChecker =
                        FakeMonitoringNotificationPermissionChecker(
                            required = true,
                            granted = false,
                        ),
                    serviceGateway = gateway,
                )

            val result = controller.startAlwaysOnMonitoring()

            assertEquals(
                MonitoringServiceControllerResult.Rejected(
                    MonitoringPolicyValidationError.MissingNotificationPermission,
                ),
                result,
            )
            assertEquals(emptyList<String>(), gateway.actions)
        }

    @Test
    fun `start persists always-on mode and starts foreground service`() =
        runTest {
            val monitoringRepository = FakeMonitoringSettingsRepository()
            val gateway = RecordingMonitoringServiceGateway()
            val scheduler = RecordingPeriodicMonitoringScheduler()
            val controller =
                controller(
                    monitoringSettingsRepository = monitoringRepository,
                    serviceGateway = gateway,
                    periodicScheduler = scheduler,
                )

            val result = controller.startAlwaysOnMonitoring()

            assertEquals(MonitoringServiceControllerResult.Started, result)
            assertEquals(MonitoringMode.AlwaysOnForegroundService, monitoringRepository.state.value.mode)
            assertEquals(listOf("start"), gateway.actions)
            assertEquals(listOf("cancel"), scheduler.actions)
        }

    @Test
    fun `start battery-friendly persists periodic mode and schedules worker`() =
        runTest {
            val monitoringRepository = FakeMonitoringSettingsRepository()
            val gateway = RecordingMonitoringServiceGateway()
            val scheduler = RecordingPeriodicMonitoringScheduler()
            val controller =
                controller(
                    monitoringSettingsRepository = monitoringRepository,
                    serviceGateway = gateway,
                    periodicScheduler = scheduler,
                )

            val result = controller.startBatteryFriendlyMonitoring()

            assertEquals(MonitoringServiceControllerResult.Started, result)
            assertEquals(MonitoringMode.BatteryFriendlyPeriodic, monitoringRepository.state.value.mode)
            assertEquals(listOf("stopRuntime"), gateway.actions)
            assertEquals(listOf("schedule:PT15M"), scheduler.actions)
        }

    @Test
    fun `start battery-friendly rejects missing device URL`() =
        runTest {
            val gateway = RecordingMonitoringServiceGateway()
            val scheduler = RecordingPeriodicMonitoringScheduler()
            val controller =
                controller(
                    settingsRepository = FakeSettingsRepository(settings(serverUrl = null)),
                    serviceGateway = gateway,
                    periodicScheduler = scheduler,
                )

            val result = controller.startBatteryFriendlyMonitoring()

            assertEquals(
                MonitoringServiceControllerResult.Rejected(MonitoringPolicyValidationError.MissingDeviceUrl),
                result,
            )
            assertEquals(emptyList<String>(), gateway.actions)
            assertEquals(emptyList<String>(), scheduler.actions)
        }

    @Test
    fun `stop persists off mode and sends stop action`() =
        runTest {
            val monitoringRepository =
                FakeMonitoringSettingsRepository(
                    MonitoringSettings.default.copy(mode = MonitoringMode.AlwaysOnForegroundService),
                )
            val gateway = RecordingMonitoringServiceGateway()
            val scheduler = RecordingPeriodicMonitoringScheduler()
            val controller =
                controller(
                    monitoringSettingsRepository = monitoringRepository,
                    serviceGateway = gateway,
                    periodicScheduler = scheduler,
                )

            val result = controller.stopMonitoring()

            assertEquals(MonitoringServiceControllerResult.Stopped, result)
            assertEquals(MonitoringMode.Off, monitoringRepository.state.value.mode)
            assertEquals(listOf("stop"), gateway.actions)
            assertEquals(listOf("cancel"), scheduler.actions)
        }

    @Test
    fun `refresh delegates refresh action`() {
        val gateway = RecordingMonitoringServiceGateway()
        val controller = controller(serviceGateway = gateway)

        controller.refreshNow()

        assertEquals(listOf("refresh"), gateway.actions)
    }

    private fun controller(
        settingsRepository: SettingsRepository = FakeSettingsRepository(settings()),
        monitoringSettingsRepository: FakeMonitoringSettingsRepository = FakeMonitoringSettingsRepository(),
        permissionChecker: MonitoringNotificationPermissionChecker = FakeMonitoringNotificationPermissionChecker(),
        serviceGateway: RecordingMonitoringServiceGateway = RecordingMonitoringServiceGateway(),
        periodicScheduler: PeriodicMonitoringScheduler = RecordingPeriodicMonitoringScheduler(),
    ): AirQualityMonitoringServiceController =
        AirQualityMonitoringServiceController(
            settingsRepository = settingsRepository,
            monitoringSettingsRepository = monitoringSettingsRepository,
            permissionChecker = permissionChecker,
            serviceGateway = serviceGateway,
            periodicScheduler = periodicScheduler,
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

        override suspend fun updateAdaptivePollingEnabled(enabled: Boolean) {
            state.value = state.value.copy(adaptivePollingEnabled = enabled)
        }
    }

    private class FakeMonitoringNotificationPermissionChecker(
        private val required: Boolean = false,
        private val granted: Boolean = true,
    ) : MonitoringNotificationPermissionChecker {
        override val isNotificationPermissionRequired: Boolean = required

        override fun canPostNotifications(): Boolean = granted
    }

    private class RecordingMonitoringServiceGateway : MonitoringServiceGateway {
        val actions = mutableListOf<String>()

        override fun startForegroundMonitoring() {
            actions += "start"
        }

        override fun stopForegroundMonitoring() {
            actions += "stop"
        }

        override fun stopForegroundMonitoringRuntime() {
            actions += "stopRuntime"
        }

        override fun refreshNow() {
            actions += "refresh"
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
}
