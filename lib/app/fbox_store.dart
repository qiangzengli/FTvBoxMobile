import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:io';

import 'package:fbox/data/demo_catalog.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:fbox/services/cast_service.dart';
import 'package:fbox/services/config_repository.dart';
import 'package:fbox/services/download_manager.dart';
import 'package:fbox/services/fbox_storage.dart';
import 'package:fbox/services/live_epg_service.dart';
import 'package:fbox/services/live_playlist_parser.dart';
import 'package:fbox/services/network_service.dart';
import 'package:fbox/services/source_api_service.dart';
import 'package:flutter/material.dart';

const demoLiveSourceSetting = '__demo__';

class FBoxStore extends ChangeNotifier {
  FBoxStore({NetworkService? network}) : network = network ?? NetworkService() {
    sourceApi = SourceApiService(this.network);
    configRepository = ConfigRepository(network: this.network);
    downloads = DownloadManager(this.network)..addListener(_forwardChanges);
    cast = CastService(this.network);
    liveEpg = LiveEpgService(this.network);
    for (final item in DemoCatalog.items) {
      _media[item.storageId] = item;
    }
  }

  static const _favoritesKey = 'library.favorites';
  static const _historyKey = 'library.history';
  static const _mediaKey = 'library.media';
  static const _subscriptionsKey = 'subscriptions';
  static const _settingsKey = 'settings';
  static const _progressKey = 'playback.progress';
  static const _themeModeKey = 'theme.mode';
  static const _themeColorKey = 'theme.color';
  static const _currentSourceStorageKey = 'source.current';

  final NetworkService network;
  late final SourceApiService sourceApi;
  late final ConfigRepository configRepository;
  late final DownloadManager downloads;
  late final CastService cast;
  late final LiveEpgService liveEpg;
  final LivePlaylistParser livePlaylistParser = const LivePlaylistParser();

  FBoxStorage? _storage;
  bool _initialized = false;
  bool get initialized => _initialized;

  ThemeMode get themeMode => _themeMode;
  ThemeMode _themeMode = ThemeMode.system;

  Color get themeColor => _themeColor;
  Color _themeColor = const Color(0xFF567DF4);

  UnmodifiableSetView<String> get favoriteIds =>
      UnmodifiableSetView(_favorites);
  final Set<String> _favorites = {};

  UnmodifiableListView<String> get historyIds => UnmodifiableListView(_history);
  final List<String> _history = [];
  final Map<String, MediaItem> _media = {};
  final Map<String, double> _progress = {};

  UnmodifiableListView<FBoxSubscription> get subscriptions =>
      UnmodifiableListView(_subscriptions);
  final List<FBoxSubscription> _subscriptions = [];
  final List<FBoxConfig> _configs = [];

  List<SiteSource> get sources =>
      _configs.expand((config) => config.sites).toList(growable: false);
  List<ParseSource> get parsers =>
      _configs.expand((config) => config.parsers).toList(growable: false);
  List<LiveSource> get liveSources =>
      _configs.expand((config) => config.liveSources).toList(growable: false);

  String get currentSourceKey => _currentSourceKey;
  String _currentSourceKey = '';
  SiteSource? get currentSource => sourceByKey(_currentSourceKey);

  List<String> get categories => List.unmodifiable(_categories);
  List<String> _categories = const ['推荐', '电影', '连续剧', '综艺', '动漫'];
  Map<String, String> _categoryIds = const {};
  Map<String, List<SourceFilter>> _categoryFilters = const {};
  List<MediaItem> get catalog => List.unmodifiable(_catalog);
  List<MediaItem> _catalog = DemoCatalog.items;
  bool get catalogLoading => _catalogLoading;
  bool _catalogLoading = false;
  bool get catalogLoadingMore => _catalogLoadingMore;
  bool _catalogLoadingMore = false;
  bool get catalogHasMore => _catalogPage < _catalogPageCount;
  int _catalogPage = 1;
  int _catalogPageCount = 1;
  String get catalogError => _catalogError;
  String _catalogError = '';
  int _catalogGeneration = 0;

