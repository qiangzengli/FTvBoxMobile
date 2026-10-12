import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:fbox/domain/media_item.dart';
import 'package:fbox/services/network_service.dart';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

enum DownloadState { queued, downloading, completed, failed, canceled }

class DownloadEntry {
  const DownloadEntry({
    required this.id,
    required this.title,
    required this.episode,
    required this.url,
    required this.filePath,
    required this.state,
    required this.received,
    required this.total,
    required this.createdAt,
    this.error = '',
  });

  final String id;
  final String title;
  final String episode;
  final String url;
  final String filePath;
  final DownloadState state;
  final int received;
  final int total;
  final DateTime createdAt;
  final String error;

  double get progress => total <= 0 ? 0 : (received / total).clamp(0, 1);

  DownloadEntry copyWith({
    DownloadState? state,
    int? received,
    int? total,
    String? error,
    String? filePath,
  }) => DownloadEntry(
    id: id,
    title: title,
    episode: episode,
    url: url,
    filePath: filePath ?? this.filePath,
    state: state ?? this.state,
    received: received ?? this.received,
    total: total ?? this.total,
    createdAt: createdAt,
    error: error ?? this.error,
  );

  Map<String, Object?> toJson() => {
    'id': id,
    'title': title,
    'episode': episode,
    'url': url,
    'filePath': filePath,
    'state': state.name,
    'received': received,
    'total': total,
    'createdAt': createdAt.toIso8601String(),
    'error': error,
  };

  factory DownloadEntry.fromJson(Map<String, Object?> json) => DownloadEntry(
    id: json['id']?.toString() ?? '',
    title: json['title']?.toString() ?? '',
    episode: json['episode']?.toString() ?? '',
    url: json['url']?.toString() ?? '',
    filePath: json['filePath']?.toString() ?? '',
    state: DownloadState.values.firstWhere(
      (value) => value.name == json['state'],
      orElse: () => DownloadState.failed,
    ),
    received: int.tryParse(json['received']?.toString() ?? '') ?? 0,
    total: int.tryParse(json['total']?.toString() ?? '') ?? 0,
    createdAt:
        DateTime.tryParse(json['createdAt']?.toString() ?? '') ??
        DateTime.now(),
    error: json['error']?.toString() ?? '',
  );
}

class DownloadManager extends ChangeNotifier {
  DownloadManager(this.network, {this.documentsDirectoryProvider});

  final NetworkService network;
  final Future<Directory> Function()? documentsDirectoryProvider;
  final List<DownloadEntry> _entries = [];
  final Map<String, _ActiveDownload> _active = {};
  final Map<String, Future<DownloadEntry>> _running = {};
  final Map<String, DateTime> _lastProgressNotifications = {};
  final Set<String> _canceled = {};
  final Set<String> _removed = {};
  Future<void> _saveTail = Future<void>.value();

  List<DownloadEntry> get entries => List.unmodifiable(_entries);

  Future<void> initialize() async {
    try {
      final file = await _indexFile();
      if (!await file.exists()) return;
      final decoded = jsonDecode(await file.readAsString());
      if (decoded is! List) return;
      _entries
        ..clear()
        ..addAll(
          decoded.whereType<Map>().map(
            (value) => DownloadEntry.fromJson(Map<String, Object?>.from(value)),
          ),
        );
      for (var index = 0; index < _entries.length; index++) {
        if (_entries[index].state == DownloadState.downloading ||
            _entries[index].state == DownloadState.queued) {
          _entries[index] = _entries[index].copyWith(
            state: DownloadState.failed,
            error: '下载被应用退出中断',
          );
        }
      }
      notifyListeners();
    } on Object {
      // A corrupt index should not prevent the application from starting.
    }
  }

  Future<DownloadEntry> start({
    required MediaItem item,
    required MediaEpisode episode,
  }) {
    final id = '${item.storageId}::${episode.name}';
    final running = _running[id];
    if (running != null) return running;
    final operation = _startUnique(item: item, episode: episode, id: id);
    _running[id] = operation;
    unawaited(
      operation.then<void>(
        (_) => _finishRunning(id, operation),
        onError: (Object _, StackTrace _) => _finishRunning(id, operation),
      ),
    );
    return operation;
  }

