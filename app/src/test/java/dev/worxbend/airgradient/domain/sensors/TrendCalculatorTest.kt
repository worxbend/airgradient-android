package dev.worxbend.airgradient.domain.sensors

import dev.worxbend.airgradient.domain.model.TrendDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendCalculatorTest {
    @Test
    fun `missing current reading produces unknown trend`() {
        val trend = TrendCalculator.calculate(current = null, previous = 10.0, unit = "ppm", lowerIsBetter = true)

        assertEquals(TrendDirection.UNKNOWN, trend.direction)
        assertEquals("No reading", trend.deltaLabel)
    }

    @Test
    fun `missing previous reading produces unknown trend`() {
        val trend = TrendCalculator.calculate(current = 10.0, previous = null, unit = "ppm", lowerIsBetter = true)

        assertEquals(TrendDirection.UNKNOWN, trend.direction)
        assertEquals("No previous reading", trend.deltaLabel)
    }

    @Test
    fun `small delta is stable`() {
        val trend = TrendCalculator.calculate(current = 10.04, previous = 10.0, unit = "ppm", lowerIsBetter = true)

        assertEquals(TrendDirection.STABLE, trend.direction)
        assertEquals("→ 0 ppm", trend.deltaLabel)
    }

    @Test
    fun `lower pollutant readings are improved and higher readings are worse`() {
        val improved = TrendCalculator.calculate(current = 8.0, previous = 10.0, unit = "ppm", lowerIsBetter = true)
        val worse = TrendCalculator.calculate(current = 12.0, previous = 10.0, unit = "ppm", lowerIsBetter = true)

        assertEquals(TrendDirection.DOWN, improved.direction)
        assertEquals("Improved", improved.description)
        assertEquals("↓ -2 ppm", improved.deltaLabel)

        assertEquals(TrendDirection.UP, worse.direction)
        assertEquals("Worse", worse.description)
        assertEquals("↑ +2 ppm", worse.deltaLabel)
    }

    @Test
    fun `formats reference style numbers`() {
        assertEquals("9.9", TrendCalculator.formatNumber(9.94))
        assertEquals("10", TrendCalculator.formatNumber(9.96))
        assertEquals("11", TrendCalculator.formatNumber(10.6))
    }
}
