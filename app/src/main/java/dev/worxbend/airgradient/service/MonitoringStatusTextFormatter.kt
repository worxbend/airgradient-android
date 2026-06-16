package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringStatus
import dev.worxbend.airgradient.domain.monitoring.MonitoringStopReason
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MonitoringStatusTextFormatter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val instantFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)

    fun title(status: MonitoringStatus): String =
        when (status) {
            MonitoringStatus.Off -> "AirGradient monitoring off"
            is MonitoringStatus.Starting -> "Starting AirGradient monitoring"
            is MonitoringStatus.Active -> "AirGradient monitoring active"
            is MonitoringStatus.Stopped -> "AirGradient monitoring stopped"
        }

    fun body(status: MonitoringStatus): String =
        when (status) {
            MonitoringStatus.Off -> "Background checks are not running."
            is MonitoringStatus.Starting -> "Preparing local AirGradient checks."
            is MonitoringStatus.Active -> status.activeBody()
            is MonitoringStatus.Stopped -> "Stopped: ${status.reason.label()}."
        }

    private fun MonitoringStatus.Active.activeBody(): String {
        val lastCheck =
            lastCheckedAt?.let { checkedAt -> "Last check ${checkedAt.label()}." }
                ?: "Waiting for the first check."
        val lastSuccess =
            lastSuccessfulReadAt?.let { successAt -> " Last success ${successAt.label()}." }.orEmpty()
        return "$lastCheck$lastSuccess Polling every ${pollingInterval.label()}."
    }

    private fun Instant.label(): String = instantFormatter.format(atZone(zoneId))

    private fun Duration.label(): String =
        if (seconds < SECONDS_PER_MINUTE) {
            "$seconds sec"
        } else {
            "${toMinutes()} min"
        }

    private fun MonitoringStopReason.label(): String =
        when (this) {
            MonitoringStopReason.UserRequested -> "stopped by user"
            MonitoringStopReason.MissingDeviceUrl -> "device URL removed"
            MonitoringStopReason.MissingNotificationPermission -> "notification permission missing"
            MonitoringStopReason.InvalidConfiguration -> "invalid configuration"
            MonitoringStopReason.ServiceDestroyed -> "service stopped"
        }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
