import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

class LocalVideoPage extends StatelessWidget {
  const LocalVideoPage({super.key});

  @override
  Widget build(BuildContext context) {
    final isIOS = defaultTargetPlatform == TargetPlatform.iOS;
    final storageLabel = isIOS ? '在我的 iPhone' : '内部存储';
    final storagePath = isIOS ? '文件 · FBox' : '/storage/emulated/0';
    final folders = isIOS
        ? const [('FBox', '2 个视频'), ('下载项', '5 个视频')]
        : const [
            ('Download', '2 个视频'),
            ('Movies', '5 个视频'),
            ('DCIM', '12 个视频'),
          ];
    return Scaffold(
      appBar: AppBar(title: const Text('本地视频')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
        children: [
          ListTile(
            leading: Icon(
              isIOS ? Icons.phone_iphone_rounded : Icons.phone_android_rounded,
            ),
            title: Text(storageLabel),
            subtitle: Text(storagePath),
          ),
          const Divider(),
          ...folders.map(
            (folder) => ListTile(
              leading: const Icon(
                Icons.folder_rounded,
                color: Color(0xFFFFB74D),
                size: 34,
              ),
              title: Text(folder.$1),
              subtitle: Text(folder.$2),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {},
            ),
          ),
        ],
      ),
    );
  }
}