  void _finishRunning(String id, Future<DownloadEntry> operation) {
    if (identical(_running[id], operation)) {
      _running.remove(id);
      _lastProgressNotifications.remove(id);
      _canceled.remove(id);
      _removed.remove(id);
    }
  }

  Future<DownloadEntry> _startUnique({
    required MediaItem item,
    required MediaEpisode episode,
    required String id,
  }) async {
    final root = await _downloadDirectory();
    final extension = _extensionFor(episode.url);
    final fileName =
        '${_safeName(item.title)}-${_safeName(episode.name)}'
        '-${_stableSuffix(id)}$extension';
    var destination = path.join(root.path, fileName);
    var entry = DownloadEntry(
      id: id,
      title: item.title,
      episode: episode.name,
      url: episode.url,
      filePath: destination,
      state: DownloadState.queued,
      received: 0,
      total: 0,
      createdAt: DateTime.now(),
    );
    _replace(entry);
    await _save();

    if (Uri.tryParse(episode.url)?.path.toLowerCase().endsWith('.m3u8') ==
        true) {
      return _downloadHls(
        entry: entry,
        headers: episode.headers,
        destination: destination,
      );
    }

    IOSink? sink;
    StreamedNetworkResponse? response;
    try {
      response = await network.stream(
        Uri.parse(episode.url),
        headers: episode.headers,
      );
      if (_isHlsResponse(response.headers)) {
        response.close();
        response = null;
        destination = path.setExtension(destination, '.m3u8');
        entry = entry.copyWith(filePath: destination);
        _replace(entry);
        return await _downloadHls(
          entry: entry,
          headers: episode.headers,
          destination: destination,
        );
      }
      if (_canceled.contains(id)) throw const _DownloadCanceled();
      final temporary = File('$destination.part');
      sink = temporary.openWrite();
      entry = entry.copyWith(
        state: DownloadState.downloading,
        total: response.contentLength ?? 0,
      );
      _replace(entry);
      var received = 0;
      final completer = Completer<void>();
      final subscription = response.stream.listen(
        (chunk) {
          received += chunk.length;
          sink?.add(chunk);
          entry = entry.copyWith(received: received);
          _replace(
            entry,
            persist: false,
            notify: _shouldNotifyProgress(entry.id),
          );
        },
        onError: completer.completeError,
        onDone: completer.complete,
        cancelOnError: true,
      );
      _active[id] = _ActiveDownload(subscription, completer);
      if (_canceled.contains(id)) {
        await subscription.cancel();
        _active.remove(id);
        throw const _DownloadCanceled();
      }
      await completer.future;
      await sink.flush();
      await sink.close();
      sink = null;
      if (_canceled.contains(id) || entry.state == DownloadState.canceled) {
        throw const _DownloadCanceled();
      }
      if (await File(destination).exists()) await File(destination).delete();
      if (_canceled.contains(id)) throw const _DownloadCanceled();
      await temporary.rename(destination);
      if (_canceled.contains(id)) {
        if (await File(destination).exists()) await File(destination).delete();
        throw const _DownloadCanceled();
      }
      entry = entry.copyWith(
        state: DownloadState.completed,
        received: received,
        total: response.contentLength ?? received,
      );
      _replace(entry);
      return entry;
    } on _DownloadCanceled {
      await sink?.close();
      sink = null;
      final partial = File('$destination.part');
      if (await partial.exists()) await partial.delete();
      entry = entry.copyWith(state: DownloadState.canceled, error: '');
      _replace(entry);
      return entry;
    } catch (error) {
      entry = entry.copyWith(state: DownloadState.failed, error: '$error');
      _replace(entry);
      rethrow;
    } finally {
      _active.remove(id);
      await sink?.close();
      response?.close();
      await _save();
    }
  }

  Future<void> cancel(String id) async {
    final index = _entries.indexWhere((entry) => entry.id == id);
    if (index < 0) return;
    if (_entries[index].state == DownloadState.downloading ||
        _entries[index].state == DownloadState.queued) {
      _canceled.add(id);
    }
    final active = _active.remove(id);
    if (active != null) {
      await active.subscription.cancel();
      if (!active.completer.isCompleted) {
        active.completer.completeError(const _DownloadCanceled());
      }
    }
    _entries[index] = _entries[index].copyWith(state: DownloadState.canceled);
    await _save();
    notifyListeners();
  }

