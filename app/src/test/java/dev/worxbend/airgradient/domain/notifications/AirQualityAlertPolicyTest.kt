package dev.worxbend.airgradient.domain.notifications

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AirQualityAlertPolicyTest {
    @Test
    fun `sensor alert requires two consecutive degraded readings`() {
        val policy = AirQualityAlertPolicy()

        assertTrue(policy.evaluateSnapshot(sampleSnapshot.copy(co2 = 1_000.0), now).isEmpty())

        val alerts = policy.evaluateSnapshot(sampleSnapshot.copy(co2 = 1_000.0), now.plusSeconds(30))

        assertEquals(1, alerts.size)
        assertEquals(AirQualityAlertKind.CO2, alerts.single().kind)
        assertEquals(AirQualityAlertSeverity.NOTICE, alerts.single().severity)
    }

    @Test
    fun `cooldown suppresses repeated sensor alerts until window passes`() {
        val policy = AirQualityAlertPolicy(cooldown = Duration.ofMinutes(20))
        val degradedSnapshot = sampleSnapshot.copy(pm25 = 40.0)

        policy.evaluateSnapshot(degradedSnapshot, now)
        assertEquals(1, policy.evaluateSnapshot(degradedSnapshot, now.plusSeconds(30)).size)
        assertTrue(policy.evaluateSnapshot(degradedSnapshot, now.plusSeconds(60)).isEmpty())

        val alerts = policy.evaluateSnapshot(degradedSnapshot, now.plus(Duration.ofMinutes(21)))

        assertEquals(1, alerts.size)
        assertEquals(AirQualityAlertKind.PM25, alerts.single().kind)
    }

    @Test
    fun `severity escalation bypasses cooldown`() {
        val policy = AirQualityAlertPolicy(cooldown = Duration.ofMinutes(20))

        policy.evaluateSnapshot(sampleSnapshot.copy(co2 = 1_000.0), now)
        policy.evaluateSnapshot(sampleSnapshot.copy(co2 = 1_000.0), now.plusSeconds(30))

        val alerts = policy.evaluateSnapshot(sampleSnapshot.copy(co2 = 2_100.0), now.plusSeconds(60))

        assertEquals(1, alerts.size)
        assertEquals(AirQualityAlertSeverity.CRITICAL, alerts.single().severity)
    }

    @Test
    fun `recovery clears consecutive alert state`() {
        val policy = AirQualityAlertPolicy(cooldown = Duration.ofMinutes(20))
        val degradedSnapshot = sampleSnapshot.copy(aqi = 125)

        policy.evaluateSnapshot(degradedSnapshot, now)
        assertEquals(1, policy.evaluateSnapshot(degradedSnapshot, now.plusSeconds(30)).size)

        assertTrue(policy.evaluateSnapshot(sampleSnapshot.copy(aqi = 50), now.plusSeconds(60)).isEmpty())
        assertTrue(policy.evaluateSnapshot(degradedSnapshot, now.plusSeconds(90)).isEmpty())

        val alerts = policy.evaluateSnapshot(degradedSnapshot, now.plusSeconds(120))

        assertEquals(1, alerts.size)
        assertEquals(AirQualityAlertKind.AQI, alerts.single().kind)
    }

    @Test
    fun `third consecutive fetch failure creates offline warning`() {
        val policy = AirQualityAlertPolicy()

        assertTrue(policy.evaluateFetchFailure(AirGradientError.Timeout, now).isEmpty())
        assertTrue(policy.evaluateFetchFailure(AirGradientError.Timeout, now.plusSeconds(30)).isEmpty())

        val alerts = policy.evaluateFetchFailure(AirGradientError.Timeout, now.plusSeconds(60))

        assertEquals(1, alerts.size)
        assertEquals(AirQualityAlertKind.DEVICE_OFFLINE, alerts.single().kind)
        assertEquals(AirQualityAlertSeverity.WARNING, alerts.single().severity)
    }

    @Test
    fun `successful snapshot resets offline failure count`() {
        val policy = AirQualityAlertPolicy()

        policy.evaluateFetchFailure(AirGradientError.Timeout, now)
        policy.evaluateFetchFailure(AirGradientError.Timeout, now.plusSeconds(30))
        policy.evaluateSnapshot(sampleSnapshot, now.plusSeconds(60))

        assertTrue(policy.evaluateFetchFailure(AirGradientError.Timeout, now.plusSeconds(90)).isEmpty())
    }

    private companion object {
        val now: Instant = Instant.parse("2026-06-16T00:00:00Z")

        val sampleSnapshot =
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
                measuredAt = now,
            )
    }
}
