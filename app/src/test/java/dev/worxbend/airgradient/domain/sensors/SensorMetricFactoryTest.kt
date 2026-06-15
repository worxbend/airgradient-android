package dev.worxbend.airgradient.domain.sensors

import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.SensorMetricKind
import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.domain.model.TrendDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SensorMetricFactoryTest {
    @Test
    fun `creates dashboard metrics with formatted values statuses and trends`() {
        val previous = sampleSnapshot.copy(pm25 = 10.0, co2 = 700.0)
        val current = sampleSnapshot.copy(pm25 = 20.0, co2 = 850.0)

        val metrics = SensorMetricFactory.createMetrics(current = current, previous = previous)

        assertEquals(10, metrics.size)

        val pm25 = metrics.single { it.kind == SensorMetricKind.PM25 }
        assertEquals("PM2.5", pm25.displayName)
        assertEquals("20 ug/m3", pm25.valueLabel)
        assertEquals(SensorStatus.MODERATE, pm25.status)
        assertEquals(TrendDirection.UP, pm25.trend.direction)
        assertEquals("Worse", pm25.trend.description)

        val co2 = metrics.single { it.kind == SensorMetricKind.CO2 }
        assertEquals("850 ppm", co2.valueLabel)
        assertEquals(SensorStatus.MODERATE, co2.status)
    }

    @Test
    fun `missing values are displayed as placeholders`() {
        val current = sampleSnapshot.copy(aqi = null, pm25 = null, co2 = null)

        val metrics = SensorMetricFactory.createMetrics(current = current, previous = null)
        val aqi = metrics.single { it.kind == SensorMetricKind.AQI }

        assertEquals("--", aqi.valueLabel)
        assertEquals(SensorStatus.UNKNOWN, aqi.status)
        assertEquals(TrendDirection.UNKNOWN, aqi.trend.direction)
    }

    private companion object {
        val sampleSnapshot =
            AirMeasureSnapshot(
                aqi = 42,
                pm003Count = 442.0,
                pm01 = 3.0,
                pm25 = 7.0,
                pm10 = 8.0,
                co2 = 447.0,
                tvoc = 100.0,
                nox = 1.0,
                temperatureCelsius = 24.47,
                humidityPercent = 49.0,
                measuredAt = Instant.parse("2026-06-16T00:00:00Z"),
            )
    }
}
