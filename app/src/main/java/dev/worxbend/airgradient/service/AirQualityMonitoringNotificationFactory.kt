package dev.worxbend.airgradient.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.worxbend.airgradient.R
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringStatus
import java.time.Duration

class AirQualityMonitoringNotificationFactory(
    context: Context,
) {
    private val appContext = context.applicationContext

    init {
        createNotificationChannel()
    }

    fun create(status: MonitoringStatus): Notification =
        NotificationCompat
            .Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(status.title())
            .setContentText(status.body())
            .setStyle(NotificationCompat.BigTextStyle().bigText(status.body()))
            .setContentIntent(openAppPendingIntent())
            .setOngoing(status.mode != MonitoringMode.Off)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(refreshAction())
            .addAction(stopAction())
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }

        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openAppPendingIntent(): PendingIntent? {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return null
        return PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_OPEN_APP,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshAction(): NotificationCompat.Action =
        NotificationCompat.Action
            .Builder(
                R.drawable.ic_launcher_foreground,
                "Refresh now",
                servicePendingIntent(AirQualityMonitoringService.ACTION_REFRESH_NOW, REQUEST_CODE_REFRESH_NOW),
            ).build()

    private fun stopAction(): NotificationCompat.Action =
        NotificationCompat.Action
            .Builder(
                R.drawable.ic_launcher_foreground,
                "Stop",
                servicePendingIntent(AirQualityMonitoringService.ACTION_STOP, REQUEST_CODE_STOP),
            ).build()

    private fun servicePendingIntent(
        action: String,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getService(
            appContext,
            requestCode,
            AirQualityMonitoringService.intent(appContext, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun MonitoringStatus.title(): String =
        when (this) {
            MonitoringStatus.Off -> "AirGradient monitoring off"
            is MonitoringStatus.Starting -> "Starting AirGradient monitoring"
            is MonitoringStatus.Active -> "AirGradient monitoring active"
            is MonitoringStatus.Stopped -> "AirGradient monitoring stopped"
        }

    private fun MonitoringStatus.body(): String =
        when (this) {
            MonitoringStatus.Off -> "Background checks are not running."
            is MonitoringStatus.Starting -> "Preparing local AirGradient checks."
            is MonitoringStatus.Active -> activeBody()
            is MonitoringStatus.Stopped -> "Stopped: ${reason.name}."
        }

    private fun MonitoringStatus.Active.activeBody(): String {
        val lastCheck =
            lastCheckedAt?.let { checkedAt -> "Last check $checkedAt." }
                ?: "Waiting for the first check."
        val lastSuccess =
            lastSuccessfulReadAt?.let { successAt -> " Last success $successAt." }.orEmpty()
        return "$lastCheck$lastSuccess Polling every ${pollingInterval.label()}."
    }

    private fun Duration.label(): String =
        if (seconds < SECONDS_PER_MINUTE) {
            "$seconds sec"
        } else {
            "${toMinutes()} min"
        }

    companion object {
        const val CHANNEL_ID = "air_quality_monitoring"
        const val NOTIFICATION_ID = 1_000

        private const val CHANNEL_NAME = "AirGradient monitoring"
        private const val CHANNEL_DESCRIPTION = "Persistent status while always-on AirGradient monitoring is active."
        private const val REQUEST_CODE_OPEN_APP = 20
        private const val REQUEST_CODE_REFRESH_NOW = 21
        private const val REQUEST_CODE_STOP = 22
        private const val SECONDS_PER_MINUTE = 60L
    }
}
