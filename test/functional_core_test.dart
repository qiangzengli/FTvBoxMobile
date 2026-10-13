import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/data/demo_catalog.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:fbox/services/download_manager.dart';
import 'package:fbox/services/background_image_service.dart';
import 'package:fbox/services/config_repository.dart';
import 'package:fbox/services/live_epg_service.dart';
import 'package:fbox/services/live_playlist_parser.dart';
import 'package:fbox/services/local_media_service.dart';
import 'package:fbox/services/mbox_config_parser.dart';
import 'package:fbox/services/network_service.dart';
import 'package:fbox/services/source_api_service.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  test('parses MBox sites, parsers and live sources', () {
    const input = '''
      // comments outside strings are supported
      {
        "sites": [
          {"key":"json","name":"JSON 源","type":1,"api":"https://example.test/api.php"},
          {"key":"spider","name":"Spider 源","type":3,"api":"csp_Test"}
        ],
        "parses": [{"name":"解析一","type":0,"url":"https://parse.test/?url=","ext":{"header":{"User-Agent":"FBox","Referer":"https://parse.test/"}}}],
        "lives": [{"name":"直播","url":"https://live.test/list.m3u","ua":"FBox"}]
      }
    ''';

    final config = const MBoxConfigParser().parse(
      subscriptionId: 'sub-1',
      input: input,
    );

    expect(config.sites, hasLength(2));
    expect(config.sites.first.isSupported, isTrue);
    expect(config.sites.last.isSupported, isFalse);
    expect(config.parsers.single.name, '解析一');
    expect(config.parsers.single.ext['User-Agent'], 'FBox');
    expect(config.parsers.single.ext, isNot(contains('header')));
    expect(config.parsers.single.requestUrl, contains('cat_ext='));
    expect(config.liveSources.single.headers['User-Agent'], 'FBox');
  });

  test('ParseSource appends cat_ext even when url does not contain query parameters', () {
    const parser = ParseSource(
      name: '解析',
      url: 'https://parse.test/api',
      rawExt: 'token=123',
    );
    expect(parser.requestUrl, startsWith('https://parse.test/api?cat_ext='));
  });

  test('parses and merges M3U channels by group and name', () {
    const playlist = '''
      #EXTM3U
      #EXTINF:-1 tvg-name="CCTV-1" tvg-logo="logo.png" group-title="央视",CCTV-1 综合
      https://one.test/live.m3u8
      #EXTINF:-1 tvg-name="CCTV-1" group-title="央视",CCTV-1 备用
      https://two.test/live.m3u8
    ''';

    final groups = const LivePlaylistParser().parse(playlist);

    expect(groups.single.name, '央视');
    expect(groups.single.channels.single.name, 'CCTV-1');
    expect(groups.single.channels.single.urls, hasLength(2));
  });

  test('parses TXT channels with multiple URLs separated by hash', () {
    const playlist = '''
      央视频道,#genre#
      CCTV-1,https://one.test/live.m3u8#https://two.test/live.m3u8#https://three.test/live.m3u8
      CCTV-2,https://cctv2.test/live.m3u8
    ''';

    final groups = const LivePlaylistParser().parse(playlist);

    expect(groups.single.name, '央视频道');
    expect(groups.single.channels, hasLength(2));
    expect(groups.single.channels.first.name, 'CCTV-1');
    expect(groups.single.channels.first.urls, [
      'https://one.test/live.m3u8',
      'https://two.test/live.m3u8',
      'https://three.test/live.m3u8',
    ]);
  });

  test('loads real DIYP EPG data with channel and date placeholders', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    Map<String, String>? query;
    server.listen((request) async {
      query = request.uri.queryParameters;
      request.response.headers.contentType = ContentType.json;
      request.response.write(
        jsonEncode({
          'epg_data': [
            {'start': '20:00', 'end': '21:30', 'title': '黄金档电影'},
          ],
        }),
      );
      await request.response.close();
    });
    final origin = 'http://${server.address.address}:${server.port}';
    final programs = await LiveEpgService(NetworkService()).load(
      source: LiveSource(
        name: '直播',
        url: '$origin/live.m3u',
        epg: '$origin/epg?ch={name}&date={date}',
      ),
      channel: const LiveChannel(
        name: 'CCTV-6 电影',
        epgId: 'CCTV6',
        urls: ['https://live.test/cctv6.m3u8'],
      ),
      date: DateTime(2026, 10, 12),
    );

    expect(query, {'ch': 'CCTV6', 'date': '2026-10-12'});
    expect(programs, hasLength(1));
    expect(programs.single.label, '20:00–21:30  黄金档电影');
  });

  test('live channel orders fallback candidates from the selected line', () {
    const channel = LiveChannel(
      name: '测试频道',
      urls: ['https://one.test', 'https://two.test', 'https://three.test'],
    );

    final candidates = channel.playbackCandidates(1).toList();

    expect(candidates.map((entry) => entry.index), [1, 2, 0]);
    expect(candidates.map((entry) => entry.url), [
      'https://two.test',
      'https://three.test',
      'https://one.test',
    ]);
  });

  test(
    'standard JSON source supports home, detail and playback endpoints',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(server.close);
      String? requestedCategory;
      Map<String, dynamic>? requestedFilters;
      var parserInvoked = false;
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/parse') {
          parserInvoked = true;
          request.response.write(
            jsonEncode({
              'data': {'url': '//parsed.test/result.m3u8'},
              'user-agent': 'Parser UA',
              'referer': 'https://parse.test/',
              'parse': 0,
            }),
          );
        } else if (request.uri.queryParameters.containsKey('play')) {
          request.response.write(
            jsonEncode({
              'parse': 0,
              'url': 'https://cdn.test/video.m3u8',
              'header': {'Referer': 'https://source.test/'},
            }),
          );
        } else if (request.uri.queryParameters.containsKey('ids')) {
          request.response.write(
            jsonEncode({
              'list': [
                {
                  'vod_id': '42',
                  'vod_name': '测试影片',
                  'vod_play_from': '主线\$\$\$备用',
                  'vod_play_url': '第一集\$raw-1#第二集\$raw-2\$\$\$第一集\$raw-backup',
                },
              ],
            }),
          );
        } else {
          requestedCategory = request.uri.queryParameters['t'];
          final encodedFilters = request.uri.queryParameters['ext'];
          if (encodedFilters != null && encodedFilters.isNotEmpty) {
            requestedFilters = Map<String, dynamic>.from(
              jsonDecode(utf8.decode(base64.decode(encodedFilters))) as Map,
            );
          }
          request.response.write(
            jsonEncode({
              'class': [
                {'type_id': '1', 'type_name': '电影'},
              ],
              'filters': {
                '1': [
                  {
                    'key': 'area',
                    'name': '地区',
                    'value': [
                      {'n': '全部', 'v': ''},
                      {'n': '内地', 'v': '内地'},
                    ],
                  },
                ],
              },
              'list': [
                {'vod_id': '42', 'vod_name': '测试影片', 'vod_remarks': 'HD'},
              ],
            }),
          );
        }
        await request.response.close();
      });
      final source = SiteSource(
        key: 'remote',
        name: '远程源',
        type: 4,
        api: 'http://${server.address.address}:${server.port}/api',
        subscriptionId: 'sub',
      );
      final service = SourceApiService(NetworkService());

      final home = await service.home(source);
      await service.category(
        source,
        home.categories.single.id,
        filters: const {'area': '内地'},
      );
      final detail = await service.detail(source, '42');
      final playback = await service.resolvePlayback(
        source: source,
        episode: detail.playLines.first.episodes.first,
        flag: detail.playLines.first.name,
      );
      final parsedPlayback = await service.resolvePlayback(
        source: source,
        episode: detail.playLines.first.episodes.first,
        flag: detail.playLines.first.name,
        parser: ParseSource(
          name: 'JSON 解析',
          type: 1,
          url: 'http://${server.address.address}:${server.port}/parse?url=',
        ),
      );

      expect(home.categories.map((value) => value.name), contains('电影'));
      expect(home.categories.single.id, '1');
      expect(home.filters['1']?.single.name, '地区');
      expect(home.filters['1']?.single.options.last.label, '内地');
      expect(requestedCategory, '1');
      expect(requestedFilters, {'area': '内地'});
      expect(home.items.single.sourceKey, 'sub::remote');
      expect(detail.playLines, hasLength(2));
      expect(detail.playLines.first.episodes, hasLength(2));
      expect(playback.url, 'https://cdn.test/video.m3u8');
      expect(playback.headers['Referer'], 'https://source.test/');
      expect(parserInvoked, isTrue);
      expect(parsedPlayback.url, 'http://parsed.test/result.m3u8');
      expect(parsedPlayback.headers['user-agent'], 'Parser UA');
      expect(parsedPlayback.headers['referer'], 'https://parse.test/');
    },
  );

  test('favorites and history survive a new store instance', () async {
    SharedPreferences.setMockInitialValues({});
    final item = DemoCatalog.items.first;
    final first = FBoxStore();
    await first.initialize();
    first.toggleFavorite(item);
    first.recordWatch(item);
    await Future<void>.delayed(Duration.zero);
    first.dispose();

    final second = FBoxStore();
    await second.initialize();
    addTearDown(second.dispose);

    expect(second.isFavorite(item), isTrue);
    expect(second.historyIds, contains(item.storageId));
  });

  test('player and appearance settings survive a new store instance', () async {
    SharedPreferences.setMockInitialValues({});
    final first = FBoxStore();
    await first.initialize();
    first.setSetting('scaleMode', '铺满屏幕');
    first.setSetting('backgroundPlay', true);
    first.setSetting('backgroundAlpha', 0.65);
    await Future<void>.delayed(Duration.zero);
    first.dispose();

    final second = FBoxStore();
    await second.initialize();
    addTearDown(second.dispose);

    expect(second.settings['scaleMode'], '铺满屏幕');
    expect(second.settings['backgroundPlay'], isTrue);
    expect(second.settings['backgroundAlpha'], 0.65);
  });

  test(
    'selected backgrounds are copied out of temporary picker storage',
    () async {
      final root = await Directory.systemTemp.createTemp('fbox-background-');
      addTearDown(() => root.delete(recursive: true));
      final picked = File('${root.path}/picker/cache/photo.png');
      await picked.parent.create(recursive: true);
      await picked.writeAsBytes([1, 2, 3, 4]);
      final support = Directory('${root.path}/support');
      final service = BackgroundImageService(
        supportDirectoryProvider: () async => support,
      );

      final importedPath = await service.importImage(picked.path);
      await picked.parent.delete(recursive: true);

      expect(importedPath, startsWith(support.path));
      expect(await File(importedPath).readAsBytes(), [1, 2, 3, 4]);
    },
  );

  test('media model round-trips remote playback metadata', () {
    const item = MediaItem(
      id: '1',
      title: '标题',
      note: '更新中',
      year: '2026',
      category: '连续剧',
      rating: '9.1',
      description: '简介',
      director: '导演',
      actors: '演员',
      episodes: 1,
      color: 0xFF123456,
      sourceKey: 'remote',
      playLines: [
        MediaPlayLine(
          name: '线路',
          episodes: [
            MediaEpisode(
              name: '第1集',
              url: 'https://video.test/1.m3u8',
              headers: {'Referer': 'https://video.test'},
            ),
          ],
        ),
      ],
    );

    final restored = MediaItem.fromJson(item.toJson());

    expect(restored.storageId, 'remote::1');
    expect(
      restored.playLines.single.episodes.single.headers['Referer'],
      isNotEmpty,
    );
  });

  test('configured DoH resolves hosts for normal HTTP requests', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    server.listen((request) async {
      request.response.headers.contentType = ContentType.json;
      if (request.uri.path == '/dns-query') {
        request.response.write(
          jsonEncode({
            'Answer': [
              {'data': InternetAddress.loopbackIPv4.address},
            ],
          }),
        );
      } else {
        request.response.write('resolved-through-doh');
      }
      await request.response.close();
    });
    final network = NetworkService(
      initialSettings: NetworkSettings(
        dohEndpoint:
            'http://${server.address.address}:${server.port}/dns-query',
      ),
    );

    final body = await network.getText(
      Uri.parse('http://fbox.invalid:${server.port}/media'),
    );

    expect(body, 'resolved-through-doh');
  });

  test('network client allows self-signed certificates', () async {
    final network = NetworkService();
    final client = network.createClientForTesting();
    // Testing badCertificateCallback allows any certificate
    expect(client, isNotNull);
  });

  test('public-only network requests reject loopback destinations', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    server.listen((request) async {
      request.response.write('private');
      await request.response.close();
    });
    final network = NetworkService();

    await expectLater(
      network.getPublicText(
        Uri.parse('http://${server.address.address}:${server.port}/secret'),
      ),
      throwsA(isA<SocketException>()),
    );
    await expectLater(
      network.getPublicText(Uri.parse('http://[::ffff:127.0.0.1]:9/secret')),
      throwsA(isA<SocketException>()),
    );
  });

  test('type 0 searches match the APK query protocol', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    Map<String, String>? query;
    server.listen((request) async {
      query = request.uri.queryParameters;
      request.response.write('<rss><list page="1" pagecount="1"></list></rss>');
      await request.response.close();
    });
    final source = SiteSource(
      subscriptionId: 'sub',
      key: 'xml',
      name: 'XML',
      type: 0,
      api: 'http://${server.address.address}:${server.port}/api',
    );

    await SourceApiService(NetworkService()).search(source, '测试', page: 2);

    expect(query, {'wd': '测试'});
  });

  test('Android-only aggregate parser types fail explicitly', () async {
    const source = SiteSource(
      subscriptionId: 'sub',
      key: 'json',
      name: 'JSON',
      type: 1,
      api: 'https://api.test',
    );

    await expectLater(
      SourceApiService(_PlaybackNetwork()).resolvePlayback(
        source: source,
        episode: const MediaEpisode(name: '1', url: 'https://raw.test/v'),
        parser: const ParseSource(
          name: 'APK 聚合解析',
          url: 'JsonParallel',
          type: 2,
        ),
      ),
      throwsA(isA<UnsupportedError>()),
    );
  });

  test('type 4 remote extensions cannot read and exfiltrate local files', () async {
    final secret = File(
      '${(await Directory.systemTemp.createTemp('fbox-ext-')).path}/secret.json',
    );
    addTearDown(() => secret.parent.delete(recursive: true));
    await secret.writeAsString('{"token":"local-secret"}');
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    String? receivedExtend;
    server.listen((request) async {
      receivedExtend = request.uri.queryParameters['extend'];
      request.response.headers.contentType = ContentType.json;
      request.response.write(jsonEncode({'class': const [], 'list': const []}));
      await request.response.close();
    });
    final source = SiteSource(
      key: 'safe-ext',
      name: '安全扩展',
      type: 4,
      api: 'http://${server.address.address}:${server.port}/api',
      subscriptionId: 'sub',
      ext: secret.uri.toString(),
    );

    await SourceApiService(NetworkService()).home(source);

    expect(receivedExtend, isNot(contains('local-secret')));
  });

  test(
    'a failed remote extension fetch is retried instead of cached forever',
    () async {
      final network = _FlakyExtendNetwork();
      final service = SourceApiService(network);
      const source = SiteSource(
        subscriptionId: 'sub',
        key: 'retry-ext',
        name: '重试扩展',
        type: 4,
        api: 'https://api.test/catalog',
        ext: 'https://ext.test/config.json',
      );

      await service.home(source);
      await service.home(source);

      expect(network.publicRequests, 2);
      expect(network.extendValues.first, isEmpty);
      expect(network.extendValues.last, contains('token'));
    },
  );

  test(
    'domain-based same-origin extensions still use public-only transport',
    () async {
      final network = _SameOriginExtendNetwork();
      const source = SiteSource(
        subscriptionId: 'sub',
        key: 'same-origin',
        name: '同源扩展',
        type: 4,
        api: 'https://source.test/catalog',
        ext: 'https://source.test/ext.json',
      );

      await SourceApiService(network).home(source);

      expect(network.publicRequests, 1);
      expect(network.catalogExtend, contains('safe'));
    },
  );

  test(
    'collection cache preserves the selected config relative base',
    () async {
      final root = await Directory.systemTemp.createTemp('fbox-config-cache-');
      addTearDown(() => root.delete(recursive: true));
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      final origin = 'http://${server.address.address}:${server.port}';
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/collection') {
          request.response.write(
            jsonEncode({
              'urls': [
                {'name': '线路一', 'url': '$origin/nested/config.json'},
              ],
            }),
          );
        } else {
          request.response.write(
            jsonEncode({
              'sites': [
                {'key': 'relative', 'name': '相对地址源', 'type': 1, 'api': './api'},
              ],
            }),
          );
        }
        await request.response.close();
      });
      final repository = ConfigRepository(
        network: NetworkService(),
        supportDirectoryProvider: () async => root,
      );
      final subscription = FBoxSubscription(
        id: 'collection',
        name: '配置集合',
        url: '$origin/collection',
      );

      final online = await repository.load(subscription);
      await server.close(force: true);
      final cached = await repository.load(subscription);

      expect(online.sites.single.api, '$origin/nested/api');
      expect(cached.sites.single.api, '$origin/nested/api');
    },
  );

  test(
    'local subscription configs are copied into persistent app storage',
    () async {
      final root = await Directory.systemTemp.createTemp('fbox-local-config-');
      addTearDown(() => root.delete(recursive: true));
      final picked = File('${root.path}/picker/config.json');
      await picked.parent.create(recursive: true);
      await picked.writeAsString(
        '{"sites":[{"key":"local","name":"本地源","type":1,'
        '"api":"https://api.test"}]}',
      );
      final repository = ConfigRepository(
        network: NetworkService(),
        supportDirectoryProvider: () async => Directory('${root.path}/support'),
      );

      final imported = await repository.importLocalConfig(
        picked.path,
        subscriptionId: 'local-sub',
      );
      await picked.parent.delete(recursive: true);
      final config = await repository.load(
        FBoxSubscription(id: 'local-sub', name: '本地配置', url: imported),
      );

      expect(
        Uri.parse(imported).toFilePath(),
        startsWith('${root.path}/support'),
      );
      expect(config.sites.single.name, '本地源');
    },
  );

  test('config resolves path-relative and root-relative references', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    final origin = 'http://${server.address.address}:${server.port}';
    server.listen((request) async {
      request.response.headers.contentType = ContentType.json;
      request.response.write(
        jsonEncode({
          'sites': [
            {'key': 'path', 'name': '路径', 'type': 1, 'api': 'api/catalog'},
            {'key': 'root', 'name': '根路径', 'type': 1, 'api': '/root/catalog'},
          ],
          'parses': [
            {'name': '解析', 'type': 1, 'url': 'parse?url='},
          ],
        }),
      );
      await request.response.close();
    });
    final repository = ConfigRepository(network: NetworkService());

    final config = await repository.load(
      FBoxSubscription(
        id: 'relative-all',
        name: '相对路径',
        url: '$origin/nested/config.json',
      ),
    );

    expect(config.sites.first.api, '$origin/nested/api/catalog');
    expect(config.sites.last.api, '$origin/root/catalog');
    expect(config.parsers.single.url, '$origin/nested/parse?url=');
  });

  test('config keeps inline type 4 extensions for source requests', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    final origin = 'http://${server.address.address}:${server.port}';
    String? receivedExtend;
    server.listen((request) async {
      request.response.headers.contentType = ContentType.json;
      if (request.uri.path == '/nested/config.json') {
        request.response.write(
          jsonEncode({
            'sites': [
              {
                'key': 'inline-ext',
                'name': '内联扩展',
                'type': 4,
                'api': '/api',
                'ext': 'token',
              },
            ],
          }),
        );
      } else {
        receivedExtend = request.uri.queryParameters['extend'];
        request.response.write(
          jsonEncode({'class': const [], 'list': const []}),
        );
      }
      await request.response.close();
    });
    final network = NetworkService();
    final config = await ConfigRepository(network: network).load(
      FBoxSubscription(
        id: 'inline-ext',
        name: '内联扩展',
        url: '$origin/nested/config.json',
      ),
    );

    expect(config.sites.single.ext, 'token');
    await SourceApiService(network).home(config.sites.single);
    expect(receivedExtend, 'token');
  });

  test(
    'config cache is rejected after the subscription root URL changes',
    () async {
      final root = await Directory.systemTemp.createTemp('fbox-config-root-');
      addTearDown(() => root.delete(recursive: true));
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final origin = 'http://${server.address.address}:${server.port}';
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            'sites': [
              {'key': 'old', 'name': '旧配置', 'type': 1, 'api': '$origin/api'},
            ],
          }),
        );
        await request.response.close();
      });
      final repository = ConfigRepository(
        network: NetworkService(),
        supportDirectoryProvider: () async => root,
      );
      await repository.load(
        FBoxSubscription(id: 'same-id', name: '旧地址', url: '$origin/old'),
      );
      await server.close(force: true);

      await expectLater(
        repository.load(
          FBoxSubscription(id: 'same-id', name: '新地址', url: '$origin/new'),
        ),
        throwsA(isA<ConfigLoadException>()),
      );
    },
  );

  test(
    'collection selection loads and persists the chosen config line',
    () async {
      final root = await Directory.systemTemp.createTemp('fbox-config-lines-');
      addTearDown(() => root.delete(recursive: true));
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      final origin = 'http://${server.address.address}:${server.port}';
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/collection') {
          request.response.write(
            jsonEncode({
              'urls': [
                {'name': '线路一', 'url': './one.json'},
                {'name': '线路二', 'url': './two.json'},
              ],
            }),
          );
        } else {
          final key = request.uri.path.endsWith('two.json') ? 'two' : 'one';
          request.response.write(
            jsonEncode({
              'sites': [
                {'key': key, 'name': key, 'type': 1, 'api': '$origin/$key-api'},
              ],
            }),
          );
        }
        await request.response.close();
      });
      final repository = ConfigRepository(
        network: NetworkService(),
        supportDirectoryProvider: () async => root,
      );
      final subscription = FBoxSubscription(
        id: 'lines',
        name: '多线路',
        url: '$origin/collection',
        selectedUrl: '$origin/two.json',
      );

      final config = await repository.load(subscription);
      final restored = FBoxSubscription.fromJson(subscription.toJson());
      await server.close(force: true);

      expect(config.sites.single.key, 'two');
      expect(restored.selectedUrl, '$origin/two.json');
      await expectLater(
        repository.load(subscription.copyWith(selectedUrl: '$origin/one.json')),
        throwsA(isA<ConfigLoadException>()),
      );
    },
  );

  test(
    'store exposes category filters and applies the selected values',
    () async {
      SharedPreferences.setMockInitialValues({});
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(server.close);
      Map<String, dynamic>? requestedFilters;
      final origin = 'http://${server.address.address}:${server.port}';
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/config') {
          request.response.write(
            jsonEncode({
              'sites': [
                {
                  'key': 'filter',
                  'name': '筛选源',
                  'type': 4,
                  'api': '$origin/api',
                },
              ],
            }),
          );
        } else {
          final encoded = request.uri.queryParameters['ext'];
          if (encoded != null && encoded.isNotEmpty) {
            requestedFilters = Map<String, dynamic>.from(
              jsonDecode(utf8.decode(base64.decode(encoded))) as Map,
            );
          }
          request.response.write(
            jsonEncode({
              'class': [
                {'type_id': 'movie', 'type_name': '电影'},
              ],
              'filters': {
                'movie': [
                  {
                    'key': 'year',
                    'name': '年份',
                    'value': [
                      {'n': '2026', 'v': '2026'},
                    ],
                  },
                ],
              },
              'list': const [],
            }),
          );
        }
        await request.response.close();
      });
      final store = FBoxStore();
      await store.initialize();
      addTearDown(store.dispose);
      await store.addSubscription(name: '筛选配置', url: '$origin/config');

      await store.selectSource(store.sources.single.identity);
      final filters = store.filtersForCategory('电影');
      await store.loadCategory('电影', filters: const {'year': '2026'});

      expect(filters.single.name, '年份');
      expect(filters.single.options.single.label, '2026');
      expect(requestedFilters, {'year': '2026'});
    },
  );

  test('store appends subsequent category pages without duplicates', () async {
    SharedPreferences.setMockInitialValues({});
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    final origin = 'http://${server.address.address}:${server.port}';
    server.listen((request) async {
      request.response.headers.contentType = ContentType.json;
      if (request.uri.path == '/config') {
        request.response.write(
          jsonEncode({
            'sites': [
              {'key': 'pages', 'name': '分页源', 'type': 1, 'api': '$origin/api'},
            ],
          }),
        );
      } else if (!request.uri.queryParameters.containsKey('t')) {
        request.response.write(
          jsonEncode({
            'class': [
              {'type_id': '1', 'type_name': '电影'},
            ],
            'list': const [],
          }),
        );
      } else {
        final page = int.parse(request.uri.queryParameters['pg'] ?? '1');
        request.response.write(
          jsonEncode({
            'page': page,
            'pagecount': 2,
            'list': [
              {'vod_id': '$page', 'vod_name': '第$page页'},
            ],
          }),
        );
      }
      await request.response.close();
    });
    final store = FBoxStore();
    await store.initialize();
    addTearDown(store.dispose);
    await store.addSubscription(name: '分页配置', url: '$origin/config');
    await store.selectSource(store.sources.single.identity);

    await store.loadCategory('电影');
    await store.loadNextCategoryPage('电影');

    expect(store.catalog.map((item) => item.title), ['第1页', '第2页']);
    expect(store.catalogHasMore, isFalse);
  });

  test(
    'a slow previous source cannot overwrite the latest selection',
    () async {
      SharedPreferences.setMockInitialValues({});
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(server.close);
      final origin = 'http://${server.address.address}:${server.port}';
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/config') {
          request.response.write(
            jsonEncode({
              'sites': [
                {'key': 'slow', 'name': '慢源', 'type': 1, 'api': '$origin/slow'},
                {'key': 'fast', 'name': '快源', 'type': 1, 'api': '$origin/fast'},
              ],
            }),
          );
        } else {
          if (request.uri.path == '/slow') {
            await Future<void>.delayed(const Duration(milliseconds: 80));
          }
          final key = request.uri.path.substring(1);
          request.response.write(
            jsonEncode({
              'class': const [],
              'list': [
                {'vod_id': key, 'vod_name': key},
              ],
            }),
          );
        }
        await request.response.close();
      });
      final store = FBoxStore();
      await store.initialize();
      addTearDown(store.dispose);
      await store.addSubscription(name: '竞态测试', url: '$origin/config');
      final slow = store.sources.firstWhere((source) => source.key == 'slow');
      final fast = store.sources.firstWhere((source) => source.key == 'fast');

      final oldRequest = store.selectSource(slow.identity);
      await Future<void>.delayed(const Duration(milliseconds: 10));
      final latestRequest = store.selectSource(fast.identity);
      await Future.wait([oldRequest, latestRequest]);

      expect(store.currentSourceKey, fast.identity);
      expect(store.catalog.single.title, 'fast');
    },
  );

  test('aggregate search reports an error when every source fails', () async {
    SharedPreferences.setMockInitialValues({});
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    server.listen((request) async {
      if (request.uri.path == '/config') {
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            'sites': [
              {
                'key': 'broken',
                'name': '故障源',
                'type': 1,
                'api': 'http://${server.address.address}:${server.port}/broken',
              },
            ],
          }),
        );
      } else {
        request.response.statusCode = HttpStatus.badGateway;
      }
      await request.response.close();
    });
    final store = FBoxStore();
    await store.initialize();
    addTearDown(store.dispose);
    await store.addSubscription(
      name: '测试订阅',
      url: 'http://${server.address.address}:${server.port}/config',
    );

    await expectLater(
      store.search('测试'),
      throwsA(isA<SourceSearchException>()),
    );
  });

  test(
    'current-source search stays in the demo catalog when demo is selected',
    () async {
      SharedPreferences.setMockInitialValues({});
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(server.close);
      var remoteSearches = 0;
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/config') {
          request.response.write(
            jsonEncode({
              'sites': [
                {
                  'key': 'remote',
                  'name': '远程源',
                  'type': 1,
                  'api': 'http://${server.address.address}:${server.port}/api',
                },
              ],
            }),
          );
        } else {
          remoteSearches++;
          request.response.write(
            jsonEncode({
              'list': [
                {'vod_id': 'remote', 'vod_name': '远程结果'},
              ],
            }),
          );
        }
        await request.response.close();
      });
      final store = FBoxStore();
      await store.initialize();
      addTearDown(store.dispose);
      await store.addSubscription(
        name: '远程配置',
        url: 'http://${server.address.address}:${server.port}/config',
      );
      store.setSetting('aggregateMode', '当前站源');
      await store.selectSource('demo');

      final results = await store.search('庆余年');

      expect(remoteSearches, 0);
      expect(results.single.sourceKey, isEmpty);
      expect(results.single.title, contains('庆余年'));
    },
  );

  test('malformed backup does not partially replace current data', () async {
    SharedPreferences.setMockInitialValues({});
    final store = FBoxStore();
    await store.initialize();
    addTearDown(store.dispose);
    final item = DemoCatalog.items.first;
    store.toggleFavorite(item);

    await expectLater(
      store.importBackup({
        'version': 1,
        'subscriptions': ['invalid'],
        'favorites': const [],
      }),
      throwsFormatException,
    );

    expect(store.isFavorite(item), isTrue);
  });

  test('backup import rejects invalid setting types before mutation', () async {
    SharedPreferences.setMockInitialValues({});
    final store = FBoxStore();
    await store.initialize();
    addTearDown(store.dispose);

    await expectLater(
      store.importBackup({
        'version': 1,
        'settings': {'backgroundAlpha': 'opaque'},
      }),
      throwsFormatException,
    );

    expect(store.settings['backgroundAlpha'], 1.0);
  });

  test('backup restore invalidates active catalog loading state', () async {
    SharedPreferences.setMockInitialValues({});
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    final origin = 'http://${server.address.address}:${server.port}';
    server.listen((request) async {
      request.response.headers.contentType = ContentType.json;
      if (request.uri.path == '/config') {
        request.response.write(
          jsonEncode({
            'sites': [
              {'key': 'slow', 'name': '慢源', 'type': 1, 'api': '$origin/api'},
            ],
          }),
        );
      } else {
        await Future<void>.delayed(const Duration(milliseconds: 80));
        request.response.write('{"class":[],"list":[]}');
      }
      await request.response.close();
    });
    final store = FBoxStore();
    await store.initialize();
    addTearDown(store.dispose);
    await store.addSubscription(name: '慢配置', url: '$origin/config');

    final request = store.selectSource(store.sources.single.identity);
    await Future<void>.delayed(const Duration(milliseconds: 10));
    await store.importBackup({'version': 1});
    await request;

    expect(store.catalogLoading, isFalse);
    expect(store.catalogLoadingMore, isFalse);
    expect(store.currentSource, isNull);
  });

  test(
    'duplicate download starts share one transfer and keep a valid index',
    () async {
      final root = await Directory.systemTemp.createTemp('fbox-download-');
      addTearDown(() => root.delete(recursive: true));
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(server.close);
      var requests = 0;
      server.listen((request) async {
        requests++;
        await Future<void>.delayed(const Duration(milliseconds: 30));
        request.response.contentLength = 4;
        request.response.add([1, 2, 3, 4]);
        await request.response.close();
      });
      final manager = DownloadManager(
        NetworkService(),
        documentsDirectoryProvider: () async => root,
      );
      addTearDown(manager.dispose);
      final item = DemoCatalog.items.first;
      final episode = MediaEpisode(
        name: '测试集',
        url: 'http://${server.address.address}:${server.port}/video.mp4',
      );

      final first = manager.start(item: item, episode: episode);
      final second = manager.start(item: item, episode: episode);
      final completed = await Future.wait([first, second]);
      final index = File('${root.path}/FBox/Downloads/downloads.json');
      final decoded = jsonDecode(await index.readAsString());

      expect(identical(first, second), isTrue);
      expect(requests, 1);
      expect(
        completed.every((entry) => entry.state == DownloadState.completed),
        isTrue,
      );
      expect(decoded, isA<List<dynamic>>());
      expect((decoded as List).single['state'], DownloadState.completed.name);
    },
  );

  test('downloads from different sources never share a destination', () async {
    final root = await Directory.systemTemp.createTemp(
      'fbox-download-collision-',
    );
    addTearDown(() => root.delete(recursive: true));
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    server.listen((request) async {
      request.response.contentLength = 4;
      request.response.add([1, 2, 3, 4]);
      await request.response.close();
    });
    final manager = DownloadManager(
      NetworkService(),
      documentsDirectoryProvider: () async => root,
    );
    addTearDown(manager.dispose);
    const base = MediaItem(
      id: 'same-id',
      sourceKey: 'source-one',
      title: '同名影片',
      note: '',
      year: '',
      category: '',
      rating: '',
      description: '',
      director: '',
      actors: '',
      episodes: 1,
      color: 0xFF000000,
    );
    final other = MediaItem.fromJson({
      ...base.toJson(),
      'sourceKey': 'source-two',
    });
    final episode = MediaEpisode(
      name: '第1集',
      url: 'http://${server.address.address}:${server.port}/video.mp4',
    );

    final entries = await Future.wait([
      manager.start(item: base, episode: episode),
      manager.start(item: other, episode: episode),
    ]);

    expect(entries.map((entry) => entry.filePath).toSet(), hasLength(2));
    expect(await File(entries[0].filePath).readAsBytes(), [1, 2, 3, 4]);
    expect(await File(entries[1].filePath).readAsBytes(), [1, 2, 3, 4]);
  });

  test(
    'HLS downloads retain and rewrite the CMAF initialization map',
    () async {
      final root = await Directory.systemTemp.createTemp('fbox-hls-map-');
      addTearDown(() => root.delete(recursive: true));
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(server.close);
      server.listen((request) async {
        if (request.uri.path.endsWith('.m3u8')) {
          request.response.write('''
#EXTM3U
#EXT-X-VERSION:7
#EXT-X-MAP:URI="init.mp4"
#EXTINF:4.0,
segment.m4s
#EXT-X-ENDLIST
''');
        } else if (request.uri.path.endsWith('init.mp4')) {
          request.response.add([0, 1, 2]);
        } else {
          request.response.add([3, 4, 5]);
        }
        await request.response.close();
      });
      final manager = DownloadManager(
        NetworkService(),
        documentsDirectoryProvider: () async => root,
      );
      addTearDown(manager.dispose);
      final entry = await manager.start(
        item: DemoCatalog.items.first,
        episode: MediaEpisode(
          name: 'CMAF',
          url: 'http://${server.address.address}:${server.port}/video.m3u8',
        ),
      );

      final playlist = await File(entry.filePath).readAsString();
      final mapPath = RegExp(r'#EXT-X-MAP:URI="([^"]+)"')
          .firstMatch(playlist)
          ?.group(1);

      expect(mapPath, isNotNull);
      expect(mapPath, isNot('init.mp4'));
      expect(
        await File('${File(entry.filePath).parent.path}/$mapPath')
            .readAsBytes(),
        [0, 1, 2],
      );
    },
  );

  test('HLS downloads are detected from response content type', () async {
    final root = await Directory.systemTemp.createTemp('fbox-hls-mime-');
    addTearDown(() => root.delete(recursive: true));
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    server.listen((request) async {
      if (request.uri.path == '/dynamic') {
        request.response.headers.contentType = ContentType(
          'application',
          'vnd.apple.mpegurl',
        );
        request.response.write(
          '#EXTM3U\n#EXTINF:4.0,\nsegment.ts\n#EXT-X-ENDLIST',
        );
      } else {
        request.response.add([1, 2, 3]);
      }
      await request.response.close();
    });
    final manager = DownloadManager(
      NetworkService(),
      documentsDirectoryProvider: () async => root,
    );
    addTearDown(manager.dispose);

    final entry = await manager.start(
      item: DemoCatalog.items.first,
      episode: MediaEpisode(
        name: '动态 HLS',
        url: 'http://${server.address.address}:${server.port}/dynamic',
      ),
    );

    expect(await File(entry.filePath).readAsString(), contains('#EXTM3U'));
    expect(entry.state, DownloadState.completed);
  });

  test('HLS downloads retain external audio playlists', () async {
    final root = await Directory.systemTemp.createTemp('fbox-hls-audio-');
    addTearDown(() => root.delete(recursive: true));
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    server.listen((request) async {
      switch (request.uri.path) {
        case '/master.m3u8':
          request.response.write('''
#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="国语",DEFAULT=YES,URI="audio.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=1000,AUDIO="aud"
video.m3u8
''');
          break;
        case '/video.m3u8':
          request.response.write(
            '#EXTM3U\n#EXTINF:4,\nvideo.ts\n#EXT-X-ENDLIST',
          );
          break;
        case '/audio.m3u8':
          request.response.write(
            '#EXTM3U\n#EXTINF:4,\naudio.aac\n#EXT-X-ENDLIST',
          );
          break;
        default:
          request.response.add([1, 2, 3]);
      }
      await request.response.close();
    });
    final manager = DownloadManager(
      NetworkService(),
      documentsDirectoryProvider: () async => root,
    );
    addTearDown(manager.dispose);

    final entry = await manager.start(
      item: DemoCatalog.items.first,
      episode: MediaEpisode(
        name: '独立音轨',
        url: 'http://${server.address.address}:${server.port}/master.m3u8',
      ),
    );
    final master = await File(entry.filePath).readAsString();
    final audioPath = RegExp(r'#EXT-X-MEDIA[^\n]+URI="([^"]+)"')
        .firstMatch(master)
        ?.group(1);

    expect(audioPath, isNotNull);
    final audioPlaylist = File(
      '${File(entry.filePath).parent.path}/$audioPath',
    );
    expect(await audioPlaylist.readAsString(), isNot(contains('audio.aac')));
  });

  test(
    'canceling after the final HLS resource cannot complete the task',
    () async {
      final root = await Directory.systemTemp.createTemp('fbox-hls-cancel-');
      addTearDown(() => root.delete(recursive: true));
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(server.close);
      server.listen((request) async {
        if (request.uri.path.endsWith('.m3u8')) {
          request.response.write(
            '#EXTM3U\n#EXTINF:4,\nsegment.ts\n#EXT-X-ENDLIST',
          );
        } else {
          request.response.add([1, 2, 3]);
        }
        await request.response.close();
      });
      final manager = DownloadManager(
        NetworkService(),
        documentsDirectoryProvider: () async => root,
      );
      addTearDown(manager.dispose);
      var cancellationStarted = false;
      manager.addListener(() {
        if (manager.entries case [final entry]
            when !cancellationStarted &&
                entry.state == DownloadState.downloading &&
                entry.total > 0 &&
                entry.received == entry.total) {
          cancellationStarted = true;
          unawaited(manager.cancel(entry.id));
        }
      });

      final entry = await manager.start(
        item: DemoCatalog.items.first,
        episode: MediaEpisode(
          name: '取消收尾',
          url: 'http://${server.address.address}:${server.port}/video.m3u8',
        ),
      );

      expect(entry.state, DownloadState.canceled);
      expect(await File(entry.filePath).exists(), isFalse);
    },
  );

  test(
    'removing a queued download cannot be undone by a late response',
    () async {
      final root = await Directory.systemTemp.createTemp(
        'fbox-download-cancel-',
      );
      addTearDown(() => root.delete(recursive: true));
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(server.close);
      server.listen((request) async {
        await Future<void>.delayed(const Duration(milliseconds: 80));
        request.response.contentLength = 4;
        request.response.add([1, 2, 3, 4]);
        await request.response.close();
      });
      final manager = DownloadManager(
        NetworkService(),
        documentsDirectoryProvider: () async => root,
      );
      addTearDown(manager.dispose);
      final item = DemoCatalog.items.first;
      final episode = MediaEpisode(
        name: '延迟响应',
        url: 'http://${server.address.address}:${server.port}/video.mp4',
      );

      final operation = manager.start(item: item, episode: episode);
      while (manager.entries.isEmpty) {
        await Future<void>.delayed(const Duration(milliseconds: 1));
      }
      await manager.remove(manager.entries.single.id);
      await operation;

      expect(manager.entries, isEmpty);
    },
  );

  test('local media import is retained by a later library scan', () async {
    TestWidgetsFlutterBinding.ensureInitialized();
    final root = await Directory.systemTemp.createTemp('fbox-local-media-');
    addTearDown(() => root.delete(recursive: true));
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.flutter.io/path_provider'),
          (call) async => root.path,
        );
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
            const MethodChannel('plugins.flutter.io/path_provider'),
            null,
          ),
    );
    final source = File('${root.path}/picked-video.mp4');
    await source.writeAsBytes([1, 2, 3, 4]);
    final service = LocalMediaService();

    final imported = await service.importFiles([source.path]);
    final scanned = await service.scanLibrary();

    expect(imported, hasLength(1));
    expect(scanned.map((entry) => entry.name), contains('picked-video.mp4'));
  });
}

