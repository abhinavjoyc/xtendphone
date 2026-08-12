package com.example.flutter_sip_ua

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        /** MethodChannel shared with Dart (see lib/services/foreground_service.dart). */
        const val CHANNEL = "xtendphone/foreground_service"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val channel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL,
        )
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "start" -> {
                    val extension = call.argument<String>("extension") ?: ""
                    val server = call.argument<String>("server") ?: ""
                    SipForegroundService.start(this, extension, server)
                    result.success(null)
                }
                "stop" -> {
                    SipForegroundService.stop(this)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
}
