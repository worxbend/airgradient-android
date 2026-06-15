package dev.worxbend.airgradient.domain.model

enum class SensorStatus(
    val severity: Int,
) {
    GOOD(severity = 0),
    MODERATE(severity = 1),
    WARNING(severity = 2),
    CRITICAL(severity = 3),
    UNKNOWN(severity = -1),
}
