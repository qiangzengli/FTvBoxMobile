import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/features/library/library_page.dart';
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
  String _homeLayout = '3 列 · 海报模式';
  String _aggregateMode = '多源聚合';
  String _background = '默认背景';
  String _homeSource = 'FBox 演示订阅';
  String _liveSource = '演示直播源';
  String _dns = '腾讯 DoH';
  String _player = '系统播放器';
  String _renderMode = 'SurfaceView';
  String _scaleMode = '适应屏幕';
  String _ijkCache = '关闭';
  double _cacheSize = 12.6;

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
            subtitle: const Text('FBox 默认蓝'),
            trailing: Container(
              width: 24,
              height: 24,
              decoration: BoxDecoration(
                color: store.themeColor,
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
          ListTile(
            leading: const Icon(Icons.grid_view_rounded),
            title: const Text('主页内容'),
            subtitle: Text(_homeLayout),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectOption(
              title: '主页内容',
              options: const ['3 列 · 海报模式', '2 列 · 海报模式', '列表模式'],
              selected: _homeLayout,
              onSelected: (value) => setState(() => _homeLayout = value),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.hub_outlined),
            title: const Text('聚合模式'),
            subtitle: Text(_aggregateMode),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectOption(
              title: '聚合模式',
              options: const ['多源聚合', '当前站源', '关闭聚合'],
              selected: _aggregateMode,
              onSelected: (value) => setState(() => _aggregateMode = value),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.image_outlined),
            title: const Text('背景图片'),
            subtitle: Text(_background),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectOption(
              title: '背景图片',
              options: const ['默认背景', '纯色背景', '相册选择'],
              selected: _background,
              onSelected: (value) => setState(() => _background = value),
            ),
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
          ListTile(
            leading: const Icon(Icons.dns_outlined),
            title: const Text('首页站源'),
            subtitle: Text(_homeSource),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectOption(
              title: '首页站源',
              options: const ['FBox 演示订阅', '上次使用', '每次询问'],
              selected: _homeSource,
              onSelected: (value) => setState(() => _homeSource = value),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.live_tv_outlined),
            title: const Text('直播源'),
            subtitle: Text(_liveSource),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectOption(
              title: '直播源',
              options: const ['演示直播源', '订阅直播源', '本地播放列表'],
              selected: _liveSource,
              onSelected: (value) => setState(() => _liveSource = value),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.security_outlined),
            title: const Text('安全 DNS'),
            subtitle: Text(_dns),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectOption(
              title: '安全 DNS',
              options: const ['腾讯 DoH', '阿里 DoH', '系统 DNS', '关闭'],
              selected: _dns,
              onSelected: (value) => setState(() => _dns = value),
            ),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.visibility_off_outlined),
            title: const Text('无痕浏览'),
            value: _privateBrowsing,
            onChanged: (value) => setState(() => _privateBrowsing = value),
          ),
          const Divider(height: 1),
          const _SectionTitle('播放器'),
          ListTile(
            leading: const Icon(Icons.play_circle_outline_rounded),
            title: const Text('播放器'),
            subtitle: Text(_player),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectOption(
              title: '播放器',
              options: const ['系统播放器', 'IJK 播放器', '外部播放器'],
              selected: _player,
              onSelected: (value) => setState(() => _player = value),
            ),
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
          ListTile(
            leading: const Icon(Icons.speed_rounded),
            title: const Text('渲染方式'),
            subtitle: Text(_renderMode),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectOption(
              title: '渲染方式',
              options: const ['SurfaceView', 'TextureView'],
              selected: _renderMode,
              onSelected: (value) => setState(() => _renderMode = value),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.aspect_ratio_rounded),
            title: const Text('画面缩放'),
            subtitle: Text(_scaleMode),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectOption(
              title: '画面缩放',
              options: const ['适应屏幕', '铺满屏幕', '原始比例', '16:9'],
              selected: _scaleMode,
              onSelected: (value) => setState(() => _scaleMode = value),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.cached_rounded),
            title: const Text('IJK 缓存'),
            subtitle: Text(_ijkCache),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectOption(
              title: 'IJK 缓存',
              options: const ['关闭', '100 MB', '300 MB', '1 GB'],
              selected: _ijkCache,
              onSelected: (value) => setState(() => _ijkCache = value),
            ),
          ),
          const Divider(height: 1),
          const _SectionTitle('数据'),
          ListTile(
            leading: const Icon(Icons.backup_outlined),
            title: const Text('数据备份还原'),
            subtitle: const Text('备份订阅、收藏与设置'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _showActionSheet(
              title: '数据备份还原',
              actions: const ['创建本地备份', '从本地备份还原'],
            ),
          ),
          ListTile(
            leading: const Icon(Icons.history_rounded),
            title: const Text('历史记录'),
            subtitle: const Text('管理或清空观看历史'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _showHistoryActions(store),
          ),
          ListTile(
            key: const Key('settings-clear-cache'),
            leading: const Icon(Icons.cleaning_services_outlined),
            title: const Text('清理缓存'),
            subtitle: Text('${_cacheSize.toStringAsFixed(1)} MB'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              setState(() => _cacheSize = 0);
              _showMessage('缓存已清理');
            },
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  void _showThemeColors(BuildContext context) {
    final store = FBoxScope.of(context);
    const colors = [
      Color(0xFF567DF4),
      Color(0xFF00796B),
      Color(0xFFFF5F00),
      Color(0xFF8E5DB7),
      Color(0xFFEB4D68),
    ];
    showModalBottomSheet<void>(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (context) => SafeArea(
        child: Padding(
          padding: EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '主题色',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 18),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: colors
                    .map(
                      (color) =>
                          _ColorDot(color, store.themeColor == color, () {
                            store.setThemeColor(color);
                            Navigator.pop(context);
                          }),
                    )
                    .toList(),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _selectOption({
    required String title,
    required List<String> options,
    required String selected,
    required ValueChanged<String> onSelected,
  }) async {
    final value = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(context).height * 0.72,
          ),
          child: ListView(
            shrinkWrap: true,
            children: [
              ListTile(
                title: Text(
                  title,
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
              ...options.map(
                (option) => ListTile(
                  title: Text(option),
                  trailing: option == selected
                      ? Icon(
                          Icons.check_rounded,
                          color: Theme.of(context).colorScheme.primary,
                        )
                      : null,
                  onTap: () => Navigator.pop(context, option),
                ),
              ),
            ],
          ),
        ),
      ),
    );
    if (value != null && mounted) onSelected(value);
  }

  Future<void> _showActionSheet({
    required String title,
    required List<String> actions,
  }) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(context).height * 0.72,
          ),
          child: ListView(
            shrinkWrap: true,
            children: [
              ListTile(
                title: Text(
                  title,
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
              ...actions.map(
                (item) => ListTile(
                  title: Text(item),
                  onTap: () => Navigator.pop(context, item),
                ),
              ),
            ],
          ),
        ),
      ),
    );
    if (action != null && mounted) _showMessage('$action（本地演示）');
  }

  Future<void> _showHistoryActions(FBoxStore store) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(context).height * 0.72,
          ),
          child: ListView(
            shrinkWrap: true,
            children: [
              const ListTile(
                title: Text(
                  '历史记录',
                  style: TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
              ListTile(
                title: const Text('管理历史记录'),
                onTap: () => Navigator.pop(context, 'manage'),
              ),
              ListTile(
                title: const Text('清空历史记录'),
                textColor: Theme.of(context).colorScheme.error,
                onTap: () => Navigator.pop(context, 'clear'),
              ),
            ],
          ),
        ),
      ),
    );
    if (!mounted) return;
    if (action == 'manage') {
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => const LibraryPage(kind: LibraryKind.history),
        ),
      );
    } else if (action == 'clear') {
      store.clearHistory();
      _showMessage('历史记录已清空');
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
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
        style: TextStyle(
          color: Theme.of(context).colorScheme.primary,
          fontSize: 13,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _ColorDot extends StatelessWidget {
  const _ColorDot(this.color, this.selected, this.onTap);

  final Color color;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkResponse(
      key: Key('theme-color-${color.toARGB32().toRadixString(16)}'),
      onTap: onTap,
      radius: 28,
      child: Container(
        width: 42,
        height: 42,
        decoration: BoxDecoration(
          color: color,
          shape: BoxShape.circle,
          border: selected ? Border.all(color: Colors.white, width: 4) : null,
          boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 4)],
        ),
        child: selected ? const Icon(Icons.check, color: Colors.white) : null,
      ),
    );
  }
}
