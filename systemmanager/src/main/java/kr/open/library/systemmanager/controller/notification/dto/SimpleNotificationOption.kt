package kr.open.library.systemmanager.controller.notification.dto

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat.Action

public data class SimpleNotificationOption(
    public val notificationId: Int,
    public val title: String? = null,
    public val content: String? = null,
    public val isAutoCancel: Boolean = false,
    public val smallIcon: Int? = null,
    public val largeIcon: Bitmap? = null,
    public val clickIntent: Intent? = null,
    public val snippet: String? = null,
    public val actions: List<Action>? = null,
    public val onGoing: Boolean = false
)

public data class SimpleProgressNotificationOption(
    public val notificationId: Int,
    public val title: String? = null,
    public val content: String? = null,
    public val isAutoCancel: Boolean = false,
    public val smallIcon: Int? = null,
    public val clickIntent: Intent? = null,
    public val actions: List<Action>? = null,
    public val progressPercent: Int,
    public val onGoing: Boolean = false
)

public data class SimplePendingIntentOption(
    public val actionId: Int,
    public val clickIntent: Intent,
    public val flags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)