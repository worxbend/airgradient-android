package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationError
import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository

class MonitoringStartupReconciler(
    private val monitoringSettingsRepository: MonitoringSettingsRepository,
    private val monitoringServiceController: MonitoringServiceController,
) {
    suspend fun reconcile(): MonitoringStartupReconciliationResult {
        val mode = monitoringSettingsRepository.getMonitoringSettings().mode

        return when (mode) {
            MonitoringMode.Off -> {
                MonitoringStartupReconciliationResult.MonitoringOff
            }

            MonitoringMode.AlwaysOnForegroundService -> {
                restoreMode(
                    mode = mode,
                    start = monitoringServiceController::startAlwaysOnMonitoring,
                )
            }

            MonitoringMode.BatteryFriendlyPeriodic -> {
                restoreMode(
                    mode = mode,
                    start = monitoringServiceController::startBatteryFriendlyMonitoring,
                )
            }
        }
    }

    private suspend fun restoreMode(
        mode: MonitoringMode,
        start: suspend () -> MonitoringServiceControllerResult,
    ): MonitoringStartupReconciliationResult =
        when (val result = start()) {
            MonitoringServiceControllerResult.Started -> {
                MonitoringStartupReconciliationResult.Restored(mode)
            }

            MonitoringServiceControllerResult.Stopped -> {
                MonitoringStartupReconciliationResult.Disabled(mode)
            }

            is MonitoringServiceControllerResult.Rejected -> {
                monitoringServiceController.stopMonitoring()
                MonitoringStartupReconciliationResult.RejectedAndDisabled(
                    mode = mode,
                    error = result.error,
                )
            }
        }
}

sealed interface MonitoringStartupReconciliationResult {
    data object MonitoringOff : MonitoringStartupReconciliationResult

    data class Restored(
        val mode: MonitoringMode,
    ) : MonitoringStartupReconciliationResult

    data class Disabled(
        val mode: MonitoringMode,
    ) : MonitoringStartupReconciliationResult

    data class RejectedAndDisabled(
        val mode: MonitoringMode,
        val error: MonitoringPolicyValidationError,
    ) : MonitoringStartupReconciliationResult
}
