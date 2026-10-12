import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/data/demo_catalog.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/features/detail/detail_page.dart';
import 'package:fbox/features/library/library_page.dart';
import 'package:fbox/features/live/live_page.dart';
import 'package:fbox/features/player/player_page.dart';
import 'package:fbox/features/subscription/subscription_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

const _item = MediaItem(
  id: 'test-series',
  title: '测试剧集',
  note: '更新中',
  year: '2026',
  category: '连续剧',
  rating: '9.0',
  description: '用于验证 FBox 本地 UI 交互。',
  director: '测试导演',
  actors: '测试演员',
  episodes: 65,
  color: 0xFF607D8B,
);

void main() {
  testWidgets('detail switches sources and reaches every episode range', (
    tester,
  ) async {
    final store = FBoxStore();
    addTearDown(store.dispose);
    await tester.pumpWidget(
      FBoxScope(
        store: store,
        child: const MaterialApp(home: DetailPage(item: _item)),
      ),
    );

    await tester.ensureVisible(find.byKey(const Key('detail-quick-source')));
    await tester.tap(find.byKey(const Key('detail-quick-source')));
    await tester.pump();
    expect(
      tester
          .widget<ChoiceChip>(find.byKey(const Key('detail-source-1')))
          .selected,
      isTrue,
    );

    await tester.ensureVisible(find.text('61-65'));
    await tester.tap(find.text('61-65'));
    await tester.pump();
    await tester.ensureVisible(find.byKey(const Key('detail-episode-61')));
    await tester.tap(find.byKey(const Key('detail-episode-61')));
    await tester.pumpAndSettle();

    expect(find.text('测试剧集 · 第 61 集'), findsOneWidget);
    expect(find.text('备用线路'), findsOneWidget);
  });

  testWidgets('player exposes speed, subtitle, danmaku and lock controls', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: PlayerPage(item: _item)));

    expect(find.text('字幕'), findsOneWidget);
    expect(find.text('弹幕'), findsOneWidget);
    expect(find.text('投屏'), findsOneWidget);

    await tester.tap(find.byKey(const Key('player-speed')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('1.5×'));
    await tester.pumpAndSettle();
    expect(find.text('1.50×'), findsOneWidget);

    await tester.ensureVisible(find.text('设置'));
    await tester.tap(find.text('设置'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('画面比例 · 适应屏幕'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('16:9'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('设置'));
    await tester.pumpAndSettle();
    expect(find.text('画面比例 · 16:9'), findsOneWidget);
    await tester.tap(find.text('画面比例 · 16:9'));
    await tester.pumpAndSettle();
    await tester.tapAt(const Offset(5, 5));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('player-fullscreen')), findsOneWidget);

    await tester.tap(find.byKey(const Key('player-lock')));
    await tester.pump();
    expect(find.byKey(const Key('player-play-toggle')), findsNothing);
    expect(find.byIcon(Icons.lock_rounded), findsOneWidget);
  });

  testWidgets('subscription entries can be edited and deleted', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: SubscriptionPage()));

    await tester.tap(find.byKey(const Key('subscription-menu-0')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('编辑'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byType(TextField),
      'https://fbox.test/config.json',
    );
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();
    expect(find.text('https://fbox.test/config.json'), findsOneWidget);

    await tester.tap(find.byKey(const Key('subscription-menu-0')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(TextButton, '删除'));
    await tester.pumpAndSettle();
    expect(find.text('暂无订阅，点击下方按钮添加'), findsOneWidget);
  });

  testWidgets('live overlay adapts to a compact landscape surface', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(844, 390);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    await tester.pumpWidget(const MaterialApp(home: LivePage()));
    await tester.pump();

    expect(find.text('CCTV-1 综合'), findsWidgets);
    await tester.tap(find.text('演示直播源'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('备用直播源'));
    await tester.pumpAndSettle();
    expect(find.text('备用直播源'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('history edit mode removes selected local records', (
    tester,
  ) async {
    final store = FBoxStore();
    final item = DemoCatalog.items.first;
    store.recordWatch(item);
    addTearDown(store.dispose);
    await tester.pumpWidget(
      FBoxScope(
        store: store,
        child: const MaterialApp(home: LibraryPage(kind: LibraryKind.history)),
      ),
    );

    await tester.tap(find.byKey(const Key('library-edit')));
    await tester.pump();
    await tester.tap(find.text(item.title));
    await tester.pump();
    await tester.tap(find.byKey(const Key('library-delete-selected')));
    await tester.pump();

    expect(store.historyIds, isEmpty);
    expect(find.text('暂无观看记录'), findsOneWidget);
  });
}
