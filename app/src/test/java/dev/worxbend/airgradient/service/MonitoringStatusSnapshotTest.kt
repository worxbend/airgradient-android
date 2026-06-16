package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickSkipReason
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MonitoringStatusSnapshotTest {
    @Test
    fun `success records completed check and successful reading time`() {
        val snapshot =
            MonitoringStatusSnapshot().after(
                MonitoringTickResult.Success(
                    snapshot = healthySnapshot,
                    checkedAt = checkedAt,
                ),
            )

        assertEquals(checkedAt, snapshot.lastCheckedAt)
        assertEquals(measuredAt, snapshot.lastSuccessfulReadAt)
    }

    @Test
    fun `failure records completed check and keeps previous successful reading time`() {
        val previous =
            MonitoringStatusSnapshot(
                lastCheckedAt = checkedAt.minusSeconds(60),
                lastSuccessfulReadAt = measuredAt,
            )

        val snapshot =
            previous.after(
                MonitoringTickResult.Failure(
                    error = AirGradientError.Timeout,
                    consecutiveFailureCount = 1,
                    checkedAt = checkedAt,
                ),
            )

        assertEquals(checkedAt, snapshot.lastCheckedAt)
        assertEquals(measuredAt, snapshot.lastSuccessfulReadAt)
    }

    @Test
    fun `skipped tick leaves visible status timestamps unchanged`() {
        val previous =
            MonitoringStatusSnapshot(
                lastCheckedAt = checkedAt,
                lastSuccessfulReadAt = measuredAt,
            )

        val snapshot =
            previous.after(
                MonitoringTickResult.Skipped(
                    reason = MonitoringTickSkipReason.RequestAlreadyRunning,
                    checkedAt = checkedAt.plusSeconds(30),
                ),
            )

        assertEquals(previous, snapshot)
    }

    private companion object {
        val checkedAt: Instant = Instant.parse("2026-06-16T11:00:00Z")
        val measuredAt: Instant = Instant.parse("2026-06-16T10:59:56Z")

        val healthySnapshot =
            AirMeasureSnapshot(
                aqi = 29,
                pm003Count = 442.0,
                pm01 = 3.0,
                pm25 = 7.0,
                pm10 = 8.0,
                co2 = 447.0,
                tvoc = 100.0,
                nox = 1.0,
                temperatureCelsius = 24.47,
                humidityPercent = 49.0,
                measuredAt = measuredAt,
            )
    }
}
