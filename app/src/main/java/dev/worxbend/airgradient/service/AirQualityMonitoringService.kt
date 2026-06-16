package dev.worxbend.airgradient.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import dev.worxbend.airgradient.AirGradientApplication
import dev.worxbend.airgradient.app.AppGraph
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import dev.worxbend.airgradient.domain.monitoring.MonitoringStatus
import dev.worxbend.airgradient.domain.monitoring.MonitoringStopReason
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

@Suppress("TooManyFunctions")
class AirQualityMonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var appGraph: AppGraph
    private lateinit var loopRunner: MonitoringLoopRunner
    private lateinit var statusNotificationUpdater: PersistentStatusNotificationUpdater
    private var monitoringJob: Job? = null
    private var foregroundStarted = false
    private var lastCheckedAt: Instant? = null
    private var lastSuccessfulReadAt: Instant? = null

    override fun onCreate() {
        super.onCreate()
        appGraph = (application as AirGradientApplication).appGraph
        loopRunner = appGraph.monitoringLoopRunner()
        statusNotificationUpdater = appGraph.persistentStatusNotificationUpdater()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                serviceScope.launch {
                    stopMonitoring(MonitoringStopReason.UserRequested)
                }
                return START_NOT_STICKY
            }

            ACTION_REFRESH_NOW -> {
                ensureForegroundStarted()
                refreshNow()
            }

            ACTION_START -> {
                ensureForegroundStarted()
                startMonitoringLoop()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitoringJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureForegroundStarted() {
        if (foregroundStarted) return

        val status =
            MonitoringStatus.Starting(
                mode = MonitoringMode.AlwaysOnForegroundService,
                startedAt = Instant.now(),
            )
        startForeground(
            AirQualityMonitoringNotificationFactory.NOTIFICATION_ID,
            statusNotificationUpdater.create(status),
        )
        foregroundStarted = true
    }

    private fun startMonitoringLoop() {
        if (monitoringJob?.isActive == true) return

        monitoringJob =
            serviceScope.launch {
                while (isActive) {
                    val shouldContinue = refreshOnceFromCurrentSettings()

                    if (!shouldContinue) {
                        break
                    }

                    val interval =
                        appGraph.monitoringSettingsRepository
                            .getMonitoringSettings()
                            .foregroundPollingInterval
                    delay(interval.toMillis())
                }
            }
    }

    private fun refreshNow() {
        serviceScope.launch {
            refreshOnceFromCurrentSettings()
        }
    }

    private suspend fun refreshOnceFromCurrentSettings(): Boolean {
        val appSettings = appGraph.settingsRepository.settings.first()
        val monitoringSettings = appGraph.monitoringSettingsRepository.getMonitoringSettings()
        val stopReason =
            when {
                monitoringSettings.mode != MonitoringMode.AlwaysOnForegroundService -> {
                    MonitoringStopReason.UserRequested
                }

                appSettings.serverUrl.isNullOrBlank() -> {
                    MonitoringStopReason.MissingDeviceUrl
                }

                else -> {
                    null
                }
            }

        return if (stopReason == null) {
            runConfiguredRefresh(appSettings, monitoringSettings)
            true
        } else {
            stopMonitoring(stopReason)
            false
        }
    }

    private suspend fun runConfiguredRefresh(
        appSettings: AppSettings,
        monitoringSettings: MonitoringSettings,
    ) {
        updateActiveStatus(monitoringSettings)
        when (val result = loopRunner.runOneTick(appSettings)) {
            is MonitoringTickResult.Success -> {
                lastCheckedAt = result.checkedAt
                lastSuccessfulReadAt = result.snapshot.measuredAt
            }

            is MonitoringTickResult.Failure -> {
                lastCheckedAt = result.checkedAt
            }

            is MonitoringTickResult.Skipped -> {
                lastCheckedAt = result.checkedAt
            }
        }
        updateActiveStatus(monitoringSettings)
    }

    private fun updateActiveStatus(monitoringSettings: MonitoringSettings) {
        statusNotificationUpdater.update(
            MonitoringStatus.Active(
                mode = MonitoringMode.AlwaysOnForegroundService,
                pollingInterval = monitoringSettings.foregroundPollingInterval,
                lastCheckedAt = lastCheckedAt,
                lastSuccessfulReadAt = lastSuccessfulReadAt,
            ),
        )
    }

    private suspend fun stopMonitoring(reason: MonitoringStopReason) {
        monitoringJob?.cancel()
        monitoringJob = null
        appGraph.monitoringSettingsRepository.updateMonitoringMode(MonitoringMode.Off)
        statusNotificationUpdater.update(
            MonitoringStatus.Stopped(
                reason = reason,
                stoppedAt = Instant.now(),
            ),
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        statusNotificationUpdater.cancel()
        stopSelf()
    }

    companion object {
        const val ACTION_START = "dev.worxbend.airgradient.action.START_MONITORING"
        const val ACTION_REFRESH_NOW = "dev.worxbend.airgradient.action.REFRESH_MONITORING_NOW"
        const val ACTION_STOP = "dev.worxbend.airgradient.action.STOP_MONITORING"

        fun intent(
            context: Context,
            action: String,
        ): Intent =
            Intent(context, AirQualityMonitoringService::class.java).apply {
                this.action = action
            }
    }
}
