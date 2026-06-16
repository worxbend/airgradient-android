package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringStatus
import dev.worxbend.airgradient.domain.monitoring.MonitoringStopReason
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class MonitoringStatusTextFormatterTest {
    private val formatter = MonitoringStatusTextFormatter(ZoneOffset.UTC)

    @Test
    fun `formats active monitoring with readable timestamps`() {
        val status =
            MonitoringStatus.Active(
                mode = MonitoringMode.AlwaysOnForegroundService,
                pollingInterval = Duration.ofSeconds(30),
                lastCheckedAt = Instant.parse("2026-06-16T09:31:00Z"),
                lastSuccessfulReadAt = Instant.parse("2026-06-16T09:30:00Z"),
            )

        assertEquals("AirGradient monitoring active", formatter.title(status))
        assertEquals(
            "Last check Jun 16, 09:31. Last success Jun 16, 09:30. Polling every 30 sec.",
            formatter.body(status),
        )
    }

    @Test
    fun `formats active monitoring before first check`() {
        val status =
            MonitoringStatus.Active(
                mode = MonitoringMode.AlwaysOnForegroundService,
                pollingInterval = Duration.ofMinutes(2),
                lastCheckedAt = null,
                lastSuccessfulReadAt = null,
            )

        assertEquals(
            "Waiting for the first check. Polling every 2 min.",
            formatter.body(status),
        )
    }

    @Test
    fun `formats stopped reasons as user copy`() {
        val status =
            MonitoringStatus.Stopped(
                reason = MonitoringStopReason.MissingDeviceUrl,
                stoppedAt = Instant.parse("2026-06-16T09:31:00Z"),
            )

        assertEquals("AirGradient monitoring stopped", formatter.title(status))
        assertEquals("Stopped: device URL removed.", formatter.body(status))
    }
}
