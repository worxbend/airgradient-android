package dev.worxbend.airgradient.presentation.dashboard

import dev.worxbend.airgradient.domain.notifications.NotificationDecisionEngine
import dev.worxbend.airgradient.domain.notifications.NotificationMessageDispatcher
import dev.worxbend.airgradient.domain.repository.NotificationStateRepository

data class DashboardNotificationDependencies(
    val notificationStateRepository: NotificationStateRepository,
    val notificationDecisionEngine: NotificationDecisionEngine,
    val notificationMessageDispatcher: NotificationMessageDispatcher,
)
