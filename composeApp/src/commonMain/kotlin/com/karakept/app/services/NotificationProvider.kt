package com.karakept.app.services

enum class NotificationType { DIGEST, LIST_UPDATE }

interface NotificationProvider {
    fun canSendNotifications(): Boolean
    fun sendNotification(title: String, message: String, type: NotificationType)
}