  Future<void> remove(String id) async {
    _removed.add(id);
    await cancel(id);
    final index = _entries.indexWhere((entry) => entry.id == id);
    if (index < 0) return;
    final file = File(_entries[index].filePath);
    if (await file.exists()) await file.delete();
    final mediaDirectory = Directory(
      '${path.withoutExtension(_entries[index].filePath)}.media',
    );
    if (await mediaDirectory.exists()) {
      await mediaDirectory.delete(recursive: true);
    }
    _entries.removeAt(index);
    await _save();
    notifyListeners();
    if (!_running.containsKey(id)) _removed.remove(id);
  }

  void _replace(
    DownloadEntry entry, {
    bool persist = true,
    bool notify = true,
  }) {
    if (_removed.contains(entry.id)) return;
    final index = _entries.indexWhere((value) => value.id == entry.id);
    if (index < 0) {
      _entries.insert(0, entry);
    } else {
      _entries[index] = entry;
    }
    if (notify) notifyListeners();
    if (persist) unawaited(_save());
  }

  bool _shouldNotifyProgress(String id) {
    final now = DateTime.now();
    final last = _lastProgressNotifications[id];
    if (last != null &&
        now.difference(last) < const Duration(milliseconds: 200)) {
      return false;
    }
    _lastProgressNotifications[id] = now;
    return true;
  }

  Future<Directory> _downloadDirectory() async {
    final root =
        await (documentsDirectoryProvider?.call() ??
            getApplicationDocumentsDirectory());
    final directory = Directory(path.join(root.path, 'FBox', 'Downloads'));
    await directory.create(recursive: true);
    return directory;
  }

  Future<File> _indexFile() async =>
      File(path.join((await _downloadDirectory()).path, 'downloads.json'));

  Future<void> _save() async {
    final snapshot = jsonEncode(
      _entries.map((entry) => entry.toJson()).toList(),
    );
    final operation = _saveTail.then((_) async {
      final file = await _indexFile();
      await file.writeAsString(snapshot, flush: true);
    });
    _saveTail = operation.catchError((Object _, StackTrace _) {});
    await operation;
  }

  static String _safeName(String value) => value
      .replaceAll(RegExp(r'[\\/:*?"<>|]'), '_')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();

  static String _stableSuffix(String value) {
    var hash = 0x811C9DC5;
    for (final byte in utf8.encode(value)) {
      hash ^= byte;
      hash = (hash * 0x01000193) & 0xFFFFFFFF;
    }
    return hash.toRadixString(16).padLeft(8, '0');
  }

  static String _extensionFor(String url) {
    final extension = path.extension(Uri.tryParse(url)?.path ?? '');
    return extension.isEmpty || extension.length > 8 ? '.mp4' : extension;
  }

  static bool _isHlsResponse(Map<String, String> headers) {
    final contentType =
        headers[HttpHeaders.contentTypeHeader]?.toLowerCase() ?? '';
    return contentType.contains('mpegurl') ||
        contentType.contains('application/x-mpegurl');
  }