  Map<String, Object?> get settings => UnmodifiableMapView(_settings);
  final Map<String, Object?> _settings = {
    'privateBrowsing': false,
    'rememberProgress': true,
    'proxy': '',
    'dns': '腾讯 DoH',
    'dohEndpoint': 'https://doh.pub/dns-query',
    'homeLayout': '3 列 · 海报模式',
    'aggregateMode': '多源聚合',
    'autoLoad': true,
    'background': '默认背景',
    'backgroundImagePath': '',
    'backgroundAlpha': 1.0,
    'glass': false,
    'backgroundBlur': false,
    'homeSourceKey': '',
    'liveSourceUrl': demoLiveSourceSetting,
    'player': '系统播放器',
    'hardwareDecode': true,
    'adFilter': true,
    'longPressSpeed': true,
    'backgroundPlay': false,
    'renderMode': 'SurfaceView',
    'scaleMode': '适应屏幕',
    'ijkCache': '关闭',
  };

  bool get privateBrowsing => _settings['privateBrowsing'] == true;
  bool get rememberProgress => _settings['rememberProgress'] != false;
  String get proxy => _settings['proxy']?.toString() ?? '';
  String get dnsMode => _settings['dns']?.toString() ?? '系统 DNS';

  Future<void> initialize() async {
    if (_initialized) return;
    try {
      _storage = await FBoxStorage.open();
      final storage = _storage!;
      _favorites.addAll(storage.readStringList(_favoritesKey));
      _history.addAll(storage.readStringList(_historyKey));
      final rawMedia = storage.readJsonList(_mediaKey);
      for (final value in rawMedia.whereType<Map>()) {
        final item = MediaItem.fromJson(Map<String, Object?>.from(value));
        if (item.id.isNotEmpty) _media[item.storageId] = item;
      }
      final rawSubscriptions = storage.readJsonList(_subscriptionsKey);
      _subscriptions.addAll(
        rawSubscriptions.whereType<Map>().map(
          (value) =>
              FBoxSubscription.fromJson(Map<String, Object?>.from(value)),
        ),
      );
      final storedSettings = storage.readJsonObject(_settingsKey);
      if (storedSettings != null) _settings.addAll(storedSettings);
      final storedProgress = storage.readJsonObject(_progressKey);
      if (storedProgress != null) {
        _progress.addAll(
          storedProgress.map(
            (key, value) =>
                MapEntry(key, double.tryParse(value.toString()) ?? 0),
          ),
        );
      }
      _themeMode = _decodeTheme(storage.readString(_themeModeKey));
      _themeColor = Color(
        storage.readInt(_themeColorKey) ?? const Color(0xFF567DF4).toARGB32(),
      );
      _currentSourceKey = storage.readString(_currentSourceStorageKey) ?? '';
      _updateNetworkSettings();
      await downloads.initialize();
      if (_settings['autoLoad'] != false) await refreshAllSubscriptions();
      final preferredHomeSource = _settings['homeSourceKey']?.toString() ?? '';
      final sourceToRestore = preferredHomeSource.isNotEmpty
          ? preferredHomeSource
          : _currentSourceKey;
      if (sourceToRestore == 'demo') {
        await selectSource('demo');
      } else if (sourceToRestore.isNotEmpty &&
          sourceByKey(sourceToRestore) != null) {
        try {
          await selectSource(sourceToRestore);
        } on Object {
          // Keep the restored source selected so the user can retry it.
        }
      }
    } on Object {
      // The in-memory demo remains usable if platform storage is unavailable.
    } finally {
      _initialized = true;
      notifyListeners();
    }
  }

  SiteSource? sourceByKey(String key) {
    for (final source in sources) {
      if (source.identity == key) return source;
    }
    // Backward compatibility for media/history saved before source identities
    // included the subscription id. Only use a raw key when it is unambiguous.
    final legacyMatches = sources.where((source) => source.key == key).toList();
    if (legacyMatches.length == 1) {
      return legacyMatches.single;
    }
    return null;
  }

