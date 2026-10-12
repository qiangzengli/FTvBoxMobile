import 'package:fbox/features/about/about_dialog.dart';
import 'package:fbox/features/library/library_page.dart';
import 'package:fbox/features/live/live_page.dart';
import 'package:fbox/features/local/local_video_page.dart';
import 'package:fbox/features/settings/settings_page.dart';
import 'package:fbox/features/subscription/subscription_page.dart';
import 'package:fbox/features/url_play/url_play_page.dart';
import 'package:flutter/material.dart';

enum _ProfileDestination {
  subscriptions,
  history,
  favorites,
  live,
  local,
  url,
  about,
  settings,
}

class MyPage extends StatelessWidget {
  const MyPage({super.key});

  static const _items =
      <
        ({
          IconData icon,
          String label,
          String keyName,
          _ProfileDestination destination,
        })
      >[
        (
          icon: Icons.storage_rounded,
          label: '订阅管理',
          keyName: 'profile-subscriptions',
          destination: _ProfileDestination.subscriptions,
        ),
        (
          icon: Icons.history_rounded,
          label: '观看历史',
          keyName: 'profile-history',
          destination: _ProfileDestination.history,
        ),
        (
          icon: Icons.star_border_rounded,
          label: '收藏',
          keyName: 'profile-favorites',
          destination: _ProfileDestination.favorites,
        ),
        (
          icon: Icons.live_tv_rounded,
          label: '直播',
          keyName: 'profile-live',
          destination: _ProfileDestination.live,
        ),
        (
          icon: Icons.video_library_outlined,
          label: '本地视频',
          keyName: 'profile-local',
          destination: _ProfileDestination.local,
        ),
        (
          icon: Icons.link_rounded,
          label: '播放链接',
          keyName: 'profile-url-play',
          destination: _ProfileDestination.url,
        ),
        (
          icon: Icons.info_outline_rounded,
          label: '关于',
          keyName: 'profile-about',
          destination: _ProfileDestination.about,
        ),
        (
          icon: Icons.settings_outlined,
          label: '设置',
          keyName: 'profile-settings',
          destination: _ProfileDestination.settings,
        ),
      ];

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(40, 30, 20, 16),
            child: Text(
              'FBox',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.w500),
            ),
          ),
          Expanded(
            child: Container(
              margin: const EdgeInsets.fromLTRB(20, 0, 20, 14),
              decoration: BoxDecoration(
                color: Theme.of(context).cardColor,
                borderRadius: BorderRadius.circular(14),
              ),
              child: ListView.builder(
                padding: const EdgeInsets.symmetric(vertical: 14),
                itemCount: _items.length,
                itemBuilder: (context, index) {
                  final item = _items[index];
                  return InkWell(
                    key: Key(item.keyName),
                    onTap: () => _open(context, item.destination),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 24,
                        vertical: 14,
                      ),
                      child: Row(
                        children: [
                          Icon(item.icon, size: 28),
                          const SizedBox(width: 20),
                          Text(
                            item.label,
                            style: const TextStyle(fontSize: 16),
                          ),
                          const Spacer(),
                          Icon(
                            Icons.chevron_right_rounded,
                            size: 26,
                            color: Theme.of(context).colorScheme.onSurface
                                .withValues(alpha: 0.55),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
          Center(
            child: Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: Text(
                'FBox 1.0.0',
                style: TextStyle(
                  fontSize: 11,
                  color: Theme.of(context).colorScheme.onSurface
                      .withValues(alpha: 0.45),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _open(BuildContext context, _ProfileDestination destination) {
    final page = switch (destination) {
      _ProfileDestination.subscriptions => const SubscriptionPage(),
      _ProfileDestination.history => const LibraryPage(
        kind: LibraryKind.history,
      ),
      _ProfileDestination.favorites => const LibraryPage(
        kind: LibraryKind.favorites,
      ),
      _ProfileDestination.live => const LivePage(),
      _ProfileDestination.local => const LocalVideoPage(),
      _ProfileDestination.url => const UrlPlayPage(),
      _ProfileDestination.settings => const SettingsPage(),
      _ProfileDestination.about => null,
    };
    if (page == null) {
      showFBoxAboutDialog(context);
      return;
    }
    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => page));
  }
}
