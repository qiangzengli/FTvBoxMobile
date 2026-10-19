import 'dart:convert';
import 'dart:io';

import 'package:fbox/domain/media_item.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:fbox/services/network_service.dart';
import 'package:flutter/services.dart';
import 'package:xml/xml.dart';

class SourceApiService {
  SourceApiService(this.network);

  final NetworkService network;
  String spiderBridgeUrl = '';
  final Map<String, _ExtendCacheEntry> _extendCache = {};
  static const _extendCacheTtl = Duration(minutes: 10);
  static const _spiderChannel = MethodChannel('com.fbox.app/spider');
  static const _bridgeChannel = MethodChannel('com.fbox.app/spider_bridge');

  bool supports(SiteSource source) {
    if (source.isSupported) return true;
    if (source.type != 3 || source.jar.isEmpty || !Platform.isIOS) {
      return false;
    }
    final bridge = Uri.tryParse(spiderBridgeUrl.trim());
    return bridge != null &&
        {'http', 'https'}.contains(bridge.scheme) &&
        bridge.host.isNotEmpty &&
        bridge.pathSegments.where((segment) => segment.isNotEmpty).isNotEmpty;
  }

  Future<String> _spider(
    SiteSource source,
    String operation, [
    Map<String, Object?> arguments = const {},
  ]) async {
    final request = <String, Object?>{
      'jar': source.jar,
      'api': source.api,
      'key': source.identity,
      'ext': source.ext,
      'operation': operation,
      ...arguments,
    };
    final timeout = Duration(seconds: source.timeoutSeconds + 45);
    final String? response;
    if (Platform.isIOS && spiderBridgeUrl.trim().isNotEmpty) {
      final base = Uri.parse(spiderBridgeUrl.trim());
      final endpoint = base.replace(
        pathSegments: [
          ...base.pathSegments.where((part) => part.isNotEmpty),
          'invoke',
        ],
      );
      final body = await network.postText(
        endpoint,
        body: jsonEncode(request),
        headers: const {'Content-Type': 'application/json; charset=utf-8'},
        timeout: timeout,
        bypassProxy: true,
      );
      final decoded = jsonDecode(body);
      if (decoded is! Map) throw const FormatException('Spider 转发返回格式错误');
      if (decoded['error'] != null) {
        throw StateError('Spider 转发失败：${decoded['error']}');
      }
      response = decoded['value']?.toString();
    } else {
      response = await _spiderChannel
          .invokeMethod<String>('invoke', request)
          .timeout(timeout);
    }
    if (response == null || response.trim().isEmpty) {
      throw FormatException('站源“${source.name}”没有返回内容');
    }
    return response;
  }

  Future<SourceCatalog> home(SiteSource source) async {
    _requireSupported(source);
    final body = source.type == 3
        ? await _spider(source, 'home')
        : source.type == 4
        ? await _request(source, {
            'filter': 'true',
            if (source.ext.isNotEmpty) 'extend': await _extendFor(source),
          })
        : await network.getText(
            Uri.parse(source.api),
            timeout: Duration(seconds: source.timeoutSeconds),
          );
    final catalog = source.type == 0
        ? _parseXmlCatalog(body, source)
        : _parseJsonCatalog(body, source);
    if (source.type != 3 || catalog.items.isNotEmpty) return catalog;
    try {
      final videos = _parseJsonCatalog(
        await _spider(source, 'homeVideo'),
        source,
      );
      if (videos.items.isEmpty) return catalog;
      return SourceCatalog(
        categories: catalog.categories,
        items: videos.items,
        page: catalog.page,
        pageCount: catalog.pageCount,
        filters: catalog.filters,
      );
    } catch (_) {
      return catalog;
    }
  }

  Future<SourceCatalog> category(
    SiteSource source,
    String category, {
    int page = 1,
    Map<String, String> filters = const {},
  }) async {
    _requireSupported(source);
    final params = <String, String>{
      'ac': source.type == 0 ? 'videolist' : 'detail',
      't': category,
      'pg': '$page',
    };
    if (source.type == 4) {
      params['filter'] = 'true';
      params['ext'] = base64.encode(utf8.encode(jsonEncode(filters)));
      if (source.ext.isNotEmpty) {
        params['extend'] = await _extendFor(source);
      }
    } else if (filters.isNotEmpty) {
      params
        ..addAll(filters)
        ..['f'] = jsonEncode(filters);
    }
    final body = source.type == 3
        ? await _spider(source, 'category', {
            'category': category,
            'page': '$page',
            'filters': filters,
          })
        : await _request(source, params);
    return source.type == 0
        ? _parseXmlCatalog(body, source)
        : _parseJsonCatalog(body, source);
  }

