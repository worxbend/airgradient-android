package dev.worxbend.airgradient.presentation.dashboard

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object DashboardPresentationFormatter {
    private val instantFormatter = DateTimeFormatter.ISO_INSTANT
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun lastUpdatedLabel(snapshot: AirMeasureSnapshot): String {
        val formattedTime = timeFormatter.withZone(ZoneId.systemDefault()).format(snapshot.measuredAt)
        return "Last updated $formattedTime"
    }

    fun lastBackgroundCheckLabel(checkedAt: Instant): String {
        val formattedInstant = instantFormatter.format(checkedAt)
        return "Last background check $formattedInstant"
    }

    fun lastSuccessfulBackgroundReadLabel(readAt: Instant): String {
        val formattedInstant = instantFormatter.format(readAt)
        return "Last successful reading $formattedInstant"
    }

    fun fetchFailureStatusLabel(error: AirGradientError): String = "Fetch failed: ${error.toShortMessage()}"

    fun warningMessage(error: AirGradientError): String =
        when (error) {
            AirGradientError.DeviceUnreachable -> "Showing the last reading; the device is currently unreachable."
            AirGradientError.Timeout -> "Showing the last reading; the latest request timed out."
            else -> "Showing the last reading; refresh failed."
        }

    fun dashboardError(error: AirGradientError): DashboardError =
        DashboardError(
            cause = error,
            title = error.toErrorTitle(),
            message = error.toErrorMessage(),
        )

    private fun AirGradientError.toErrorTitle(): String =
        when (this) {
            AirGradientError.MissingDeviceUrl -> "Device URL required"
            AirGradientError.InvalidDeviceUrl -> "Device URL is invalid"
            AirGradientError.DeviceUnreachable -> "Device unreachable"
            AirGradientError.Timeout -> "Connection timed out"
            is AirGradientError.HttpFailure -> "Device returned HTTP $statusCode"
            AirGradientError.MalformedPayload -> "Unexpected device response"
            AirGradientError.Unknown -> "Refresh failed"
        }

    private fun AirGradientError.toErrorMessage(): String =
        when (this) {
            AirGradientError.MissingDeviceUrl -> "Configure an AirGradient local-server URL."
            AirGradientError.InvalidDeviceUrl -> "Check the device URL and try again."
            AirGradientError.DeviceUnreachable -> "Make sure the device is powered on and on this network."
            AirGradientError.Timeout -> "The device did not respond before the request timed out."
            is AirGradientError.HttpFailure -> "The device rejected the measurements request."
            AirGradientError.MalformedPayload -> "The device response could not be read as measurements."
            AirGradientError.Unknown -> "The latest measurements could not be loaded."
        }

    private fun AirGradientError.toShortMessage(): String =
        when (this) {
            AirGradientError.MissingDeviceUrl -> "Configure an AirGradient local-server URL."
            AirGradientError.InvalidDeviceUrl -> "Invalid device URL."
            AirGradientError.DeviceUnreachable -> "Device unreachable."
            AirGradientError.Timeout -> "Request timed out."
            is AirGradientError.HttpFailure -> "HTTP $statusCode."
            AirGradientError.MalformedPayload -> "Malformed payload."
            AirGradientError.Unknown -> "Unknown error."
        }
}