  List<MediaItem> itemsForIds(Iterable<String> ids) => ids
      .map((id) => _media[id])
      .whereType<MediaItem>()
      .toList(growable: false);

  bool isFavorite(MediaItem item) => _favorites.contains(item.storageId);

  void toggleFavorite(MediaItem item) {
    _rememberMedia(item);
    if (!_favorites.add(item.storageId)) _favorites.remove(item.storageId);
    _persistLibrary();
    notifyListeners();
  }

  void recordWatch(MediaItem item) {
    _rememberMedia(item);
    if (privateBrowsing) return;
    _history.remove(item.storageId);
    _history.insert(0, item.storageId);
    _persistLibrary();
    notifyListeners();
  }

  double playbackProgress(MediaItem item, String episodeName) =>
      _progress['${item.storageId}::$episodeName'] ?? 0;

  void savePlaybackProgress(
    MediaItem item,
    String episodeName,
    double seconds,
  ) {
    if (!rememberProgress || privateBrowsing) return;
    _progress['${item.storageId}::$episodeName'] = seconds;
    final storage = _storage;
    if (storage != null) unawaited(storage.writeJson(_progressKey, _progress));
  }

  void removeHistory(Iterable<String> ids) {
    final previousLength = _history.length;
    _history.removeWhere(ids.toSet().contains);
    if (_history.length != previousLength) {
      _persistLibrary();
      notifyListeners();
    }
  }

  void clearHistory() {
    if (_history.isEmpty) return;
    _history.clear();
    _persistLibrary();
    notifyListeners();
  }

  void removeFavorites(Iterable<String> ids) {
    final previousLength = _favorites.length;
    _favorites.removeAll(ids);
    if (_favorites.length != previousLength) {
      _persistLibrary();
      notifyListeners();
    }
  }

  void clearFavorites() {
    if (_favorites.isEmpty) return;
    _favorites.clear();
    _persistLibrary();
    notifyListeners();
  }

  void setThemeMode(ThemeMode value) {
    if (_themeMode == value) return;
    _themeMode = value;
    final storage = _storage;
    if (storage != null) {
      unawaited(storage.writeString(_themeModeKey, value.name));
    }
    notifyListeners();
  }

  void setThemeColor(Color value) {
    if (_themeColor == value) return;
    _themeColor = value;
    final storage = _storage;
    if (storage != null) {
      unawaited(storage.writeInt(_themeColorKey, value.toARGB32()));
    }
    notifyListeners();
  }

  void setSetting(String key, Object? value) {
    if (_settings[key] == value) return;
    _settings[key] = value;
    if (key == 'proxy' || key == 'dohEndpoint') _updateNetworkSettings();
    final storage = _storage;
    if (storage != null) {
      unawaited(storage.writeJson(_settingsKey, _settings));
    }
    notifyListeners();
  }

  Future<FBoxSubscription> addSubscription({
    required String name,
    required String url,
  }) async {
    final id = DateTime.now().microsecondsSinceEpoch.toString();
    final subscription = FBoxSubscription(
      id: id,
      name: name.trim().isEmpty ? url.trim() : name.trim(),
      url: url.trim(),
    );
    final config = await configRepository.load(subscription);
    _subscriptions.add(
      subscription.copyWith(lastUpdated: DateTime.now(), lastError: ''),
    );
    _replaceConfig(config);
    await _persistSubscriptions();
    notifyListeners();
    return subscription;
  }

  Future<FBoxSubscription> addLocalSubscription({
    required String name,
    required String filePath,
  }) async {
    final id = DateTime.now().microsecondsSinceEpoch.toString();
    final location = await configRepository.importLocalConfig(
      filePath,
      subscriptionId: id,
    );
    final subscription = FBoxSubscription(
      id: id,
      name: name.trim().isEmpty ? '本地配置' : name.trim(),
      url: location,
    );
    late final FBoxConfig config;
    try {
      config = await configRepository.load(subscription);
    } catch (_) {
      final file = File(Uri.parse(location).toFilePath());
      if (await file.exists()) await file.delete();
      rethrow;
    }
    _subscriptions.add(
      subscription.copyWith(lastUpdated: DateTime.now(), lastError: ''),
    );
    _replaceConfig(config);
    await _persistSubscriptions();
    notifyListeners();
    return subscription;
  }

