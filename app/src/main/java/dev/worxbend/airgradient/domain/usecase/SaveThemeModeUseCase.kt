package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.repository.SettingsRepository

class SaveThemeModeUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(themeMode: AppThemeMode) {
        settingsRepository.saveThemeMode(themeMode)
    }
}
