package dev.worxbend.airgradient.presentation.settings

import dev.worxbend.airgradient.domain.error.AirGradientError

internal object SettingsPresentationFormatter {
    fun connectionFailureMessage(error: AirGradientError): String =
        when (error) {
            AirGradientError.MissingDeviceUrl,
            AirGradientError.InvalidDeviceUrl,
            -> "Enter a valid local device URL."

            AirGradientError.DeviceUnreachable -> "The device is unreachable on this network."

            AirGradientError.Timeout -> "The device did not respond before the request timed out."

            is AirGradientError.HttpFailure -> "The device returned HTTP ${error.statusCode}."

            AirGradientError.MalformedPayload -> "The device responded, but the payload was not readable."

            AirGradientError.Unknown -> "The connection test failed."
        }
}
