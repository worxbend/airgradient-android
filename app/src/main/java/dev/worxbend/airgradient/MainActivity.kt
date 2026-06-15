package dev.worxbend.airgradient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.worxbend.airgradient.presentation.dashboard.DashboardScreen
import dev.worxbend.airgradient.presentation.theme.AirGradientTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AirGradientTheme {
                DashboardScreen()
            }
        }
    }
}
