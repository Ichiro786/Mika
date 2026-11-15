package com.wangzan101.mika.whatsapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.R
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.settings.FeatureFlags
import com.wangzan101.mika.task.TaskService

/** Keeps the linked-device connection open while WhatsApp is on; tasks still run in TaskService. */
class WhatsappService : Service() {
    private val runtime get() = (application as MikaApp).whatsapp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TaskService.ensureChannels(this)
        ensureQuietChannel()
        if (intent?.action == ACTION_TURN_OFF && !FeatureFlags.get(this).quitRequested()) runtime.setEnabled(false)
        val foreground = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(NOTIFICATION_ID, notification())
        }.onFailure { Log.w("OrbitWhatsapp", "WhatsApp service could not enter the foreground", it) }.isSuccess
        if (!foreground || FeatureFlags.get(this).quitRequested() || !runtime.sync()) {
            runtime.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runtime.stop()
        super.onDestroy()
    }

    /**
     * Android needs a notice for a connection that stays open in the background. It goes to a channel that is off from
     * the start, so nothing shows in the shade or status bar; the connection still runs and the toggle in Settings
     * turns it off.
     */
    private fun ensureQuietChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_LINK) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_LINK, tr(R.string.whatsapp_title), NotificationManager.IMPORTANCE_NONE))
        }
    }

    private fun notification(): Notification =
        Notification.Builder(this, CHANNEL_LINK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr(R.string.whatsapp_notif_title))
            .setContentText(tr(R.string.whatsapp_notif_body))
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(TaskService.openApp(this))
            .build()

    companion object {
        const val ACTION_TURN_OFF = "com.wangzan101.mika.whatsapp.TURN_OFF"
        private const val NOTIFICATION_ID = 30_002
        private const val CHANNEL_LINK = "orbit_whatsapp_link"
    }
}
