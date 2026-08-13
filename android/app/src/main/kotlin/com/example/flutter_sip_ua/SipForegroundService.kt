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
 * MethodChannel (see [SipForegroundServiceChannel]):
 *   * [ACTION_START] — begin the persistent "Connected as <ext>" notification.
 *   * [SipForegroundService.stop] — tear the service down (sign-out /
 *     unregistered).
 *   * [SipForegroundService.showIncomingCall] — raise the full-screen
 *     incoming-call notification (Accept/Decline actions).
 *   * [ACTION_DECLINE] — the Decline button on that notification; forwarded to
 *     Dart so the UA rejects the INVITE.
 */
class SipForegroundService : Service() {

    companion object {
        const val CHANNEL_CONNECTED = "sip_connected"
        const val CHANNEL_INCOMING = "sip_incoming"

        const val NOTIF_ID_CONNECTED = 1001
        const val NOTIF_ID_INCOMING = 1002

        const val ACTION_START = "com.example.flutter_sip_ua.action.START"
        const val ACTION_ANSWER = "com.example.flutter_sip_ua.action.ANSWER"
        const val ACTION_DECLINE = "com.example.flutter_sip_ua.action.DECLINE"

        const val EXTRA_EXTENSION = "extension"
        const val EXTRA_SERVER = "server"
        const val EXTRA_CALL_ID = "callId"

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

        /**
         * Raise the high-importance incoming-call notification: wakes the
         * screen via a full-screen intent and offers Accept / Decline actions.
         * The Accept action opens [MainActivity] (whose intent is forwarded to
         * Dart by [SipForegroundServiceChannel.handleCallActionIntent]); the
         * Decline action is handled here in [onStartCommand]. Works whether or
         * not the service itself is running.
         */
        fun showIncomingCall(context: Context, callId: String, caller: String) {
            ensureChannels(context)
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIF_ID_INCOMING, buildIncomingCallNotification(context, callId, caller))
        }

        /** Dismiss the incoming-call notification (answered / declined / cancelled). */
        fun hideIncomingCall(context: Context) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIF_ID_INCOMING)
        }

        private fun buildIncomingCallNotification(
            context: Context,
            callId: String,
            caller: String,
        ): Notification {
            val openApp = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val fullScreen = PendingIntent.getActivity(
                context,
                1,
                openApp,
                pendingIntentFlags() or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val answer = PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_ANSWER)
                    .putExtra(EXTRA_CALL_ID, callId),
                pendingIntentFlags() or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val decline = PendingIntent.getService(
                context,
                3,
                Intent(context, SipForegroundService::class.java)
                    .setAction(ACTION_DECLINE)
                    .putExtra(EXTRA_CALL_ID, callId),
                pendingIntentFlags() or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_INCOMING)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH)
            }
            return builder
                .setSmallIcon(R.drawable.ic_stat_sip)
                .setContentTitle("Incoming call")
                .setContentText(caller)
                .setCategory(Notification.CATEGORY_CALL)
                .setAutoCancel(true)
                .setFullScreenIntent(fullScreen, true)
                .addAction(R.drawable.ic_stat_sip, "Answer", answer)
                .addAction(R.drawable.ic_stat_sip, "Decline", decline)
                .build()
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }

        private fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
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
        return when (intent.action) {
            ACTION_START -> {
                val extension = intent.getStringExtra(EXTRA_EXTENSION) ?: "unknown"
                val server = intent.getStringExtra(EXTRA_SERVER) ?: ""
                startForegroundCompat(extension, server)
                isRunning = true
                START_STICKY
            }
            // Notification action (Decline; Answer is routed via MainActivity
            // as a getActivity PendingIntent). Forward to Dart so the UA
            // rejects the INVITE (and the notification is dismissed).
            ACTION_ANSWER, ACTION_DECLINE -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
                SipForegroundServiceChannel.queueAction(
                    if (intent.action == ACTION_ANSWER) "answer" else "decline",
                    callId,
                )
                START_STICKY
            }
            else -> {
                stopSelf()
                START_NOT_STICKY
            }
        }
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
}