  Future<void> updateSubscription(
    String id, {
    required String name,
    required String url,
  }) async {
    final index = _subscriptions.indexWhere((value) => value.id == id);
    if (index < 0) return;
    final previousUrl = _subscriptions[index].url;
    final updated = _subscriptions[index].copyWith(
      name: name.trim().isEmpty ? url.trim() : name.trim(),
      url: url.trim(),
      selectedUrl: url.trim() == _subscriptions[index].url
          ? _subscriptions[index].selectedUrl
          : '',
    );
    final config = await configRepository.load(updated);
    _subscriptions[index] = updated.copyWith(
      lastUpdated: DateTime.now(),
      lastError: '',
    );
    _replaceConfig(config);
    await _persistSubscriptions();
    if (previousUrl != updated.url) {
      await configRepository.deleteImportedLocalConfig(previousUrl);
    }
    notifyListeners();
  }

  Future<void> refreshSubscription(String id) async {
    final index = _subscriptions.indexWhere((value) => value.id == id);
    if (index < 0) return;
    try {
      final config = await configRepository.load(_subscriptions[index]);
      _subscriptions[index] = _subscriptions[index].copyWith(
        lastUpdated: DateTime.now(),
        lastError: '',
      );
      _replaceConfig(config);
    } catch (error) {
      _subscriptions[index] = _subscriptions[index].copyWith(
        lastError: '$error',
      );
      rethrow;
    } finally {
      await _persistSubscriptions();
      notifyListeners();
    }
  }

  Future<List<({String name, String url})>> subscriptionLines(String id) async {
    final subscription = _subscriptions.firstWhere(
      (value) => value.id == id,
      orElse: () => throw StateError('找不到订阅：$id'),
    );
    return configRepository.collectionLines(subscription);
  }

  Future<void> switchSubscriptionLine(String id, String selectedUrl) async {
    final index = _subscriptions.indexWhere((value) => value.id == id);
    if (index < 0) throw StateError('找不到订阅：$id');
    final updated = _subscriptions[index].copyWith(selectedUrl: selectedUrl);
    final config = await configRepository.load(updated);
    _subscriptions[index] = updated.copyWith(
      lastUpdated: DateTime.now(),
      lastError: '',
    );
    _replaceConfig(config);
    await _persistSubscriptions();
    notifyListeners();
  }

  Future<void> refreshAllSubscriptions() async {
    for (final subscription in [..._subscriptions]) {
      if (!subscription.enabled) continue;
      try {
        await refreshSubscription(subscription.id);
      } on Object {
        // Keep loading other independent subscriptions.
      }
    }
  }

  Future<void> removeSubscription(String id) async {
    final removed = _subscriptions.where((value) => value.id == id).firstOrNull;
    _subscriptions.removeWhere((value) => value.id == id);
    _configs.removeWhere((value) => value.subscriptionId == id);
    if (sourceByKey(_currentSourceKey) == null) {
      _catalogGeneration++;
      _currentSourceKey = '';
      _categories = const ['推荐', '电影', '连续剧', '综艺', '动漫'];
      _categoryIds = const {};
      _categoryFilters = const {};
      _catalog = DemoCatalog.items;
      _catalogLoading = false;
      _catalogLoadingMore = false;
      _catalogPage = 1;
      _catalogPageCount = 1;
      _catalogError = '';
      final storage = _storage;
      if (storage != null) {
        unawaited(storage.writeString(_currentSourceStorageKey, ''));
      }
    }
    await _persistSubscriptions();
    if (removed != null) {
      await configRepository.deleteImportedLocalConfig(removed.url);
    }
    notifyListeners();
  }

