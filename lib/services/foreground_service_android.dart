/// `dart:io` implementation of the foreground service bridge.
///
/// Talks to `MainActivity` over the `xtendphone/foreground_service`
/// MethodChannel. No-ops on iOS and desktop (a foreground service is an
/// Android concept), which keeps the platform-stub pattern consistent with the
/// rest of the codebase.
library;

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

const _channel = MethodChannel('xtendphone/foreground_service');

/// Android is the only platform with a native implementation today.
bool get supported =>
    !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

Future<bool> start({required String extension, required String server}) async {
  if (!supported) return false;
  try {
    await _channel.invokeMethod<void>('start', {
      'extension': extension,
      'server': server,
    });
    return true;
  } catch (_) {
    return false;
  }
}

Future<void> stop() async {
  if (!supported) return;
  try {
    await _channel.invokeMethod<void>('stop');
  } catch (_) {
    // Nothing to stop.
  }
}

void init() {
  _channel.setMethodCallHandler((call) async {
    switch (call.method) {
      case 'onIncomingCallAction':
        final args = call.arguments;
        if (args is! Map) break;
        final action = args['action'] as String?;
        final callId = args['callId'] as String?;
        if (action != null && callId != null) {
          _incomingCallActionController.add((action, callId));
        }
        break;
      default:
        break;
    }
  });
  // Tell the native side the app is ready to receive action callbacks so it
  // can flush anything it queued before the handler above was installed.
  _channel.invokeMethod<void>('appReady').catchError((_) {});
}

/// Native -> Dart actions from the incoming-call notification, as a pair of
/// (`action`, `callId`).
Stream<(String, String)> get incomingCallActions =>
    _incomingCallActionController.stream;

final _incomingCallActionController =
    StreamController<(String, String)>.broadcast();
