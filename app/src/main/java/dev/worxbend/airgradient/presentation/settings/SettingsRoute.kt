package dev.worxbend.airgradient.presentation.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state.value,
        onNavigateBack = onNavigateBack,
        actions =
            SettingsScreenActions(
                onDeviceUrlChanged = viewModel::onDeviceUrlChanged,
                onSaveDeviceUrl = viewModel::saveDeviceUrl,
                onTestConnection = viewModel::testConnection,
                onRefreshIntervalSelected = viewModel::onRefreshIntervalSelected,
                onNotificationsEnabledChanged = viewModel::onNotificationsEnabledChanged,
                onThemeModeSelected = viewModel::onThemeModeSelected,
            ),
    )
}