  Future<DownloadEntry> _downloadHls({
    required DownloadEntry entry,
    required Map<String, String> headers,
    required String destination,
  }) async {
    final mediaDirectory = Directory(
      '${path.withoutExtension(destination)}.media',
    );
    final temporary = File('$destination.part');
    try {
      await mediaDirectory.create(recursive: true);
      if (_canceled.contains(entry.id)) throw const _DownloadCanceled();
      final rootPlaylistUri = Uri.parse(entry.url);
      final rootPlaylist = await network.getText(
        rootPlaylistUri,
        headers: headers,
      );
      if (_canceled.contains(entry.id)) throw const _DownloadCanceled();
      final variant = _selectHlsVariant(rootPlaylist, rootPlaylistUri);
      final mediaPlaylists = <Uri, String>{};
      if (variant != null) {
        mediaPlaylists[variant] = await network.getText(
          variant,
          headers: headers,
        );
        if (_canceled.contains(entry.id)) throw const _DownloadCanceled();
        for (final line in rootPlaylist.split(RegExp(r'\r?\n'))) {
          final mediaUri = _hlsMediaUri(line.trim(), rootPlaylistUri);
          if (mediaUri == null || mediaPlaylists.containsKey(mediaUri)) {
            continue;
          }
          mediaPlaylists[mediaUri] = await network.getText(
            mediaUri,
            headers: headers,
          );
          if (_canceled.contains(entry.id)) throw const _DownloadCanceled();
        }
      } else {
        mediaPlaylists[rootPlaylistUri] = rootPlaylist;
      }
      final resources = <Uri>{};
      for (final playlist in mediaPlaylists.entries) {
        for (final line in playlist.value.split(RegExp(r'\r?\n'))) {
          final trimmed = line.trim();
          final key = _hlsKeyUri(trimmed, playlist.key);
          if (key != null) resources.add(key);
          final initializationMap = _hlsMapUri(trimmed, playlist.key);
          if (initializationMap != null) resources.add(initializationMap);
          if (trimmed.isNotEmpty && !trimmed.startsWith('#')) {
            resources.add(playlist.key.resolve(trimmed));
          }
        }
      }
      entry = entry.copyWith(
        state: DownloadState.downloading,
        total: resources.length,
      );
      _replace(entry);
      final names = <String, String>{};
      var completed = 0;
      for (final resource in resources) {
        if (_canceled.contains(entry.id)) throw const _DownloadCanceled();
        if (names.containsKey(resource.toString())) continue;
        final extension = path.extension(resource.path);
        final fileName =
            'asset_${names.length.toString().padLeft(5, '0')}'
            '${extension.isEmpty || extension.length > 8 ? '.bin' : extension}';
        final response = await network.getBytes(resource, headers: headers);
        await File(path.join(mediaDirectory.path, fileName))
            .writeAsBytes(response.bytes, flush: true);
        names[resource.toString()] = fileName;
        completed++;
        entry = entry.copyWith(received: completed);
        _replace(
          entry,
          persist: false,
          notify: _shouldNotifyProgress(entry.id),
        );
      }
      final folder = path.basename(mediaDirectory.path);
      late final String rewrittenPlaylist;
      if (variant == null) {
        rewrittenPlaylist = _rewriteHlsMediaPlaylist(
          rootPlaylist,
          rootPlaylistUri,
          names,
          prefix: '$folder/',
        );
      } else {
        final playlistNames = <String, String>{};
        var playlistIndex = 0;
        for (final playlist in mediaPlaylists.entries) {
          final fileName =
              'playlist_${playlistIndex.toString().padLeft(3, '0')}.m3u8';
          playlistIndex++;
          await File(path.join(mediaDirectory.path, fileName)).writeAsString(
            _rewriteHlsMediaPlaylist(
              playlist.value,
              playlist.key,
              names,
              prefix: '',
            ),
            flush: true,
          );
          playlistNames[playlist.key.toString()] = fileName;
        }
        rewrittenPlaylist = _rewriteHlsMasterPlaylist(
          rootPlaylist,
          rootPlaylistUri,
          variant,
          playlistNames,
          folder,
        );
      }
      if (_canceled.contains(entry.id)) throw const _DownloadCanceled();
      await temporary.writeAsString(rewrittenPlaylist, flush: true);
      if (_canceled.contains(entry.id)) throw const _DownloadCanceled();
      if (await File(destination).exists()) await File(destination).delete();
      if (_canceled.contains(entry.id)) throw const _DownloadCanceled();
      await temporary.rename(destination);
      if (_canceled.contains(entry.id)) {
        if (await File(destination).exists()) await File(destination).delete();
        throw const _DownloadCanceled();
      }
      entry = entry.copyWith(
        state: DownloadState.completed,
        received: resources.length,
        total: resources.length,
      );
      _replace(entry);
      return entry;
    } on _DownloadCanceled {
      if (await temporary.exists()) await temporary.delete();
      if (await mediaDirectory.exists()) {
        await mediaDirectory.delete(recursive: true);
      }
      entry = entry.copyWith(state: DownloadState.canceled, error: '');
      _replace(entry);
      return entry;
    } catch (error) {
      entry = entry.copyWith(state: DownloadState.failed, error: '$error');
      _replace(entry);
      rethrow;
    } finally {
      await _save();
    }
  }

  static Uri? _selectHlsVariant(String playlist, Uri base) {
    final lines = playlist.split(RegExp(r'\r?\n'));
    Uri? selected;
    var selectedBandwidth = -1;
    for (var index = 0; index < lines.length - 1; index++) {
      final line = lines[index].trim();
      if (!line.startsWith('#EXT-X-STREAM-INF')) continue;
      final bandwidth =
          int.tryParse(
            RegExp(r'BANDWIDTH=(\d+)').firstMatch(line)?.group(1) ?? '',
          ) ??
          0;
      var next = index + 1;
      while (next < lines.length && lines[next].trim().startsWith('#')) {
        next++;
      }
      if (next < lines.length && bandwidth >= selectedBandwidth) {
        selected = base.resolve(lines[next].trim());
        selectedBandwidth = bandwidth;
      }
    }
    return selected;
  }

