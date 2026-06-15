package dev.worxbend.airgradient.domain.sensors

import dev.worxbend.airgradient.domain.model.Trend
import dev.worxbend.airgradient.domain.model.TrendDirection
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object TrendCalculator {
    fun calculate(
        current: Double?,
        previous: Double?,
        unit: String,
        lowerIsBetter: Boolean,
    ): Trend =
        when {
            current == null -> {
                Trend(
                    direction = TrendDirection.UNKNOWN,
                    deltaLabel = "No reading",
                    description = "No current reading",
                )
            }

            previous == null -> {
                Trend(
                    direction = TrendDirection.UNKNOWN,
                    deltaLabel = "No previous reading",
                    description = "No previous reading",
                )
            }

            else -> {
                calculateDeltaTrend(
                    current = current,
                    previous = previous,
                    unit = unit,
                    lowerIsBetter = lowerIsBetter,
                )
            }
        }

    fun formatNumber(value: Double): String {
        val roundedToInteger = value.toLong()
        val distanceFromInteger = abs(value - roundedToInteger)
        val distanceFromNextInteger = abs(value - (roundedToInteger + 1))

        return if (abs(value) >= INTEGER_FORMAT_THRESHOLD ||
            distanceFromInteger <= INTEGER_EPSILON ||
            distanceFromNextInteger <= INTEGER_EPSILON
        ) {
            value.roundToLong().toString()
        } else {
            ONE_DECIMAL_FORMAT.format(Locale.US, value)
        }
    }

    private fun calculateDeltaTrend(
        current: Double,
        previous: Double,
        unit: String,
        lowerIsBetter: Boolean,
    ): Trend {
        val delta = current - previous

        return if (abs(delta) < STABLE_DELTA_THRESHOLD) {
            Trend(
                direction = TrendDirection.STABLE,
                deltaLabel = "→ 0${unit.suffix()}",
                description = "Stable",
            )
        } else {
            directionalTrend(delta = delta, unit = unit, lowerIsBetter = lowerIsBetter)
        }
    }

    private fun directionalTrend(
        delta: Double,
        unit: String,
        lowerIsBetter: Boolean,
    ): Trend {
        val direction = if (delta > 0) TrendDirection.UP else TrendDirection.DOWN
        val directionSymbol = if (delta > 0) "↑" else "↓"
        val sign = if (delta > 0) "+" else "-"
        val qualityDescription =
            when {
                lowerIsBetter && delta < 0 -> "Improved"
                lowerIsBetter && delta > 0 -> "Worse"
                !lowerIsBetter && delta > 0 -> "Higher"
                else -> "Lower"
            }

        return Trend(
            direction = direction,
            deltaLabel = "$directionSymbol $sign${formatNumber(abs(delta))}${unit.suffix()}",
            description = qualityDescription,
        )
    }

    private fun String.suffix(): String = if (isBlank()) "" else " $this"

    private const val STABLE_DELTA_THRESHOLD = 0.05
    private const val INTEGER_FORMAT_THRESHOLD = 10.0
    private const val INTEGER_EPSILON = 0.05
    private const val ONE_DECIMAL_FORMAT = "%.1f"
}
