package dev.worxbend.airgradient.data.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.worxbend.airgradient.R
import dev.worxbend.airgradient.domain.notifications.AirQualityAlert
import dev.worxbend.airgradient.domain.notifications.AirQualityAlertNotifier
import dev.worxbend.airgradient.domain.notifications.AirQualityAlertSeverity

class AndroidAirQualityAlertNotifier(
    context: Context,
) : AirQualityAlertNotifier {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    init {
        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    override fun showAlert(alert: AirQualityAlert) {
        if (!hasNotificationPermission()) return

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
        val notification =
            builder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(alert.title)
                .setContentText(alert.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
                .setContentIntent(launchPendingIntent())
                .setAutoCancel(true)
                .setPriority(alert.severity.notificationPriority())
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()

        notificationManager.notify(alert.kind.notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = CHANNEL_DESCRIPTION
            }

        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun launchPendingIntent(): PendingIntent? {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return null
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(appContext, REQUEST_CODE_OPEN_APP, launchIntent, flags)
    }

    private fun AirQualityAlertSeverity.notificationPriority(): Int =
        when (this) {
            AirQualityAlertSeverity.NOTICE -> NotificationCompat.PRIORITY_DEFAULT
            AirQualityAlertSeverity.WARNING -> NotificationCompat.PRIORITY_HIGH
            AirQualityAlertSeverity.CRITICAL -> NotificationCompat.PRIORITY_HIGH
        }

    private companion object {
        const val CHANNEL_ID = "air_quality_alerts"
        const val CHANNEL_NAME = "Air quality alerts"
        const val CHANNEL_DESCRIPTION = "Notifications for degraded AirGradient readings."
        const val REQUEST_CODE_OPEN_APP = 10
    }
}
