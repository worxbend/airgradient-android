package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringPermissionState
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicy
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationError
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationResult
import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

class AirQualityMonitoringServiceController(
    private val settingsRepository: SettingsRepository,
    private val monitoringSettingsRepository: MonitoringSettingsRepository,
    private val permissionChecker: MonitoringNotificationPermissionChecker,
    private val serviceGateway: MonitoringServiceGateway,
) : MonitoringServiceController {
    override suspend fun startAlwaysOnMonitoring(): MonitoringServiceControllerResult {
        val appSettings = settingsRepository.settings.first()
        val monitoringSettings = monitoringSettingsRepository.getMonitoringSettings()
        val policy =
            MonitoringPolicy.default.copy(
                mode = MonitoringMode.AlwaysOnForegroundService,
                foregroundPollingInterval = monitoringSettings.foregroundPollingInterval,
                periodicBackgroundInterval = monitoringSettings.periodicBackgroundInterval,
            )
        val permissionState =
            MonitoringPermissionState(
                hasConfiguredDeviceUrl = !appSettings.serverUrl.isNullOrBlank(),
                notificationPermissionRequired = permissionChecker.isNotificationPermissionRequired,
                notificationPermissionGranted = permissionChecker.canPostNotifications(),
            )

        return when (val validation = policy.validate(permissionState)) {
            MonitoringPolicyValidationResult.Valid -> {
                monitoringSettingsRepository.updateMonitoringMode(MonitoringMode.AlwaysOnForegroundService)
                serviceGateway.startForegroundMonitoring()
                MonitoringServiceControllerResult.Started
            }

            is MonitoringPolicyValidationResult.Invalid -> {
                MonitoringServiceControllerResult.Rejected(validation.error)
            }
        }
    }

    override suspend fun stopMonitoring(): MonitoringServiceControllerResult.Stopped {
        monitoringSettingsRepository.updateMonitoringMode(MonitoringMode.Off)
        serviceGateway.stopForegroundMonitoring()
        return MonitoringServiceControllerResult.Stopped
    }

    override fun refreshNow() {
        serviceGateway.refreshNow()
    }
}

interface MonitoringServiceController {
    suspend fun startAlwaysOnMonitoring(): MonitoringServiceControllerResult

    suspend fun stopMonitoring(): MonitoringServiceControllerResult.Stopped

    fun refreshNow()
}

sealed interface MonitoringServiceControllerResult {
    data object Started : MonitoringServiceControllerResult

    data object Stopped : MonitoringServiceControllerResult

    data class Rejected(
        val error: MonitoringPolicyValidationError,
    ) : MonitoringServiceControllerResult
}

interface MonitoringNotificationPermissionChecker {
    val isNotificationPermissionRequired: Boolean

    fun canPostNotifications(): Boolean
}

interface MonitoringServiceGateway {
    fun startForegroundMonitoring()

    fun stopForegroundMonitoring()

    fun refreshNow()
}
