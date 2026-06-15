package dev.worxbend.airgradient.domain.model

data class SensorMetric(
    val kind: SensorMetricKind,
    val displayName: String,
    val valueLabel: String,
    val unit: String,
    val status: SensorStatus,
    val trend: Trend,
    val interpretation: String,
)

enum class SensorMetricKind {
    AQI,
    CO2,
    PM25,
    PM01,
    PM10,
    PM003_COUNT,
    TVOC,
    NOX,
    TEMPERATURE,
    HUMIDITY,
}
