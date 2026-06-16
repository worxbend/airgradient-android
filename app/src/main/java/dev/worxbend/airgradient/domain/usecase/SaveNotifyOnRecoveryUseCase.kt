package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.repository.SettingsRepository

class SaveNotifyOnRecoveryUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        settingsRepository.saveNotifyOnRecovery(enabled)
    }
}
