package dev.worxbend.airgradient.domain.notifications

import dev.worxbend.airgradient.domain.model.AppSettings

object NotificationPolicyFactory {
    fun fromSettings(settings: AppSettings): NotificationPolicy =
        NotificationPolicy.default.copy(
            notificationsEnabled = settings.notificationsEnabled,
            minimumSeverity = settings.minimumNotificationSeverity,
            notifyOnRecovery = settings.notifyOnRecovery,
            notifyOnDeviceUnreachable = settings.notifyOnDeviceUnreachable,
        )
}
