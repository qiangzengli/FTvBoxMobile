import 'dart:async';
import 'dart:io';

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

class LivePage extends StatefulWidget {
  const LivePage({super.key});

  @override
  State<LivePage> createState() => _LivePageState();
}

class _LivePageState extends State<LivePage> {
  int _group = 0;
  int _channel = 0;
  bool _playing = true;
  bool _controlsVisible = true;
  bool _channelsVisible = true;
  String _source = '演示直播源';
  LiveSource? _activeSource;
  String _programLabel = '20:00  黄金档特别节目';
  VideoPlayerController? _controller;
  String _error = '';
  bool _sourceInitialized = false;
  int _sourceLoadGeneration = 0;
  int _playGeneration = 0;
  int _epgLoadGeneration = 0;
  final Map<String, int> _selectedLines = {};

  static const _demoGroups = ['央视频道', '卫视频道', '数字频道'];
  static const _demoChannels = [
    ['CCTV-1 综合', 'CCTV-2 财经', 'CCTV-5 体育', 'CCTV-6 电影'],
    ['湖南卫视', '东方卫视', '浙江卫视', '江苏卫视'],
    ['风云足球', '世界地理', '第一剧场', '怀旧剧场'],
  ];
  List<LiveChannelGroup>? _loadedGroups;

  List<String> get groups =>
      _loadedGroups?.map((group) => group.name).toList() ?? _demoGroups;

  List<List<String>> get channels =>
      _loadedGroups
          ?.map(
            (group) => group.channels.map((channel) => channel.name).toList(),
          )
          .toList() ??
      _demoChannels;

  String get _currentChannel => channels[_group][_channel];

  LiveChannel? get _currentLiveChannel {
    final loaded = _loadedGroups;
    if (loaded == null || loaded.isEmpty) return null;
    if (_group >= loaded.length || _channel >= loaded[_group].channels.length) {
      return null;
    }
    return loaded[_group].channels[_channel];
  }

  String get _currentChannelKey {
    final channel = _currentLiveChannel;
    return channel == null ? '' : '$_source|${channel.group}|${channel.name}';
  }

  int get _currentLineIndex {
    final channel = _currentLiveChannel;
    if (channel == null || channel.urls.isEmpty) return 0;
    return (_selectedLines[_currentChannelKey] ?? 0).clamp(
      0,
      channel.urls.length - 1,
    );
  }

  String? get _currentLiveUrl {
    final channel = _currentLiveChannel;
    if (channel == null || channel.urls.isEmpty) return null;
    return channel.urls[_currentLineIndex];
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final store = FBoxScope.maybeOf(context);
    if (_sourceInitialized || store == null) return;
    _sourceInitialized = true;
    final preferredUrl = store.settings['liveSourceUrl']?.toString() ?? '';
    if (preferredUrl.isEmpty || preferredUrl == demoLiveSourceSetting) return;
    if (store.liveSources.isEmpty) return;
    final initialSource = store.liveSources.firstWhere(
      (source) => source.url == preferredUrl,
      orElse: () => store.liveSources.first,
    );
    unawaited(_loadSource(initialSource));
  }

