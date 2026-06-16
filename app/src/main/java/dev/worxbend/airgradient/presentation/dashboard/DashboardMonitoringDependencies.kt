package dev.worxbend.airgradient.presentation.dashboard

import dev.worxbend.airgradient.domain.usecase.ObserveMonitoringRuntimeStateUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveMonitoringSettingsUseCase
import dev.worxbend.airgradient.service.MonitoringServiceController

data class DashboardMonitoringDependencies(
    val observeMonitoringSettings: ObserveMonitoringSettingsUseCase,
    val observeMonitoringRuntimeState: ObserveMonitoringRuntimeStateUseCase,
    val monitoringServiceController: MonitoringServiceController,
)
