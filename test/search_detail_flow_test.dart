import 'package:fbox/app/fbox_app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('search opens a detail page and toggles favorite state', (
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

    await tester.tap(find.byKey(const Key('search-result-qing-yu-nian')));
    await tester.pumpAndSettle();

    expect(find.text('庆余年 第二季'), findsWidgets);
    expect(find.text('立即播放'), findsOneWidget);
    expect(find.text('收藏'), findsOneWidget);

    await tester.tap(find.byKey(const Key('detail-favorite')));
    await tester.pump();

    expect(find.text('已收藏'), findsOneWidget);
  });
}
