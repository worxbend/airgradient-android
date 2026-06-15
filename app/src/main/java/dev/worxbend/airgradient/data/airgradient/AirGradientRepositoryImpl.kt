package dev.worxbend.airgradient.data.airgradient

import dev.worxbend.airgradient.data.airgradient.mapper.AirGradientMeasureMapper
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import dev.worxbend.airgradient.domain.repository.AirGradientRepository
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizationResult
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizer

class AirGradientRepositoryImpl(
    private val remoteDataSource: AirGradientRemoteDataSource = AirGradientRemoteDataSource(),
    private val mapper: AirGradientMeasureMapper = AirGradientMeasureMapper(),
) : AirGradientRepository {
    override suspend fun fetchCurrentMeasurement(serverUrl: String?): AirGradientFetchResult {
        val fetchPlan =
            when (val result = DeviceUrlNormalizer.normalize(serverUrl.orEmpty())) {
                DeviceUrlNormalizationResult.Unconfigured -> FetchPlan.Failed(AirGradientError.MissingDeviceUrl)
                DeviceUrlNormalizationResult.Invalid -> FetchPlan.Failed(AirGradientError.InvalidDeviceUrl)
                is DeviceUrlNormalizationResult.Normalized -> FetchPlan.Ready(result.value)
            }

        return when (fetchPlan) {
            is FetchPlan.Failed -> AirGradientFetchResult.Failure(fetchPlan.error)
            is FetchPlan.Ready -> fetchMeasurement(fetchPlan.normalizedUrl)
        }
    }

    private suspend fun fetchMeasurement(normalizedUrl: String): AirGradientFetchResult =
        when (val result = remoteDataSource.fetchCurrentMeasure(normalizedUrl)) {
            is RemoteMeasureResult.Failure -> AirGradientFetchResult.Failure(result.error)
            is RemoteMeasureResult.Success -> AirGradientFetchResult.Success(mapper.map(result.dto))
        }

    private sealed interface FetchPlan {
        data class Ready(
            val normalizedUrl: String,
        ) : FetchPlan

        data class Failed(
            val error: AirGradientError,
        ) : FetchPlan
    }
}