  Future<List<MediaItem>> search(
    SiteSource source,
    String query, {
    int page = 1,
  }) async {
    _requireSupported(source);
    final params = <String, String>{
      'wd': query,
      if (source.type != 0) 'ac': 'detail',
      if (source.type != 0) 'pg': '$page',
    };
    if (source.type == 4) {
      params['quick'] = 'false';
      if (source.ext.isNotEmpty) {
        params['extend'] = await _extendFor(source);
      }
    }
    final body = source.type == 3
        ? await _spider(source, 'search', {'query': query, 'page': '$page'})
        : await _request(source, params);
    final catalog = source.type == 0
        ? _parseXmlCatalog(body, source)
        : _parseJsonCatalog(body, source);
    return catalog.items;
  }

  Future<MediaItem> detail(SiteSource source, String id) async {
    _requireSupported(source);
    final params = <String, String>{
      'ac': source.type == 0 ? 'videolist' : 'detail',
      'ids': id,
    };
    if (source.type == 4 && source.ext.isNotEmpty) {
      params['extend'] = await _extendFor(source);
    }
    final body = source.type == 3
        ? await _spider(source, 'detail', {'id': id})
        : await _request(source, params);
    final catalog = source.type == 0
        ? _parseXmlCatalog(body, source)
        : _parseJsonCatalog(body, source);
    if (catalog.items.isEmpty) throw const FormatException('站源未返回影片详情');
    return catalog.items.first;
  }

  Future<PlaybackRequest> resolvePlayback({
    required SiteSource source,
    required MediaEpisode episode,
    String flag = '',
    ParseSource? parser,
  }) async {
    var result = PlaybackRequest(url: episode.url, headers: episode.headers);
    if (source.type == 3) {
      final body = await _spider(source, 'player', {
        'id': episode.url,
        'flag': flag,
      });
      result = _parsePlayback(body, fallback: result);
    } else if (source.type == 4) {
      final body = await _request(source, {
        'play': episode.url,
        'flag': flag,
        if (source.ext.isNotEmpty) 'extend': await _extendFor(source),
      });
      result = _parsePlayback(body, fallback: result);
    } else if (source.playUrl.isNotEmpty) {
      result = PlaybackRequest(
        url: '${source.playUrl}${Uri.encodeComponent(episode.url)}',
        headers: episode.headers,
        parseRequired: true,
      );
    }

    if (parser != null) {
      if (parser.type != 1) {
        throw UnsupportedError(
          '解析线路“${parser.name}”使用 APK Android 专用聚合类型 ${parser.type}，'
          '当前跨平台版本仅支持网页解析（type 0）和 JSON 解析（type 1）。',
        );
      }
      final baseUrl = parser.requestUrl;
      final target = baseUrl.contains('{url}')
          ? baseUrl.replaceAll('{url}', Uri.encodeComponent(result.url))
          : '$baseUrl${Uri.encodeComponent(result.url)}';
      final body = await network.getText(
        Uri.parse(target),
        headers: parser.ext,
        timeout: Duration(seconds: source.timeoutSeconds),
      );
      result = _parsePlayback(body, fallback: result);
    }
    return source.type == 3 ? _normalizeSpiderPlayback(source, result) : result;
  }

  Future<PlaybackRequest> _normalizeSpiderPlayback(
    SiteSource source,
    PlaybackRequest result,
  ) async {
    if (result.url.startsWith('video://')) {
      return PlaybackRequest(
        url: result.url.substring('video://'.length),
        headers: result.headers,
        parseRequired: result.parseRequired,
      );
    }
    if (!result.url.startsWith('proxy://')) return result;
    final baseText = Platform.isIOS
        ? spiderBridgeUrl.trim()
        : await _bridgeChannel.invokeMethod<String>('localAddress') ?? '';
    if (baseText.isEmpty) {
      throw const FormatException('Spider 代理服务尚未启动');
    }
    final base = Uri.parse(baseText);
    final query = result.url.substring('proxy://'.length);
    final separator = query.isEmpty ? '' : '&';
    final endpoint = base.replace(
      pathSegments: [
        ...base.pathSegments.where((part) => part.isNotEmpty),
        'proxy',
      ],
      query:
          '$query${separator}siteKey=${Uri.encodeQueryComponent(source.identity)}',
    );
    return PlaybackRequest(url: endpoint.toString(), headers: result.headers);
  }

