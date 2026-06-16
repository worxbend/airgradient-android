package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.repository.SettingsRepository

class SaveRefreshIntervalUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(seconds: Int) {
        settingsRepository.saveRefreshIntervalSeconds(seconds)
    }
}
