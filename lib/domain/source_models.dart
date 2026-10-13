import 'dart:convert';

import 'package:fbox/domain/media_item.dart';

class FBoxSubscription {
  const FBoxSubscription({
    required this.id,
    required this.name,
    required this.url,
    this.selectedUrl = '',
    this.enabled = true,
    this.lastUpdated,
    this.lastError = '',
  });

  final String id;
  final String name;
  final String url;
  final String selectedUrl;
  final bool enabled;
  final DateTime? lastUpdated;
  final String lastError;

  FBoxSubscription copyWith({
    String? name,
    String? url,
    String? selectedUrl,
    bool? enabled,
    DateTime? lastUpdated,
    String? lastError,
  }) => FBoxSubscription(
    id: id,
    name: name ?? this.name,
    url: url ?? this.url,
    selectedUrl: selectedUrl ?? this.selectedUrl,
    enabled: enabled ?? this.enabled,
    lastUpdated: lastUpdated ?? this.lastUpdated,
    lastError: lastError ?? this.lastError,
  );

  Map<String, Object?> toJson() => {
    'id': id,
    'name': name,
    'url': url,
    'selectedUrl': selectedUrl,
    'enabled': enabled,
    'lastUpdated': lastUpdated?.toIso8601String(),
    'lastError': lastError,
  };

  factory FBoxSubscription.fromJson(Map<String, Object?> json) =>
      FBoxSubscription(
        id: json['id']?.toString() ?? '',
        name: json['name']?.toString() ?? '',
        url: json['url']?.toString() ?? '',
        selectedUrl: json['selectedUrl']?.toString() ?? '',
        enabled: json['enabled'] != false,
        lastUpdated: DateTime.tryParse(json['lastUpdated']?.toString() ?? ''),
        lastError: json['lastError']?.toString() ?? '',
      );
}

class SiteSource {
  const SiteSource({
    required this.key,
    required this.name,
    required this.type,
    required this.api,
    required this.subscriptionId,
    this.searchable = true,
    this.quickSearch = true,
    this.filterable = true,
    this.changeable = true,
    this.ext = '',
    this.playUrl = '',
    this.jar = '',
    this.timeoutSeconds = 15,
    this.categories = const [],
  });

  final String key;
  final String name;
  final int type;
  final String api;
  final String subscriptionId;
  final bool searchable;
  final bool quickSearch;
  final bool filterable;
  final bool changeable;
  final String ext;
  final String playUrl;
  final String jar;
  final int timeoutSeconds;
  final List<String> categories;

  bool get isSupported => type == 0 || type == 1 || type == 4;
  String get identity => subscriptionId.isEmpty ? key : '$subscriptionId::$key';
}

class ParseSource {
  const ParseSource({
    required this.name,
    required this.url,
    this.type = 0,
    this.ext = const {},
    this.rawExt = '',
  });

  final String name;
  final String url;
  final int type;
  final Map<String, String> ext;
  final String rawExt;

  String get requestUrl {
    if (rawExt.isEmpty) return url;
    final encoded = base64Url.encode(utf8.encode(rawExt));
    if (!url.contains('?')) {
      return '$url?cat_ext=$encoded';
    }
    final separator = url.indexOf('?') + 1;
    return '${url.substring(0, separator)}cat_ext=$encoded&'
        '${url.substring(separator)}';
  }
}

class LiveSource {
  const LiveSource({
    required this.name,
    required this.url,
    this.headers = const {},
    this.epg = '',
    this.logo = '',
  });

  final String name;
  final String url;
  final Map<String, String> headers;
  final String epg;
  final String logo;
}

class LiveChannelGroup {
  const LiveChannelGroup({required this.name, required this.channels});

  final String name;
  final List<LiveChannel> channels;
}

class LiveChannel {
  const LiveChannel({
    required this.name,
    required this.urls,
    this.epgId = '',
    this.logo = '',
    this.group = '其他',
    this.headers = const {},
  });

  final String name;
  final List<String> urls;
  final String epgId;
  final String logo;
  final String group;
  final Map<String, String> headers;

  Iterable<({int index, String url})> playbackCandidates(
    int preferredIndex,
  ) sync* {
    if (urls.isEmpty) return;
    final start = preferredIndex.clamp(0, urls.length - 1);
    for (var offset = 0; offset < urls.length; offset++) {
      final index = (start + offset) % urls.length;
      yield (index: index, url: urls[index]);
    }
  }
}

class LiveProgram {
  const LiveProgram({
    required this.start,
    required this.end,
    required this.title,
  });

  final String start;
  final String end;
  final String title;

  String get label {
    final time = end.isEmpty ? start : '$start–$end';
    return time.isEmpty ? title : '$time  $title';
  }
}

class FBoxConfig {
  const FBoxConfig({
    required this.subscriptionId,
    required this.sites,
    required this.parsers,
    required this.liveSources,
    this.wallpaper = '',
    this.danmaku = '',
  });

  final String subscriptionId;
  final List<SiteSource> sites;
  final List<ParseSource> parsers;
  final List<LiveSource> liveSources;
  final String wallpaper;
  final String danmaku;
}

class SourceCatalog {
  const SourceCatalog({
    required this.categories,
    required this.items,
    this.page = 1,
    this.pageCount = 1,
    this.filters = const {},
  });

  final List<SourceCategory> categories;
  final List<MediaItem> items;
  final int page;
  final int pageCount;
  final Map<String, List<SourceFilter>> filters;
}

class SourceCategory {
  const SourceCategory({required this.id, required this.name});

  final String id;
  final String name;
}

class SourceFilter {
  const SourceFilter({
    required this.key,
    required this.name,
    required this.options,
  });

  final String key;
  final String name;
  final List<SourceFilterOption> options;
}

class SourceFilterOption {
  const SourceFilterOption({required this.label, required this.value});

  final String label;
  final String value;
}

class PlaybackRequest {
  const PlaybackRequest({
    required this.url,
    this.headers = const {},
    this.parseRequired = false,
  });

  final String url;
  final Map<String, String> headers;
  final bool parseRequired;
}
