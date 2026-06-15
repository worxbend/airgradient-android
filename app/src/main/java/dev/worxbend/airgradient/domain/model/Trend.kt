package dev.worxbend.airgradient.domain.model

data class Trend(
    val direction: TrendDirection,
    val deltaLabel: String,
    val description: String,
)

enum class TrendDirection {
    UP,
    DOWN,
    STABLE,
    UNKNOWN,
}
