import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/app/fbox_theme.dart';
import 'package:flutter/material.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  bool _autoLoad = true;
  bool _rememberProgress = true;
  bool _hardwareDecode = true;
  bool _glass = false;
  bool _backgroundBlur = false;
  bool _privateBrowsing = false;
  bool _adFilter = true;
  bool _longPressSpeed = true;
  bool _backgroundPlay = false;
  double _backgroundAlpha = 1;

  @override
  Widget build(BuildContext context) {
    final store = FBoxScope.of(context);
    final themeLabel = switch (store.themeMode) {
      ThemeMode.system => '跟随系统',
      ThemeMode.dark => '已开启',
      ThemeMode.light => '已关闭',
    };
    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: ListView(
        children: [
          const _SectionTitle('主题与外观'),
          ListTile(
            leading: const Icon(Icons.palette_outlined),
            title: const Text('主题色'),
            subtitle: const Text('MBox 默认蓝'),
            trailing: Container(
              width: 24,
              height: 24,
              decoration: const BoxDecoration(
                color: FBoxTheme.primary,
                shape: BoxShape.circle,
              ),
            ),
            onTap: () => _showThemeColors(context),
          ),
          SwitchListTile(
            key: const Key('settings-dark-switch'),
            secondary: const Icon(Icons.dark_mode_outlined),
            title: const Text('深色模式'),
            subtitle: Text(themeLabel),
            value: store.themeMode == ThemeMode.dark,
            onChanged: (enabled) =>
                store.setThemeMode(enabled ? ThemeMode.dark : ThemeMode.light),
          ),
          const ListTile(
            leading: Icon(Icons.grid_view_rounded),
            title: Text('主页内容'),
            subtitle: Text('3 列 · 海报模式'),
            trailing: Icon(Icons.chevron_right),
          ),
          const ListTile(
            leading: Icon(Icons.hub_outlined),
            title: Text('聚合模式'),
            subtitle: Text('多源聚合'),
            trailing: Icon(Icons.chevron_right),
          ),
          const ListTile(
            leading: Icon(Icons.image_outlined),
            title: Text('背景图片'),
            subtitle: Text('默认背景'),
            trailing: Icon(Icons.chevron_right),
          ),
          ListTile(
            leading: const Icon(Icons.opacity_rounded),
            title: const Text('背景图片透明度'),
            subtitle: Slider(
              value: _backgroundAlpha,
              onChanged: (value) => setState(() => _backgroundAlpha = value),
            ),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.blur_on_rounded),
            title: const Text('液态玻璃'),
            value: _glass,
            onChanged: (value) => setState(() => _glass = value),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.blur_circular_rounded),
            title: const Text('应用背景模糊'),
            value: _backgroundBlur,
            onChanged: (value) => setState(() => _backgroundBlur = value),
          ),
          const Divider(height: 1),
          const _SectionTitle('通用'),
          SwitchListTile(
            secondary: const Icon(Icons.refresh_rounded),
            title: const Text('启动时自动加载上次配置'),
            value: _autoLoad,
            onChanged: (value) => setState(() => _autoLoad = value),
          ),
          const ListTile(
            leading: Icon(Icons.dns_outlined),
            title: Text('首页站源'),
            subtitle: Text('FBox 演示订阅'),
            trailing: Icon(Icons.chevron_right),
          ),
          const ListTile(
            leading: Icon(Icons.live_tv_outlined),
            title: Text('直播源'),
            subtitle: Text('演示直播源'),
            trailing: Icon(Icons.chevron_right),
          ),
          const ListTile(
            leading: Icon(Icons.security_outlined),
            title: Text('安全 DNS'),
            subtitle: Text('腾讯 DoH'),
            trailing: Icon(Icons.chevron_right),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.visibility_off_outlined),
            title: const Text('无痕浏览'),
            value: _privateBrowsing,
            onChanged: (value) => setState(() => _privateBrowsing = value),
          ),
          const Divider(height: 1),
          const _SectionTitle('播放器'),
          const ListTile(
            leading: Icon(Icons.play_circle_outline_rounded),
            title: Text('播放器'),
            subtitle: Text('系统播放器'),
            trailing: Icon(Icons.chevron_right),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.memory_rounded),
            title: const Text('IJK 解码方式'),
            subtitle: Text(_hardwareDecode ? '硬件解码' : '软件解码'),
            value: _hardwareDecode,
            onChanged: (value) => setState(() => _hardwareDecode = value),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.filter_alt_outlined),
            title: const Text('广告过滤'),
            value: _adFilter,
            onChanged: (value) => setState(() => _adFilter = value),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.fast_forward_rounded),
            title: const Text('长按倍速'),
            value: _longPressSpeed,
            onChanged: (value) => setState(() => _longPressSpeed = value),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.picture_in_picture_alt_outlined),
            title: const Text('后台播放'),
            value: _backgroundPlay,
            onChanged: (value) => setState(() => _backgroundPlay = value),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.timelapse_rounded),
            title: const Text('记忆播放进度'),
            value: _rememberProgress,
            onChanged: (value) => setState(() => _rememberProgress = value),
          ),
          const ListTile(
            leading: Icon(Icons.speed_rounded),
            title: Text('渲染方式'),
            subtitle: Text('SurfaceView'),
            trailing: Icon(Icons.chevron_right),
          ),
          const ListTile(
            leading: Icon(Icons.aspect_ratio_rounded),
            title: Text('画面缩放'),
            subtitle: Text('适应屏幕'),
            trailing: Icon(Icons.chevron_right),
          ),
          const ListTile(
            leading: Icon(Icons.cached_rounded),
            title: Text('IJK 缓存'),
            subtitle: Text('关闭'),
            trailing: Icon(Icons.chevron_right),
          ),
          const Divider(height: 1),
          const _SectionTitle('数据'),
          const ListTile(
            leading: Icon(Icons.backup_outlined),
            title: Text('数据备份还原'),
            subtitle: Text('备份订阅、收藏与设置'),
            trailing: Icon(Icons.chevron_right),
          ),
          const ListTile(
            leading: Icon(Icons.history_rounded),
            title: Text('历史记录'),
            subtitle: Text('管理或清空观看历史'),
            trailing: Icon(Icons.chevron_right),
          ),
          const ListTile(
            leading: Icon(Icons.cleaning_services_outlined),
            title: Text('清理缓存'),
            subtitle: Text('12.6 MB'),
            trailing: Icon(Icons.chevron_right),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  void _showThemeColors(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (context) => const SafeArea(
        child: Padding(
          padding: EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '主题色',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
              ),
              SizedBox(height: 18),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _ColorDot(Color(0xFF567DF4), true),
                  _ColorDot(Color(0xFF00796B), false),
                  _ColorDot(Color(0xFFFF5F00), false),
                  _ColorDot(Color(0xFF8E5DB7), false),
                  _ColorDot(Color(0xFFEB4D68), false),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 20, 18, 7),
      child: Text(
        title,
        style: const TextStyle(
          color: FBoxTheme.primary,
          fontSize: 13,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _ColorDot extends StatelessWidget {
  const _ColorDot(this.color, this.selected);

  final Color color;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 42,
      height: 42,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        border: selected ? Border.all(color: Colors.white, width: 4) : null,
        boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 4)],
      ),
      child: selected ? const Icon(Icons.check, color: Colors.white) : null,
    );
  }
}
