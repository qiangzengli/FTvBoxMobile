import 'dart:convert';

import 'package:fbox/domain/source_models.dart';

class MBoxConfigParser {
  const MBoxConfigParser();

  List<({String name, String url})> parseCollection(String input) {
    final decoded = _decodeObject(input);
    if (decoded.containsKey('sites') || decoded['urls'] is! List) {
      return const [];
    }
    return (decoded['urls'] as List)
        .map((entry) {
          if (entry is String) return (name: '', url: entry.trim());
          if (entry is Map) {
            return (
              name: entry['name']?.toString().trim() ?? '',
              url: (entry['url'] ?? entry['api'])?.toString().trim() ?? '',
            );
          }
          return (name: '', url: '');
        })
        .where((entry) => entry.url.isNotEmpty)
        .toList();
  }

  FBoxConfig parse({required String subscriptionId, required String input}) {
    final root = _decodeObject(input);
    final rootSpider = root['spider']?.toString().trim() ?? '';
    final sites = <SiteSource>[];
    for (final raw in _list(root['sites'])) {
      if (raw is! Map) continue;
      final key = raw['key']?.toString().trim() ?? '';
      final api = raw['api']?.toString().trim() ?? '';
      final type = _asInt(raw['type'], -1);
      if (key.isEmpty || api.isEmpty || type < 0) continue;
      sites.add(
        SiteSource(
          key: key,
          name: raw['name']?.toString().trim().isNotEmpty == true
              ? raw['name'].toString().trim()
              : key,
          type: type,
          api: api,
          subscriptionId: subscriptionId,
          searchable: _asInt(raw['searchable'], 1) != 0,
          quickSearch: _asInt(raw['quickSearch'], 1) != 0,
          filterable:
              key.startsWith('py_') || _asInt(raw['filterable'], 1) != 0,
          changeable: _asInt(raw['changeable'], 1) != 0,
          ext: _stringify(raw['ext']),
          playUrl: raw['playUrl']?.toString().trim() ?? '',
          jar: (raw['jar']?.toString().trim().isNotEmpty == true
              ? raw['jar'].toString().trim()
              : type == 3
              ? rootSpider
              : ''),
          timeoutSeconds: _asInt(raw['timeout'], 15).clamp(5, 60),
          categories: _list(
            raw['categories'],
          ).map((value) => value.toString()).toList(),
        ),
      );
    }

    final parsers = <ParseSource>[];
    for (final raw in _list(root['parses'])) {
      if (raw is! Map) continue;
      final url = raw['url']?.toString().trim() ?? '';
      if (url.isEmpty) continue;
      parsers.add(
        ParseSource(
          name: raw['name']?.toString().trim() ?? url,
          url: url,
          type: _asInt(raw['type'], 0),
          ext: _parserHeaders(raw['ext']),
          rawExt: _stringify(raw['ext']),
        ),
      );
    }

    final lives = <LiveSource>[];
    for (final raw in _list(root['lives'])) {
      if (raw is! Map) continue;
      final rawUrls = raw['urls'] is List ? raw['urls'] as List : [raw['url']];
      for (final value in rawUrls) {
        final url = value?.toString().trim() ?? '';
        if (url.isEmpty) continue;
        lives.add(
          LiveSource(
            name: raw['name']?.toString().trim() ?? '直播源',
            url: url,
            headers: {
              ..._stringMap(raw['header']),
              if ((raw['ua']?.toString() ?? '').isNotEmpty)
                'User-Agent': raw['ua'].toString(),
            },
            epg: raw['epg']?.toString().trim() ?? '',
            logo: raw['logo']?.toString().trim() ?? '',
          ),
        );
      }
    }

    return FBoxConfig(
      subscriptionId: subscriptionId,
      sites: sites,
      parsers: parsers,
      liveSources: lives,
      wallpaper: root['wallpaper']?.toString() ?? '',
      danmaku: root['danmaku']?.toString() ?? '',
    );
  }

  Map<String, dynamic> _decodeObject(String input) {
    var normalized = input.trim().replaceFirst('\ufeff', '');
    final marker = RegExp(r'[A-Za-z0-9]{8}\*\*').firstMatch(normalized);
    if (marker != null) {
      final start = marker.end;
      try {
        normalized = utf8.decode(base64.decode(normalized.substring(start)));
      } on FormatException {
        // Keep the original text so the caller receives a useful JSON error.
      }
    }
    final first = normalized.indexOf('{');
    final last = normalized.lastIndexOf('}');
    if (first >= 0 && last > first) {
      normalized = normalized.substring(first, last + 1);
    }
    final decoded = jsonDecode(_stripComments(normalized));
    if (decoded is! Map) throw const FormatException('订阅配置不是 JSON 对象');
    return Map<String, dynamic>.from(decoded);
  }

  static String _stripComments(String input) {
    final out = StringBuffer();
    var inString = false;
    var escaped = false;
    var lineComment = false;
    var blockComment = false;
    for (var index = 0; index < input.length; index++) {
      final current = input[index];
      final next = index + 1 < input.length ? input[index + 1] : '';
      if (lineComment) {
        if (current == '\n') {
          lineComment = false;
          out.write(current);
        }
        continue;
      }
      if (blockComment) {
        if (current == '*' && next == '/') {
          blockComment = false;
          index++;
        }
        continue;
      }
      if (!inString && current == '/' && next == '/') {
        lineComment = true;
        index++;
        continue;
      }
      if (!inString && current == '/' && next == '*') {
        blockComment = true;
        index++;
        continue;
      }
      out.write(current);
      if (current == '"' && !escaped) inString = !inString;
      escaped = current == '\\' && !escaped;
      if (current != '\\') escaped = false;
    }
    return out.toString();
  }

  static List<dynamic> _list(Object? value) => value is List ? value : const [];

  static int _asInt(Object? value, int fallback) =>
      int.tryParse(value?.toString() ?? '') ?? fallback;

  static String _stringify(Object? value) {
    if (value == null) return '';
    if (value is String) return value;
    return jsonEncode(value);
  }

  static Map<String, String> _stringMap(Object? value) {
    if (value is! Map) return const {};
    return value.map((key, item) => MapEntry('$key', '$item'));
  }

  static Map<String, String> _parserHeaders(Object? value) {
    if (value is! Map) return const {};
    final nested = value['header'];
    if (nested is Map) return _stringMap(nested);
    return _stringMap(value);
  }
}
