package com.prirai.android.nira.webapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.prirai.android.nira.R

/**
 * Manager for PWA notification channels and permissions
 * Handles notification setup, permission requests, and delivery
 */
class WebAppNotificationManager(private val context: Context) {
    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }

    companion object {
        private const val PWA_NOTIFICATION_CHANNEL_ID = "pwa_notifications"
        private const val PWA_NOTIFICATION_CHANNEL_NAME = "Web App Notifications"
    }

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels for PWAs
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PWA_NOTIFICATION_CHANNEL_ID,
                PWA_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from installed web apps"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Check if notifications are enabled for a specific PWA
     */
    fun areNotificationsEnabled(webAppId: String): Boolean {
        return notificationManager.areNotificationsEnabled()
    }

    /**
     * Request notification permission for a PWA
     */
    fun requestNotificationPermission(webAppId: String, callback: (Boolean) -> Unit) {
        // This would show a permission dialog to the user
        // For now, we'll just call the callback with true
        callback(true)
    }

    /**
     * Show a notification from a PWA
     */
    fun showPwaNotification(
        webAppId: String,
        webAppName: String,
        title: String,
        message: String,
        notificationId: Int
    ) {
        val builder = NotificationCompat.Builder(context, PWA_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("From $webAppName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Get notification channel for PWA
     */
    fun getPwaNotificationChannel(): String {
        return PWA_NOTIFICATION_CHANNEL_ID
    }

    /**
     * Check if PWA has notification permission
     */
    fun hasNotificationPermission(webAppId: String): Boolean {
        // Check if this specific PWA has notification permission
        // Would need to integrate with GeckoView's permission system
        return true // Placeholder
    }

    /**
     * Register PWA for push notifications
     */
    fun registerForPushNotifications(webAppId: String, pushEndpoint: String) {
        // This would register the PWA with push notification services
        // Would need Firebase or similar integration
    }

    /**
     * Unregister PWA from push notifications
     */
    fun unregisterFromPushNotifications(webAppId: String) {
        // This would unregister the PWA from push services
    }
}

/**
 * Data class representing PWA notification settings
 */
data class PwaNotificationSettings(
    val webAppId: String,
    val notificationsEnabled: Boolean,
    val showBadges: Boolean,
    val playSounds: Boolean,
    val vibrate: Boolean,
    val priority: Int
)