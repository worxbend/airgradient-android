package dev.worxbend.airgradient.data.monitoring

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.worxbend.airgradient.domain.monitoring.MonitoringRuntimeState
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import dev.worxbend.airgradient.domain.repository.MonitoringRuntimeStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException

class MonitoringRuntimeStateRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : MonitoringRuntimeStateRepository {
    private val state =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }.map(::mapPreferences)

    override fun observeMonitoringRuntimeState(): Flow<MonitoringRuntimeState> = state

    override suspend fun getMonitoringRuntimeState(): MonitoringRuntimeState = state.first()

    override suspend fun recordTickResult(result: MonitoringTickResult) {
        when (result) {
            is MonitoringTickResult.Success -> saveSuccess(result)
            is MonitoringTickResult.Failure -> saveFailure(result)
            is MonitoringTickResult.Skipped -> Unit
        }
    }

    override suspend fun clearMonitoringRuntimeState() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private suspend fun saveSuccess(result: MonitoringTickResult.Success) {
        dataStore.edit { preferences ->
            preferences[LAST_CHECKED_AT] = result.checkedAt.toString()
            preferences[LAST_SUCCESSFUL_CHECK_AT] = result.checkedAt.toString()
            preferences[LAST_SUCCESSFUL_MEASUREMENT_AT] = result.snapshot.measuredAt.toString()
            preferences.remove(LAST_FAILURE_AT)
            preferences[CONSECUTIVE_FAILURE_COUNT] = 0
        }
    }

    private suspend fun saveFailure(result: MonitoringTickResult.Failure) {
        dataStore.edit { preferences ->
            preferences[LAST_CHECKED_AT] = result.checkedAt.toString()
            preferences[LAST_FAILURE_AT] = result.checkedAt.toString()
            preferences[CONSECUTIVE_FAILURE_COUNT] = result.consecutiveFailureCount
        }
    }

    private fun mapPreferences(preferences: Preferences): MonitoringRuntimeState =
        MonitoringRuntimeState(
            lastCheckedAt = preferences[LAST_CHECKED_AT].toInstantOrNull(),
            lastSuccessfulCheckAt = preferences[LAST_SUCCESSFUL_CHECK_AT].toInstantOrNull(),
            lastSuccessfulMeasurementAt = preferences[LAST_SUCCESSFUL_MEASUREMENT_AT].toInstantOrNull(),
            lastFailureAt = preferences[LAST_FAILURE_AT].toInstantOrNull(),
            consecutiveFailureCount = preferences[CONSECUTIVE_FAILURE_COUNT] ?: 0,
        )

    private fun String?.toInstantOrNull(): Instant? =
        this?.let { value ->
            try {
                Instant.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
        }

    private companion object {
        val LAST_CHECKED_AT: Preferences.Key<String> = stringPreferencesKey("last_checked_at")
        val LAST_SUCCESSFUL_CHECK_AT: Preferences.Key<String> =
            stringPreferencesKey("last_successful_check_at")
        val LAST_SUCCESSFUL_MEASUREMENT_AT: Preferences.Key<String> =
            stringPreferencesKey("last_successful_measurement_at")
        val LAST_FAILURE_AT: Preferences.Key<String> = stringPreferencesKey("last_failure_at")
        val CONSECUTIVE_FAILURE_COUNT: Preferences.Key<Int> = intPreferencesKey("consecutive_failure_count")
    }
}