  Future<void> selectSource(String key) async {
    if (key.isEmpty || key == 'demo') {
      _catalogGeneration++;
      _currentSourceKey = '';
      _categories = const ['推荐', '电影', '连续剧', '综艺', '动漫'];
      _categoryIds = const {};
      _categoryFilters = const {};
      _catalog = DemoCatalog.items;
      _catalogLoading = false;
      _catalogLoadingMore = false;
      _catalogPage = 1;
      _catalogPageCount = 1;
      final storage = _storage;
      if (storage != null) {
        unawaited(storage.writeString(_currentSourceStorageKey, ''));
      }
      _catalogError = '';
      notifyListeners();
      return;
    }
    final source = sourceByKey(key);
    if (source == null) throw StateError('找不到站源：$key');
    final generation = ++_catalogGeneration;
    _currentSourceKey = source.identity;
    final storage = _storage;
    if (storage != null) {
      unawaited(
        storage.writeString(_currentSourceStorageKey, _currentSourceKey),
      );
    }
    _catalogLoading = true;
    _catalogLoadingMore = false;
    _catalogError = '';
    _catalog = const [];
    notifyListeners();
    try {
      final result = await sourceApi.home(source);
      if (!_isCurrentCatalogRequest(generation, source.identity)) return;
      _categories = ['推荐', ...result.categories.map((value) => value.name)];
      _categoryIds = {
        for (final category in result.categories) category.name: category.id,
      };
      _categoryFilters = result.filters;
      _catalog = result.items;
      _catalogPage = result.page;
      _catalogPageCount = result.pageCount;
      for (final item in result.items) {
        _rememberMedia(item);
      }
    } catch (error) {
      if (!_isCurrentCatalogRequest(generation, source.identity)) return;
      _catalogError = '$error';
      rethrow;
    } finally {
      if (_isCurrentCatalogRequest(generation, source.identity)) {
        _catalogLoading = false;
        notifyListeners();
      }
    }
  }

  List<SourceFilter> filtersForCategory(String category) => List.unmodifiable(
    _categoryFilters[_categoryIds[category] ?? category] ?? const [],
  );

  Future<void> loadCategory(
    String category, {
    int page = 1,
    Map<String, String> filters = const {},
  }) async {
    final source = currentSource;
    if (source == null) {
      _catalogGeneration++;
      _catalog = DemoCatalog.category(category);
      _catalogLoading = false;
      _catalogLoadingMore = false;
      _catalogPage = 1;
      _catalogPageCount = 1;
      _catalogError = '';
      notifyListeners();
      return;
    }
    if (category == '推荐') {
      await selectSource(source.identity);
      return;
    }
    final generation = ++_catalogGeneration;
    _catalogLoading = true;
    _catalogLoadingMore = false;
    _catalogError = '';
    _catalog = const [];
    notifyListeners();
    try {
      final result = await sourceApi.category(
        source,
        _categoryIds[category] ?? category,
        page: page,
        filters: filters,
      );
      if (!_isCurrentCatalogRequest(generation, source.identity)) return;
      _catalog = result.items;
      _catalogPage = result.page > 1 ? result.page : page;
      _catalogPageCount = result.pageCount;
      for (final item in result.items) {
        _rememberMedia(item);
      }
    } catch (error) {
      if (!_isCurrentCatalogRequest(generation, source.identity)) return;
      _catalogError = '$error';
      rethrow;
    } finally {
      if (_isCurrentCatalogRequest(generation, source.identity)) {
        _catalogLoading = false;
        notifyListeners();
      }
    }
  }

