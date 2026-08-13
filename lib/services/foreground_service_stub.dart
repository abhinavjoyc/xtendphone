/// Non-`dart:io` (web) fallback: there is no native foreground service.
library;

bool get supported => false;

Future<bool> start({required String extension, required String server}) async =>
    false;

Future<void> stop() async {}

Future<void> showIncomingCall({
  required String callId,
  required String caller,
}) async {}

Future<void> hideIncomingCall() async {}

void init() {}

Stream<(String, String)> get incomingCallActions =>
    const Stream<(String, String)>.empty();
