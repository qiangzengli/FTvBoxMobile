import 'dart:async';

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/data/demo_catalog.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:fbox/features/detail/detail_page.dart';
import 'package:fbox/features/library/library_page.dart';
import 'package:fbox/features/live/live_page.dart';
import 'package:fbox/features/player/player_page.dart';
import 'package:fbox/features/search/search_page.dart';
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

class _LivePreferenceStore extends FBoxStore {
  int loadCount = 0;

  @override
  List<LiveSource> get liveSources => const [
    LiveSource(name: '真实直播源', url: 'https://live.test/list.m3u'),
  ];

  @override
  Future<List<LiveChannelGroup>> loadLiveSource(LiveSource source) async {
    loadCount++;
    return const [];
  }
}

class _RepeatingSearchStore extends FBoxStore {
  final List<int> requestedPages = [];

  late final List<MediaItem> _page = List.generate(
    24,
    (index) => MediaItem(
      id: 'repeat-$index',
      title: '重复结果 $index',
      note: '',
      year: '',
      category: '',
      rating: '',
      description: '',
      director: '',
      actors: '',
      episodes: 1,
      color: 0xFF607D8B,
    ),
  );

  @override
  Future<List<MediaItem>> search(String query, {int page = 1}) async {
    requestedPages.add(page);
    return _page;
  }
}

class _EpgPreferenceStore extends FBoxStore {
  int epgLoadCount = 0;

  @override
  List<LiveSource> get liveSources => const [
    LiveSource(
      name: 'EPG 直播源',
      url: 'https://live.test/list.m3u',
      epg: 'https://epg.test/?ch={name}&date={date}',
    ),
  ];

  @override
  Future<List<LiveChannelGroup>> loadLiveSource(LiveSource source) async =>
      const [
        LiveChannelGroup(
          name: '央视频道',
          channels: [
            LiveChannel(
              name: 'CCTV-6 电影',
              epgId: 'CCTV6',
              urls: ['https://live.test/cctv6.m3u8'],
            ),
          ],
        ),
      ];

  @override
  Future<List<LiveProgram>> loadLiveEpg(
    LiveSource source,
    LiveChannel channel,
  ) async {
    epgLoadCount++;
    return const [LiveProgram(start: '20:00', end: '21:30', title: '真实节目')];
  }
}

class _DelayedEpgPreferenceStore extends _EpgPreferenceStore {
  final epgResult = Completer<List<LiveProgram>>();

  @override
  Future<List<LiveChannelGroup>> loadLiveSource(
    LiveSource source,
  ) async => const [
    LiveChannelGroup(
      name: '央视频道',
      channels: [
        LiveChannel(name: 'CCTV-6 电影', urls: ['https://live.test/cctv6.m3u8']),
        LiveChannel(
          name: 'CCTV-7 国防军事',
          urls: ['https://live.test/cctv7.m3u8'],
        ),
      ],
    ),
  ];

  @override
  Future<List<LiveProgram>> loadLiveEpg(
    LiveSource source,
    LiveChannel channel,
  ) => epgResult.future;
}

