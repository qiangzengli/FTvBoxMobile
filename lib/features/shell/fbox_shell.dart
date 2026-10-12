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
    return Scaffold(
      body: IndexedStack(index: _index, children: const [HomePage(), MyPage()]),
      bottomNavigationBar: BottomNavigationBar(
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
}
