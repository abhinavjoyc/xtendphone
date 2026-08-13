package com.example.flutter_sip_ua

import io.flutter.app.FlutterApplication
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.FlutterEngineGroup

/**
 * Application that owns the Flutter engine so it outlives every Activity.
 *
 * With the default embedding, the engine is created by and destroyed with
 * [MainActivity]: swiping the app away kills the Dart isolate — and with it
 * the SIP socket and REGISTER refresh. A host-provided engine (returned from
 * [MainActivity.provideFlutterEngine]) is instead kept alive by the framework
 * (`shouldDestroyEngineWithHost` returns false for host engines), so the
 * isolate keeps running in the background, exactly what
 * [SipForegroundService] needs to keep the registration alive.
 */
class SipApplication : FlutterApplication() {
    companion object {
        const val ENGINE_ID = "phone_engine"
    }

    private lateinit var engineGroup: FlutterEngineGroup

    override fun onCreate() {
        super.onCreate()
        engineGroup = FlutterEngineGroup(this)
        val engine = engineGroup.createAndRunEngine(
            FlutterEngineGroup.Options(this).setAutomaticallyRegisterPlugins(true),
        )
        FlutterEngineCache.getInstance().put(ENGINE_ID, engine)
        // Bind the Dart <-> native channel now: main() runs as soon as the
        // engine starts, and it may drive the foreground service before the
        // first Activity has attached.
        SipForegroundServiceChannel.bind(engine, this)
    }
}
