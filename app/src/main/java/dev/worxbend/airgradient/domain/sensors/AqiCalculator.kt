package dev.worxbend.airgradient.domain.sensors

import kotlin.math.roundToInt

object AqiCalculator {
    private val pm25Breakpoints =
        listOf(
            AqiBreakpoint(concentrationLow = 0.0, concentrationHigh = 12.0, indexLow = 0, indexHigh = 50),
            AqiBreakpoint(concentrationLow = 12.1, concentrationHigh = 35.4, indexLow = 51, indexHigh = 100),
            AqiBreakpoint(concentrationLow = 35.5, concentrationHigh = 55.4, indexLow = 101, indexHigh = 150),
            AqiBreakpoint(concentrationLow = 55.5, concentrationHigh = 150.4, indexLow = 151, indexHigh = 200),
            AqiBreakpoint(concentrationLow = 150.5, concentrationHigh = 250.4, indexLow = 201, indexHigh = 300),
            AqiBreakpoint(concentrationLow = 250.5, concentrationHigh = 500.4, indexLow = 301, indexHigh = 500),
        )

    fun calculateFromPm25(pm25: Double?): Int? {
        if (pm25 == null) {
            return null
        }

        val concentration = pm25.coerceIn(minimumValue = 0.0, maximumValue = 500.4)
        val breakpoint =
            pm25Breakpoints.firstOrNull { concentration in it.concentrationLow..it.concentrationHigh }
                ?: pm25Breakpoints.last()

        return interpolate(concentration = concentration, breakpoint = breakpoint)
    }

    private fun interpolate(
        concentration: Double,
        breakpoint: AqiBreakpoint,
    ): Int {
        val indexSpan = breakpoint.indexHigh - breakpoint.indexLow
        val concentrationSpan = breakpoint.concentrationHigh - breakpoint.concentrationLow
        val index =
            (indexSpan / concentrationSpan) *
                (concentration - breakpoint.concentrationLow) +
                breakpoint.indexLow

        return index.roundToInt().coerceIn(MIN_AQI, MAX_AQI)
    }

    private data class AqiBreakpoint(
        val concentrationLow: Double,
        val concentrationHigh: Double,
        val indexLow: Int,
        val indexHigh: Int,
    )

    private const val MIN_AQI = 0
    private const val MAX_AQI = 500
}