  static Uri? _hlsKeyUri(String line, Uri base) {
    if (!line.startsWith('#EXT-X-KEY')) return null;
    final value = RegExp(r'URI="([^"]+)"').firstMatch(line)?.group(1);
    return value == null ? null : base.resolve(value);
  }

  static Uri? _hlsMapUri(String line, Uri base) {
    if (!line.startsWith('#EXT-X-MAP')) return null;
    final value = RegExp(r'URI="([^"]+)"').firstMatch(line)?.group(1);
    return value == null ? null : base.resolve(value);
  }

  static Uri? _hlsMediaUri(String line, Uri base) {
    if (!line.startsWith('#EXT-X-MEDIA')) return null;
    final value = RegExp(r'URI="([^"]+)"').firstMatch(line)?.group(1);
    return value == null ? null : base.resolve(value);
  }

  static String _rewriteHlsMediaPlaylist(
    String playlist,
    Uri playlistUri,
    Map<String, String> names, {
    required String prefix,
  }) {
    final rewritten = <String>[];
    for (final line in playlist.split(RegExp(r'\r?\n'))) {
      final trimmed = line.trim();
      final attributedUri =
          _hlsKeyUri(trimmed, playlistUri) ?? _hlsMapUri(trimmed, playlistUri);
      if (attributedUri != null) {
        final name = names[attributedUri.toString()];
        rewritten.add(
          name == null
              ? line
              : line.replaceFirst(RegExp(r'URI="[^"]+"'), 'URI="$prefix$name"'),
        );
      } else if (trimmed.isNotEmpty && !trimmed.startsWith('#')) {
        final name = names[playlistUri.resolve(trimmed).toString()];
        rewritten.add(name == null ? line : '$prefix$name');
      } else {
        rewritten.add(line);
      }
    }
    return rewritten.join('\n');
  }

  static String _rewriteHlsMasterPlaylist(
    String playlist,
    Uri playlistUri,
    Uri selectedVariant,
    Map<String, String> playlistNames,
    String folder,
  ) {
    final lines = playlist.split(RegExp(r'\r?\n'));
    final rewritten = <String>[];
    for (var index = 0; index < lines.length; index++) {
      final line = lines[index];
      final trimmed = line.trim();
      if (trimmed.startsWith('#EXT-X-MEDIA')) {
        final mediaUri = _hlsMediaUri(trimmed, playlistUri);
        final name = mediaUri == null
            ? null
            : playlistNames[mediaUri.toString()];
        rewritten.add(
          name == null
              ? line
              : line.replaceFirst(
                  RegExp(r'URI="[^"]+"'),
                  'URI="$folder/$name"',
                ),
        );
        continue;
      }
      if (trimmed.startsWith('#EXT-X-STREAM-INF')) {
        var uriIndex = index + 1;
        while (uriIndex < lines.length &&
            lines[uriIndex].trim().startsWith('#')) {
          uriIndex++;
        }
        if (uriIndex < lines.length &&
            playlistUri.resolve(lines[uriIndex].trim()) == selectedVariant) {
          final name = playlistNames[selectedVariant.toString()];
          if (name != null) {
            rewritten
              ..add(line)
              ..add('$folder/$name');
          }
        }
        index = uriIndex;
        continue;
      }
      if (trimmed.isEmpty || trimmed.startsWith('#')) rewritten.add(line);
    }
    return rewritten.join('\n');
  }

  @override
  void dispose() {
    for (final active in _active.values) {
      unawaited(active.subscription.cancel());
      if (!active.completer.isCompleted) {
        active.completer.completeError(const _DownloadCanceled());
      }
    }
    _active.clear();
    super.dispose();
  }
}

class _ActiveDownload {
  const _ActiveDownload(this.subscription, this.completer);

  final StreamSubscription<List<int>> subscription;
  final Completer<void> completer;
}

class _DownloadCanceled implements Exception {
  const _DownloadCanceled();
}
