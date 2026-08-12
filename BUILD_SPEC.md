# Build Spec — Background-capable SIP Dialer (Android-first)

## Context, don't re-derive this
- Base: fork of https://github.com/KellyKinyama/flutter_sip_ua (already in this repo).
- It is a PURE DART SIP stack. No flutter_webrtc, no libwebrtc, no WebRTC/ICE.
- Transports already implemented and working: WS/WSS, UDP, TCP, TLS
  (see lib/sip/transport.dart — SipTransport.forUri() picks transport from
  the SIP URI scheme/params: sip:host;transport=tcp, sips:host, etc.)
- Audio codec today: G.711 only (lib/sip/audio/g711.dart). That's fine —
  do NOT add Opus/WebRTC in this phase. Video is out of scope this phase.
- SIP server: FreeSWITCH. Use TLS transport against FreeSWITCH's external
  TLS profile (default port 5061) for registration and calls.
- Target this phase: ANDROID ONLY, fully working and testable.
  iOS: scaffold the code so it compiles, but it will NOT be tested this
  phase (no Apple Developer account yet). Don't spend effort polishing iOS.

## Phase 1 — Android background service (primary goal)
1. Add a proper Android foreground service (`flutter_background_service`
   package, or hand-rolled `Service` + `MethodChannel` if that package
   fights with the existing Riverpod SIP state) that:
   - Starts when the user logs in / registers.
   - Keeps the SipTransport connection (TCP/TLS) and SIP registration
     (REGISTER refresh) alive while the app is backgrounded or swiped away.
   - Shows a persistent low-priority notification ("Connected as <ext>")
     while running, per Android's foreground service requirements.
   - Survives Doze/App Standby — request battery optimization exemption
     from the user on first run (`disableBatteryOptimizations` via
     permission_handler or the battery_optimization package).
2. Incoming call while backgrounded:
   - On receiving SIP INVITE while the foreground service is running,
     show a full-screen intent notification (Accept/Decline actions) that
     wakes the screen and launches the call UI even from a locked/backgrounded
     state — this does NOT require push yet, since the socket is already
     alive via the foreground service. Push (FCM) is the fallback for when
     Android has killed the process entirely; build the service to be robust
     first, add FCM wake-up as phase 2 hardening.
3. Test criteria for this phase: register from FreeSWITCH ext, background
   the app (home button, not force-stop), call the ext from another SIP
   client/phone, confirm the app rings and can answer with working two-way
   G.711 audio.

## Phase 2 — FCM wake-up hardening (Android)
1. Add `firebase_messaging`. Placeholder `google-services.json` — real one
   gets dropped in by the developer after Firebase project setup.
2. On app registration, send the FCM token + SIP extension to the relay
   service (see PUSH_RELAY_SPEC.md, separate doc, build this as its own
   Node.js project, not inside the Flutter app).
3. Handle high-priority FCM data messages in a background message handler
   that restarts the foreground service if it's been killed, then shows the
   incoming-call full-screen notification as in Phase 1.

## Phase 3 — iOS scaffold only (not tested this phase)
1. Add `flutter_callkit_incoming` dependency.
2. Add native Swift PushKit delegate (`AppDelegate.swift`): register for
   VoIP pushes, on receipt report the call to CallKit via
   flutter_callkit_incoming's native bridge.
3. Add `Info.plist` background modes: `voip`, `audio`.
4. Do NOT attempt to test, sign, or run this on a real device/simulator
   this phase — no Apple Developer account yet. Just make sure the Dart
   side compiles with the iOS-specific code behind the existing
   `dart.library.io` / platform stub pattern already used elsewhere in
   this codebase (see transport_tcp_stub.dart / transport_tcp_io.dart for
   the pattern to follow).

## Non-goals this phase
- No Opus, no video, no WebRTC anything.
- No iOS testing or provisioning.
- No UI redesign — keep the existing Browser-Phone-style UI, just wire the
  background service into it.

## Git workflow
- All work happens on this Ubuntu machine via OpenCode.
- Commit in small logical chunks (one commit per numbered item above),
  not one giant commit — makes it reviewable.
- Push to origin after each working, buildable commit.
- Testing happens on the Windows machine: `git pull`, open in Android
  Studio, `flutter pub get`, run on a physical Android device or emulator.
