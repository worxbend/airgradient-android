package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository
import java.time.Duration

class SavePeriodicBackgroundIntervalUseCase(
    private val monitoringSettingsRepository: MonitoringSettingsRepository,
) {
    suspend operator fun invoke(minutes: Int) {
        monitoringSettingsRepository.updatePeriodicBackgroundInterval(Duration.ofMinutes(minutes.toLong()))
    }
}
