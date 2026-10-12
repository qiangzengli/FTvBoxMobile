import 'package:fbox/app/fbox_app.dart';
import 'package:fbox/features/shell/fbox_shell.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Future<void> setPhoneSurface(WidgetTester tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(390, 844);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
  }

  Future<void> launch(WidgetTester tester) async {
    await tester.pumpWidget(const FBoxApp());
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pumpAndSettle();
  }

  testWidgets('phone home and My pages match visual baselines', (tester) async {
    await setPhoneSurface(tester);
    await launch(tester);

    await expectLater(
      find.byType(FBoxShell),
      matchesGoldenFile('goldens/home.png'),
    );

    await tester.tap(find.byKey(const Key('bottom-nav-my')));
    await tester.pumpAndSettle();

    await expectLater(
      find.byType(FBoxShell),
      matchesGoldenFile('goldens/my.png'),
    );
  });

  testWidgets('detail page matches the phone visual baseline', (tester) async {
    await setPhoneSurface(tester);
    await launch(tester);

    await tester.tap(find.byKey(const Key('home-search')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('search-field')), '庆余年');
    await tester.pump();
    await tester.tap(find.byKey(const Key('search-result-qing-yu-nian')));
    await tester.pumpAndSettle();

    await expectLater(
      find.byKey(const Key('detail-page')),
      matchesGoldenFile('goldens/detail.png'),
    );
  });
}
