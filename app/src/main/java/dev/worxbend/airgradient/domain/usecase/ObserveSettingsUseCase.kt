package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class ObserveSettingsUseCase(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<AppSettings> = settingsRepository.settings
}
