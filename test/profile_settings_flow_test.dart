import 'package:fbox/app/fbox_app.dart';
import 'package:fbox/features/settings/settings_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('My menu opens settings and changes the dark theme preference', (
    tester,
  ) async {
    await tester.pumpWidget(const FBoxApp());
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('bottom-nav-my')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('profile-settings')));
    await tester.pumpAndSettle();

    expect(find.text('主题与外观'), findsOneWidget);
    expect(find.text('深色模式'), findsOneWidget);
    expect(find.text('跟随系统'), findsOneWidget);

    await tester.tap(find.byKey(const Key('settings-dark-switch')));
    await tester.pumpAndSettle();

    expect(find.text('已开启'), findsOneWidget);

    await tester.tap(find.text('主题色'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('theme-color-ffff5f00')));
    await tester.pumpAndSettle();
    expect(
      Theme.of(tester.element(find.byType(SettingsPage))).colorScheme.primary,
      const Color(0xFFFF5F00),
    );

    await tester.tap(find.text('主页内容'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('2 列 · 海报模式'));
    await tester.pumpAndSettle();
    expect(find.text('2 列 · 海报模式'), findsOneWidget);

    await tester.scrollUntilVisible(
      find.byKey(const Key('settings-clear-cache')),
      500,
    );
    await tester.tap(find.byKey(const Key('settings-clear-cache')));
    await tester.pump();
    expect(find.text('0.0 MB'), findsOneWidget);
    expect(find.text('缓存已清理'), findsOneWidget);
  });
}
