package dev.worxbend.airgradient.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.worxbend.airgradient.AirGradientApplication
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import kotlinx.coroutines.flow.first

class AirQualityCheckWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val appGraph = (applicationContext as AirGradientApplication).appGraph
        val appSettings = appGraph.settingsRepository.settings.first()
        val monitoringSettings = appGraph.monitoringSettingsRepository.getMonitoringSettings()

        when {
            monitoringSettings.mode != MonitoringMode.BatteryFriendlyPeriodic -> {
                appGraph.periodicMonitoringScheduler.cancelPeriodicMonitoring()
            }

            appSettings.serverUrl.isNullOrBlank() -> {
                appGraph.monitoringSettingsRepository.updateMonitoringMode(MonitoringMode.Off)
                appGraph.periodicMonitoringScheduler.cancelPeriodicMonitoring()
            }

            else -> {
                appGraph.monitoringLoopRunner().runOneTick(appSettings)
            }
        }

        return Result.success()
    }
}