  Future<void> loadNextCategoryPage(
    String category, {
    Map<String, String> filters = const {},
  }) async {
    final source = currentSource;
    if (source == null ||
        category == '推荐' ||
        _catalogLoading ||
        _catalogLoadingMore ||
        !catalogHasMore) {
      return;
    }
    final generation = ++_catalogGeneration;
    final nextPage = _catalogPage + 1;
    _catalogLoadingMore = true;
    _catalogError = '';
    notifyListeners();
    try {
      final result = await sourceApi.category(
        source,
        _categoryIds[category] ?? category,
        page: nextPage,
        filters: filters,
      );
      if (!_isCurrentCatalogRequest(generation, source.identity)) return;
      final existing = {for (final item in _catalog) item.storageId: item};
      for (final item in result.items) {
        existing[item.storageId] = item;
        _rememberMedia(item);
      }
      _catalog = existing.values.toList(growable: false);
      _catalogPage = nextPage;
      _catalogPageCount = result.items.isEmpty
          ? nextPage
          : result.pageCount < nextPage
          ? nextPage
          : result.pageCount;
    } catch (error) {
      if (!_isCurrentCatalogRequest(generation, source.identity)) return;
      _catalogError = '$error';
      rethrow;
    } finally {
      if (_isCurrentCatalogRequest(generation, source.identity)) {
        _catalogLoadingMore = false;
        notifyListeners();
      }
    }
  }

  Future<List<MediaItem>> search(String query, {int page = 1}) async {
    final normalized = query.trim();
    if (sources.isEmpty) {
      return page == 1 ? DemoCatalog.search(normalized) : const [];
    }
    final aggregate = _settings['aggregateMode'] == '多源聚合';
    final selectedSource = currentSource;
    if (!aggregate && selectedSource == null) {
      return page == 1 ? DemoCatalog.search(normalized) : const [];
    }
    final candidates = (aggregate ? sources : <SiteSource>[selectedSource!])
        .where((source) => source.searchable && source.isSupported)
        .toList();
    if (candidates.isEmpty) return const [];
    var successCount = 0;
    final errors = <String, String>{};
    final results = await Future.wait(
      candidates.map((source) async {
        try {
          final items = await sourceApi.search(source, normalized, page: page);
          successCount++;
          return items;
        } catch (error) {
          errors[source.name] = '$error';
          return <MediaItem>[];
        }
      }),
    );
    if (successCount == 0 && errors.isNotEmpty) {
      throw SourceSearchException(errors);
    }
    final unique = <String, MediaItem>{};
    for (final item in results.expand((value) => value)) {
      unique[item.storageId] = item;
      _rememberMedia(item);
    }
    return unique.values.toList();
  }

  Future<MediaItem> loadDetail(MediaItem item) async {
    if (item.sourceKey.isEmpty) return item;
    final source = sourceByKey(item.sourceKey);
    if (source == null) throw StateError('站源已被删除：${item.sourceKey}');
    final detail = await sourceApi.detail(
      source,
      item.detailId.isEmpty ? item.id : item.detailId,
    );
    _rememberMedia(detail);
    _persistLibrary();
    return detail;
  }

  Future<PlaybackRequest> resolvePlayback({
    required MediaItem item,
    required MediaEpisode episode,
    required String line,
    ParseSource? parser,
  }) async {
    if (item.sourceKey.isEmpty) {
      return PlaybackRequest(url: episode.url, headers: episode.headers);
    }
    final source = sourceByKey(item.sourceKey);
    if (source == null) throw StateError('站源已被删除：${item.sourceKey}');
    return sourceApi.resolvePlayback(
      source: source,
      episode: episode,
      flag: line,
      parser: parser,
    );
  }

  Future<List<LiveChannelGroup>> loadLiveSource(LiveSource source) async {
    final content = await configRepository.readLocation(source.url);
    return livePlaylistParser.parse(content, headers: source.headers);
  }

  Future<List<LiveProgram>> loadLiveEpg(
    LiveSource source,
    LiveChannel channel,
  ) => liveEpg.load(source: source, channel: channel);

  Map<String, Object?> exportBackup() => {
    'version': 1,
    'createdAt': DateTime.now().toIso8601String(),
    'subscriptions': _subscriptions.map((value) => value.toJson()).toList(),
    'favorites': _favorites.toList(),
    'history': _history,
    'media': _media.values.map((value) => value.toJson()).toList(),
    'progress': _progress,
    'settings': _settings,
    'themeMode': _themeMode.name,
    'themeColor': _themeColor.toARGB32(),
    'currentSourceKey': _currentSourceKey,
  };

