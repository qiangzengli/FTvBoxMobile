import 'package:fbox/app/fbox_app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('starts on the MBox-style home and opens the My tab', (
    tester,
  ) async {
    await tester.pumpWidget(const FBoxApp());
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pumpAndSettle();

    expect(find.text('尚未选择站源，暂无内容'), findsOneWidget);
    expect(find.text('搜索'), findsOneWidget);
    expect(find.text('首页'), findsOneWidget);
    expect(find.text('我的'), findsOneWidget);

    await tester.tap(find.byKey(const Key('bottom-nav-my')));
    await tester.pumpAndSettle();

    expect(find.text('订阅管理'), findsOneWidget);
    expect(find.text('观看历史'), findsOneWidget);
    expect(find.text('本地视频'), findsOneWidget);
    expect(find.text('设置'), findsOneWidget);
  });
}
