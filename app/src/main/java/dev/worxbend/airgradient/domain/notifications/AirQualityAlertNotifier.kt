package dev.worxbend.airgradient.domain.notifications

interface AirQualityAlertNotifier {
    fun showAlert(alert: AirQualityAlert)
}

object NoOpAirQualityAlertNotifier : AirQualityAlertNotifier {
    override fun showAlert(alert: AirQualityAlert) = Unit
}

interface NotificationMessageDispatcher {
    fun show(message: NotificationMessage)
}

object NoOpNotificationMessageDispatcher : NotificationMessageDispatcher {
    override fun show(message: NotificationMessage) = Unit
}
