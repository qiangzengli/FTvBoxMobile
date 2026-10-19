import 'dart:convert';
import 'dart:io';

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:fbox/features/home/home_page.dart';
import 'package:fbox/features/player/player_page.dart';
import 'package:fbox/features/settings/settings_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:video_player/video_player.dart';

import 'fixture_media.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('iOS displays Spider source capability accurately', (
    tester,
  ) async {
    expect(Platform.isIOS, isTrue);
    final store = FBoxStore();
    await store.initialize();
    final configuration = File(
      '${Directory.systemTemp.path}/fbox-ios-spider-smoke.json',
    );
    await configuration.writeAsString('''
      {
        "spider":"https://example.invalid/spider.jar",
        "sites":[
          {"key":"ios_smoke","name":"iOS Spider 测试源","type":3,"api":"csp_Test"}
        ]
      }
    ''');
    final subscription = await store.addLocalSubscription(
      name: 'iOS 模拟器测试',
      filePath: configuration.path,
    );
    try {
      await tester.pumpWidget(
        FBoxScope(
          store: store,
          child: const MaterialApp(home: Scaffold(body: HomePage())),
        ),
      );
      await tester.pumpAndSettle();
      expect(store.sources.single.isSupported, isFalse);
      await tester.tap(find.text('选择站源').first);
      await tester.pumpAndSettle();
      expect(find.text('iOS Spider 测试源'), findsOneWidget);
      expect(find.text('请在设置中填写 Android Spider 转发地址'), findsOneWidget);
    } finally {
      await tester.pumpWidget(const SizedBox.shrink());
      await store.removeSubscription(subscription.id);
      await configuration.delete();
      store.dispose();
    }
  });

  testWidgets('iOS player settings update the stored picture ratio', (
    tester,
  ) async {
    const item = MediaItem(
      id: 'settings-smoke',
      title: '播放器设置测试',
      note: '',
      year: '',
      category: '',
      rating: '',
      description: '',
      director: '',
      actors: '',
      episodes: 1,
      color: 0xFF334455,
    );
    final store = FBoxStore();
    await tester.pumpWidget(
      FBoxScope(
        store: store,
        child: const MaterialApp(home: PlayerPage(item: item)),
      ),
    );
    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    await tester.tap(find.textContaining('画面比例'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('16:9'));
    await tester.pumpAndSettle();
    expect(store.settings['scaleMode'], '16:9');
    await tester.pumpWidget(const SizedBox.shrink());
    store.dispose();
  });

  testWidgets('iOS settings save and check the Android bridge address', (
    tester,
  ) async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    server.listen((request) async {
      expectSync(request.uri.path, '/testtoken/health');
      request.response.headers.contentType = ContentType.json;
      request.response.write('{"ok":true}');
      await request.response.close();
    });
    final store = FBoxStore();
    try {
      await tester.pumpWidget(
        FBoxScope(
          store: store,
          child: const MaterialApp(home: SettingsPage()),
        ),
      );
      await tester.scrollUntilVisible(find.text('Android Spider 转发地址'), 300);
      await tester.tap(find.text('Android Spider 转发地址'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byType(TextField).last,
        'http://127.0.0.1:${server.port}/testtoken',
      );
      await tester.tap(find.text('保存'));
      await tester.pumpAndSettle();
      expect(
        store.spiderBridgeUrl,
        'http://127.0.0.1:${server.port}/testtoken',
      );
      expect(find.text('已连接 Android Spider 服务'), findsOneWidget);
    } finally {
      await tester.pumpWidget(const SizedBox.shrink());
      store.dispose();
      await server.close(force: true);
    }
  });

  testWidgets('iOS bridge loads home, detail and playback metadata', (
    tester,
  ) async {
    expect(Platform.isIOS, isTrue);
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final operations = <String>[];
    var playerCalls = 0;
    server.listen((request) async {
      if (request.uri.path == '/testtoken/proxy') {
        expectSync(request.uri.queryParameters['do'], 'sample');
        expectSync(
          request.uri.queryParameters['siteKey'],
          contains('bridge_smoke'),
        );
        request.response.headers.contentType = ContentType(
          'application',
          'vnd.apple.mpegurl',
        );
        request.response.write('#EXTM3U\n#EXT-X-VERSION:3\n');
        await request.response.close();
        return;
      }
      expectSync(request.uri.path, '/testtoken/invoke');
      final payload =
          jsonDecode(await utf8.decoder.bind(request).join()) as Map;
      final operation = payload['operation'] as String;
      operations.add(operation);
      final value = switch (operation) {
        'home' => jsonEncode({
          'class': [
            {'type_id': 'film', 'type_name': '电影'},
          ],
          'list': [
            {
              'vod_id': 'film-1',
              'vod_name': '模拟桥影片',
              'vod_pic': 'https://example.invalid/poster.jpg',
            },
          ],
        }),
        'detail' => jsonEncode({
          'list': [
            {
              'vod_id': 'film-1',
              'vod_name': '模拟桥影片',
              'vod_pic': 'https://example.invalid/poster.jpg',
              'vod_play_from': '主线',
              'vod_play_url': r'第1集$https://media.example.invalid/1.m3u8',
            },
          ],
        }),
        'player' => jsonEncode(
          ++playerCalls == 1
              ? {
                  'parse': 0,
                  'url': 'https://media.example.invalid/1.m3u8',
                  'header': {'Referer': 'https://site.example.invalid/'},
                }
              : {'parse': 0, 'url': 'proxy://do=sample'},
        ),
        _ => '{}',
      };
      request.response.headers.contentType = ContentType.json;
      request.response.write(jsonEncode({'value': value}));
      await request.response.close();
    });
    final store = FBoxStore();
    store.setSetting('proxy', 'http://127.0.0.1:1');
    store.setSetting(
      'spiderBridgeUrl',
      'http://127.0.0.1:${server.port}/testtoken',
    );
    final configuration = File(
      '${Directory.systemTemp.path}/fbox-ios-bridge-smoke.json',
    );
    await configuration.writeAsString('''
      {
        "spider":"https://example.invalid/spider.jar",
        "sites":[
          {"key":"bridge_smoke","name":"桥接测试源","type":3,"api":"csp_Test"}
        ]
      }
    ''');
    final subscription = await store.addLocalSubscription(
      name: 'iOS 桥接测试',
      filePath: configuration.path,
    );
    try {
      final source = store.sources.single;
      expect(store.supportsSource(source), isTrue);
      await store.selectSource(source.identity);
      expect(
        store.catalog.single.posterUrl,
        'https://example.invalid/poster.jpg',
      );
      final detail = await store.loadDetail(store.catalog.single);
      expect(detail.playLines.single.episodes.single.name, '第1集');
      final playback = await store.resolvePlayback(
        item: detail,
        episode: detail.playLines.single.episodes.single,
        line: detail.playLines.single.name,
      );
      expect(playback.url, 'https://media.example.invalid/1.m3u8');
      expect(playback.headers['Referer'], 'https://site.example.invalid/');
      final proxied = await store.resolvePlayback(
        item: detail,
        episode: detail.playLines.single.episodes.single,
        line: detail.playLines.single.name,
      );
      expect(proxied.url, contains('/testtoken/proxy?'));
      expect(
        await store.network.getDirectText(Uri.parse(proxied.url)),
        startsWith('#EXTM3U'),
      );
      expect(operations, ['home', 'detail', 'player', 'player']);
    } finally {
      await store.removeSubscription(subscription.id);
      await configuration.delete();
      store.dispose();
      await server.close(force: true);
    }
  });

  testWidgets('iOS player initializes a real MP4 stream through proxy URL', (
    tester,
  ) async {
    expect(Platform.isIOS, isTrue);
    final bytes = base64Decode(smokeMp4Base64);
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    server.listen((request) async {
      expectSync(request.uri.path, '/testtoken/proxy');
      expectSync(request.uri.queryParameters['do'], 'video');
      final range = request.headers.value(HttpHeaders.rangeHeader);
      final match = RegExp(r'bytes=(\d+)-(\d*)').firstMatch(range ?? '');
      final start = int.tryParse(match?.group(1) ?? '') ?? 0;
      final end = int.tryParse(match?.group(2) ?? '') ?? bytes.length - 1;
      final boundedEnd = end.clamp(start, bytes.length - 1);
      request.response.headers.contentType = ContentType('video', 'mp4');
      request.response.headers.set(HttpHeaders.acceptRangesHeader, 'bytes');
      if (match != null) {
        request.response.statusCode = HttpStatus.partialContent;
        request.response.headers.set(
          HttpHeaders.contentRangeHeader,
          'bytes $start-$boundedEnd/${bytes.length}',
        );
      }
      request.response.contentLength = boundedEnd - start + 1;
      if (request.method != 'HEAD') {
        request.response.add(bytes.sublist(start, boundedEnd + 1));
      }
      await request.response.close();
    });
    const item = MediaItem(
      id: 'ios-video-smoke',
      title: '真实视频测试',
      note: '',
      year: '',
      category: '',
      rating: '',
      description: '',
      director: '',
      actors: '',
      episodes: 1,
      color: 0xFF334455,
    );
    final store = FBoxStore();
    try {
      await tester.pumpWidget(
        FBoxScope(
          store: store,
          child: MaterialApp(
            home: PlayerPage(
              item: item,
              playback: PlaybackRequest(
                url: 'http://127.0.0.1:${server.port}/testtoken/proxy?do=video',
              ),
            ),
          ),
        ),
      );
      for (var attempt = 0; attempt < 20; attempt++) {
        await tester.pump(const Duration(milliseconds: 250));
        if (find.byType(VideoPlayer).evaluate().isNotEmpty) break;
      }
      expect(find.byType(VideoPlayer), findsOneWidget);
    } finally {
      await tester.pumpWidget(const SizedBox.shrink());
      store.dispose();
      await server.close(force: true);
    }
  });
}
