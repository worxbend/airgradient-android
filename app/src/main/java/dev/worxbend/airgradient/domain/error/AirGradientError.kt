package dev.worxbend.airgradient.domain.error

sealed interface AirGradientError {
    data object MissingDeviceUrl : AirGradientError

    data object InvalidDeviceUrl : AirGradientError

    data object DeviceUnreachable : AirGradientError

    data object Timeout : AirGradientError

    data class HttpFailure(
        val statusCode: Int,
    ) : AirGradientError

    data object MalformedPayload : AirGradientError

    data object Unknown : AirGradientError
}
