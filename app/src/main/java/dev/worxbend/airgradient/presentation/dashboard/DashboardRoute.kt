package dev.worxbend.airgradient.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    onOpenSettings: () -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()

    DashboardScreen(
        state = state.value,
        onRefresh = viewModel::refresh,
        onOpenSettings = onOpenSettings,
        onConfigureDevice = onOpenSettings,
    )
}
