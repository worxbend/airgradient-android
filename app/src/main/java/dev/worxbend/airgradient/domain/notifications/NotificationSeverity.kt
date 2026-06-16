package dev.worxbend.airgradient.domain.notifications

enum class NotificationSeverity(
    val rank: Int,
) {
    Warning(rank = 2),
    Critical(rank = 3),
}
