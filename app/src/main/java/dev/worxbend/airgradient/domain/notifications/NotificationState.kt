package dev.worxbend.airgradient.domain.notifications

import dev.worxbend.airgradient.domain.model.SensorStatus
import java.time.Instant

data class NotificationState(
    val lastConditionStatus: SensorStatus?,
    val lastDominantMetricKey: String?,
    val activeProblemStartedAt: Instant?,
    val lastNotificationAt: Instant?,
    val lastNotificationByKey: Map<String, Instant>,
    val lastSuccessfulReadAt: Instant?,
    val consecutiveFailureCount: Int,
    val recoveryCandidateStartedAt: Instant?,
) {
    companion object {
        val default: NotificationState =
            NotificationState(
                lastConditionStatus = null,
                lastDominantMetricKey = null,
                activeProblemStartedAt = null,
                lastNotificationAt = null,
                lastNotificationByKey = emptyMap(),
                lastSuccessfulReadAt = null,
                consecutiveFailureCount = 0,
                recoveryCandidateStartedAt = null,
            )
    }
}
