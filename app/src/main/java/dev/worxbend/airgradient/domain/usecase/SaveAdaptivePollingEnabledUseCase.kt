package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository

class SaveAdaptivePollingEnabledUseCase(
    private val repository: MonitoringSettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.updateAdaptivePollingEnabled(enabled)
    }
}
