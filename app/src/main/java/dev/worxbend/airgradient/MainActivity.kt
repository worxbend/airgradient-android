package dev.worxbend.airgradient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.worxbend.airgradient.presentation.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appGraph = (application as AirGradientApplication).appGraph

        setContent {
            AppRoot(appGraph = appGraph)
        }
    }
}
