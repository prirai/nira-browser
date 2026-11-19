package com.prirai.android.nira.privacy

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.privatemode.notification.AbstractPrivateNotificationService
import mozilla.components.support.base.android.NotificationsDelegate

/** Foreground notification shown while private tabs are open; tapping it erases them. */
class PrivateNotificationService : AbstractPrivateNotificationService() {
    override val store: BrowserStore by lazy { components.store }
    override val notificationsDelegate: NotificationsDelegate by lazy { components.notificationsDelegate }

    override fun NotificationCompat.Builder.buildNotification() {
        val erase = PendingIntent.getService(
            this@PrivateNotificationService,
            0,
            Intent(this@PrivateNotificationService, PrivateNotificationService::class.java).setAction(ACTION_ERASE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        this
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(this@PrivateNotificationService.getString(R.string.private_notification_title))
            .setContentText(this@PrivateNotificationService.getString(R.string.private_notification_text))
            .setContentIntent(erase)
    }

    override fun notifyLocaleChanged() = Unit

    override fun ignoreTaskActions(): List<String> = emptyList()

    override fun ignoreTaskComponentClasses(): List<String> = emptyList()
}
