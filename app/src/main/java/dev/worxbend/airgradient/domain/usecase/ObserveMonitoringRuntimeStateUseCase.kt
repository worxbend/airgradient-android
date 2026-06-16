package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.repository.MonitoringRuntimeStateRepository

class ObserveMonitoringRuntimeStateUseCase(
    private val repository: MonitoringRuntimeStateRepository,
) {
    operator fun invoke() = repository.observeMonitoringRuntimeState()
}
