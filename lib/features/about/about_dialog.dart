import 'package:flutter/material.dart';

Future<void> showFBoxAboutDialog(BuildContext context) {
  return showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      title: const Row(
        children: [
          Icon(Icons.live_tv_rounded, size: 34),
          SizedBox(width: 12),
          Text('FBox'),
        ],
      ),
      content: const SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('版本 1.0.0'),
            SizedBox(height: 14),
            Text(
              '基于 MBox 1.1.9 界面复刻的 Flutter 双平台应用。\n\n'
              '软件当前提供界面与本地演示功能，不制作、收集或发布任何订阅内容。',
              style: TextStyle(height: 1.5),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('确定'),
        ),
      ],
    ),
  );
}
