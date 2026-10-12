import 'package:fbox/domain/source_models.dart';

class LivePlaylistParser {
  const LivePlaylistParser();

  List<LiveChannelGroup> parse(
    String input, {
    Map<String, String> headers = const {},
  }) {
    final channels = input.contains('#EXTINF')
        ? _parseM3u(input, headers)
        : _parseText(input, headers);
    final grouped = <String, List<LiveChannel>>{};
    for (final channel in channels) {
      final existing = grouped.putIfAbsent(channel.group, () => []);
      final duplicate = existing.indexWhere(
        (item) => item.name == channel.name,
      );
      if (duplicate < 0) {
        existing.add(channel);
      } else {
        final previous = existing[duplicate];
        existing[duplicate] = LiveChannel(
          name: previous.name,
          urls: [...previous.urls, ...channel.urls],
          epgId: previous.epgId.isNotEmpty ? previous.epgId : channel.epgId,
          logo: previous.logo.isNotEmpty ? previous.logo : channel.logo,
          group: previous.group,
          headers: {...previous.headers, ...channel.headers},
        );
      }
    }
    return grouped.entries
        .map(
          (entry) => LiveChannelGroup(name: entry.key, channels: entry.value),
        )
        .toList();
  }

  List<LiveChannel> _parseM3u(String input, Map<String, String> headers) {
    final result = <LiveChannel>[];
    String? info;
    for (final rawLine in input.split(RegExp(r'\r?\n'))) {
      final line = rawLine.trim();
      if (line.startsWith('#EXTINF')) {
        info = line;
      } else if (line.isNotEmpty && !line.startsWith('#') && info != null) {
        final name = info.contains(',')
            ? info.substring(info.lastIndexOf(',') + 1).trim()
            : '未命名频道';
        result.add(
          LiveChannel(
            name: _attribute(info, 'tvg-name').isNotEmpty
                ? _attribute(info, 'tvg-name')
                : name,
            urls: [line],
            epgId: _attribute(info, 'tvg-id'),
            logo: _attribute(info, 'tvg-logo'),
            group: _attribute(info, 'group-title').isNotEmpty
                ? _attribute(info, 'group-title')
                : '其他',
            headers: headers,
          ),
        );
        info = null;
      }
    }
    return result;
  }

  List<LiveChannel> _parseText(String input, Map<String, String> headers) {
    final result = <LiveChannel>[];
    var group = '其他';
    for (final rawLine in input.split(RegExp(r'\r?\n'))) {
      final line = rawLine.trim();
      if (line.isEmpty || line.startsWith('//')) continue;
      if (line.endsWith(',#genre#')) {
        group = line.substring(0, line.indexOf(',')).trim();
        continue;
      }
      final separator = line.indexOf(',');
      if (separator <= 0 || separator >= line.length - 1) continue;
      result.add(
        LiveChannel(
          name: line.substring(0, separator).trim(),
          urls: [line.substring(separator + 1).trim()],
          group: group,
          headers: headers,
        ),
      );
    }
    return result;
  }

  static String _attribute(String input, String name) {
    return RegExp(
          '$name="([^"]*)"',
          caseSensitive: false,
        ).firstMatch(input)?.group(1) ??
        '';
  }
}