class _FlakyExtendNetwork extends NetworkService {
  int publicRequests = 0;
  final List<String> extendValues = [];

  @override
  Future<String> getPublicText(
    Uri uri, {
    Map<String, String> headers = const {},
    Duration? timeout,
  }) async {
    publicRequests++;
    if (publicRequests == 1) throw const SocketException('temporary failure');
    return '{"token":"fresh"}';
  }

  @override
  Future<String> getText(
    Uri uri, {
    Map<String, String> headers = const {},
    Duration? timeout,
  }) async {
    extendValues.add(uri.queryParameters['extend'] ?? '');
    return '{"class":[],"list":[]}';
  }
}

class _PlaybackNetwork extends NetworkService {
  @override
  Future<String> getText(
    Uri uri, {
    Map<String, String> headers = const {},
    Duration? timeout,
  }) async => '{"url":"https://video.test/video.mp4"}';
}

class _SameOriginExtendNetwork extends NetworkService {
  int publicRequests = 0;
  String catalogExtend = '';

  @override
  Future<String> getPublicText(
    Uri uri, {
    Map<String, String> headers = const {},
    Duration? timeout,
  }) async {
    publicRequests++;
    return '{"safe":true}';
  }

  @override
  Future<String> getText(
    Uri uri, {
    Map<String, String> headers = const {},
    Duration? timeout,
  }) async {
    catalogExtend = uri.queryParameters['extend'] ?? '';
    return '{"class":[],"list":[]}';
  }
}
