package dev.worxbend.airgradient.domain.sensors

import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.SensorStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SensorThresholdsTest {
    @Test
    fun `classifies CO2 boundary values`() {
        assertEquals(SensorStatus.UNKNOWN, SensorThresholds.classifyCo2(null))
        assertEquals(SensorStatus.GOOD, SensorThresholds.classifyCo2(799.9))
        assertEquals(SensorStatus.MODERATE, SensorThresholds.classifyCo2(800.0))
        assertEquals(SensorStatus.WARNING, SensorThresholds.classifyCo2(1_200.0))
        assertEquals(SensorStatus.CRITICAL, SensorThresholds.classifyCo2(2_000.0))
    }

    @Test
    fun `classifies PM25 boundary values`() {
        assertEquals(SensorStatus.GOOD, SensorThresholds.classifyPm25(11.9))
        assertEquals(SensorStatus.MODERATE, SensorThresholds.classifyPm25(12.0))
        assertEquals(SensorStatus.WARNING, SensorThresholds.classifyPm25(35.0))
        assertEquals(SensorStatus.CRITICAL, SensorThresholds.classifyPm25(55.0))
    }

    @Test
    fun `classifies AQI boundary values`() {
        assertEquals(SensorStatus.GOOD, SensorThresholds.classifyAqi(50))
        assertEquals(SensorStatus.MODERATE, SensorThresholds.classifyAqi(100))
        assertEquals(SensorStatus.WARNING, SensorThresholds.classifyAqi(150))
        assertEquals(SensorStatus.CRITICAL, SensorThresholds.classifyAqi(200))
        assertEquals(SensorStatus.CRITICAL, SensorThresholds.classifyAqi(300))
        assertEquals(SensorStatus.CRITICAL, SensorThresholds.classifyAqi(301))
    }

    @Test
    fun `overall status chooses worst classified sensor`() {
        val snapshot =
            AirMeasureSnapshot(
                aqi = 42,
                pm003Count = null,
                pm01 = null,
                pm25 = 10.0,
                pm10 = null,
                co2 = 2_100.0,
                tvoc = 100.0,
                nox = 1.0,
                temperatureCelsius = null,
                humidityPercent = null,
                measuredAt = Instant.parse("2026-06-16T00:00:00Z"),
            )

        assertEquals(SensorStatus.CRITICAL, SensorThresholds.overallStatus(snapshot))
    }

    @Test
    fun `overall status is unknown when all classified sensors are missing`() {
        val snapshot =
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
                measuredAt = Instant.parse("2026-06-16T00:00:00Z"),
            )

        assertEquals(SensorStatus.UNKNOWN, SensorThresholds.overallStatus(snapshot))
    }
}
