package com.example.flutter_sip_ua

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Foreground service that keeps the SIP registration alive while the app is
 * backgrounded.
 *
 * While a foreground service is running, Android keeps the process at
 * foreground priority and does not kill it (Doze is also deferred). The main
 * Dart isolate — where the SIP user agent lives — keeps executing its timers
 * and sockets for as long as the process is alive, so the existing REGISTER
 * refresh and inbound INVITE handling keep working with no separate background
 * isolate. The persistent low-priority notification here is exactly what
 * Android's foreground-service contract requires.
 *
 * Dart drives this service over the `xtendphone/foreground_service`
 * MethodChannel (see [MainActivity]):
 *   * [ACTION_START] — begin the persistent "Connected as <ext>" notification.
 *   * [SipForegroundService.stop] — tear the service down (sign-out /
 *     unregistered).
 */
class SipForegroundService : Service() {

    companion object {
        const val CHANNEL_CONNECTED = "sip_connected"
        const val CHANNEL_INCOMING = "sip_incoming"

        const val NOTIF_ID_CONNECTED = 1001
        const val NOTIF_ID_INCOMING = 1002

        const val ACTION_START = "com.example.flutter_sip_ua.action.START"

        const val EXTRA_EXTENSION = "extension"
        const val EXTRA_SERVER = "server"

        @Volatile
        private var isRunning = false

        /** Request the foreground service be started with the given account. */
        fun start(context: Context, extension: String, server: String) {
            val intent = Intent(context, SipForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_EXTENSION, extension)
                .putExtra(EXTRA_SERVER, server)
            startServiceCompat(context, intent)
        }

        /**
         * Best-effort stop. Safe to call when the service is not running.
         * Uses [Context.stopService] rather than a start intent so we never
         * trigger the O+ "startForeground within 5s" requirement.
         */
        fun stop(context: Context) {
            if (!isRunning) return
            context.stopService(Intent(context, SipForegroundService::class.java))
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // System-initiated restart after the process was killed: there is no
        // live Dart isolate to drive us, so shut down rather than advertise a
        // stale connection. The app re-registers (and restarts us) on next run.
        if (intent == null) {
            stopForegroundCompat()
            stopSelf()
            isRunning = false
            return START_NOT_STICKY
        }
        val extension = intent.getStringExtra(EXTRA_EXTENSION) ?: "unknown"
        val server = intent.getStringExtra(EXTRA_SERVER) ?: ""
        startForegroundCompat(extension, server)
        isRunning = true
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Without engine survival the Dart isolate dies when the task is
        // swiped away, so the registration can't stay alive — stop truthfully
        // instead of leaving a stale "Connected" notification behind.
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        stopForegroundCompat()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun startForegroundCompat(extension: String, server: String) {
        startForeground(
            NOTIF_ID_CONNECTED,
            buildConnectedNotification(extension, server),
        )
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        stopForeground(true)
    }

    private fun buildConnectedNotification(extension: String, server: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingIntentFlags(),
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_CONNECTED)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }
        return builder
            .setSmallIcon(R.drawable.ic_stat_sip)
            .setContentTitle("Connected as $extension")
            .setContentText(server)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .build()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONNECTED,
                "SIP connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows that your extension is connected to the SIP server"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INCOMING,
                "Incoming calls",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts for incoming SIP calls"
                setShowBadge(false)
            },
        )
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
