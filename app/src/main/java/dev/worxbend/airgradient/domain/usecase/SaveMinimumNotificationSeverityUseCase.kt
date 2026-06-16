package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.notifications.NotificationSeverity
import dev.worxbend.airgradient.domain.repository.SettingsRepository

class SaveMinimumNotificationSeverityUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(severity: NotificationSeverity) {
        settingsRepository.saveMinimumNotificationSeverity(severity)
    }
}
