package dev.worxbend.airgradient.domain.monitoring

import java.time.Duration

data class MonitoringPolicy(
    val mode: MonitoringMode,
    val foregroundPollingInterval: Duration,
    val periodicBackgroundInterval: Duration,
    val stopOnInvalidConfiguration: Boolean,
    val maxConsecutiveFailuresBeforeDeviceAlert: Int,
) {
    init {
        require(!foregroundPollingInterval.isNegative && foregroundPollingInterval >= MIN_FOREGROUND_POLLING_INTERVAL) {
            "Foreground polling interval must be at least ${MIN_FOREGROUND_POLLING_INTERVAL.seconds} seconds."
        }
        require(
            !periodicBackgroundInterval.isNegative &&
                periodicBackgroundInterval >= MIN_PERIODIC_BACKGROUND_INTERVAL,
        ) {
            "Periodic background interval must be at least ${MIN_PERIODIC_BACKGROUND_INTERVAL.toMinutes()} minutes."
        }
        require(maxConsecutiveFailuresBeforeDeviceAlert > 0) {
            "Max consecutive failures before device alert must be positive."
        }
    }

    fun validate(permissionState: MonitoringPermissionState): MonitoringPolicyValidationResult =
        when {
            mode == MonitoringMode.Off -> {
                MonitoringPolicyValidationResult.Valid
            }

            !permissionState.hasConfiguredDeviceUrl -> {
                MonitoringPolicyValidationResult.Invalid(MonitoringPolicyValidationError.MissingDeviceUrl)
            }

            mode == MonitoringMode.AlwaysOnForegroundService && !permissionState.canPostNotifications -> {
                MonitoringPolicyValidationResult.Invalid(MonitoringPolicyValidationError.MissingNotificationPermission)
            }

            else -> {
                MonitoringPolicyValidationResult.Valid
            }
        }

    companion object {
        val MIN_FOREGROUND_POLLING_INTERVAL: Duration = Duration.ofSeconds(30)
        val MIN_PERIODIC_BACKGROUND_INTERVAL: Duration = Duration.ofMinutes(15)

        val default: MonitoringPolicy =
            MonitoringPolicy(
                mode = MonitoringMode.Off,
                foregroundPollingInterval = MIN_FOREGROUND_POLLING_INTERVAL,
                periodicBackgroundInterval = MIN_PERIODIC_BACKGROUND_INTERVAL,
                stopOnInvalidConfiguration = true,
                maxConsecutiveFailuresBeforeDeviceAlert = 3,
            )
    }
}

sealed interface MonitoringPolicyValidationResult {
    data object Valid : MonitoringPolicyValidationResult

    data class Invalid(
        val error: MonitoringPolicyValidationError,
    ) : MonitoringPolicyValidationResult
}

enum class MonitoringPolicyValidationError {
    MissingDeviceUrl,
    MissingNotificationPermission,
}