  @override
  void dispose() {
    _sourceLoadGeneration++;
    _playGeneration++;
    _epgLoadGeneration++;
    unawaited(_controller?.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) => GestureDetector(
            key: const Key('live-surface'),
            behavior: HitTestBehavior.opaque,
            onTap: () => setState(() => _controlsVisible = !_controlsVisible),
            child: Stack(
              fit: StackFit.expand,
              children: [
                _LiveCanvas(controller: _controller, error: _error),
                if (_controlsVisible) ...[
                  _LiveTopBar(
                    channel: _currentChannel,
                    source: _source,
                    onBack: () => Navigator.pop(context),
                    onSource: _selectSource,
                  ),
                  Center(
                    child: IconButton(
                      key: const Key('live-play-toggle'),
                      iconSize: 62,
                      color: Colors.white,
                      onPressed: _togglePlay,
                      icon: Icon(
                        _playing
                            ? Icons.pause_circle_filled_rounded
                            : Icons.play_circle_fill_rounded,
                      ),
                    ),
                  ),
                  if (_channelsVisible)
                    Positioned(
                      left: 12,
                      right: 12,
                      top: constraints.maxHeight < 420 ? 56 : 86,
                      bottom: constraints.maxHeight < 420 ? 84 : 126,
                      child: _ChannelOverlay(
                        groups: groups,
                        channels: channels[_group],
                        selectedGroup: _group,
                        selectedChannel: _channel,
                        onGroup: (index) {
                          _epgLoadGeneration++;
                          setState(() {
                            _group = index;
                            _channel = 0;
                            _programLabel = _epgPrompt;
                          });
                          unawaited(_playCurrent());
                        },
                        onChannel: (index) {
                          _epgLoadGeneration++;
                          setState(() {
                            _channel = index;
                            _programLabel = _epgPrompt;
                          });
                          unawaited(_playCurrent());
                        },
                      ),
                    ),
                  Positioned(
                    left: 12,
                    right: 12,
                    bottom: 10,
                    child: _LiveBottomBar(
                      channel: _currentChannel,
                      program: _programLabel,
                      channelsVisible: _channelsVisible,
                      onChannels: () =>
                          setState(() => _channelsVisible = !_channelsVisible),
                      onEpg: _showEpg,
                      onCast: _cast,
                      onSettings: _showLiveSettings,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<String?> _showOptions({
    required String title,
    required List<String> options,
  }) {
    return showModalBottomSheet<String>(
      context: context,
      backgroundColor: const Color(0xFF1D1D1D),
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
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              ...options.map(
                (option) => ListTile(
                  title: Text(
                    option,
                    style: const TextStyle(color: Colors.white),
                  ),
                  onTap: () => Navigator.pop(context, option),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _selectSource() async {
    final store = FBoxScope.maybeOf(context);
    if (store == null || store.liveSources.isEmpty) {
      await _useDemoSource(store);
      return;
    }
    final selected = await showModalBottomSheet<Object>(
      context: context,
      backgroundColor: const Color(0xFF1D1D1D),
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            const ListTile(
              title: Text('直播源', style: TextStyle(color: Colors.white)),
            ),
            ListTile(
              title: const Text('演示直播源', style: TextStyle(color: Colors.white)),
              onTap: () => Navigator.pop(context, demoLiveSourceSetting),
            ),
            for (final source in store.liveSources)
              ListTile(
                title: Text(
                  source.name,
                  style: const TextStyle(color: Colors.white),
                ),
                subtitle: Text(
                  source.url,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: Colors.white54),
                ),
                onTap: () => Navigator.pop(context, source),
              ),
          ],
        ),
      ),
    );
    if (selected == demoLiveSourceSetting) {
      await _useDemoSource(store);
    } else if (selected is LiveSource) {
      await _loadSource(selected);
    }
  }

  Future<void> _useDemoSource(FBoxStore? store) async {
    _sourceLoadGeneration++;
    _playGeneration++;
    _epgLoadGeneration++;
    final previous = _controller;
    if (mounted) {
      setState(() {
        _source = '演示直播源';
        _activeSource = null;
        _programLabel = '20:00  黄金档特别节目';
        _loadedGroups = null;
        _group = 0;
        _channel = 0;
        _controller = null;
        _playing = true;
        _error = '';
      });
    }
    await previous?.dispose();
    store?.setSetting('liveSourceUrl', demoLiveSourceSetting);
  }

  Future<void> _loadSource(LiveSource source) async {
    final store = FBoxScope.maybeOf(context);
    if (store == null) return;
    final generation = ++_sourceLoadGeneration;
    _epgLoadGeneration++;
    setState(() => _error = '正在加载 ${source.name}…');
    try {
      final groups = await store.loadLiveSource(source);
      if (!mounted || generation != _sourceLoadGeneration) return;
      if (groups.isEmpty) throw const FormatException('直播列表为空');
      setState(() {
        _source = source.name;
        _activeSource = source;
        _programLabel = source.epg.isEmpty ? '该直播源未配置节目单' : '点击节目单查看';
        _loadedGroups = groups;
        _group = 0;
        _channel = 0;
        _error = '';
      });
      store.setSetting('liveSourceUrl', source.url);
      await _playCurrent();
    } catch (error) {
      if (mounted && generation == _sourceLoadGeneration) {
        setState(() => _error = '直播源加载失败：$error');
      }
    }
  }

  String get _epgPrompt {
    final source = _activeSource;
    return source == null
        ? '20:00  黄金档特别节目'
        : source.epg.isEmpty
        ? '该直播源未配置节目单'
        : '点击节目单查看';
  }

  Future<void> _showEpg() async {
    final generation = ++_epgLoadGeneration;
    final store = FBoxScope.maybeOf(context);
    final source = _activeSource;
    final channel = _currentLiveChannel;
    if (store == null || source == null || channel == null) {
      await _showOptions(
        title: '节目单',
        options: const ['20:00  黄金档特别节目', '21:30  晚间新闻', '22:10  精品剧场'],
      );
      return;
    }
    if (source.epg.isEmpty) {
      await _showOptions(title: '节目单', options: const ['该直播源未配置节目单']);
      return;
    }
    try {
      final programs = await store.loadLiveEpg(source, channel);
      if (!mounted || generation != _epgLoadGeneration) return;
      final options = programs.isEmpty
          ? const ['暂无节目单']
          : programs.map((program) => program.label).toList(growable: false);
      if (programs.isNotEmpty) {
        setState(() => _programLabel = programs.first.label);
      }
      await _showOptions(title: '节目单', options: options);
    } catch (error) {
      if (!mounted || generation != _epgLoadGeneration) return;
      await _showOptions(title: '节目单', options: ['节目单加载失败：$error']);
    }
  }

  Future<void> _showLiveSettings() async {
    final channel = _currentLiveChannel;
    if (channel != null && channel.urls.length > 1) {
      await _selectLiveLine();
      return;
    }
    if (mounted) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(content: Text('当前频道只有一条线路，画面与解码由系统播放器管理')),
        );
    }
  }

  Future<void> _selectLiveLine() async {
    final channel = _currentLiveChannel;
    if (channel == null || channel.urls.length < 2) return;
    final options = [
      for (var index = 0; index < channel.urls.length; index++)
        '线路 ${index + 1} · ${_lineLabel(channel.urls[index])}',
    ];
    final selected = await _showOptions(title: '线路选择', options: options);
    if (selected == null) return;
    final index = options.indexOf(selected);
    if (index < 0 || index == _currentLineIndex) return;
    _selectedLines[_currentChannelKey] = index;
    await _playCurrent(preferredLine: index);
  }

  static String _lineLabel(String value) {
    final uri = Uri.tryParse(value);
    if (uri == null) return value;
    if (uri.host.isNotEmpty) return uri.host;
    return uri.pathSegments.isEmpty ? value : uri.pathSegments.last;
  }

  Future<void> _playCurrent({int? preferredLine}) async {
    final generation = ++_playGeneration;
    final channel = _currentLiveChannel;
    if (channel == null || channel.urls.isEmpty) return;
    final allowBackgroundPlayback =
        FBoxScope.maybeOf(context)?.settings['backgroundPlay'] == true;
    final previous = _controller;
    setState(() {
      _controller = null;
      _error = '';
      _playing = false;
    });
    await previous?.dispose();
    if (!mounted || generation != _playGeneration) return;
    Object? lastError;
    final channelKey = _currentChannelKey;
    final firstLine = preferredLine ?? _selectedLines[channelKey] ?? 0;
    for (final candidate in channel.playbackCandidates(firstLine)) {
      final controller = VideoPlayerController.networkUrl(
        Uri.parse(candidate.url),
        httpHeaders: channel.headers,
        videoPlayerOptions: VideoPlayerOptions(
          allowBackgroundPlayback: allowBackgroundPlayback,
        ),
        formatHint: candidate.url.toLowerCase().contains('.m3u8')
            ? VideoFormat.hls
            : null,
      );
      if (!mounted || generation != _playGeneration) {
        await controller.dispose();
        return;
      }
      try {
        await controller.initialize();
        if (!mounted || generation != _playGeneration) {
          await controller.dispose();
          return;
        }
        await controller.play();
        if (!mounted || generation != _playGeneration) {
          await controller.dispose();
          return;
        }
        _selectedLines[channelKey] = candidate.index;
        setState(() {
          _controller = controller;
          _playing = true;
          _error = '';
        });
        return;
      } catch (error) {
        lastError = error;
        await controller.dispose();
      }
    }
    if (mounted && generation == _playGeneration) {
      setState(() => _error = '所有直播线路播放失败：$lastError');
    }
  }

  Future<void> _togglePlay() async {
    final controller = _controller;
    if (controller == null) {
      setState(() => _playing = !_playing);
      return;
    }
    if (controller.value.isPlaying) {
      await controller.pause();
    } else {
      await controller.play();
    }
    if (mounted) setState(() => _playing = controller.value.isPlaying);
  }

  Future<void> _cast() async {
    final store = FBoxScope.maybeOf(context);
    final channel = _currentLiveChannel;
    final mediaUrl = _currentLiveUrl;
    if (store == null || channel == null || mediaUrl == null) {
      await _showOptions(title: '投屏设备', options: const ['当前演示源不可投屏']);
      return;
    }
    try {
      if (Platform.isIOS) {
        await store.cast.showAirPlayPicker();
        return;
      }
      final devices = await store.cast.discoverDlna();
      if (!mounted) return;
      if (devices.isEmpty) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('未发现 DLNA 设备')));
        return;
      }
      final names = devices.map((device) => device.name).toList();
      final name = await _showOptions(title: '投屏设备', options: names);
      if (name == null) return;
      final device = devices.firstWhere((value) => value.name == name);
      await store.cast.playDlna(
        device: device,
        mediaUrl: mediaUrl,
        title: channel.name,
      );
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('投屏失败：$error')));
      }
    }
  }
}

