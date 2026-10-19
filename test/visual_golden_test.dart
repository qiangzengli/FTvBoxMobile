import 'package:fbox/app/fbox_app.dart';
import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/app/fbox_theme.dart';
import 'fixtures/demo_catalog.dart';
import 'package:fbox/features/detail/detail_page.dart';
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
    final store = FBoxStore();
    addTearDown(store.dispose);
    await tester.pumpWidget(
      FBoxScope(
        store: store,
        child: MaterialApp(
          theme: FBoxTheme.light(store.themeColor),
          home: DetailPage(item: DemoCatalog.items.first),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await expectLater(
      find.byKey(const Key('detail-page')),
      matchesGoldenFile('goldens/detail.png'),
    );
  });
}
