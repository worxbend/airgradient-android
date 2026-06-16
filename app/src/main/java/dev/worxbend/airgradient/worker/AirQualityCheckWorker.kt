package dev.worxbend.airgradient.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.worxbend.airgradient.AirGradientApplication

class AirQualityCheckWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val appGraph = (applicationContext as AirGradientApplication).appGraph
        appGraph.batteryFriendlyMonitoringCheckRunner().runCheck()
        return Result.success()
    }
}
