package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.repository.SettingsRepository

class SaveNotificationsEnabledUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        settingsRepository.saveNotificationsEnabled(enabled)
    }
}