class _LiveCanvas extends StatelessWidget {
  const _LiveCanvas({required this.controller, required this.error});

  final VideoPlayerController? controller;
  final String error;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: RadialGradient(
          colors: [Color(0xFF24324F), Colors.black],
          radius: 1.15,
        ),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (controller?.value.isInitialized == true)
            FittedBox(
              fit: BoxFit.contain,
              child: SizedBox(
                width: controller!.value.size.width,
                height: controller!.value.size.height,
                child: VideoPlayer(controller!),
              ),
            )
          else
            const Center(
              child: Icon(
                Icons.live_tv_rounded,
                color: Colors.white24,
                size: 94,
              ),
            ),
          if (error.isNotEmpty)
            Center(
              child: Container(
                padding: const EdgeInsets.all(14),
                color: Colors.black54,
                child: Text(
                  error,
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Colors.white),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _LiveTopBar extends StatelessWidget {
  const _LiveTopBar({
    required this.channel,
    required this.source,
    required this.onBack,
    required this.onSource,
  });

  final String channel;
  final String source;
  final VoidCallback onBack;
  final VoidCallback onSource;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: 0,
      right: 0,
      top: 0,
      child: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Colors.black87, Colors.transparent],
          ),
        ),
        child: Row(
          children: [
            IconButton(
              onPressed: onBack,
              color: Colors.white,
              icon: const Icon(Icons.arrow_back),
            ),
            Expanded(
              child: Text(
                channel,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            TextButton.icon(
              onPressed: onSource,
              style: TextButton.styleFrom(foregroundColor: Colors.white),
              icon: const Icon(Icons.alt_route, size: 18),
              label: Text(source),
            ),
          ],
        ),
      ),
    );
  }
}

