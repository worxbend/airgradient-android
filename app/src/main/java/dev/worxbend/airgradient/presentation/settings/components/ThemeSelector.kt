package dev.worxbend.airgradient.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.domain.model.AppThemeMode

@Composable
fun ThemeSelector(
    selectedThemeMode: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Theme changes apply immediately.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeMode.entries.forEach { themeMode ->
                FilterChip(
                    selected = selectedThemeMode == themeMode,
                    onClick = { onSelected(themeMode) },
                    label = { Text(text = themeMode.label) },
                )
            }
        }
    }
}

private val AppThemeMode.label: String
    get() =
        when (this) {
            AppThemeMode.SYSTEM -> "System"
            AppThemeMode.LIGHT -> "Light"
            AppThemeMode.DARK -> "Dark"
        }
