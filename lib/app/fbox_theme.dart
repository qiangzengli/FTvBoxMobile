import 'package:flutter/material.dart';

abstract final class FBoxTheme {
  static const primary = Color(0xFF567DF4);
  static const accent = Color(0xFF3E4FB1);

  static ThemeData get light => _theme(
    brightness: Brightness.light,
    page: Colors.white,
    surface: const Color(0xFFF7F7F7),
    foreground: const Color(0xFF050505),
    secondary: const Color(0xFF787878),
  );

  static ThemeData get dark => _theme(
    brightness: Brightness.dark,
    page: const Color(0xFF121212),
    surface: const Color(0xFF212121),
    foreground: Colors.white,
    secondary: const Color(0xFFAFAFAF),
  );

  static ThemeData _theme({
    required Brightness brightness,
    required Color page,
    required Color surface,
    required Color foreground,
    required Color secondary,
  }) {
    final scheme = ColorScheme.fromSeed(
      seedColor: primary,
      brightness: brightness,
      primary: primary,
      surface: page,
      onSurface: foreground,
    );
    return ThemeData(
      useMaterial3: false,
      brightness: brightness,
      colorScheme: scheme,
      primaryColor: primary,
      scaffoldBackgroundColor: page,
      fontFamilyFallback: const ['PingFang SC', 'Noto Sans CJK SC'],
      splashFactory: InkRipple.splashFactory,
      appBarTheme: AppBarTheme(
        elevation: 0,
        centerTitle: true,
        backgroundColor: page,
        foregroundColor: foreground,
      ),
      cardColor: surface,
      dividerColor: secondary.withValues(alpha: 0.15),
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: page,
        selectedItemColor: primary,
        unselectedItemColor: const Color(0xFF777373),
        elevation: 8,
        selectedLabelStyle: const TextStyle(fontSize: 12),
        unselectedLabelStyle: const TextStyle(fontSize: 12),
      ),
    );
  }
}
