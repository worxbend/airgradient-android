package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository

class ObserveMonitoringSettingsUseCase(
    private val repository: MonitoringSettingsRepository,
) {
    operator fun invoke() = repository.observeMonitoringSettings()
}
