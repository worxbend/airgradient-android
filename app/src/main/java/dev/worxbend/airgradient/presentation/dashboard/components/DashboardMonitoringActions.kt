package dev.worxbend.airgradient.presentation.dashboard.components

internal data class DashboardMonitoringActions(
    val onStartMonitoring: () -> Unit,
    val onStopMonitoring: () -> Unit,
)
