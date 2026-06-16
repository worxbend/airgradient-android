package dev.worxbend.airgradient.domain.sensors

import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.SensorMetric
import dev.worxbend.airgradient.domain.model.SensorMetricKind
import dev.worxbend.airgradient.domain.model.SensorStatus

object SensorMetricFactory {
    fun createMetrics(
        current: AirMeasureSnapshot,
        previous: AirMeasureSnapshot?,
    ): List<SensorMetric> =
        metricDefinitions.map { definition ->
            definition.toMetric(current = current, previous = previous)
        }

    private fun MetricDefinition.toMetric(
        current: AirMeasureSnapshot,
        previous: AirMeasureSnapshot?,
    ): SensorMetric {
        val unit = unitReader(current)
        val value = valueReader(current)
        val previousValue = previous?.let(valueReader)
        val status = statusReader(current)
        val trend =
            TrendCalculator.calculate(
                current = value,
                previous = previousValue,
                unit = unit,
                lowerIsBetter = lowerIsBetter,
            )

        return SensorMetric(
            kind = kind,
            displayName = displayName,
            valueLabel = formatValue(value = value, unit = unit),
            unit = unit,
            status = status,
            trend = trend,
            interpretation = interpretationReader(status),
        )
    }

    private fun formatValue(
        value: Double?,
        unit: String,
    ): String =
        if (value == null) {
            "--"
        } else {
            val formattedValue = TrendCalculator.formatNumber(value)
            if (unit.isBlank()) formattedValue else "$formattedValue $unit"
        }

    private data class MetricDefinition(
        val kind: SensorMetricKind,
        val displayName: String,
        val unitReader: (AirMeasureSnapshot) -> String,
        val lowerIsBetter: Boolean,
        val valueReader: (AirMeasureSnapshot) -> Double?,
        val statusReader: (AirMeasureSnapshot) -> SensorStatus,
        val interpretationReader: (SensorStatus) -> String,
    )

    private val metricDefinitions =
        listOf(
            MetricDefinition(
                kind = SensorMetricKind.AQI,
                displayName = "AQI",
                unitReader = { "" },
                lowerIsBetter = true,
                valueReader = { it.aqi?.toDouble() },
                statusReader = { SensorThresholds.classifyAqi(it.aqi) },
                interpretationReader = ::airQualityInterpretation,
            ),
            MetricDefinition(
                kind = SensorMetricKind.TEMPERATURE,
                displayName = "Temperature",
                unitReader = { "C" },
                lowerIsBetter = false,
                valueReader = AirMeasureSnapshot::temperatureCelsius,
                statusReader = { SensorStatus.UNKNOWN },
                interpretationReader = { "Comfort" },
            ),
            MetricDefinition(
                kind = SensorMetricKind.HUMIDITY,
                displayName = "Humidity",
                unitReader = { "%" },
                lowerIsBetter = false,
                valueReader = AirMeasureSnapshot::humidityPercent,
                statusReader = { SensorStatus.UNKNOWN },
                interpretationReader = { "Comfort" },
            ),
            MetricDefinition(
                kind = SensorMetricKind.CO2,
                displayName = "CO2",
                unitReader = { "ppm" },
                lowerIsBetter = true,
                valueReader = AirMeasureSnapshot::co2,
                statusReader = { SensorThresholds.classifyCo2(it.co2) },
                interpretationReader = ::airQualityInterpretation,
            ),
            MetricDefinition(
                kind = SensorMetricKind.PM25,
                displayName = "PM2.5",
                unitReader = { "ug/m3" },
                lowerIsBetter = true,
                valueReader = AirMeasureSnapshot::pm25,
                statusReader = { SensorThresholds.classifyPm25(it.pm25) },
                interpretationReader = ::airQualityInterpretation,
            ),
            MetricDefinition(
                kind = SensorMetricKind.PM01,
                displayName = "PM1.0",
                unitReader = { "ug/m3" },
                lowerIsBetter = true,
                valueReader = AirMeasureSnapshot::pm01,
                statusReader = { SensorStatus.UNKNOWN },
                interpretationReader = { "Particle reading" },
            ),
            MetricDefinition(
                kind = SensorMetricKind.PM10,
                displayName = "PM10",
                unitReader = { "ug/m3" },
                lowerIsBetter = true,
                valueReader = AirMeasureSnapshot::pm10,
                statusReader = { SensorStatus.UNKNOWN },
                interpretationReader = { "Particle reading" },
            ),
            MetricDefinition(
                kind = SensorMetricKind.PM003_COUNT,
                displayName = "PM0.3",
                unitReader = { "count" },
                lowerIsBetter = true,
                valueReader = AirMeasureSnapshot::pm003Count,
                statusReader = { SensorStatus.UNKNOWN },
                interpretationReader = { "Particle count" },
            ),
            MetricDefinition(
                kind = SensorMetricKind.TVOC,
                displayName = "TVOC",
                unitReader = { it.tvocUnit.displayLabel },
                lowerIsBetter = true,
                valueReader = AirMeasureSnapshot::tvoc,
                statusReader = { SensorThresholds.classifyTvoc(it.tvoc) },
                interpretationReader = ::airQualityInterpretation,
            ),
            MetricDefinition(
                kind = SensorMetricKind.NOX,
                displayName = "NOx",
                unitReader = { it.noxUnit.displayLabel },
                lowerIsBetter = true,
                valueReader = AirMeasureSnapshot::nox,
                statusReader = { SensorThresholds.classifyNox(it.nox) },
                interpretationReader = ::airQualityInterpretation,
            ),
        )

    private fun airQualityInterpretation(status: SensorStatus): String =
        when (status) {
            SensorStatus.GOOD -> "Good"
            SensorStatus.MODERATE -> "Moderate"
            SensorStatus.WARNING -> "Elevated"
            SensorStatus.CRITICAL -> "Needs attention"
            SensorStatus.UNKNOWN -> "No reading"
        }
}
