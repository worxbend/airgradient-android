package dev.worxbend.airgradient

import android.app.Application
import dev.worxbend.airgradient.app.AppGraph

class AirGradientApplication : Application() {
    val appGraph: AppGraph by lazy {
        AppGraph(this)
    }
}
