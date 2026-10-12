import 'dart:convert';
import 'dart:io';

import 'package:fbox/domain/source_models.dart';
import 'package:fbox/services/mbox_config_parser.dart';
import 'package:fbox/services/network_service.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

class ConfigRepository {
  ConfigRepository({
    required this.network,
    this.parser = const MBoxConfigParser(),
    this.supportDirectoryProvider,
  });

  final NetworkService network;
  final MBoxConfigParser parser;
  final Future<Directory> Function()? supportDirectoryProvider;

  Future<String> importLocalConfig(
    String sourcePath, {
    required String subscriptionId,
  }) async {
    final source = File(sourcePath);
    if (!await source.exists()) throw const ConfigLoadException('所选本地配置不存在');
    final root = await _supportDirectory();
    final directory = Directory(path.join(root.path, 'local_configs'));
    await directory.create(recursive: true);
    final safeId = base64Url
        .encode(utf8.encode(subscriptionId))
        .replaceAll('=', '');
    final rawExtension = path.extension(source.path).toLowerCase();
    final extension = const {'.json', '.txt'}.contains(rawExtension)
        ? rawExtension
        : '.json';
    final destination = File(path.join(directory.path, '$safeId$extension'));
    final temporary = File('${destination.path}.part');
    try {
      if (await temporary.exists()) await temporary.delete();
      await source.copy(temporary.path);
      if (await destination.exists()) await destination.delete();
      await temporary.rename(destination.path);
      return destination.uri.toString();
    } on FileSystemException catch (error) {
      throw ConfigLoadException('保存本地配置失败：$error');
    }
  }

  Future<void> deleteImportedLocalConfig(String location) async {
    final uri = Uri.tryParse(location);
    if (uri == null || uri.scheme != 'file') return;
    final root = await _supportDirectory();
    final managedDirectory = path.normalize(
      path.join(root.absolute.path, 'local_configs'),
    );
    final file = File(uri.toFilePath()).absolute;
    if (!path.isWithin(managedDirectory, file.path)) return;
    if (await file.exists()) await file.delete();
  }

  Future<FBoxConfig> load(FBoxSubscription subscription) async {
    Object? fetchError;
    String? attemptedLocation;
    try {
      final content = await readLocation(subscription.url);
      final collection = parser.parseCollection(content);
      final configLocation = collection.isEmpty
          ? subscription.url
          : _selectedCollectionLocation(subscription, collection);
      attemptedLocation = configLocation;
      final resolvedContent = collection.isEmpty
          ? content
          : await readLocation(configLocation);
      final parsed = parser.parse(
        subscriptionId: subscription.id,
        input: resolvedContent,
      );
      final config = _resolveRelativeLocations(parsed, configLocation);
      try {
        await _writeCache(
          subscription.id,
          content: resolvedContent,
          location: configLocation,
          subscriptionUrl: subscription.url,
        );
      } on Object {
        // A valid remote config remains usable if the platform cache is
        // temporarily unavailable or out of space.
      }
      return config;
    } catch (error) {
      fetchError = error;
    }
    final cached = await _readCache(subscription.id);
    if (cached != null) {
      if (cached.subscriptionUrl.isEmpty ||
          cached.subscriptionUrl != subscription.url) {
        throw ConfigLoadException('订阅加载失败：$fetchError（缓存属于其他订阅地址）');
      }
      final explicitlySelected = subscription.selectedUrl.trim();
      if (explicitlySelected.isNotEmpty &&
          cached.location.isNotEmpty &&
          cached.location != explicitlySelected) {
        throw ConfigLoadException(
          '配置线路加载失败：$fetchError（缓存属于其他线路 ${cached.location}）',
        );
      }
      if (attemptedLocation != null &&
          cached.location.isNotEmpty &&
          cached.location != attemptedLocation) {
        throw ConfigLoadException('配置线路加载失败：$fetchError（缓存线路不匹配）');
      }
      return _resolveRelativeLocations(
        parser.parse(subscriptionId: subscription.id, input: cached.content),
        cached.location.isEmpty ? subscription.url : cached.location,
      );
    }
    throw ConfigLoadException('订阅加载失败：$fetchError');
  }

  Future<String> readLocation(String location) async {
    final normalized = location.trim();
    if (normalized.isEmpty) throw const ConfigLoadException('订阅地址为空');
    if (normalized.startsWith('file://')) {
      return File(Uri.parse(normalized).toFilePath()).readAsString();
    }
    if (normalized.startsWith('clan://localhost/')) {
      final localPath = Uri.decodeFull(
        normalized.substring('clan://localhost'.length),
      );
      return File(localPath).readAsString();
    }
    final uri = Uri.tryParse(
      normalized.contains('://') ? normalized : 'http://$normalized',
    );
    if (uri == null || !{'http', 'https'}.contains(uri.scheme)) {
      throw ConfigLoadException('不支持的订阅地址：$location');
    }
    return network.getText(uri);
  }

