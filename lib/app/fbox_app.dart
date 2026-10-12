import 'package:fbox/app/fbox_theme.dart';
import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/features/splash/splash_page.dart';
import 'package:flutter/material.dart';

class FBoxApp extends StatefulWidget {
  const FBoxApp({super.key});

  @override
  State<FBoxApp> createState() => _FBoxAppState();
}

class _FBoxAppState extends State<FBoxApp> {
  final _store = FBoxStore();

  @override
  void dispose() {
    _store.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _store,
      builder: (context, _) => FBoxScope(
        store: _store,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          title: 'FBox',
          theme: FBoxTheme.light,
          darkTheme: FBoxTheme.dark,
          themeMode: _store.themeMode,
          home: const LaunchGate(),
        ),
      ),
    );
  }
}
