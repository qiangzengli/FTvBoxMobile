import 'dart:io';

import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

class BackgroundImageService {
  const BackgroundImageService({this.supportDirectoryProvider});

  final Future<Directory> Function()? supportDirectoryProvider;

  Future<String> importImage(String sourcePath) async {
    final source = File(sourcePath);
    if (!await source.exists()) throw const FileSystemException('所选背景图片不存在');
    final support =
        await (supportDirectoryProvider?.call() ??
            getApplicationSupportDirectory());
    final directory = Directory(path.join(support.path, 'FBox', 'Background'));
    await directory.create(recursive: true);
    final rawExtension = path.extension(source.path).toLowerCase();
    final extension = rawExtension.isEmpty || rawExtension.length > 10
        ? '.image'
        : rawExtension;
    final destination = File(path.join(directory.path, 'background$extension'));
    if (path.equals(source.absolute.path, destination.absolute.path)) {
      return destination.path;
    }
    final temporary = File('${destination.path}.part');
    if (await temporary.exists()) await temporary.delete();
    await source.copy(temporary.path);
    if (await destination.exists()) await destination.delete();
    await temporary.rename(destination.path);
    await for (final entity in directory.list()) {
      if (entity is File &&
          entity.path != destination.path &&
          path.basename(entity.path).startsWith('background.')) {
        await entity.delete();
      }
    }
    return destination.path;
  }
}
