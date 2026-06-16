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

@Composable
fun RefreshIntervalSelector(
    selectedSeconds: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Foreground dashboard refresh interval",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            REFRESH_INTERVAL_OPTIONS.forEach { seconds ->
                FilterChip(
                    selected = selectedSeconds == seconds,
                    onClick = { onSelected(seconds) },
                    label = { Text(text = seconds.toIntervalLabel()) },
                )
            }
        }
    }
}

private fun Int.toIntervalLabel(): String =
    if (this < SECONDS_PER_MINUTE) {
        "${this}s"
    } else {
        "${this / SECONDS_PER_MINUTE}m"
    }

private const val SECONDS_PER_MINUTE = 60
private val REFRESH_INTERVAL_OPTIONS = listOf(5, 10, 30, 60, 300)
