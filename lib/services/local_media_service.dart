import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

class LocalMediaEntry {
  const LocalMediaEntry({
    required this.filePath,
    required this.name,
    required this.size,
    required this.modifiedAt,
  });

  final String filePath;
  final String name;
  final int size;
  final DateTime modifiedAt;
}

class LocalMediaService {
  static const _extensions = {
    '.mp4',
    '.m4v',
    '.mov',
    '.mkv',
    '.avi',
    '.webm',
    '.ts',
    '.m2ts',
    '.flv',
    '.m3u8',
    '.mp3',
    '.m4a',
    '.aac',
    '.flac',
  };

  Future<File> _indexFile() async {
    final directory = await _libraryDirectory();
    return File(path.join(directory.path, 'imported_index.json'));
  }

  Future<Set<String>> _readIndex() async {
    try {
      final file = await _indexFile();
      if (!await file.exists()) return {};
      final decoded = jsonDecode(await file.readAsString());
      if (decoded is List) {
        return decoded.map((e) => e.toString()).toSet();
      }
    } on Object {
      // Return empty set on corrupt index
    }
    return {};
  }

  Future<void> _writeIndex(Set<String> paths) async {
    final file = await _indexFile();
    await file.writeAsString(jsonEncode(paths.toList()), flush: true);
  }

  Future<List<LocalMediaEntry>> scanLibrary() async {
    final directory = await _libraryDirectory();
    final localEntries = await _scan(directory, recursive: true);
    final externalPaths = await _readIndex();
    final externalEntries = <LocalMediaEntry>[];
    final validExternalPaths = <String>{};
    for (final extPath in externalPaths) {
      final file = File(extPath);
      if (await file.exists() && _isMedia(file.path)) {
        validExternalPaths.add(extPath);
        externalEntries.add(await _entry(file));
      }
    }
    if (validExternalPaths.length != externalPaths.length) {
      await _writeIndex(validExternalPaths);
    }
    final allEntries = <String, LocalMediaEntry>{};
    for (final entry in [...localEntries, ...externalEntries]) {
      allEntries[path.normalize(entry.filePath)] = entry;
    }
    final result = allEntries.values.toList();
    result.sort((a, b) => b.modifiedAt.compareTo(a.modifiedAt));
    return result;
  }

  Future<List<LocalMediaEntry>> importFiles(Iterable<String> filePaths) async {
    final imported = <LocalMediaEntry>[];
    final index = await _readIndex();
    for (final filePath in filePaths) {
      final source = File(filePath);
      if (!await source.exists() || !_isMedia(source.path)) continue;
      index.add(source.path);
      imported.add(await _entry(source));
    }
    await _writeIndex(index);
    return imported;
  }

  Future<List<LocalMediaEntry>> importDirectory(String directoryPath) async {
    final source = Directory(directoryPath);
    if (!await source.exists()) throw const FileSystemException('所选目录不存在');
    final files = await _scan(source, recursive: true);
    return importFiles(files.map((entry) => entry.filePath));
  }

  Future<void> remove(LocalMediaEntry entry) async {
    final root = await _libraryDirectory();
    final normalizedRoot = path.normalize(root.absolute.path);
    final normalizedFile = path.normalize(File(entry.filePath).absolute.path);
    final index = await _readIndex();
    if (index.remove(entry.filePath) || index.remove(normalizedFile)) {
      await _writeIndex(index);
    }
    if (path.isWithin(normalizedRoot, normalizedFile)) {
      final file = File(normalizedFile);
      if (await file.exists()) await file.delete();
    }
  }

  Future<Directory> _libraryDirectory() async {
    final documents = await getApplicationDocumentsDirectory();
    final directory = Directory(path.join(documents.path, 'FBox', 'Local'));
    await directory.create(recursive: true);
    return directory;
  }

  Future<List<LocalMediaEntry>> _scan(
    Directory directory, {
    required bool recursive,
  }) async {
    final entries = <LocalMediaEntry>[];
    await for (final entity in directory.list(
      recursive: recursive,
      followLinks: false,
    )) {
      if (entity is File && _isMedia(entity.path)) {
        entries.add(await _entry(entity));
      }
    }
    entries.sort((a, b) => b.modifiedAt.compareTo(a.modifiedAt));
    return entries;
  }

  static Future<LocalMediaEntry> _entry(File file) async {
    final stat = await file.stat();
    return LocalMediaEntry(
      filePath: file.path,
      name: path.basename(file.path),
      size: stat.size,
      modifiedAt: stat.modified,
    );
  }

  static bool _isMedia(String filePath) =>
      _extensions.contains(path.extension(filePath).toLowerCase());
}
