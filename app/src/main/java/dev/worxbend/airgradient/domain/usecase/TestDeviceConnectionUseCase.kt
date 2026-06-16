package dev.worxbend.airgradient.domain.usecase

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizationResult
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizer

class TestDeviceConnectionUseCase(
    private val getCurrentMeasurement: GetCurrentMeasurementUseCase,
) {
    suspend operator fun invoke(input: String): TestDeviceConnectionResult =
        when (val normalized = DeviceUrlNormalizer.normalize(input)) {
            DeviceUrlNormalizationResult.Invalid,
            DeviceUrlNormalizationResult.Unconfigured,
            -> TestDeviceConnectionResult.InvalidUrl

            is DeviceUrlNormalizationResult.Normalized -> testNormalizedUrl(normalized.value)
        }

    private suspend fun testNormalizedUrl(normalizedUrl: String): TestDeviceConnectionResult =
        when (val result = getCurrentMeasurement(normalizedUrl)) {
            is AirGradientFetchResult.Success -> TestDeviceConnectionResult.Success(normalizedUrl)
            is AirGradientFetchResult.Failure -> TestDeviceConnectionResult.Failure(result.error)
        }
}

sealed interface TestDeviceConnectionResult {
    data class Success(
        val normalizedUrl: String,
    ) : TestDeviceConnectionResult

    data class Failure(
        val error: AirGradientError,
    ) : TestDeviceConnectionResult

    data object InvalidUrl : TestDeviceConnectionResult
}
