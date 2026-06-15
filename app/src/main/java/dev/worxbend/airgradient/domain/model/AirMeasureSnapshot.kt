package dev.worxbend.airgradient.domain.model

import java.time.Instant

data class AirMeasureSnapshot(
    val aqi: Int?,
    val pm003Count: Double?,
    val pm01: Double?,
    val pm25: Double?,
    val pm10: Double?,
    val co2: Double?,
    val tvoc: Double?,
    val tvocUnit: SensorMeasurementUnit = SensorMeasurementUnit.INDEX,
    val nox: Double?,
    val noxUnit: SensorMeasurementUnit = SensorMeasurementUnit.INDEX,
    val temperatureCelsius: Double?,
    val humidityPercent: Double?,
    val measuredAt: Instant,
)
