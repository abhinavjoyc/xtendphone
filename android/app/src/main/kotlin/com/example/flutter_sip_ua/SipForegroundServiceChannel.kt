package com.example.flutter_sip_ua

import android.content.Context
import android.content.Intent
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * Single bridge for the `xtendphone/foreground_service` MethodChannel.
 *
 * Bound once when the Application-owned Flutter engine is created (see
 * [SipApplication]) so the Dart side can drive [SipForegroundService] before
 * any Activity exists, and kept around so [MainActivity] can push native ->
 * Dart events (incoming-call notification actions) even while the app is only
 * alive in the background.
 */
object SipForegroundServiceChannel {
    const val CHANNEL = "xtendphone/foreground_service"

    private var methodChannel: MethodChannel? = null
    private var appContext: Context? = null

    /** Native -> Dart actions queued until the Dart handler is installed. */
    private val pendingActions = ArrayDeque<Pair<String, String>>()

    /** Install the Dart -> native handler. Idempotent; first bind wins. */
    fun bind(engine: FlutterEngine, context: Context) {
        if (methodChannel != null) return
        appContext = context
        val ch = MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel = ch
        ch.setMethodCallHandler { call, result ->
            when (call.method) {
                "start" -> {
                    val extension = call.argument<String>("extension") ?: ""
                    val server = call.argument<String>("server") ?: ""
                    SipForegroundService.start(context, extension, server)
                    result.success(null)
                }
                "stop" -> {
                    SipForegroundService.stop(context)
                    result.success(null)
                }
                "showIncomingCall" -> {
                    val callId = call.argument<String>("callId") ?: ""
                    val caller = call.argument<String>("caller") ?: ""
                    SipForegroundService.showIncomingCall(context, callId, caller)
                    result.success(null)
                }
                "hideIncomingCall" -> {
                    SipForegroundService.hideIncomingCall(context)
                    result.success(null)
                }
                "appReady" -> {
                    // Dart has installed its handler; deliver anything queued.
                    flushPendingActions()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    /**
     * Forward a [MainActivity] intent that carries the notification's Answer
     * action. Called from `onNewIntent` and a cold-start `onCreate`.
     */
    fun handleCallActionIntent(intent: Intent?) {
        if (intent?.action != SipForegroundService.ACTION_ANSWER) return
        val callId = intent.getStringExtra(SipForegroundService.EXTRA_CALL_ID) ?: return
        queueAction("answer", callId)
    }

    /** Queue a native -> Dart action; flushed immediately or on `appReady`. */
    fun queueAction(action: String, callId: String) {
        pendingActions.addLast(action to callId)
        flushPendingActions()
    }

    private fun flushPendingActions() {
        val ch = methodChannel ?: return
        while (pendingActions.isNotEmpty()) {
            val (action, callId) = pendingActions.removeFirst()
            ch.invokeMethod(
                "onIncomingCallAction",
                mapOf("action" to action, "callId" to callId),
            )
        }
    }
}
