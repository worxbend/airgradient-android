package dev.worxbend.airgradient.data.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.domain.notifications.NotificationState
import dev.worxbend.airgradient.domain.repository.NotificationStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant

@Suppress("TooManyFunctions")
class NotificationStateRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : NotificationStateRepository {
    private val preferences =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }

    override fun observeNotificationState(): Flow<NotificationState> =
        preferences.map { values -> values[NOTIFICATION_STATE]?.decodeState() ?: NotificationState.default }

    override suspend fun getNotificationState(): NotificationState = observeNotificationState().first()

    override suspend fun saveNotificationState(state: NotificationState) {
        dataStore.edit { values ->
            values[NOTIFICATION_STATE] = json.encodeToString(StoredNotificationState.serializer(), state.toStored())
        }
    }

    override suspend fun updateNotificationState(transform: (NotificationState) -> NotificationState) {
        dataStore.edit { values ->
            val currentState = values[NOTIFICATION_STATE]?.decodeState() ?: NotificationState.default
            values[NOTIFICATION_STATE] =
                json.encodeToString(StoredNotificationState.serializer(), transform(currentState).toStored())
        }
    }

    override suspend fun clearNotificationState() {
        dataStore.edit { values ->
            values.remove(NOTIFICATION_STATE)
        }
    }

    private fun String.decodeState(): NotificationState =
        runCatching {
            json.decodeFromString(StoredNotificationState.serializer(), this).toDomain()
        }.getOrDefault(NotificationState.default)

    private fun NotificationState.toStored(): StoredNotificationState =
        StoredNotificationState(
            lastConditionStatus = lastConditionStatus?.name,
            lastDominantMetricKey = lastDominantMetricKey,
            activeProblemStartedAt = activeProblemStartedAt?.toString(),
            lastNotificationAt = lastNotificationAt?.toString(),
            lastNotificationByKey = lastNotificationByKey.mapValues { (_, value) -> value.toString() },
            lastSuccessfulReadAt = lastSuccessfulReadAt?.toString(),
            consecutiveFailureCount = consecutiveFailureCount,
            recoveryCandidateStartedAt = recoveryCandidateStartedAt?.toString(),
        )

    private fun StoredNotificationState.toDomain(): NotificationState =
        NotificationState(
            lastConditionStatus = lastConditionStatus.toSensorStatusOrNull(),
            lastDominantMetricKey = lastDominantMetricKey,
            activeProblemStartedAt = activeProblemStartedAt.toInstantOrNull(),
            lastNotificationAt = lastNotificationAt.toInstantOrNull(),
            lastNotificationByKey = lastNotificationByKey.mapNotNullValues { value -> value.toInstantOrNull() },
            lastSuccessfulReadAt = lastSuccessfulReadAt.toInstantOrNull(),
            consecutiveFailureCount = consecutiveFailureCount.coerceAtLeast(0),
            recoveryCandidateStartedAt = recoveryCandidateStartedAt.toInstantOrNull(),
        )

    private fun String?.toSensorStatusOrNull(): SensorStatus? =
        this?.let { storedValue -> SensorStatus.entries.firstOrNull { it.name == storedValue } }

    private fun String?.toInstantOrNull(): Instant? =
        this?.let { value ->
            runCatching { Instant.parse(value) }.getOrNull()
        }

    private fun <K, V, R : Any> Map<K, V>.mapNotNullValues(transform: (V) -> R?): Map<K, R> =
        mapNotNull { (key, value) ->
            transform(value)?.let { mappedValue -> key to mappedValue }
        }.toMap()

    @Serializable
    private data class StoredNotificationState(
        val lastConditionStatus: String? = null,
        val lastDominantMetricKey: String? = null,
        val activeProblemStartedAt: String? = null,
        val lastNotificationAt: String? = null,
        val lastNotificationByKey: Map<String, String> = emptyMap(),
        val lastSuccessfulReadAt: String? = null,
        val consecutiveFailureCount: Int = 0,
        val recoveryCandidateStartedAt: String? = null,
    )

    private companion object {
        val NOTIFICATION_STATE: Preferences.Key<String> = stringPreferencesKey("notification_state_v1")
    }
}
