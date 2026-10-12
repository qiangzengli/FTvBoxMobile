import 'package:fbox/app/fbox_app.dart';
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
  });
}
