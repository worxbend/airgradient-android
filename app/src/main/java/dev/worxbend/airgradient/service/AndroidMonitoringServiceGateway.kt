package dev.worxbend.airgradient.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class AndroidMonitoringNotificationPermissionChecker(
    context: Context,
) : MonitoringNotificationPermissionChecker {
    private val appContext = context.applicationContext

    override val isNotificationPermissionRequired: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun canPostNotifications(): Boolean =
        !isNotificationPermissionRequired ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

class AndroidMonitoringServiceGateway(
    context: Context,
) : MonitoringServiceGateway {
    private val appContext = context.applicationContext

    override fun startForegroundMonitoring() {
        val intent = AirQualityMonitoringService.intent(appContext, AirQualityMonitoringService.ACTION_START)
        ContextCompat.startForegroundService(appContext, intent)
    }

    override fun stopForegroundMonitoring() {
        appContext.startService(AirQualityMonitoringService.intent(appContext, AirQualityMonitoringService.ACTION_STOP))
    }

    override fun refreshNow() {
        appContext.startService(
            AirQualityMonitoringService.intent(appContext, AirQualityMonitoringService.ACTION_REFRESH_NOW),
        )
    }
}