  SourceCatalog _parseJsonCatalog(String body, SiteSource source) {
    final decoded = jsonDecode(_trimJson(body));
    if (decoded is! Map) throw const FormatException('站源响应不是 JSON 对象');
    final root = Map<String, dynamic>.from(decoded);
    final rawList = switch (root['list']) {
      List value => value,
      _ when root['data'] is List => root['data'] as List,
      _ when root['data'] is Map && (root['data'] as Map)['list'] is List =>
        (root['data'] as Map)['list'] as List,
      _ => const <dynamic>[],
    };
    final categories = <SourceCategory>[];
    for (final value in _asList(root['class'])) {
      if (value is! Map) continue;
      final name = (value['type_name'] ?? value['name'])?.toString() ?? '';
      final id = (value['type_id'] ?? value['id'] ?? name).toString();
      if (name.isNotEmpty && id.isNotEmpty) {
        categories.add(SourceCategory(id: id, name: name));
      }
    }
    final items = rawList
        .whereType<Map>()
        .map((value) => _mediaFromMap(value, source))
        .where((item) => item.id.isNotEmpty && item.title.isNotEmpty)
        .toList();
    return SourceCatalog(
      categories: categories,
      items: items,
      page: _asInt(root['page'], 1),
      pageCount: _asInt(root['pagecount'], 1),
      filters: _parseFilters(root['filters']),
    );
  }

  static Map<String, List<SourceFilter>> _parseFilters(dynamic raw) {
    if (raw is! Map) return const {};
    final result = <String, List<SourceFilter>>{};
    for (final entry in raw.entries) {
      final filters = <SourceFilter>[];
      final definitions = entry.value is List
          ? entry.value as List
          : <dynamic>[entry.value];
      for (final definition in definitions.whereType<Map>()) {
        final key = definition['key']?.toString() ?? '';
        final name = definition['name']?.toString() ?? key;
        if (key.isEmpty) continue;
        final options = <SourceFilterOption>[];
        for (final option in _asList(definition['value'])) {
          if (option is! Map) continue;
          final label = option['n']?.toString() ?? '';
          final value = option['v']?.toString() ?? '';
          if (label.isNotEmpty) {
            options.add(SourceFilterOption(label: label, value: value));
          }
        }
        if (options.isNotEmpty) {
          filters.add(SourceFilter(key: key, name: name, options: options));
        }
      }
      if (filters.isNotEmpty) result['${entry.key}'] = filters;
    }
    return result;
  }

  SourceCatalog _parseXmlCatalog(String body, SiteSource source) {
    final document = XmlDocument.parse(body);
    final categories = document
        .findAllElements('ty')
        .map((node) {
          final name = node.innerText.trim();
          return SourceCategory(
            id: node.getAttribute('id') ?? name,
            name: name,
          );
        })
        .where((category) => category.name.isNotEmpty)
        .toList();
    final videos = document
        .findAllElements('video')
        .map((video) {
          final map = <String, dynamic>{};
          for (final child in video.childElements) {
            if (child.name.local == 'dl') continue;
            map[child.name.local] = child.innerText.trim();
          }
          final lines = <MediaPlayLine>[];
          for (final dd in video.findAllElements('dd')) {
            lines.add(
              MediaPlayLine(
                name: dd.getAttribute('flag') ?? '默认线路',
                episodes: _parseEpisodes(dd.innerText),
              ),
            );
          }
          return _mediaFromMap(map, source, playLines: lines);
        })
        .where((item) => item.id.isNotEmpty && item.title.isNotEmpty)
        .toList();
    final list = document.findAllElements('list').firstOrNull;
    return SourceCatalog(
      categories: categories,
      items: videos,
      page: _asInt(list?.getAttribute('page'), 1),
      pageCount: _asInt(list?.getAttribute('pagecount'), 1),
    );
  }

  MediaItem _mediaFromMap(
    Map<dynamic, dynamic> value,
    SiteSource source, {
    List<MediaPlayLine>? playLines,
  }) {
    final id = (value['vod_id'] ?? value['id'])?.toString() ?? '';
    final title = (value['vod_name'] ?? value['name'])?.toString() ?? '';
    final from =
        (value['vod_play_from'] ?? value['play_from'])?.toString() ?? '';
    final urls = (value['vod_play_url'] ?? value['play_url'])?.toString() ?? '';
    final poster = _parseUrlHeaders(
      (value['vod_pic'] ?? value['pic'])?.toString() ?? '',
    );
    final lines = playLines ?? _parsePlayLines(from, urls);
    final year = (value['vod_year'] ?? value['year'])?.toString() ?? '';
    final category =
        (value['type_name'] ?? value['type'] ?? value['vod_class'])
            ?.toString() ??
        '';
    return MediaItem(
      id: id,
      detailId: id,
      sourceKey: source.identity,
      title: title,
      note:
          (value['vod_remarks'] ?? value['note'] ?? value['state'])
              ?.toString() ??
          '',
      year: year,
      category: category,
      rating: (value['vod_douban_score'] ?? value['vod_score'] ?? '')
          .toString(),
      description:
          (value['vod_content'] ?? value['vod_blurb'] ?? value['des'])
              ?.toString() ??
          '',
      director: (value['vod_director'] ?? value['director'])?.toString() ?? '',
      actors: (value['vod_actor'] ?? value['actor'])?.toString() ?? '',
      episodes: lines.isEmpty
          ? 1
          : lines
                .map((line) => line.episodes.length)
                .fold(0, (a, b) => a > b ? a : b),
      color: _colorFor('$id$title'),
      posterUrl: poster.url,
      headers: poster.headers,
      playLines: lines,
    );
  }