class _ChannelOverlay extends StatelessWidget {
  const _ChannelOverlay({
    required this.groups,
    required this.channels,
    required this.selectedGroup,
    required this.selectedChannel,
    required this.onGroup,
    required this.onChannel,
  });

  final List<String> groups;
  final List<String> channels;
  final int selectedGroup;
  final int selectedChannel;
  final ValueChanged<int> onGroup;
  final ValueChanged<int> onChannel;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.78),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white12),
      ),
      child: Row(
        children: [
          Expanded(
            flex: 4,
            child: ListView.builder(
              padding: const EdgeInsets.all(6),
              itemCount: groups.length,
              itemBuilder: (context, index) => _LiveChoice(
                label: groups[index],
                selected: selectedGroup == index,
                onTap: () => onGroup(index),
              ),
            ),
          ),
          const VerticalDivider(width: 1, color: Colors.white24),
          Expanded(
            flex: 6,
            child: ListView.builder(
              padding: const EdgeInsets.all(6),
              itemCount: channels.length,
              itemBuilder: (context, index) => _LiveChoice(
                label: channels[index],
                selected: selectedChannel == index,
                onTap: () => onChannel(index),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _LiveBottomBar extends StatelessWidget {
  const _LiveBottomBar({
    required this.channel,
    required this.program,
    required this.channelsVisible,
    required this.onChannels,
    required this.onEpg,
    required this.onCast,
    required this.onSettings,
  });

  final String channel;
  final String program;
  final bool channelsVisible;
  final VoidCallback onChannels;
  final VoidCallback onEpg;
  final VoidCallback onCast;
  final VoidCallback onSettings;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.72),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  channel,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                Text(
                  program,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: Colors.white60, fontSize: 11),
                ),
              ],
            ),
          ),
          _LiveTool(
            tooltip: '频道',
            icon: channelsVisible ? Icons.list_alt : Icons.list,
            onPressed: onChannels,
          ),
          _LiveTool(
            tooltip: '节目单',
            icon: Icons.calendar_month_outlined,
            onPressed: onEpg,
          ),
          _LiveTool(tooltip: '投屏', icon: Icons.cast, onPressed: onCast),
          _LiveTool(
            tooltip: '设置',
            icon: Icons.settings_outlined,
            onPressed: onSettings,
          ),
        ],
      ),
    );
  }
}

class _LiveTool extends StatelessWidget {
  const _LiveTool({
    required this.tooltip,
    required this.icon,
    required this.onPressed,
  });

  final String tooltip;
  final IconData icon;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: tooltip,
      visualDensity: VisualDensity.compact,
      color: Colors.white,
      onPressed: onPressed,
      icon: Icon(icon, size: 20),
    );
  }
}

class _LiveChoice extends StatelessWidget {
  const _LiveChoice({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 3),
      child: Material(
        color: selected
            ? Theme.of(context).colorScheme.primary.withValues(alpha: 0.28)
            : Colors.transparent,
        borderRadius: BorderRadius.circular(7),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(7),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 9),
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: selected
                    ? Theme.of(context).colorScheme.primary
                    : Colors.white,
                fontSize: 13,
                fontWeight: selected ? FontWeight.w700 : FontWeight.normal,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
