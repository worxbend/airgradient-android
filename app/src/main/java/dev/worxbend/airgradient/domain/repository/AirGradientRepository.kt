package dev.worxbend.airgradient.domain.repository

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot

interface AirGradientRepository {
    suspend fun fetchCurrentMeasurement(serverUrl: String?): AirGradientFetchResult
}

sealed interface AirGradientFetchResult {
    data class Success(
        val snapshot: AirMeasureSnapshot,
    ) : AirGradientFetchResult

    data class Failure(
        val error: AirGradientError,
    ) : AirGradientFetchResult
}
