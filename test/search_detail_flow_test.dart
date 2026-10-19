import 'package:fbox/app/fbox_app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('search without a source does not show fabricated results', (
    tester,
  ) async {
    await tester.pumpWidget(const FBoxApp());
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('home-search')));
    await tester.pumpAndSettle();

    expect(find.text('快速搜索'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('search-field')), '庆余年');
    await tester.pump();

    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('暂无搜索结果'), findsOneWidget);
    expect(find.byKey(const Key('search-result-qing-yu-nian')), findsNothing);
  });
}
