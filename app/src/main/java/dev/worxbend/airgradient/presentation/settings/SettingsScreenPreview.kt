package dev.worxbend.airgradient.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.presentation.theme.AirGradientTheme

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SettingsScreenPreview() {
    AirGradientTheme(dynamicColor = false) {
        SettingsScreen(
            state =
                SettingsUiState(
                    deviceUrlInput = "192.168.1.201",
                    deviceUrlPreview = DeviceUrlPreview.Valid("http://192.168.1.201"),
                    refreshIntervalSeconds = 30,
                    notificationsEnabled = false,
                    themeMode = AppThemeMode.SYSTEM,
                ),
            onNavigateBack = {},
            actions =
                SettingsScreenActions(
                    onDeviceUrlChanged = {},
                    onSaveDeviceUrl = {},
                    onTestConnection = {},
                    onRefreshIntervalSelected = {},
                    onNotificationsEnabledChanged = {},
                    onThemeModeSelected = {},
                ),
        )
    }
}
