package dev.worxbend.airgradient.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.presentation.dashboard.components.DashboardContent
import dev.worxbend.airgradient.presentation.dashboard.components.EmptyConfigurationPanel
import dev.worxbend.airgradient.presentation.dashboard.components.ErrorDashboard
import dev.worxbend.airgradient.presentation.dashboard.components.LoadingDashboard

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onConfigureDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardScaffold(
        state = state,
        onRefresh = onRefresh,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    ) {
        when (state) {
            DashboardUiState.Unconfigured -> {
                EmptyConfigurationPanel(onConfigureDevice = onConfigureDevice)
            }

            DashboardUiState.Loading -> {
                LoadingDashboard()
            }

            is DashboardUiState.Content -> {
                DashboardContent(
                    metrics = state.metrics,
                    overallStatus = state.overallStatus,
                    lastUpdatedLabel = state.lastUpdatedLabel,
                    fetchStatusLabel = state.fetchStatusLabel,
                    refreshIntervalSeconds = state.refreshIntervalSeconds,
                    isRefreshing = state.isRefreshing,
                )
            }

            is DashboardUiState.ContentWithWarning -> {
                DashboardContent(
                    metrics = state.metrics,
                    overallStatus = state.overallStatus,
                    lastUpdatedLabel = state.lastUpdatedLabel,
                    fetchStatusLabel = state.fetchStatusLabel,
                    refreshIntervalSeconds = state.refreshIntervalSeconds,
                    isRefreshing = state.isRefreshing,
                    warningMessage = state.warning.message,
                )
            }

            is DashboardUiState.Error -> {
                ErrorDashboard(
                    error = state.reason,
                    lastUpdatedLabel = state.lastUpdatedLabel,
                    metrics = state.metrics,
                    onRetry = onRefresh,
                    onConfigureDevice = onConfigureDevice,
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    DashboardScreen(
        state = DashboardUiState.Unconfigured,
        onRefresh = {},
        onOpenSettings = {},
        onConfigureDevice = {},
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScaffold(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val canRefresh = state.canRefresh()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "AirGradient",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.headerStatusLabel(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = canRefresh,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = REFRESH_ACTION_DESCRIPTION,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = SETTINGS_ACTION_DESCRIPTION,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )
        },
    ) { paddingValues ->
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(backgroundBrush()),
            ) {
                if (canRefresh) {
                    PullToRefreshBox(
                        isRefreshing = state.isPullRefreshing(),
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        content()
                    }
                } else {
                    content()
                }
            }
        }
    }
}

private fun DashboardUiState.headerStatusLabel(): String =
    when (this) {
        DashboardUiState.Unconfigured -> "Device not configured"
        DashboardUiState.Loading -> "Loading measurements"
        is DashboardUiState.Content -> overallStatus.label
        is DashboardUiState.ContentWithWarning -> warning.message
        is DashboardUiState.Error -> reason.title
    }

private fun DashboardUiState.canRefresh(): Boolean =
    when (this) {
        DashboardUiState.Unconfigured,
        DashboardUiState.Loading,
        -> false

        is DashboardUiState.Content,
        is DashboardUiState.ContentWithWarning,
        is DashboardUiState.Error,
        -> true
    }

private fun DashboardUiState.isPullRefreshing(): Boolean =
    when (this) {
        is DashboardUiState.Content -> isRefreshing

        is DashboardUiState.ContentWithWarning -> isRefreshing

        DashboardUiState.Unconfigured,
        DashboardUiState.Loading,
        is DashboardUiState.Error,
        -> false
    }

@Composable
private fun backgroundBrush(): Brush =
    Brush.verticalGradient(
        colors =
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
            ),
    )

internal const val REFRESH_ACTION_DESCRIPTION = "Refresh measurements"
internal const val SETTINGS_ACTION_DESCRIPTION = "Open settings"
