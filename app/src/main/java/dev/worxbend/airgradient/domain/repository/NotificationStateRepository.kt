package dev.worxbend.airgradient.domain.repository

import dev.worxbend.airgradient.domain.notifications.NotificationState
import kotlinx.coroutines.flow.Flow

interface NotificationStateRepository {
    fun observeNotificationState(): Flow<NotificationState>

    suspend fun getNotificationState(): NotificationState

    suspend fun saveNotificationState(state: NotificationState)

    suspend fun updateNotificationState(transform: (NotificationState) -> NotificationState)

    suspend fun clearNotificationState()
}
