package dev.worxbend.airgradient.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class BatterySaverDelayTest {
    @Test
    fun `battery saver inactive returns adaptive delay unchanged`() {
        val delay = Duration.ofMinutes(5)
        assertEquals(delay, AirQualityMonitoringService.effectiveDelayWithBatterySaver(delay, false))
    }

    @Test
    fun `battery saver active enforces 15 minute minimum when delay is shorter`() {
        val delay = Duration.ofMinutes(5)
        assertEquals(
            AirQualityMonitoringService.BATTERY_SAVER_MIN_INTERVAL,
            AirQualityMonitoringService.effectiveDelayWithBatterySaver(delay, true),
        )
    }

    @Test
    fun `battery saver active allows delay longer than 15 minutes`() {
        val delay = Duration.ofMinutes(20)
        assertEquals(delay, AirQualityMonitoringService.effectiveDelayWithBatterySaver(delay, true))
    }

    @Test
    fun `battery saver active with exactly 15 minute delay returns 15 minutes`() {
        val delay = AirQualityMonitoringService.BATTERY_SAVER_MIN_INTERVAL
        assertEquals(delay, AirQualityMonitoringService.effectiveDelayWithBatterySaver(delay, true))
    }

    @Test
    fun `battery saver active with 30 second delay enforces 15 minute minimum`() {
        val delay = Duration.ofSeconds(30)
        assertEquals(
            AirQualityMonitoringService.BATTERY_SAVER_MIN_INTERVAL,
            AirQualityMonitoringService.effectiveDelayWithBatterySaver(delay, true),
        )
    }

    @Test
    fun `battery saver inactive with 30 second delay returns 30 seconds`() {
        val delay = Duration.ofSeconds(30)
        assertEquals(delay, AirQualityMonitoringService.effectiveDelayWithBatterySaver(delay, false))
    }
}