  Future<List<({String name, String url})>> collectionLines(
    FBoxSubscription subscription,
  ) async {
    final content = await readLocation(subscription.url);
    return parser
        .parseCollection(content)
        .map(
          (line) => (
            name: line.name.isEmpty ? line.url : line.name,
            url: _resolveLocation(line.url, subscription.url),
          ),
        )
        .toList();
  }

  String _selectedCollectionLocation(
    FBoxSubscription subscription,
    List<({String name, String url})> collection,
  ) {
    final resolved = collection
        .map(
          (line) => (
            name: line.name,
            url: _resolveLocation(line.url, subscription.url),
          ),
        )
        .toList();
    final selected = subscription.selectedUrl.trim();
    if (selected.isNotEmpty) {
      for (final line in resolved) {
        if (line.url == selected) return line.url;
      }
    }
    return resolved.first.url;
  }

  static String _resolveLocation(String value, String baseLocation) {
    final trimmed = value.trim();
    if (trimmed.isEmpty ||
        trimmed.startsWith('{') ||
        trimmed.startsWith('[') ||
        trimmed.startsWith('csp_') ||
        trimmed.startsWith('py_')) {
      return value;
    }
    final candidate = Uri.tryParse(trimmed);
    if (candidate?.hasScheme == true) return value;
    final base = Uri.tryParse(baseLocation);
    return base == null ? value : base.resolve(trimmed).toString();
  }

  Future<File> _cacheFile(String subscriptionId) async {
    final root = await _supportDirectory();
    final directory = Directory(path.join(root.path, 'config_cache'));
    await directory.create(recursive: true);
    final safeId = base64Url
        .encode(utf8.encode(subscriptionId))
        .replaceAll('=', '');
    return File(path.join(directory.path, '$safeId.json'));
  }

  Future<Directory> _supportDirectory() =>
      supportDirectoryProvider?.call() ?? getApplicationSupportDirectory();

  Future<void> _writeCache(
    String subscriptionId, {
    required String content,
    required String location,
    required String subscriptionUrl,
  }) async {
    final file = await _cacheFile(subscriptionId);
    await file.writeAsString(
      jsonEncode({
        'version': 2,
        'subscriptionUrl': subscriptionUrl,
        'location': location,
        'content': content,
      }),
      flush: true,
    );
  }

  Future<({String content, String location, String subscriptionUrl})?>
  _readCache(String subscriptionId) async {
    try {
      final file = await _cacheFile(subscriptionId);
      if (!await file.exists()) return null;
      final raw = await file.readAsString();
      try {
        final decoded = jsonDecode(raw);
        if (decoded is Map && decoded['content'] is String) {
          return (
            content: decoded['content'] as String,
            location: decoded['location']?.toString() ?? '',
            subscriptionUrl: decoded['subscriptionUrl']?.toString() ?? '',
          );
        }
      } on FormatException {
        // Caches created by older versions contain the config body directly.
      }
      return (content: raw, location: '', subscriptionUrl: '');
    } on FileSystemException {
      return null;
    }
  }

  FBoxConfig _resolveRelativeLocations(FBoxConfig config, String location) {
    final normalizedLocation = location.contains('://')
        ? location
        : 'http://$location';
    String resolve(String value) => _resolveLocation(value, normalizedLocation);
    String resolveExtension(String value) {
      final trimmed = value.trim();
      if (trimmed.startsWith('./') ||
          trimmed.startsWith('../') ||
          trimmed.startsWith('/')) {
        return resolve(value);
      }
      return Uri.tryParse(trimmed)?.hasScheme == true ? resolve(value) : value;
    }

    return FBoxConfig(
      subscriptionId: config.subscriptionId,
      sites: config.sites
          .map(
            (source) => SiteSource(
              key: source.key,
              name: source.name,
              type: source.type,
              api: resolve(source.api),
              subscriptionId: source.subscriptionId,
              searchable: source.searchable,
              quickSearch: source.quickSearch,
              filterable: source.filterable,
              changeable: source.changeable,
              ext: resolveExtension(source.ext),
              playUrl: resolve(source.playUrl),
              jar: resolve(source.jar),
              timeoutSeconds: source.timeoutSeconds,
              categories: source.categories,
            ),
          )
          .toList(),
      parsers: config.parsers
          .map(
            (parser) => ParseSource(
              name: parser.name,
              url: resolve(parser.url),
              type: parser.type,
              ext: parser.ext,
              rawExt: parser.rawExt,
            ),
          )
          .toList(),
      liveSources: config.liveSources
          .map(
            (source) => LiveSource(
              name: source.name,
              url: resolve(source.url),
              headers: source.headers,
              epg: resolve(source.epg),
              logo: resolve(source.logo),
            ),
          )
          .toList(),
      wallpaper: resolve(config.wallpaper),
      danmaku: resolve(config.danmaku),
    );
  }
}

class ConfigLoadException implements Exception {
  const ConfigLoadException(this.message);

  final String message;

  @override
  String toString() => message;
}
