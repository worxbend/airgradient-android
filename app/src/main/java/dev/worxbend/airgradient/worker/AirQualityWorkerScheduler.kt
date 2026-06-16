package dev.worxbend.airgradient.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.worxbend.airgradient.service.PeriodicMonitoringScheduler
import java.time.Duration

class AirQualityWorkerScheduler(
    context: Context,
) : PeriodicMonitoringScheduler {
    private val appContext = context.applicationContext

    override fun schedulePeriodicMonitoring(interval: Duration) {
        val request =
            PeriodicWorkRequestBuilder<AirQualityCheckWorker>(interval)
                .setConstraints(networkConstraints())
                .build()

        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }

    override fun cancelPeriodicMonitoring() {
        WorkManager
            .getInstance(appContext)
            .cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun networkConstraints(): Constraints =
        Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    companion object {
        const val PERIODIC_WORK_NAME = "air_quality_periodic_monitoring"
    }
}
