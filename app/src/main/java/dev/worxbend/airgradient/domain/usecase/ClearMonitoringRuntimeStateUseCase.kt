package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.repository.MonitoringRuntimeStateRepository

class ClearMonitoringRuntimeStateUseCase(
    private val repository: MonitoringRuntimeStateRepository,
) {
    suspend operator fun invoke() {
        repository.clearMonitoringRuntimeState()
    }
}
