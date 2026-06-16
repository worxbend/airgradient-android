package dev.worxbend.airgradient.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.presentation.settings.ConnectionTestState
import dev.worxbend.airgradient.presentation.settings.DeviceUrlPreview
import dev.worxbend.airgradient.presentation.settings.DeviceUrlSaveState

@Composable
fun DeviceUrlInput(
    input: String,
    preview: DeviceUrlPreview,
    saveState: DeviceUrlSaveState,
    connectionTestState: ConnectionTestState,
    actions: DeviceUrlInputActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = actions.onInputChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Device URL") },
            singleLine = true,
            isError = preview is DeviceUrlPreview.Invalid || saveState is DeviceUrlSaveState.Invalid,
            supportingText = {
                Text(
                    text = deviceUrlSupportingText(preview, saveState),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = actions.onSave) {
                Text(text = "Save")
            }
            OutlinedButton(
                onClick = actions.onTestConnection,
                enabled = connectionTestState !is ConnectionTestState.Testing,
            ) {
                Text(text = "Test connection")
            }
        }
        ConnectionTestMessage(state = connectionTestState)
    }
}

data class DeviceUrlInputActions(
    val onInputChanged: (String) -> Unit,
    val onSave: () -> Unit,
    val onTestConnection: () -> Unit,
)

private fun deviceUrlSupportingText(
    preview: DeviceUrlPreview,
    saveState: DeviceUrlSaveState,
): String =
    when {
        saveState is DeviceUrlSaveState.Invalid -> "Use http or https with a host, for example 192.168.1.201."
        saveState is DeviceUrlSaveState.Saved && saveState.normalizedUrl == null -> "Device URL cleared."
        saveState is DeviceUrlSaveState.Saved -> "Saved ${saveState.normalizedUrl}."
        preview is DeviceUrlPreview.Valid -> "Will fetch ${preview.normalizedUrl}/measures/current."
        preview is DeviceUrlPreview.Invalid -> "Use http or https with a valid host."
        else -> "Enter the local AirGradient base URL or IP address."
    }

@Composable
private fun ConnectionTestMessage(state: ConnectionTestState) {
    when (state) {
        ConnectionTestState.Idle -> {
            Unit
        }

        ConnectionTestState.InvalidInput -> {
            Text(
                text = "Enter a valid device URL before testing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        ConnectionTestState.Testing -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text(
                    text = "Testing device endpoint...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is ConnectionTestState.Success -> {
            Text(
                text = "Connected to ${state.normalizedUrl}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        is ConnectionTestState.Failure -> {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
