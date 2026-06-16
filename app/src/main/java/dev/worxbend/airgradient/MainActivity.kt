package dev.worxbend.airgradient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dev.worxbend.airgradient.presentation.AppRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appGraph = (application as AirGradientApplication).appGraph
        lifecycleScope.launch {
            appGraph.monitoringStartupReconciler.reconcile()
        }

        setContent {
            AppRoot(appGraph = appGraph)
        }
    }
}
