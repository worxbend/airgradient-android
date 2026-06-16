package dev.worxbend.airgradient.domain.notifications

data class NotificationMessage(
    val type: NotificationType,
    val key: String,
    val severity: NotificationSeverity,
    val title: String,
    val body: String,
)

sealed interface NotificationDecision {
    val nextState: NotificationState

    data class Notify(
        val message: NotificationMessage,
        override val nextState: NotificationState,
    ) : NotificationDecision

    data class Suppress(
        val reason: NotificationSuppressionReason,
        override val nextState: NotificationState,
    ) : NotificationDecision
}

enum class NotificationSuppressionReason {
    NotificationsDisabled,
    BelowMinimumSeverity,
    CooldownActive,
    NoActionableCondition,
    RecoveryPending,
    ConsecutiveFailureThresholdNotMet,
}
