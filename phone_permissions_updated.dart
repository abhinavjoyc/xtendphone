/// One-time Android runtime permissions the foreground service needs:
/// POST_NOTIFICATIONS (Android 13+) so the persistent "Connected" notification
/// is visible, the microphone permission required by the microphone foreground
/// service type, and the battery-optimization exemption so the service survives
/// Doze / App Standby.
library;

import 'package:flutter/foundation.dart';
import 'package:permission_handler/permission_handler.dart';

/// SharedPreferences key marking that the one-time permission flow has run.
const phonePermissionsAskedKey = 'phone_permissions_v1';

bool get _android =>
    !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

/// Request RECORD_AUDIO (microphone) runtime permission.
///
/// This MUST succeed before starting SipForegroundService because the service
/// is declared with android:foregroundServiceType="microphone".
Future<bool> ensureMicrophonePermission() async {
  if (!_android) return true;

  try {
    final status = await Permission.microphone.request();
    return status.isGranted;
  } catch (_) {
    return false;
  }
}

/// Request POST_NOTIFICATIONS (Android 13+). Returns true if granted.
Future<bool> ensureNotificationPermission() async {
  if (!_android) return true;

  try {
    final status = await Permission.notification.request();
    return status.isGranted || status.isLimited;
  } catch (_) {
    return false;
  }
}

/// Open the system "ignore battery optimizations" dialog (Android). Returns
/// true if the exemption is now active.
Future<bool> requestBatteryOptimizationExemption() async {
  if (!_android) return true;

  try {
    final status = await Permission.ignoreBatteryOptimizations.request();
    return status.isGranted || status.isLimited;
  } catch (_) {
    return false;
  }
}
