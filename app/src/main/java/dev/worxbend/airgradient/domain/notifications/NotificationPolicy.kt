package dev.worxbend.airgradient.domain.notifications

import java.time.Duration

data class NotificationPolicy(
    val notificationsEnabled: Boolean,
    val minimumSeverity: NotificationSeverity,
    val cooldown: Duration,
    val persistentBadAirQualityAfter: Duration,
    val recoveryConfirmationWindow: Duration,
    val staleDataAfter: Duration,
    val notifyOnRecovery: Boolean,
    val notifyOnDeviceUnreachable: Boolean,
    val maxConsecutiveFailuresBeforeDeviceAlert: Int,
) {
    init {
        require(!cooldown.isNegative) { "Notification cooldown cannot be negative." }
        require(!persistentBadAirQualityAfter.isNegative) { "Persistent bad-air window cannot be negative." }
        require(!recoveryConfirmationWindow.isNegative) { "Recovery confirmation window cannot be negative." }
        require(!staleDataAfter.isNegative) { "Stale-data window cannot be negative." }
        require(maxConsecutiveFailuresBeforeDeviceAlert > 0) {
            "Max consecutive failures before device alert must be positive."
        }
    }

    companion object {
        val default: NotificationPolicy =
            NotificationPolicy(
                notificationsEnabled = true,
                minimumSeverity = NotificationSeverity.Warning,
                cooldown = Duration.ofMinutes(20),
                persistentBadAirQualityAfter = Duration.ofHours(2),
                recoveryConfirmationWindow = Duration.ofMinutes(1),
                staleDataAfter = Duration.ofMinutes(10),
                notifyOnRecovery = true,
                notifyOnDeviceUnreachable = true,
                maxConsecutiveFailuresBeforeDeviceAlert = 3,
            )
    }
}
