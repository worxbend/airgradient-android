package dev.worxbend.airgradient.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.presentation.settings.components.DeviceUrlInput
import dev.worxbend.airgradient.presentation.settings.components.DeviceUrlInputActions
import dev.worxbend.airgradient.presentation.settings.components.RefreshIntervalSelector
import dev.worxbend.airgradient.presentation.settings.components.ThemeSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onNavigateBack: () -> Unit,
    actions: SettingsScreenActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(text = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )
        },
    ) { paddingValues ->
        SettingsContent(
            state = state,
            actions = actions,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(settingsBackgroundBrush())
                    .padding(paddingValues),
        )
    }
}

data class SettingsScreenActions(
    val onDeviceUrlChanged: (String) -> Unit,
    val onSaveDeviceUrl: () -> Unit,
    val onTestConnection: () -> Unit,
    val onRefreshIntervalSelected: (Int) -> Unit,
    val onNotificationsEnabledChanged: (Boolean) -> Unit,
    val onThemeModeSelected: (AppThemeMode) -> Unit,
)

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsScreenActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection(title = "Device") {
                DeviceUrlInput(
                    input = state.deviceUrlInput,
                    preview = state.deviceUrlPreview,
                    saveState = state.saveState,
                    connectionTestState = state.connectionTestState,
                    actions =
                        DeviceUrlInputActions(
                            onInputChanged = actions.onDeviceUrlChanged,
                            onSave = actions.onSaveDeviceUrl,
                            onTestConnection = actions.onTestConnection,
                        ),
                )
            }
        }
        item {
            SettingsSection(title = "Refresh") {
                RefreshIntervalSelector(
                    selectedSeconds = state.refreshIntervalSeconds,
                    onSelected = actions.onRefreshIntervalSelected,
                )
            }
        }
        item {
            SettingsSection(title = "Notifications") {
                NotificationsRow(
                    enabled = state.notificationsEnabled,
                    onEnabledChanged = actions.onNotificationsEnabledChanged,
                )
            }
        }
        item {
            SettingsSection(title = "Appearance") {
                ThemeSelector(
                    selectedThemeMode = state.themeMode,
                    onSelected = actions.onThemeModeSelected,
                )
            }
        }
        item {
            AboutSection()
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun NotificationsRow(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Air quality alerts",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Notify when readings stay degraded. Disabled by default.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged,
        )
    }
}

@Composable
private fun AboutSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "This app reads AirGradient-compatible local devices at /measures/current.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistChip(
                onClick = {},
                label = { Text(text = "Data stays on this device") },
            )
        }
    }
}

@Composable
private fun settingsBackgroundBrush(): Brush =
    Brush.verticalGradient(
        colors =
            listOf(
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.07f),
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
            ),
    )
