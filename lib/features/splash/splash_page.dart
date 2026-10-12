import 'dart:async';

import 'package:fbox/features/shell/fbox_shell.dart';
import 'package:flutter/material.dart';

class LaunchGate extends StatefulWidget {
  const LaunchGate({super.key});

  @override
  State<LaunchGate> createState() => _LaunchGateState();
}

class _LaunchGateState extends State<LaunchGate> {
  bool _showSplash = true;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer(const Duration(milliseconds: 650), () {
      if (mounted) setState(() => _showSplash = false);
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 220),
      child: _showSplash ? const SplashPage() : const FBoxShell(),
    );
  }
}

class SplashPage extends StatelessWidget {
  const SplashPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      key: const ValueKey('splash'),
      color: const Color(0xFFF7F9FF),
      child: Stack(
        fit: StackFit.expand,
        children: [
          const Center(child: _SplashMark()),
          Center(
            child: Transform.translate(
              offset: const Offset(0, 98),
              child: const Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    'FBox',
                    style: TextStyle(
                      color: Color(0xFF25304F),
                      fontSize: 28,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1.2,
                      decoration: TextDecoration.none,
                    ),
                  ),
                  SizedBox(height: 9),
                  Text(
                    '自由 · 聚合 · 畅享',
                    style: TextStyle(
                      color: Color(0xFF79829E),
                      fontSize: 13,
                      fontWeight: FontWeight.w400,
                      letterSpacing: 2,
                      decoration: TextDecoration.none,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SplashMark extends StatelessWidget {
  const _SplashMark();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 72,
      height: 72,
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF7696FF), Color(0xFF3E4FB1)],
        ),
      ),
      child: Center(
        child: Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.09),
            border: Border.all(color: Colors.white, width: 2.3),
            borderRadius: BorderRadius.circular(9),
          ),
          alignment: Alignment.center,
          child: const Text(
            'F',
            style: TextStyle(
              color: Colors.white,
              fontSize: 34,
              fontWeight: FontWeight.w700,
              height: 1,
              decoration: TextDecoration.none,
            ),
          ),
        ),
      ),
    );
  }
}
