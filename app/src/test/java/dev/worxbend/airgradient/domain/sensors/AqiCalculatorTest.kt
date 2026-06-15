package dev.worxbend.airgradient.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AqiCalculatorTest {
    @Test
    fun `returns null when PM25 is missing`() {
        assertNull(AqiCalculator.calculateFromPm25(null))
    }

    @Test
    fun `calculates AQI fallback from PM25`() {
        assertEquals(29, AqiCalculator.calculateFromPm25(7.0))
        assertEquals(50, AqiCalculator.calculateFromPm25(12.0))
        assertEquals(100, AqiCalculator.calculateFromPm25(35.4))
    }

    @Test
    fun `clamps AQI fallback to supported range`() {
        assertEquals(0, AqiCalculator.calculateFromPm25(-3.0))
        assertEquals(500, AqiCalculator.calculateFromPm25(800.0))
    }
}
