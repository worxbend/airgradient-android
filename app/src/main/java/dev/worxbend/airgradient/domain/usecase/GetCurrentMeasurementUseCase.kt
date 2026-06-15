package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import dev.worxbend.airgradient.domain.repository.AirGradientRepository

class GetCurrentMeasurementUseCase(
    private val repo: AirGradientRepository,
) {
    suspend operator fun invoke(serverUrl: String?): AirGradientFetchResult = repo.fetchCurrentMeasurement(serverUrl)
}
