package dev.worxbend.airgradient.domain.notifications

interface NotificationMessageDispatcher {
    fun show(message: NotificationMessage)
}

object NoOpNotificationMessageDispatcher : NotificationMessageDispatcher {
    override fun show(message: NotificationMessage) = Unit
}
