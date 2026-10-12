import 'dart:async';
import 'dart:io';

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:fbox/features/player/player_page.dart';
import 'package:fbox/services/download_manager.dart';
import 'package:fbox/services/local_media_service.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as path;

class LocalVideoPage extends StatefulWidget {
  const LocalVideoPage({super.key});

  @override
  State<LocalVideoPage> createState() => _LocalVideoPageState();
}

class _LocalVideoPageState extends State<LocalVideoPage> {
  final LocalMediaService _localMedia = LocalMediaService();
  List<LocalMediaEntry> _localFiles = const [];
  bool _scanning = true;

  @override
  void initState() {
    super.initState();
    unawaited(_refreshLibrary());
  }

  @override
  Widget build(BuildContext context) {
    final store = FBoxScope.of(context);
    final downloads = store.downloads.entries;
    final isIOS = Platform.isIOS;
    return Scaffold(
      appBar: AppBar(
        title: const Text('本地视频'),
        actions: [
          IconButton(
            tooltip: '选择文件',
            onPressed: () => _pickFiles(context),
            icon: const Icon(Icons.add_rounded),
          ),
          if (Platform.isAndroid)
            IconButton(
              tooltip: '扫描目录',
              onPressed: _scanDirectory,
              icon: const Icon(Icons.drive_folder_upload_outlined),
            ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
        children: [
          ListTile(
            leading: Icon(
              isIOS ? Icons.phone_iphone_rounded : Icons.phone_android_rounded,
            ),
            title: Text(isIOS ? '文件 App' : '系统文件选择器'),
            subtitle: Text(isIOS ? '在我的 iPhone · FBox' : '内部存储与外置存储'),
            trailing: const Icon(Icons.folder_open_rounded),
            onTap: () => _pickFiles(context),
          ),
          const Divider(),
          const Padding(
            padding: EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: Text(
              '已导入媒体',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
            ),
          ),
          if (_scanning)
            const Center(child: CircularProgressIndicator())
          else if (_localFiles.isEmpty)
            const Padding(
              padding: EdgeInsets.all(20),
              child: Center(child: Text('暂无已导入媒体')),
            )
          else
            for (final entry in _localFiles)
              ListTile(
                leading: const Icon(Icons.video_file_outlined),
                title: Text(entry.name),
                subtitle: Text(_formatBytes(entry.size)),
                onTap: () => _openFile(context, entry.filePath),
                trailing: IconButton(
                  tooltip: '移除本地副本',
                  onPressed: () => _removeLocal(entry),
                  icon: const Icon(Icons.delete_outline),
                ),
              ),
          const Divider(),
          const Padding(
            padding: EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: Text(
              '离线下载',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
            ),
          ),
          if (downloads.isEmpty)
            const Padding(
              padding: EdgeInsets.all(28),
              child: Center(child: Text('暂无离线视频')),
            )
          else
            for (final entry in downloads) _DownloadTile(entry: entry),
        ],
      ),
    );
  }

  Future<void> _pickFiles(BuildContext context) async {
    try {
      final result = await FilePicker.platform.pickFiles(
        allowMultiple: true,
        type: FileType.video,
      );
      if (result == null || !context.mounted) return;
      final paths = result.files
          .map((file) => file.path)
          .whereType<String>()
          .toList();
      if (paths.isEmpty) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('所选文件无法读取')));
        return;
      }
      final files = await _localMedia.importFiles(paths);
      await _refreshLibrary();
      if (!context.mounted || files.isEmpty) return;
      if (files.length == 1) {
        _openFile(context, files.first.filePath);
        return;
      }
      final selected = await showModalBottomSheet<LocalMediaEntry>(
        context: context,
        showDragHandle: true,
        builder: (context) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              const ListTile(title: Text('选择要播放的视频')),
              for (final file in files)
                ListTile(
                  leading: const Icon(Icons.video_file_outlined),
                  title: Text(file.name),
                  subtitle: Text(_formatBytes(file.size)),
                  onTap: () => Navigator.pop(context, file),
                ),
            ],
          ),
        ),
      );
      if (selected != null && context.mounted) {
        _openFile(context, selected.filePath);
      }
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('文件选择失败：$error')));
      }
    }
  }

  Future<void> _scanDirectory() async {
    try {
      final directory = await FilePicker.platform.getDirectoryPath(
        dialogTitle: '选择要扫描的媒体目录',
      );
      if (directory == null) return;
      setState(() => _scanning = true);
      final imported = await _localMedia.importDirectory(directory);
      await _refreshLibrary();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已扫描并导入 ${imported.length} 个媒体文件')),
        );
      }
    } catch (error) {
      if (mounted) {
        setState(() => _scanning = false);
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('目录扫描失败：$error')));
      }
    }
  }

  Future<void> _refreshLibrary() async {
    try {
      final files = await _localMedia.scanLibrary();
      if (mounted) {
        setState(() {
          _localFiles = files;
          _scanning = false;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() => _scanning = false);
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('本地媒体扫描失败：$error')));
      }
    }
  }

  Future<void> _removeLocal(LocalMediaEntry entry) async {
    try {
      await _localMedia.remove(entry);
      await _refreshLibrary();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('移除失败：$error')));
      }
    }
  }

  void _openFile(BuildContext context, String filePath) {
    final name = path.basename(filePath);
    final item = MediaItem(
      id: 'local-${filePath.hashCode}',
      title: name,
      note: '本地视频',
      year: '',
      category: '本地',
      rating: '',
      description: filePath,
      director: '',
      actors: '',
      episodes: 1,
      color: 0xFF40655B,
    );
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PlayerPage(
          item: item,
          episodeName: '本地',
          source: '本地文件',
          playback: PlaybackRequest(url: File(filePath).uri.toString()),
        ),
      ),
    );
  }

  static String _formatBytes(int bytes) {
    if (bytes >= 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
    }
    if (bytes >= 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / 1024).toStringAsFixed(1)} KB';
  }
}

