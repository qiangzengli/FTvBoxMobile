import 'dart:io';
import 'dart:ui' as ui;

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/features/home/home_page.dart';
import 'package:fbox/features/profile/my_page.dart';
import 'package:flutter/material.dart';

class FBoxShell extends StatefulWidget {
  const FBoxShell({super.key});

  @override
  State<FBoxShell> createState() => _FBoxShellState();
}

class _FBoxShellState extends State<FBoxShell> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final store = FBoxScope.of(context);
    final background = store.settings['background']?.toString() ?? '默认背景';
    final alpha = ((store.settings['backgroundAlpha'] as num?)?.toDouble() ?? 1)
        .clamp(0.0, 1.0);
    final blur = store.settings['backgroundBlur'] == true;
    final glass = store.settings['glass'] == true;
    final backgroundLayer = _backgroundLayer(
      context,
      background: background,
      imagePath: store.settings['backgroundImagePath']?.toString() ?? '',
      alpha: alpha,
    );
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Stack(
        fit: StackFit.expand,
        children: [
          if (blur)
            ImageFiltered(
              imageFilter: ui.ImageFilter.blur(sigmaX: 8, sigmaY: 8),
              child: backgroundLayer,
            )
          else
            backgroundLayer,
          IndexedStack(index: _index, children: const [HomePage(), MyPage()]),
        ],
      ),
      bottomNavigationBar: BottomNavigationBar(
        backgroundColor: glass
            ? Theme.of(context).colorScheme.surface.withValues(alpha: 0.78)
            : null,
        currentIndex: _index,
        onTap: (value) => setState(() => _index = value),
        items: const [
          BottomNavigationBarItem(
            key: Key('bottom-nav-home'),
            icon: Icon(Icons.home_outlined),
            activeIcon: Icon(Icons.home),
            label: '首页',
          ),
          BottomNavigationBarItem(
            key: Key('bottom-nav-my'),
            icon: Icon(Icons.person_outline),
            activeIcon: Icon(Icons.person),
            label: '我的',
          ),
        ],
      ),
    );
  }

  Widget _backgroundLayer(
    BuildContext context, {
    required String background,
    required String imagePath,
    required double alpha,
  }) {
    if (background == '相册选择' &&
        imagePath.isNotEmpty &&
        File(imagePath).existsSync()) {
      return Opacity(
        opacity: alpha,
        child: Image.file(File(imagePath), fit: BoxFit.cover),
      );
    }
    if (background == '纯色背景') {
      return ColoredBox(
        color: Theme.of(context).colorScheme.surface.withValues(alpha: alpha),
      );
    }
    return ColoredBox(color: Theme.of(context).scaffoldBackgroundColor);
  }
}
