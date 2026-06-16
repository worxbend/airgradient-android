package dev.worxbend.airgradient.domain.notifications

import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.SensorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AirQualityConditionFactoryTest {
    @Test
    fun `condition uses overall status and dominant bad metric from snapshot`() {
        val condition =
            AirQualityConditionFactory.fromSnapshot(
                sampleSnapshot.copy(
                    co2 = 1_300.0,
                    pm25 = 10.0,
                ),
            )

        assertEquals(SensorStatus.WARNING, condition.status)
        assertEquals("co2", condition.dominantMetricKey)
        assertEquals("CO2", condition.dominantMetricLabel)
    }

    @Test
    fun `condition reports critical dominant metric`() {
        val condition =
            AirQualityConditionFactory.fromSnapshot(
                sampleSnapshot.copy(pm25 = 180.0),
            )

        assertEquals(SensorStatus.CRITICAL, condition.status)
        assertEquals("pm25", condition.dominantMetricKey)
        assertEquals("PM2.5", condition.dominantMetricLabel)
    }

    @Test
    fun `condition has no dominant metric when readings are good`() {
        val condition = AirQualityConditionFactory.fromSnapshot(sampleSnapshot)

        assertEquals(SensorStatus.GOOD, condition.status)
        assertNull(condition.dominantMetricKey)
        assertNull(condition.dominantMetricLabel)
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
                tvoc = 10.0,
                nox = 1.0,
                temperatureCelsius = 24.47,
                humidityPercent = 49.0,
                measuredAt = now,
            )
    }
}