void main() {
  testWidgets('search stops paging when the provider repeats its last page', (
    tester,
  ) async {
    final store = _RepeatingSearchStore();
    addTearDown(store.dispose);
    await tester.pumpWidget(
      FBoxScope(
        store: store,
        child: const MaterialApp(home: SearchPage()),
      ),
    );

    await tester.enterText(find.byKey(const Key('search-field')), '不会命中演示数据');
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
    await tester.drag(find.byType(ListView), const Offset(0, -2000));
    await tester.pumpAndSettle();
    await tester.drag(find.byType(ListView), const Offset(0, -400));
    await tester.pumpAndSettle();

    expect(store.requestedPages, [1, 2]);
  });

  testWidgets('real live sources display fetched EPG instead of demo data', (
    tester,
  ) async {
    final store = _EpgPreferenceStore();
    addTearDown(store.dispose);
    store.setSetting('liveSourceUrl', 'https://live.test/list.m3u');
    await tester.pumpWidget(
      FBoxScope(
        store: store,
        child: const MaterialApp(home: LivePage()),
      ),
    );
    await tester.pump();

    expect(find.text('黄金档特别节目'), findsNothing);
    await tester.tap(find.byTooltip('节目单'));
    await tester.pumpAndSettle();

    expect(store.epgLoadCount, 1);
    expect(find.text('20:00–21:30  真实节目'), findsWidgets);
  });

  testWidgets('late EPG data cannot overwrite a newly selected channel', (
    tester,
  ) async {
    final store = _DelayedEpgPreferenceStore();
    addTearDown(store.dispose);
    store.setSetting('liveSourceUrl', 'https://live.test/list.m3u');
    await tester.pumpWidget(
      FBoxScope(
        store: store,
        child: const MaterialApp(home: LivePage()),
      ),
    );
    await tester.pump();

    await tester.tap(find.byTooltip('节目单'));
    await tester.pump();
    await tester.tap(find.text('CCTV-7 国防军事'));
    await tester.pump();
    store.epgResult.complete(const [
      LiveProgram(start: '20:00', end: '21:30', title: '旧频道节目'),
    ]);
    await tester.pumpAndSettle();

    expect(find.textContaining('旧频道节目'), findsNothing);
    expect(find.text('点击节目单查看'), findsOneWidget);
  });

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

  testWidgets('detail handles empty play lines safely without clamp error', (
    tester,
  ) async {
    const emptyItem = MediaItem(
      id: 'empty-series',
      title: '空线路影视',
      note: '无资源',
      year: '2026',
      category: '电影',
      rating: '',
      description: '没有任何线路的影片',
      director: '',
      actors: '',
      episodes: 0,
      color: 0xFF607D8B,
      playLines: [],
    );

    final store = FBoxStore();
    addTearDown(store.dispose);
    await tester.pumpWidget(
      FBoxScope(
        store: store,
        child: const MaterialApp(home: DetailPage(item: emptyItem)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('空线路影视'), findsOneWidget);
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

  testWidgets('player does not simulate unavailable subtitles or danmaku', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: PlayerPage(item: _item)));

    expect(find.text('这个镜头太棒了'), findsNothing);
    expect(find.text('FBox 弹幕演示 →'), findsNothing);
    await tester.tap(find.text('字幕'));
    await tester.pump();
    expect(find.text('当前媒体未提供字幕轨道'), findsOneWidget);
    await tester.tap(find.text('弹幕'));
    await tester.pump();
    expect(find.text('当前配置未提供弹幕服务'), findsOneWidget);
  });

  testWidgets('player sleep timer pauses playback when its duration elapses', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: PlayerPage(item: _item)));

    await tester.tap(find.text('设置'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('定时关闭 · 关闭'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('15 分钟'));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(minutes: 15));

    expect(find.text('定时关闭已执行'), findsOneWidget);
  });

  testWidgets('player switches to the matching episode on a real play line', (
    tester,
  ) async {
    final store = FBoxStore();
    addTearDown(store.dispose);
    const item = MediaItem(
      id: 'line-switch',
      title: '线路测试',
      note: '',
      year: '',
      category: '',
      rating: '',
      description: '',
      director: '',
      actors: '',
      episodes: 2,
      color: 0xFF607D8B,
      playLines: [
        MediaPlayLine(
          name: '主线',
          episodes: [
            MediaEpisode(name: '第1集', url: 'https://one.test/1.mp4'),
            MediaEpisode(name: '第2集', url: 'https://one.test/2.mp4'),
          ],
        ),
        MediaPlayLine(
          name: '备用线',
          episodes: [
            MediaEpisode(name: '第1集', url: 'https://two.test/1.mp4'),
            MediaEpisode(name: '第2集', url: 'https://two.test/2.mp4'),
          ],
        ),
      ],
    );
    await tester.pumpWidget(
      FBoxScope(
        store: store,
        child: const MaterialApp(
          home: PlayerPage(
            item: item,
            episode: 2,
            episodeName: '第2集',
            source: '主线',
          ),
        ),
      ),
    );

    await tester.tap(find.text('主线'));
    await tester.pumpAndSettle();
    expect(find.text('备用线'), findsOneWidget);
    await tester.tap(find.text('备用线'));
    await tester.pumpAndSettle();

    expect(find.text('线路测试 · 第2集'), findsOneWidget);
    expect(find.text('备用线'), findsOneWidget);
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
    expect(find.text('演示直播源'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('demo live preference does not fall back to a remote source', (
    tester,
  ) async {
    final store = _LivePreferenceStore();
    addTearDown(store.dispose);
    store.setSetting('liveSourceUrl', demoLiveSourceSetting);

    await tester.pumpWidget(
      FBoxScope(
        store: store,
        child: const MaterialApp(home: LivePage()),
      ),
    );
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('演示直播源'), findsOneWidget);
    expect(store.loadCount, 0);
  });

  testWidgets('failed live source load keeps the previous source label', (
    tester,
  ) async {
    final store = _LivePreferenceStore();
    addTearDown(store.dispose);
    store.setSetting('liveSourceUrl', 'https://live.test/list.m3u');

    await tester.pumpWidget(
      FBoxScope(
        store: store,
        child: const MaterialApp(home: LivePage()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('演示直播源'), findsOneWidget);
    expect(find.textContaining('直播源加载失败'), findsOneWidget);
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
