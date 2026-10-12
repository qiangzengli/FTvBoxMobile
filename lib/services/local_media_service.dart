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

  Future<List<LocalMediaEntry>> scanLibrary() async {
    final directory = await _libraryDirectory();
    return _scan(directory, recursive: true);
  }

  Future<List<LocalMediaEntry>> importFiles(Iterable<String> filePaths) async {
    final directory = await _libraryDirectory();
    final imported = <LocalMediaEntry>[];
    for (final filePath in filePaths) {
      final source = File(filePath);
      if (!await source.exists() || !_isMedia(source.path)) continue;
      final destination = await _destinationFor(directory, source);
      final file =
          path.equals(
            path.normalize(source.absolute.path),
            path.normalize(destination.absolute.path),
          )
          ? source
          : await source.copy(destination.path);
      imported.add(await _entry(file));
    }
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
    if (!path.isWithin(normalizedRoot, normalizedFile)) {
      throw const FileSystemException('只能删除已导入 FBox 的媒体');
    }
    final file = File(normalizedFile);
    if (await file.exists()) await file.delete();
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

  Future<File> _destinationFor(Directory directory, File source) async {
    final originalName = _safeName(path.basename(source.path));
    var destination = File(path.join(directory.path, originalName));
    if (!await destination.exists()) return destination;
    if (path.equals(
      path.normalize(source.absolute.path),
      path.normalize(destination.absolute.path),
    )) {
      return destination;
    }
    final stem = path.basenameWithoutExtension(originalName);
    final extension = path.extension(originalName);
    var suffix = 2;
    while (await destination.exists()) {
      destination = File(
        path.join(directory.path, '$stem ($suffix)$extension'),
      );
      suffix++;
    }
    return destination;
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

  static String _safeName(String value) => value
      .replaceAll(RegExp(r'[\\/:*?"<>|]'), '_')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
}
