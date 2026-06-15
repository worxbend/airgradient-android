package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult

class RefreshDashboardUseCase(
    private val getCurrent: GetCurrentMeasurementUseCase,
) {
    suspend operator fun invoke(settings: AppSettings): AirGradientFetchResult = getCurrent(settings.serverUrl)
}
