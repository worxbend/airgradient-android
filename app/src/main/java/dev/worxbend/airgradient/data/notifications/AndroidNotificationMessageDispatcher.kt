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
import dev.worxbend.airgradient.domain.notifications.NotificationMessage
import dev.worxbend.airgradient.domain.notifications.NotificationMessageDispatcher
import dev.worxbend.airgradient.domain.notifications.NotificationSeverity
import dev.worxbend.airgradient.domain.notifications.NotificationType

class AndroidNotificationMessageDispatcher(
    context: Context,
) : NotificationMessageDispatcher {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    init {
        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    override fun show(message: NotificationMessage) {
        if (!hasNotificationPermission()) return

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
        val notification =
            builder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(message.title)
                .setContentText(message.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
                .setContentIntent(launchPendingIntent())
                .setAutoCancel(true)
                .setPriority(message.severity.notificationPriority())
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()

        notificationManager.notify(message.notificationId, notification)
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

    private val NotificationMessage.notificationId: Int
        get() = type.notificationIdBase + Math.floorMod(key.hashCode(), NOTIFICATION_ID_KEY_SPACE)

    private val NotificationType.notificationIdBase: Int
        get() =
            when (this) {
                NotificationType.AirQualityDegraded -> AIR_QUALITY_DEGRADED_ID_BASE
                NotificationType.AirQualityCritical -> AIR_QUALITY_CRITICAL_ID_BASE
                NotificationType.AirQualityPersistent -> AIR_QUALITY_PERSISTENT_ID_BASE
                NotificationType.AirQualityRecovered -> AIR_QUALITY_RECOVERED_ID_BASE
                NotificationType.DeviceUnreachable -> DEVICE_UNREACHABLE_ID_BASE
                NotificationType.StaleData -> STALE_DATA_ID_BASE
            }

    private fun NotificationSeverity.notificationPriority(): Int =
        when (this) {
            NotificationSeverity.Warning -> NotificationCompat.PRIORITY_HIGH
            NotificationSeverity.Critical -> NotificationCompat.PRIORITY_HIGH
        }

    private companion object {
        const val CHANNEL_ID = "air_quality_alerts"
        const val CHANNEL_NAME = "Air quality alerts"
        const val CHANNEL_DESCRIPTION = "Notifications for degraded AirGradient readings."
        const val REQUEST_CODE_OPEN_APP = 10
        const val NOTIFICATION_ID_KEY_SPACE = 100
        const val AIR_QUALITY_DEGRADED_ID_BASE = 2_000
        const val AIR_QUALITY_CRITICAL_ID_BASE = 2_100
        const val AIR_QUALITY_PERSISTENT_ID_BASE = 2_200
        const val AIR_QUALITY_RECOVERED_ID_BASE = 2_300
        const val DEVICE_UNREACHABLE_ID_BASE = 2_400
        const val STALE_DATA_ID_BASE = 2_500
    }
}
