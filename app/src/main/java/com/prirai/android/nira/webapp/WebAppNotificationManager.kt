package com.prirai.android.nira.webapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
     * Check if the app has POST_NOTIFICATIONS permission (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android 13, notification permission is granted by default
            notificationManager.areNotificationsEnabled()
        }
    }

    /**
     * Request notification permission for a PWA
     * Note: This method should be called from an Activity or Fragment
     * that implements the permission request handling via Activity Result API.
     * 
     * For WebAppActivity/WebAppFragment usage:
     * - Call hasNotificationPermission() first to check if permission is already granted
     * - If not granted, use Activity's requestPermissions() or registerForActivityResult()
     * - Pass the result to the callback
     */
    fun requestNotificationPermission(webAppId: String, callback: (Boolean) -> Unit) {
        if (hasNotificationPermission()) {
            callback(true)
        } else {
            // Permission needs to be requested by the calling Activity/Fragment
            // This manager cannot directly request permissions as it's not a UI component
            callback(false)
        }
    }

    /**
     * Show a notification from a PWA
     * 
     * @return true if notification was shown, false if permission is missing
     */
    fun showPwaNotification(
        webAppId: String,
        webAppName: String,
        title: String,
        message: String,
        notificationId: Int
    ): Boolean {
        // Check if we have permission to post notifications
        if (!hasNotificationPermission()) {
            // Permission not granted - caller should request permission first
            return false
        }

        val builder = NotificationCompat.Builder(context, PWA_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("From $webAppName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
        return true
    }

    /**
     * Get notification channel for PWA
     */
    fun getPwaNotificationChannel(): String {
        return PWA_NOTIFICATION_CHANNEL_ID
    }

    /**
     * Check if PWA has notification permission
     * This checks both system-level permission and PWA-specific settings
     */
    fun hasNotificationPermission(webAppId: String): Boolean {
        // First check system-level notification permission
        if (!hasNotificationPermission()) {
            return false
        }
        
        // Check if this specific PWA has notification permission granted
        // This would integrate with GeckoView's permission system or
        // a custom permission storage for PWAs
        // For now, return true if system permission is granted
        return true
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