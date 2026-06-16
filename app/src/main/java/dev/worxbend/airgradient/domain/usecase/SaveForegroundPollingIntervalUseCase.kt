package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository
import java.time.Duration

class SaveForegroundPollingIntervalUseCase(
    private val repository: MonitoringSettingsRepository,
) {
    suspend operator fun invoke(seconds: Int) {
        repository.updateForegroundPollingInterval(Duration.ofSeconds(seconds.toLong()))
    }
}
