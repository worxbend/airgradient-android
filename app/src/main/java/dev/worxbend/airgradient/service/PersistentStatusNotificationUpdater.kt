package dev.worxbend.airgradient.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.worxbend.airgradient.domain.monitoring.MonitoringStatus

class PersistentStatusNotificationUpdater(
    context: Context,
    private val notificationFactory: AirQualityMonitoringNotificationFactory =
        AirQualityMonitoringNotificationFactory(context),
    private val textFormatter: MonitoringStatusTextFormatter = MonitoringStatusTextFormatter(),
) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private var lastTitle: String? = null
    private var lastBody: String? = null

    fun create(status: MonitoringStatus) = notificationFactory.create(status)

    @SuppressLint("MissingPermission")
    fun update(status: MonitoringStatus) {
        if (!hasNotificationPermission()) return

        val title = textFormatter.title(status)
        val body = textFormatter.body(status)
        if (title == lastTitle && body == lastBody) return
        lastTitle = title
        lastBody = body

        notificationManager.notify(
            AirQualityMonitoringNotificationFactory.NOTIFICATION_ID,
            notificationFactory.create(status),
        )
    }

    fun cancel() {
        lastTitle = null
        lastBody = null
        notificationManager.cancel(AirQualityMonitoringNotificationFactory.NOTIFICATION_ID)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