  Future<void> importBackup(Map<String, dynamic> backup) async {
    if (backup['version'] != 1) throw const FormatException('不支持的备份版本');

    List<dynamic> listValue(String key) {
      final value = backup[key];
      if (value == null) return const [];
      if (value is! List) throw FormatException('备份字段 $key 必须是数组');
      return value;
    }

    Map<dynamic, dynamic> mapValue(String key) {
      final value = backup[key];
      if (value == null) return const {};
      if (value is! Map) throw FormatException('备份字段 $key 必须是对象');
      return value;
    }

    final restoredSubscriptions = <FBoxSubscription>[];
    for (final value in listValue('subscriptions')) {
      if (value is! Map) throw const FormatException('备份包含无效订阅');
      final subscription = FBoxSubscription.fromJson(
        Map<String, Object?>.from(value),
      );
      if (subscription.id.isEmpty || subscription.url.trim().isEmpty) {
        throw const FormatException('备份包含缺少 ID 或地址的订阅');
      }
      restoredSubscriptions.add(subscription);
    }
    final restoredFavorites = listValue('favorites')
        .map((value) => '$value')
        .where((value) => value.isNotEmpty)
        .toSet();
    final restoredHistory = listValue('history')
        .map((value) => '$value')
        .where((value) => value.isNotEmpty)
        .toList();
    final restoredMedia = <String, MediaItem>{
      for (final item in DemoCatalog.items) item.storageId: item,
    };
    for (final value in listValue('media')) {
      if (value is! Map) throw const FormatException('备份包含无效媒体记录');
      final item = MediaItem.fromJson(Map<String, Object?>.from(value));
      if (item.id.isEmpty || item.title.isEmpty) {
        throw const FormatException('备份包含缺少 ID 或标题的媒体记录');
      }
      restoredMedia[item.storageId] = item;
    }
    final restoredSettings = Map<String, Object?>.from(_settings);
    for (final entry in mapValue('settings').entries) {
      final key = '${entry.key}';
      if (!restoredSettings.containsKey(key)) continue;
      final expected = restoredSettings[key];
      final value = entry.value;
      if (key == 'backgroundAlpha') {
        if (value is! num) {
          throw const FormatException('备份字段 settings.backgroundAlpha 必须是数字');
        }
        restoredSettings[key] = value.toDouble().clamp(0, 1);
      } else if (expected is bool) {
        if (value is! bool) {
          throw FormatException('备份字段 settings.$key 必须是布尔值');
        }
        restoredSettings[key] = value;
      } else if (expected is String) {
        if (value is! String) {
          throw FormatException('备份字段 settings.$key 必须是字符串');
        }
        restoredSettings[key] = value;
      }
    }
    final restoredProgress = mapValue('progress')
        .map((key, value) => MapEntry('$key', double.tryParse('$value') ?? 0));
    final restoredThemeMode = _decodeTheme(backup['themeMode']?.toString());
    final restoredCurrentSourceKey =
        backup['currentSourceKey']?.toString() ?? '';
    final restoredThemeColor = Color(
      int.tryParse(backup['themeColor']?.toString() ?? '') ??
          _themeColor.toARGB32(),
    );

    _subscriptions
      ..clear()
      ..addAll(restoredSubscriptions);
    _favorites
      ..clear()
      ..addAll(restoredFavorites);
    _history
      ..clear()
      ..addAll(restoredHistory);
    _media
      ..clear()
      ..addAll(restoredMedia);
    _settings
      ..clear()
      ..addAll(restoredSettings);
    _progress
      ..clear()
      ..addAll(restoredProgress);
    _themeMode = restoredThemeMode;
    _themeColor = restoredThemeColor;
    _configs.clear();
    _catalogGeneration++;
    _currentSourceKey = restoredCurrentSourceKey;
    _categories = const ['推荐', '电影', '连续剧', '综艺', '动漫'];
    _categoryIds = const {};
    _categoryFilters = const {};
    _catalog = DemoCatalog.items;
    _catalogLoading = false;
    _catalogLoadingMore = false;
    _catalogPage = 1;
    _catalogPageCount = 1;
    _catalogError = '';
    _updateNetworkSettings();
    final storage = _storage;
    if (storage != null) {
      await Future.wait([
        _persistSubscriptions(),
        storage.writeStringList(_favoritesKey, _favorites),
        storage.writeStringList(_historyKey, _history),
        storage.writeJson(
          _mediaKey,
          _media.values.map((item) => item.toJson()).toList(),
        ),
        storage.writeJson(_settingsKey, _settings),
        storage.writeJson(_progressKey, _progress),
        storage.writeString(_themeModeKey, _themeMode.name),
        storage.writeInt(_themeColorKey, _themeColor.toARGB32()),
        storage.writeString(_currentSourceStorageKey, restoredCurrentSourceKey),
      ]);
    }
    await refreshAllSubscriptions();
    if (_currentSourceKey.isNotEmpty && currentSource != null) {
      try {
        await selectSource(_currentSourceKey);
      } on Object {
        // The source remains selected so the user can retry after recovery.
      }
    }
    notifyListeners();
  }

