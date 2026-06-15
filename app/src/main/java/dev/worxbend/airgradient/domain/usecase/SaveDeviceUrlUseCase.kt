package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.repository.SaveDeviceUrlResult
import dev.worxbend.airgradient.domain.repository.SettingsRepository

class SaveDeviceUrlUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(input: String): SaveDeviceUrlResult = settingsRepository.saveDeviceUrl(input)
}
