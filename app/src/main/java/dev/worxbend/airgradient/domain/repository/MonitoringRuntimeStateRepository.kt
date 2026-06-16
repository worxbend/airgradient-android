package dev.worxbend.airgradient.domain.repository

import dev.worxbend.airgradient.domain.monitoring.MonitoringRuntimeState
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface MonitoringRuntimeStateRepository {
    fun observeMonitoringRuntimeState(): Flow<MonitoringRuntimeState>

    suspend fun getMonitoringRuntimeState(): MonitoringRuntimeState

    suspend fun recordTickResult(result: MonitoringTickResult)

    suspend fun clearMonitoringRuntimeState()
}

object NoOpMonitoringRuntimeStateRepository : MonitoringRuntimeStateRepository {
    override fun observeMonitoringRuntimeState(): Flow<MonitoringRuntimeState> = flowOf(MonitoringRuntimeState.default)

    override suspend fun getMonitoringRuntimeState(): MonitoringRuntimeState = MonitoringRuntimeState.default

    override suspend fun recordTickResult(result: MonitoringTickResult) = Unit

    override suspend fun clearMonitoringRuntimeState() = Unit
}