  static List<MediaPlayLine> _parsePlayLines(String from, String urls) {
    if (urls.trim().isEmpty) return const [];
    final names = from.split(r'$$$');
    final groups = urls.split(r'$$$');
    return [
      for (var index = 0; index < groups.length; index++)
        MediaPlayLine(
          name: index < names.length && names[index].trim().isNotEmpty
              ? names[index].trim()
              : '线路 ${index + 1}',
          episodes: _parseEpisodes(groups[index]),
        ),
    ];
  }

  static List<MediaEpisode> _parseEpisodes(String input) {
    final episodes = <MediaEpisode>[];
    for (final raw in input.split('#')) {
      final entry = raw.trim();
      if (entry.isEmpty) continue;
      final separator = entry.indexOf(r'$');
      final name = separator > 0
          ? entry.substring(0, separator).trim()
          : '${episodes.length + 1}';
      final rawUrl = separator > 0
          ? entry.substring(separator + 1).trim()
          : entry;
      final parsed = _parseUrlHeaders(rawUrl);
      episodes.add(
        MediaEpisode(name: name, url: parsed.url, headers: parsed.headers),
      );
    }
    return episodes;
  }

  static ({String url, Map<String, String> headers}) _parseUrlHeaders(
    String raw,
  ) {
    const marker = '@Headers=';
    final index = raw.indexOf(marker);
    if (index >= 0) {
      final url = raw.substring(0, index);
      final encoded = raw.substring(index + marker.length);
      try {
        final json = jsonDecode(
          utf8.decode(base64.decode(base64.normalize(encoded))),
        );
        if (json is Map) {
          return (
            url: url,
            headers: json.map((key, value) => MapEntry('$key', '$value')),
          );
        }
      } on FormatException {
        // Invalid header suffixes must not make the URL itself unusable.
      }
      return (url: url, headers: const {});
    }
    final suffix = RegExp(
      r'@(Referer|User-Agent|Cookie|Origin|Authorization)=',
      caseSensitive: false,
    );
    final matches = suffix.allMatches(raw).toList();
    if (matches.isEmpty) return (url: raw, headers: const {});
    final headers = <String, String>{};
    for (var position = 0; position < matches.length; position++) {
      final match = matches[position];
      final end = position + 1 < matches.length
          ? matches[position + 1].start
          : raw.length;
      final value = raw.substring(match.end, end);
      try {
        headers[match.group(1)!] = Uri.decodeComponent(value);
      } on FormatException {
        headers[match.group(1)!] = value;
      }
    }
    return (url: raw.substring(0, matches.first.start), headers: headers);
  }

  static PlaybackRequest _parsePlayback(
    String body, {
    required PlaybackRequest fallback,
  }) {
    final text = body.trim();
    if (text.startsWith('proxy://') || text.startsWith('video://')) {
      return PlaybackRequest(url: text, headers: fallback.headers);
    }
    if (_looksPlayable(text)) {
      final parsed = _parseUrlHeaders(text);
      return PlaybackRequest(
        url: parsed.url,
        headers: {...fallback.headers, ...parsed.headers},
      );
    }
    try {
      final decoded = jsonDecode(_trimJson(text));
      if (decoded is! Map) return fallback;
      final data = decoded['data'];
      final nestedUrl = data is Map ? data['url']?.toString() : null;
      var url = nestedUrl ?? decoded['url']?.toString() ?? fallback.url;
      if (url.startsWith('//')) url = 'http:$url';
      final parsed = _parseUrlHeaders(url);
      final rawHeaders = decoded['header'] ?? decoded['headers'];
      return PlaybackRequest(
        url: parsed.url,
        headers: {
          ...fallback.headers,
          ...parsed.headers,
          if (rawHeaders is Map)
            ...rawHeaders.map((key, value) => MapEntry('$key', '$value')),
          if ((decoded['user-agent']?.toString() ?? '').trim().isNotEmpty)
            HttpHeaders.userAgentHeader: decoded['user-agent']
                .toString()
                .trim(),
          if ((decoded['referer']?.toString() ?? '').trim().isNotEmpty)
            HttpHeaders.refererHeader: decoded['referer'].toString().trim(),
        },
        parseRequired: _asInt(decoded['parse'], 0) == 1,
      );
    } on FormatException {
      return fallback;
    }
  }

