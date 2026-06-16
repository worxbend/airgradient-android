package dev.worxbend.airgradient.domain.repository

import dev.worxbend.airgradient.domain.notifications.NotificationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface NotificationStateRepository {
    fun observeNotificationState(): Flow<NotificationState>

    suspend fun getNotificationState(): NotificationState

    suspend fun saveNotificationState(state: NotificationState)

    suspend fun updateNotificationState(transform: (NotificationState) -> NotificationState)

    suspend fun clearNotificationState()
}

object NoOpNotificationStateRepository : NotificationStateRepository {
    override fun observeNotificationState(): Flow<NotificationState> = flowOf(NotificationState.default)

    override suspend fun getNotificationState(): NotificationState = NotificationState.default

    override suspend fun saveNotificationState(state: NotificationState) = Unit

    override suspend fun updateNotificationState(transform: (NotificationState) -> NotificationState) = Unit

    override suspend fun clearNotificationState() = Unit
}
