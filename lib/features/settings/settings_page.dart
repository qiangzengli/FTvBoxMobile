import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/features/library/library_page.dart';
import 'package:fbox/services/background_image_service.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  bool _autoLoad = true;
  bool _rememberProgress = true;
  bool _glass = false;
  bool _backgroundBlur = false;
  bool _privateBrowsing = false;
  bool _longPressSpeed = true;
  bool _backgroundPlay = false;
  double _backgroundAlpha = 1;
  String _homeLayout = '3 列 · 海报模式';
  String _aggregateMode = '多源聚合';
  String _background = '默认背景';
  String _homeSource = '未选择';
  String _liveSource = '未选择';
  String _dns = '腾讯 DoH';
  String _scaleMode = '适应屏幕';
  double _cacheSize = 12.6;
  bool _loadedSettings = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_loadedSettings) return;
    _loadedSettings = true;
    final store = FBoxScope.of(context);
    _autoLoad = store.settings['autoLoad'] != false;
    _rememberProgress = store.rememberProgress;
    _privateBrowsing = store.privateBrowsing;
    _homeLayout = store.settings['homeLayout']?.toString() ?? _homeLayout;
    _aggregateMode =
        store.settings['aggregateMode']?.toString() ?? _aggregateMode;
    _background = store.settings['background']?.toString() ?? _background;
    _backgroundAlpha =
        (store.settings['backgroundAlpha'] as num?)?.toDouble() ??
        _backgroundAlpha;
    _glass = store.settings['glass'] == true;
    _backgroundBlur = store.settings['backgroundBlur'] == true;
    _homeSource = store.currentSource?.name ?? '未选择';
    final preferredLiveUrl = store.settings['liveSourceUrl']?.toString() ?? '';
    for (final source in store.liveSources) {
      if (source.url == preferredLiveUrl) _liveSource = source.name;
    }
    _dns = store.dnsMode;
    _longPressSpeed = store.settings['longPressSpeed'] != false;
    _backgroundPlay = store.settings['backgroundPlay'] == true;
    _scaleMode = store.settings['scaleMode']?.toString() ?? _scaleMode;
  }

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
              onSelected: (value) {
                setState(() => _homeLayout = value);
                store.setSetting('homeLayout', value);
              },
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
              onSelected: (value) {
                setState(() => _aggregateMode = value);
                store.setSetting('aggregateMode', value);
              },
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
              onSelected: (value) => _selectBackground(store, value),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.opacity_rounded),
            title: const Text('背景图片透明度'),
            subtitle: Slider(
              value: _backgroundAlpha,
              onChanged: (value) {
                setState(() => _backgroundAlpha = value);
                store.setSetting('backgroundAlpha', value);
              },
            ),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.blur_on_rounded),
            title: const Text('液态玻璃'),
            value: _glass,
            onChanged: (value) {
              setState(() => _glass = value);
              store.setSetting('glass', value);
            },
          ),
          SwitchListTile(
            secondary: const Icon(Icons.blur_circular_rounded),
            title: const Text('应用背景模糊'),
            value: _backgroundBlur,
            onChanged: (value) {
              setState(() => _backgroundBlur = value);
              store.setSetting('backgroundBlur', value);
            },
          ),
          const Divider(height: 1),
          const _SectionTitle('通用'),
          SwitchListTile(
            secondary: const Icon(Icons.refresh_rounded),
            title: const Text('启动时自动加载上次配置'),
            value: _autoLoad,
            onChanged: (value) {
              setState(() => _autoLoad = value);
              store.setSetting('autoLoad', value);
            },
          ),
          ListTile(
            leading: const Icon(Icons.dns_outlined),
            title: const Text('首页站源'),
            subtitle: Text(_homeSource),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectHomeSource(store),
          ),
          ListTile(
            leading: const Icon(Icons.live_tv_outlined),
            title: const Text('直播源'),
            subtitle: Text(_liveSource),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _selectLiveSource(store),
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
              onSelected: (value) {
                setState(() => _dns = value);
                store.setSetting('dns', value);
                store.setSetting('dohEndpoint', switch (value) {
                  '腾讯 DoH' => 'https://doh.pub/dns-query',
                  '阿里 DoH' => 'https://dns.alidns.com/resolve',
                  _ => '',
                });
              },
            ),
          ),
          ListTile(
            leading: const Icon(Icons.lan_outlined),
            title: const Text('网络代理'),
            subtitle: Text(
              store.proxy.isEmpty
                  ? '未设置 · 用于订阅、接口、解析与下载'
                  : '${store.proxy} · 不接管系统播放器/WebView',
            ),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _editProxy(store),
          ),
          if (Platform.isIOS)
            ListTile(
              leading: const Icon(Icons.settings_ethernet_rounded),
              title: const Text('Android Spider 转发地址'),
              subtitle: Text(
                store.spiderBridgeUrl.isEmpty
                    ? '填写 Android 设备“设置”中的共享地址'
                    : store.spiderBridgeUrl,
              ),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => _editSpiderBridge(store),
            ),
          if (Platform.isAndroid)
            ListTile(
              leading: const Icon(Icons.share_rounded),
              title: const Text('共享 Spider 给 iOS'),
              subtitle: const Text('同一局域网中的 iPhone 可使用本站源'),
              trailing: const Icon(Icons.chevron_right),
              onTap: _showSpiderBridgeAddress,
            ),
          ListTile(
            leading: const Icon(Icons.network_check_rounded),
            title: const Text('DNS 网络诊断'),
            subtitle: const Text('解析 api.github.com 并验证当前 DoH'),
            trailing: const Icon(Icons.play_arrow_rounded),
            onTap: () => _testDns(store),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.visibility_off_outlined),
            title: const Text('无痕浏览'),
            value: _privateBrowsing,
            onChanged: (value) {
              setState(() => _privateBrowsing = value);
              store.setSetting('privateBrowsing', value);
            },
          ),
          const Divider(height: 1),
          const _SectionTitle('播放器'),
          ListTile(
            leading: const Icon(Icons.play_circle_outline_rounded),
            title: const Text('播放器'),
            subtitle: const Text('系统播放器（Android / iOS）'),
            trailing: const Icon(Icons.info_outline_rounded),
            onTap: () => _showMessage('当前跨平台版本仅支持系统播放器'),
          ),
          const ListTile(
            leading: Icon(Icons.memory_rounded),
            title: Text('IJK 解码方式'),
            subtitle: Text('当前跨平台版本不支持，解码由系统播放器管理'),
            enabled: false,
          ),
          const ListTile(
            leading: Icon(Icons.filter_alt_outlined),
            title: Text('广告过滤'),
            subtitle: Text('当前跨平台版本不提供播放器级广告过滤'),
            enabled: false,
          ),
          SwitchListTile(
            secondary: const Icon(Icons.fast_forward_rounded),
            title: const Text('长按倍速'),
            value: _longPressSpeed,
            onChanged: (value) {
              setState(() => _longPressSpeed = value);
              store.setSetting('longPressSpeed', value);
            },
          ),
          SwitchListTile(
            secondary: const Icon(Icons.picture_in_picture_alt_outlined),
            title: const Text('后台播放'),
            value: _backgroundPlay,
            onChanged: (value) {
              setState(() => _backgroundPlay = value);
              store.setSetting('backgroundPlay', value);
            },
          ),
          SwitchListTile(
            secondary: const Icon(Icons.timelapse_rounded),
            title: const Text('记忆播放进度'),
            value: _rememberProgress,
            onChanged: (value) {
              setState(() => _rememberProgress = value);
              store.setSetting('rememberProgress', value);
            },
          ),
          const ListTile(
            leading: Icon(Icons.speed_rounded),
            title: Text('渲染方式'),
            subtitle: Text('由 Android / iOS 系统播放器自动选择'),
            enabled: false,
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
              onSelected: (value) {
                setState(() => _scaleMode = value);
                store.setSetting('scaleMode', value);
              },
            ),
          ),
          const ListTile(
            leading: Icon(Icons.cached_rounded),
            title: Text('IJK 缓存'),
            subtitle: Text('当前跨平台版本不支持'),
            enabled: false,
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
              onSelected: (action) => _handleBackup(store, action),
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
            onTap: () async {
              try {
                await FilePicker.platform.clearTemporaryFiles();
              } on Object {
                // Some platforms do not expose a temporary picker cache.
              }
              if (!mounted) return;
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

  Future<void> _selectHomeSource(FBoxStore store) => _selectOption(
    title: '首页站源',
    options: [
      '未选择',
      ...store.sources.where(store.supportsSource).map((source) => source.name),
    ],
    selected: _homeSource,
    onSelected: (value) async {
      try {
        final key = value == '未选择'
            ? ''
            : store.sources
                  .firstWhere((source) => source.name == value)
                  .identity;
        await store.selectSource(key);
        store.setSetting('homeSourceKey', key);
        if (mounted) setState(() => _homeSource = value);
      } catch (error) {
        if (mounted) _showMessage('切换首页站源失败：$error');
      }
    },
  );

  Future<void> _selectBackground(FBoxStore store, String value) async {
    if (value == '相册选择') {
      final result = await FilePicker.platform.pickFiles(type: FileType.image);
      final path = result?.files.single.path;
      if (path == null || path.isEmpty) return;
      try {
        final imported = await const BackgroundImageService().importImage(path);
        store.setSetting('backgroundImagePath', imported);
      } catch (error) {
        if (mounted) _showMessage('保存背景图片失败：$error');
        return;
      }
    }
    store.setSetting('background', value);
    if (mounted) setState(() => _background = value);
  }

  Future<void> _selectLiveSource(FBoxStore store) => _selectOption(
    title: '直播源',
    options: ['未选择', ...store.liveSources.map((source) => source.name)],
    selected: _liveSource,
    onSelected: (value) {
      final url = value == '未选择'
          ? ''
          : store.liveSources.firstWhere((source) => source.name == value).url;
      store.setSetting('liveSourceUrl', url);
      setState(() => _liveSource = value);
    },
  );

  Future<void> _selectOption({
    required String title,
    required List<String> options,
    required String selected,
    required FutureOr<void> Function(String) onSelected,
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
    if (value != null && mounted) await onSelected(value);
  }

  Future<void> _showActionSheet({
    required String title,
    required List<String> actions,
    ValueChanged<String>? onSelected,
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
    if (action != null && mounted) onSelected?.call(action);
  }

  Future<void> _editProxy(FBoxStore store) async {
    var proxy = store.proxy;
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('网络代理'),
        content: TextFormField(
          initialValue: proxy,
          onChanged: (value) => proxy = value,
          autofocus: true,
          decoration: const InputDecoration(
            hintText: 'http://127.0.0.1:7890',
            helperText: '留空表示直连，支持带用户名密码的 HTTP 代理',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, proxy.trim()),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    if (value != null) store.setSetting('proxy', value);
  }

  Future<void> _editSpiderBridge(FBoxStore store) async {
    var bridgeUrl = store.spiderBridgeUrl;
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Android Spider 转发地址'),
        content: TextFormField(
          initialValue: bridgeUrl,
          onChanged: (value) => bridgeUrl = value,
          autofocus: true,
          keyboardType: TextInputType.url,
          decoration: const InputDecoration(
            hintText: 'http://192.168.1.2:8793/共享密钥',
            helperText: 'Android 和 iPhone 需连接同一局域网',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, bridgeUrl.trim()),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    if (value == null) return;
    if (value.isNotEmpty) {
      final uri = Uri.tryParse(value);
      if (uri == null ||
          !{'http', 'https'}.contains(uri.scheme) ||
          uri.host.isEmpty ||
          uri.pathSegments.where((part) => part.isNotEmpty).isEmpty) {
        _showMessage('转发地址应包含 http(s) 地址和共享密钥');
        return;
      }
    }
    store.setSetting('spiderBridgeUrl', value);
    if (value.isEmpty) return;
    try {
      final base = Uri.parse(value);
      final endpoint = base.replace(
        pathSegments: [
          ...base.pathSegments.where((part) => part.isNotEmpty),
          'health',
        ],
      );
      final result = jsonDecode(
        await store.network.getDirectText(
          endpoint,
          timeout: const Duration(seconds: 8),
        ),
      );
      if (result is! Map || result['ok'] != true) {
        throw const FormatException('服务返回无效');
      }
      if (mounted) _showMessage('已连接 Android Spider 服务');
    } catch (error) {
      if (mounted) _showMessage('地址已保存，但当前无法连接：$error');
    }
  }

  Future<void> _showSpiderBridgeAddress() async {
    try {
      final address = await const MethodChannel(
        'com.fbox.app/spider_bridge',
      ).invokeMethod<String>('address');
      if (!mounted) return;
      if (address == null || address.isEmpty) {
        _showMessage('未找到局域网地址，请连接 Wi-Fi 后重试');
        return;
      }
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('共享 Spider 给 iOS'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('保持此 Android 应用运行，并将地址填入 iPhone 的同名设置：'),
              const SizedBox(height: 12),
              SelectableText(address),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () async {
                await Clipboard.setData(ClipboardData(text: address));
                if (context.mounted) Navigator.pop(context);
              },
              child: const Text('复制地址'),
            ),
          ],
        ),
      );
    } catch (error) {
      if (mounted) _showMessage('读取共享地址失败：$error');
    }
  }

  Future<void> _testDns(FBoxStore store) async {
    _showMessage('正在执行 DNS 解析…');
    try {
      final endpoint = store.settings['dohEndpoint']?.toString() ?? '';
      final addresses = await store.network.resolveHost(
        'api.github.com',
        dohEndpoint: endpoint,
      );
      if (mounted) {
        _showMessage(
          '解析成功：${addresses.map((address) => address.address).join(', ')}',
        );
      }
    } catch (error) {
      if (mounted) _showMessage('DNS 诊断失败：$error');
    }
  }

  Future<void> _handleBackup(FBoxStore store, String action) async {
    try {
      if (action == '创建本地备份') {
        final bytes = Uint8List.fromList(utf8.encode(store.exportBackupText()));
        final name =
            'fbox-backup-${DateTime.now().toIso8601String().substring(0, 10)}.json';
        final saved = await FilePicker.platform.saveFile(
          dialogTitle: '保存 FBox 备份',
          fileName: name,
          type: FileType.custom,
          allowedExtensions: const ['json'],
          bytes: bytes,
        );
        if (mounted && saved != null) _showMessage('备份已保存');
      } else {
        final result = await FilePicker.platform.pickFiles(
          type: FileType.custom,
          allowedExtensions: const ['json'],
          withData: true,
        );
        if (result == null) return;
        final file = result.files.single;
        final bytes = file.bytes;
        if (bytes == null) throw const FormatException('无法读取备份文件');
        await store.importBackupText(utf8.decode(bytes));
        if (mounted) _showMessage('备份已恢复');
      }
    } catch (error) {
      if (mounted) _showMessage('备份操作失败：$error');
    }
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