  String exportBackupText() =>
      const JsonEncoder.withIndent('  ').convert(exportBackup());

  Future<void> importBackupText(String input) async {
    final decoded = jsonDecode(input);
    if (decoded is! Map) throw const FormatException('备份内容不是 JSON 对象');
    await importBackup(Map<String, dynamic>.from(decoded));
  }

  void _replaceConfig(FBoxConfig config) {
    _configs.removeWhere(
      (value) => value.subscriptionId == config.subscriptionId,
    );
    _configs.add(config);
  }

  bool _isCurrentCatalogRequest(int generation, String sourceIdentity) =>
      generation == _catalogGeneration && _currentSourceKey == sourceIdentity;

  void _rememberMedia(MediaItem item) {
    _media[item.storageId] = item;
  }

  void _persistLibrary() {
    final storage = _storage;
    if (storage == null) return;
    unawaited(storage.writeStringList(_favoritesKey, _favorites));
    unawaited(storage.writeStringList(_historyKey, _history));
    unawaited(
      storage.writeJson(
        _mediaKey,
        _media.values.map((item) => item.toJson()).toList(),
      ),
    );
  }

  Future<void> _persistSubscriptions() async {
    final storage = _storage;
    if (storage == null) return;
    await storage.writeJson(
      _subscriptionsKey,
      _subscriptions.map((value) => value.toJson()).toList(),
    );
  }

  void _updateNetworkSettings() {
    network.updateSettings(
      NetworkSettings(
        proxy: proxy,
        dohEndpoint: _settings['dohEndpoint']?.toString() ?? '',
      ),
    );
  }

  static ThemeMode _decodeTheme(String? value) => switch (value) {
    'dark' => ThemeMode.dark,
    'light' => ThemeMode.light,
    _ => ThemeMode.system,
  };

  void _forwardChanges() => notifyListeners();

  @override
  void dispose() {
    _catalogGeneration++;
    downloads
      ..removeListener(_forwardChanges)
      ..dispose();
    super.dispose();
  }
}

class SourceSearchException implements Exception {
  SourceSearchException(this.errors);

  final Map<String, String> errors;

  @override
  String toString() =>
      '所有站源搜索失败：${errors.entries.map((entry) => '${entry.key}: ${entry.value}').join('；')}';
}

class FBoxScope extends InheritedNotifier<FBoxStore> {
  const FBoxScope({required FBoxStore store, required super.child, super.key})
    : super(notifier: store);

  static FBoxStore of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<FBoxScope>();
    assert(scope != null, 'FBoxScope is missing above this context.');
    return scope!.notifier!;
  }

  static FBoxStore? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<FBoxScope>()?.notifier;
}
