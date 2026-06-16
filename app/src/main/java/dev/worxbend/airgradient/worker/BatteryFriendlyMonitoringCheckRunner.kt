package dev.worxbend.airgradient.worker

import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import dev.worxbend.airgradient.service.MonitoringTickRunner
import dev.worxbend.airgradient.service.PeriodicMonitoringScheduler
import kotlinx.coroutines.flow.first

class BatteryFriendlyMonitoringCheckRunner(
    private val settingsRepository: SettingsRepository,
    private val monitoringSettingsRepository: MonitoringSettingsRepository,
    private val periodicMonitoringScheduler: PeriodicMonitoringScheduler,
    private val monitoringTickRunner: MonitoringTickRunner,
) {
    suspend fun runCheck(): BatteryFriendlyMonitoringCheckResult {
        val appSettings = settingsRepository.settings.first()
        val monitoringSettings = monitoringSettingsRepository.getMonitoringSettings()

        return when {
            monitoringSettings.mode != MonitoringMode.BatteryFriendlyPeriodic -> {
                periodicMonitoringScheduler.cancelPeriodicMonitoring()
                BatteryFriendlyMonitoringCheckResult.CancelledInactiveMode
            }

            appSettings.serverUrl.isNullOrBlank() -> {
                monitoringSettingsRepository.updateMonitoringMode(MonitoringMode.Off)
                periodicMonitoringScheduler.cancelPeriodicMonitoring()
                BatteryFriendlyMonitoringCheckResult.DisabledMissingDeviceUrl
            }

            else -> {
                BatteryFriendlyMonitoringCheckResult.Checked(
                    monitoringTickRunner.runOneTick(appSettings),
                )
            }
        }
    }
}

sealed interface BatteryFriendlyMonitoringCheckResult {
    data object CancelledInactiveMode : BatteryFriendlyMonitoringCheckResult

    data object DisabledMissingDeviceUrl : BatteryFriendlyMonitoringCheckResult

    data class Checked(
        val tickResult: MonitoringTickResult,
    ) : BatteryFriendlyMonitoringCheckResult
}