class _DownloadTile extends StatelessWidget {
  const _DownloadTile({required this.entry});

  final DownloadEntry entry;

  @override
  Widget build(BuildContext context) {
    final store = FBoxScope.of(context);
    return ListTile(
      leading: Icon(
        entry.state == DownloadState.completed
            ? Icons.download_done_rounded
            : Icons.downloading_rounded,
      ),
      title: Text(entry.title),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('${entry.episode} · ${_stateLabel(entry.state)}'),
          if (entry.state == DownloadState.downloading)
            LinearProgressIndicator(
              value: entry.total > 0 ? entry.progress : null,
            ),
          if (entry.error.isNotEmpty)
            Text(entry.error, maxLines: 1, overflow: TextOverflow.ellipsis),
        ],
      ),
      trailing: IconButton(
        tooltip: '删除',
        onPressed: () => store.downloads.remove(entry.id),
        icon: const Icon(Icons.delete_outline),
      ),
      onTap: entry.state == DownloadState.completed
          ? () {
              final item = MediaItem(
                id: entry.id,
                title: entry.title,
                note: entry.episode,
                year: '',
                category: '离线',
                rating: '',
                description: entry.filePath,
                director: '',
                actors: '',
                episodes: 1,
                color: 0xFF48627A,
              );
              Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => PlayerPage(
                    item: item,
                    episodeName: entry.episode,
                    source: '离线下载',
                    playback: PlaybackRequest(
                      url: File(entry.filePath).uri.toString(),
                    ),
                  ),
                ),
              );
            }
          : null,
    );
  }

  static String _stateLabel(DownloadState state) => switch (state) {
    DownloadState.queued => '等待下载',
    DownloadState.downloading => '下载中',
    DownloadState.completed => '已完成',
    DownloadState.failed => '失败',
    DownloadState.canceled => '已取消',
  };
}
