package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickSkipReason
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AdaptivePollingBackoffTest {
    private lateinit var backoff: AdaptivePollingBackoff

    private val fiveMinutes = Duration.ofMinutes(5)
    private val now: Instant = Instant.parse("2026-06-16T12:00:00Z")

    // All sensor values clearly within GOOD thresholds (TVOC < 65, CO2 < 800, PM2.5 < 12, AQI <= 50, NOx < 20)
    private val goodSnapshot =
        AirMeasureSnapshot(
            aqi = 29,
            pm003Count = 200.0,
            pm01 = 2.0,
            pm25 = 7.0,
            pm10 = 8.0,
            co2 = 447.0,
            tvoc = 50.0,
            nox = 1.0,
            temperatureCelsius = 24.0,
            humidityPercent = 49.0,
            measuredAt = now,
        )

    private val criticalSnapshot =
        goodSnapshot.copy(
            aqi = null,
            pm25 = 80.0,
            co2 = 3000.0,
        )

    @Before
    fun setUp() {
        backoff = AdaptivePollingBackoff()
    }

    @Test
    fun `at good threshold first backoff step doubles the interval`() {
        val goodResult = MonitoringTickResult.Success(goodSnapshot, now)
        repeat(AdaptivePollingBackoff.GOOD_BACKOFF_THRESHOLD - 1) {
            backoff.updateAndGetDelay(goodResult, fiveMinutes)
        }
        val delay = backoff.updateAndGetDelay(goodResult, fiveMinutes)
        assertEquals(Duration.ofMinutes(10), delay)
    }

    @Test
    fun `one below threshold stays at configured interval`() {
        val goodResult = MonitoringTickResult.Success(goodSnapshot, now)
        repeat(AdaptivePollingBackoff.GOOD_BACKOFF_THRESHOLD - 1) {
            val delay = backoff.updateAndGetDelay(goodResult, fiveMinutes)
            assertEquals(fiveMinutes, delay)
        }
    }

    @Test
    fun `backoff caps at max`() {
        val goodResult = MonitoringTickResult.Success(goodSnapshot, now)
        repeat(20) { backoff.updateAndGetDelay(goodResult, fiveMinutes) }
        val delay = backoff.updateAndGetDelay(goodResult, fiveMinutes)
        assertEquals(AdaptivePollingBackoff.MAX_BACKOFF, delay)
    }

    @Test
    fun `warning or critical reading resets good backoff to configured interval`() {
        val goodResult = MonitoringTickResult.Success(goodSnapshot, now)
        val criticalResult = MonitoringTickResult.Success(criticalSnapshot, now)
        repeat(10) { backoff.updateAndGetDelay(goodResult, fiveMinutes) }
        val delay = backoff.updateAndGetDelay(criticalResult, fiveMinutes)
        assertEquals(fiveMinutes, delay)
    }

    @Test
    fun `good reading after critical restarts count from zero`() {
        val goodResult = MonitoringTickResult.Success(goodSnapshot, now)
        val criticalResult = MonitoringTickResult.Success(criticalSnapshot, now)
        repeat(10) { backoff.updateAndGetDelay(goodResult, fiveMinutes) }
        backoff.updateAndGetDelay(criticalResult, fiveMinutes)
        // One good after critical: count is 1, below threshold
        val delay = backoff.updateAndGetDelay(goodResult, fiveMinutes)
        assertEquals(fiveMinutes, delay)
    }

    @Test
    fun `repeated failures back off after threshold`() {
        val failure = MonitoringTickResult.Failure(AirGradientError.DeviceUnreachable, 1, now)
        repeat(AdaptivePollingBackoff.FAILURE_BACKOFF_THRESHOLD - 1) {
            backoff.updateAndGetDelay(failure, fiveMinutes)
        }
        val delay = backoff.updateAndGetDelay(failure, fiveMinutes)
        assertEquals(Duration.ofMinutes(10), delay)
    }

    @Test
    fun `failures below threshold stay at configured interval`() {
        val failure = MonitoringTickResult.Failure(AirGradientError.DeviceUnreachable, 1, now)
        repeat(AdaptivePollingBackoff.FAILURE_BACKOFF_THRESHOLD - 1) {
            val delay = backoff.updateAndGetDelay(failure, fiveMinutes)
            assertEquals(fiveMinutes, delay)
        }
    }

    @Test
    fun `successful good reading after repeated failures resets failure backoff`() {
        val failure = MonitoringTickResult.Failure(AirGradientError.DeviceUnreachable, 1, now)
        repeat(10) { backoff.updateAndGetDelay(failure, fiveMinutes) }
        val goodResult = MonitoringTickResult.Success(goodSnapshot, now)
        val delay = backoff.updateAndGetDelay(goodResult, fiveMinutes)
        assertEquals(fiveMinutes, delay)
    }

    @Test
    fun `skipped tick returns configured interval without changing backoff state`() {
        val goodResult = MonitoringTickResult.Success(goodSnapshot, now)
        repeat(10) { backoff.updateAndGetDelay(goodResult, fiveMinutes) }
        val skipped = MonitoringTickResult.Skipped(MonitoringTickSkipReason.RequestAlreadyRunning, now)
        val delay = backoff.updateAndGetDelay(skipped, fiveMinutes)
        assertEquals(fiveMinutes, delay)
    }

    @Test
    fun `skipped tick does not reset good backoff state`() {
        val goodResult = MonitoringTickResult.Success(goodSnapshot, now)
        repeat(AdaptivePollingBackoff.GOOD_BACKOFF_THRESHOLD) {
            backoff.updateAndGetDelay(goodResult, fiveMinutes)
        }
        val skipped = MonitoringTickResult.Skipped(MonitoringTickSkipReason.RequestAlreadyRunning, now)
        backoff.updateAndGetDelay(skipped, fiveMinutes)
        // Next good reading should still be backed off
        val delay = backoff.updateAndGetDelay(goodResult, fiveMinutes)
        // count was at threshold (3), skipped didn't change it, now at 4 → 4x (capped at 15 min for 5min base)
        assertEquals(AdaptivePollingBackoff.MAX_BACKOFF, delay)
    }

    @Test
    fun `reset clears all backoff state`() {
        val goodResult = MonitoringTickResult.Success(goodSnapshot, now)
        repeat(10) { backoff.updateAndGetDelay(goodResult, fiveMinutes) }
        backoff.reset()
        val delay = backoff.updateAndGetDelay(goodResult, fiveMinutes)
        assertEquals(fiveMinutes, delay)
    }

    @Test
    fun `unknown sensor status does not trigger good backoff`() {
        val unknownSnapshot =
            AirMeasureSnapshot(
                aqi = null,
                pm003Count = null,
                pm01 = null,
                pm25 = null,
                pm10 = null,
                co2 = null,
                tvoc = null,
                nox = null,
                temperatureCelsius = null,
                humidityPercent = null,
                measuredAt = now,
            )
        val unknownResult = MonitoringTickResult.Success(unknownSnapshot, now)
        repeat(10) { backoff.updateAndGetDelay(unknownResult, fiveMinutes) }
        val delay = backoff.updateAndGetDelay(unknownResult, fiveMinutes)
        assertEquals(fiveMinutes, delay)
    }

    @Test
    fun `backoff doubles from threshold with 30s base interval`() {
        val thirtySeconds = Duration.ofSeconds(30)
        val goodResult = MonitoringTickResult.Success(goodSnapshot, now)
        repeat(AdaptivePollingBackoff.GOOD_BACKOFF_THRESHOLD) {
            backoff.updateAndGetDelay(goodResult, thirtySeconds)
        }
        val delay = backoff.updateAndGetDelay(goodResult, thirtySeconds)
        assertEquals(Duration.ofMinutes(2), delay)
    }
}
