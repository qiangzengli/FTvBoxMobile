import 'package:flutter/material.dart';

class LocalVideoPage extends StatelessWidget {
  const LocalVideoPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('本地视频')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
        children: [
          const ListTile(
            leading: Icon(Icons.phone_android_rounded),
            title: Text('内部存储'),
            subtitle: Text('/storage/emulated/0'),
          ),
          const Divider(),
          ...const [
            ('Download', '2 个视频'),
            ('Movies', '5 个视频'),
            ('DCIM', '12 个视频'),
          ].map(
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
