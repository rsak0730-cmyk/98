package com.custom.dynamicislandos

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class IslandNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.notification?.extras?.let { extras ->
            if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
                return
            }

            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            
            if (title.isNotEmpty() || text.isNotEmpty()) {
                val intent = Intent("com.custom.dynamicislandos.NOTIFICATION")
                intent.putExtra("title", title)
                intent.putExtra("text", text)
                sendBroadcast(intent)
            }
        }
    }
}
