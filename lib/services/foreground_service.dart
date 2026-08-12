/// Cross-platform bridge to the Android foreground service (Phase 1).
///
/// Android keeps the process alive while a foreground service is running, and
/// our SIP user agent lives in the *main* Dart isolate, so we deliberately use
/// a hand-rolled native `Service` + `MethodChannel` instead of
/// `flutter_background_service`: a package that spawns its own isolate would
/// force a second UA instance and a full re-plumb of the Riverpod state.
///
/// The service is a no-op everywhere except Android (the `dart:io`
/// implementation also guards on [TargetPlatform.android] at runtime).
library;

import 'foreground_service_stub.dart'
    if (dart.library.io) 'foreground_service_android.dart'
    as impl;

class ForegroundService {
  const ForegroundService._();

  /// Whether a foreground service exists on this platform (Android only).
  static bool get supported => impl.supported;

  /// Start the persistent "Connected as <extension>" foreground service.
  static Future<bool> start({
    required String extension,
    required String server,
  }) => impl.start(extension: extension, server: server);

  /// Stop the foreground service. Safe to call when not running.
  static Future<void> stop() => impl.stop();

  /// Called once at app startup (from `main`) so the native side can deliver
  /// queued notification-action callbacks (incoming-call Accept/Decline).
  static void init() => impl.init();
}
