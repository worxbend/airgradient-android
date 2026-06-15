package dev.worxbend.airgradient.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.presentation.theme.AirGradientTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "AirGradient") },
            )
        },
    ) { paddingValues ->
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Configure a local device to start monitoring indoor air.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "--",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "AQI unavailable",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Device URL setup, live readings, trends, and alerts will be added soon.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlaceholderMetricCard(
                        label = "CO2",
                        value = "-- ppm",
                        modifier = Modifier.weight(1f),
                    )
                    PlaceholderMetricCard(
                        label = "PM2.5",
                        value = "-- ug/m3",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {},
                    modifier = Modifier.align(Alignment.Start),
                    enabled = false,
                ) {
                    Text(text = "Configure device")
                }
            }
        }
    }
}

@Composable
private fun PlaceholderMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DashboardScreenPreview() {
    AirGradientTheme {
        DashboardScreen()
    }
}

@Preview(showBackground = true, widthDp = 720)
@Composable
private fun DashboardScreenWidePreview() {
    AirGradientTheme {
        Row(modifier = Modifier.fillMaxWidth()) {
            DashboardScreen(modifier = Modifier.width(420.dp))
        }
    }
}
