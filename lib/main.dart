import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'providers/sip_providers.dart';
import 'services/foreground_service.dart';
import 'ui/home_page.dart';
import 'ui/theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Install the native -> Dart callbacks for the background foreground
  // service (incoming-call notification actions) before anything else runs.
  ForegroundService.init();
  // Resolve SharedPreferences synchronously before runApp so the rest of
  // the app can read it through a Provider without a FutureBuilder.
  final prefs = await SharedPreferences.getInstance();
  runApp(
    ProviderScope(
      overrides: [sharedPreferencesProvider.overrideWithValue(prefs)],
      child: const FlutterSipUaApp(),
    ),
  );
}

class FlutterSipUaApp extends ConsumerWidget {
  const FlutterSipUaApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mode = ref.watch(themeModeProvider);
    return MaterialApp(
      title: 'Dart SIP',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: mode,
      home: const HomePage(),
    );
  }
}