  static bool _looksPlayable(String value) {
    final uri = Uri.tryParse(value.trim());
    if (uri == null) return false;
    return const {'http', 'https', 'file', 'rtsp', 'rtmp'}.contains(uri.scheme);
  }

  static Uri _withQuery(String base, Map<String, String> values) {
    final uri = Uri.parse(base);
    return uri.replace(queryParameters: {...uri.queryParameters, ...values});
  }

  Future<String> _request(SiteSource source, Map<String, String> parameters) {
    final uri = _withQuery(source.api, parameters);
    if (source.type != 4 || uri.toString().length < 1800) {
      return network.getText(
        uri,
        timeout: Duration(seconds: source.timeoutSeconds),
      );
    }
    return network.postText(
      Uri.parse(source.api),
      body: Uri(queryParameters: parameters).query,
      headers: const {
        HttpHeaders.contentTypeHeader: 'application/x-www-form-urlencoded',
      },
      timeout: Duration(seconds: source.timeoutSeconds),
    );
  }

  Future<String> _extendFor(SiteSource source) async {
    final cacheKey = '${source.identity}|${source.ext}';
    final now = DateTime.now();
    final cached = _extendCache[cacheKey];
    if (cached != null && now.difference(cached.createdAt) < _extendCacheTtl) {
      try {
        return await cached.value;
      } on Object {
        if (identical(_extendCache[cacheKey], cached)) {
          _extendCache.remove(cacheKey);
        }
        return '';
      }
    }
    final entry = _ExtendCacheEntry(now, _loadExtend(source));
    _extendCache[cacheKey] = entry;
    try {
      return await entry.value;
    } on Object {
      if (identical(_extendCache[cacheKey], entry)) {
        _extendCache.remove(cacheKey);
      }
      return '';
    }
  }

  Future<String> _loadExtend(SiteSource source) async {
    final value = source.ext.trim();
    if (value.isEmpty) return '';
    final uri = Uri.tryParse(value);
    if (uri == null || !{'http', 'https'}.contains(uri.scheme)) {
      return _inlineExtend(value);
    }
    final api = Uri.tryParse(source.api);
    final sameOrigin =
        api != null &&
        api.scheme == uri.scheme &&
        api.host == uri.host &&
        api.port == uri.port;
    if (!sameOrigin && (uri.scheme != 'https' || uri.host.isEmpty)) return '';
    final explicitlyLocal =
        uri.host.toLowerCase() == 'localhost' ||
        InternetAddress.tryParse(uri.host) != null;
    final content = sameOrigin && explicitlyLocal
        ? await network.getText(
            uri,
            timeout: Duration(seconds: source.timeoutSeconds),
          )
        : await network.getPublicText(
            uri,
            timeout: Duration(seconds: source.timeoutSeconds),
          );
    final trimmed = content.trim();
    if (trimmed.isEmpty || trimmed.length > 2500) return '';
    return _inlineExtend(trimmed);
  }

  static String _inlineExtend(String value) {
    try {
      return jsonEncode(jsonDecode(value));
    } on FormatException {
      return value;
    }
  }

  static String _trimJson(String input) {
    final first = input.indexOf('{');
    final last = input.lastIndexOf('}');
    return first >= 0 && last > first
        ? input.substring(first, last + 1)
        : input;
  }

  static List<dynamic> _asList(Object? value) =>
      value is List ? value : const [];

  static int _asInt(Object? value, int fallback) =>
      int.tryParse(value?.toString() ?? '') ?? fallback;

  static int _colorFor(String value) {
    const palette = [
      0xFF48627A,
      0xFF8E594F,
      0xFF40655B,
      0xFF785165,
      0xFF765842,
      0xFF3A6577,
    ];
    return palette[value.hashCode.abs() % palette.length];
  }

  void _requireSupported(SiteSource source) {
    if (supports(source)) return;
    throw UnsupportedError('站源“${source.name}”的 Spider 包无法在当前平台运行。');
  }
}

class _ExtendCacheEntry {
  const _ExtendCacheEntry(this.createdAt, this.value);

  final DateTime createdAt;
  final Future<String> value;
}
