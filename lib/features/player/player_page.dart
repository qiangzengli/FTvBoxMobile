import 'dart:async';
import 'dart:io';

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';

class PlayerPage extends StatefulWidget {
  const PlayerPage({
    required this.item,
    this.episode = 1,
    this.episodeName,
    this.source = '高清线路',
    this.playback,
    this.playLines = const [],
    this.selectedLineIndex = 0,
    this.autoOpenCastPicker = false,
    super.key,
  });

  final MediaItem item;
  final int episode;
  final String? episodeName;
  final String source;
  final PlaybackRequest? playback;
  final List<MediaPlayLine> playLines;
  final int selectedLineIndex;
  final bool autoOpenCastPicker;

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> {
  bool _playing = false;
  bool _controlsVisible = true;
  bool _locked = false;
  double _progress = 0.18;
  double _speed = 1;
  late String _source = widget.source;
  late int _episode = widget.episode;
  late String _currentEpisodeName = widget.episodeName ?? '';
  late int _selectedLineIndex = widget.selectedLineIndex;
  late PlaybackRequest? _playback = widget.playback;
  String _scaleMode = '适应屏幕';
  String _sleepTimer = '关闭';
  Timer? _sleepTimerHandle;
  bool _backgroundPlay = false;
  bool _longPressSpeedEnabled = true;
  double _speedBeforeLongPress = 1;
  bool _fullscreen = false;
  bool _initializationStarted = false;
  int _playerGeneration = 0;
  int _lineSwitchGeneration = 0;
  VideoPlayerController? _controller;
  String _playerError = '';
  bool _playerLoading = false;
  FBoxStore? _store;

  List<MediaPlayLine> get _playLines =>
      widget.playLines.isNotEmpty ? widget.playLines : widget.item.playLines;

  bool get _hasMedia => _playback?.url.isNotEmpty == true;

  String get _episodeLabel =>
      _currentEpisodeName.isNotEmpty ? _currentEpisodeName : '第 $_episode 集';

  @override
  void initState() {
    super.initState();
    if (_hasMedia) {
      _progress = 0;
      _playerLoading = true;
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _store = FBoxScope.maybeOf(context);
    final settings = _store?.settings;
    _scaleMode = settings?['scaleMode']?.toString() ?? _scaleMode;
    _backgroundPlay = settings?['backgroundPlay'] == true;
    _longPressSpeedEnabled = settings?['longPressSpeed'] != false;
    if (_hasMedia && !_initializationStarted) {
      _initializationStarted = true;
      unawaited(_initializePlayer());
    }
  }

  @override
  void dispose() {
    _playerGeneration++;
    _lineSwitchGeneration++;
    _sleepTimerHandle?.cancel();
    final controller = _controller;
    if (controller != null && controller.value.isInitialized) {
      _store?.savePlaybackProgress(
        widget.item,
        _episodeLabel,
        controller.value.position.inMilliseconds / 1000,
      );
    }
    controller?.removeListener(_onVideoChanged);
    unawaited(controller?.dispose());
    if (_fullscreen) unawaited(_restoreSystemUi());
    super.dispose();
  }

  Future<void> _initializePlayer() async {
    final playback = _playback;
    if (playback == null || playback.url.isEmpty) return;
    final generation = ++_playerGeneration;
    VideoPlayerController? controller;
    try {
      final uri = Uri.parse(playback.url);
      controller = uri.scheme == 'file'
          ? VideoPlayerController.file(
              File.fromUri(uri),
              videoPlayerOptions: VideoPlayerOptions(
                allowBackgroundPlayback: _backgroundPlay,
              ),
            )
          : VideoPlayerController.networkUrl(
              uri,
              httpHeaders: playback.headers,
              videoPlayerOptions: VideoPlayerOptions(
                allowBackgroundPlayback: _backgroundPlay,
              ),
              formatHint: uri.path.toLowerCase().contains('.m3u8')
                  ? VideoFormat.hls
                  : null,
            );
      final previous = _controller;
      previous?.removeListener(_onVideoChanged);
      _controller = controller;
      controller.addListener(_onVideoChanged);
      await previous?.dispose();
      await controller.initialize();
      if (!mounted || generation != _playerGeneration) {
        await controller.dispose();
        return;
      }
      final resume = _store?.playbackProgress(widget.item, _episodeLabel) ?? 0;
      if (resume > 0 && resume < controller.value.duration.inSeconds - 10) {
        await controller.seekTo(
          Duration(milliseconds: (resume * 1000).round()),
        );
      }
      if (!mounted || generation != _playerGeneration) {
        await controller.dispose();
        return;
      }
      await controller.play();
      if (mounted) {
        setState(() {
          _playing = true;
          _playerLoading = false;
        });
      }
      if (widget.autoOpenCastPicker && Platform.isIOS) {
        try {
          await _store?.cast.showAirPlayPicker();
        } catch (error) {
          if (mounted) _showMessage('AirPlay 选择器打开失败：$error');
        }
      }
    } catch (error) {
      if (controller != null && !identical(_controller, controller)) {
        await controller.dispose();
      }
      if (mounted && generation == _playerGeneration) {
        setState(() {
          _playerError = '$error';
          _playerLoading = false;
        });
      }
    }
  }

  void _onVideoChanged() {
    final value = _controller?.value;
    if (!mounted || value == null) return;
    final total = value.duration.inMilliseconds;
    final reachedEnd =
        _sleepTimer == '播完本集' &&
        total > 0 &&
        value.position.inMilliseconds >= total - 500;
    setState(() {
      _playing = value.isPlaying;
      _playerLoading = value.isBuffering;
      _progress = total <= 0
          ? 0
          : (value.position.inMilliseconds / total).clamp(0, 1);
      if (value.hasError) _playerError = value.errorDescription ?? '播放失败';
      if (reachedEnd) _sleepTimer = '关闭';
    });
    if (reachedEnd) {
      unawaited(value.isPlaying ? _controller?.pause() : null);
      _showMessage('已播完本集，定时关闭已执行');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        top: !_fullscreen,
        bottom: !_fullscreen,
        child: GestureDetector(
          key: const Key('player-surface'),
          behavior: HitTestBehavior.opaque,
          onTap: () {
            if (!_locked) {
              setState(() => _controlsVisible = !_controlsVisible);
            }
          },
          onLongPressStart: (_) => _startLongPressSpeed(),
          onLongPressEnd: (_) => _stopLongPressSpeed(),
          child: Stack(
            fit: StackFit.expand,
            children: [
              _VideoCanvas(
                item: widget.item,
                controller: _controller,
                loading: _playerLoading,
                error: _playerError,
                fit: switch (_scaleMode) {
                  '铺满屏幕' => BoxFit.cover,
                  '原始比例' => BoxFit.none,
                  _ => BoxFit.contain,
                },
                forcedAspectRatio: _scaleMode == '16:9' ? 16 / 9 : null,
              ),
              if (_controlsVisible && !_locked) ...[
                _TopBar(
                  title: '${widget.item.title} · $_episodeLabel',
                  onBack: () => Navigator.pop(context),
                  onMore: _showSettingsPanel,
                ),
                Center(
                  child: IconButton(
                    key: const Key('player-play-toggle'),
                    onPressed: _togglePlay,
                    iconSize: 68,
                    color: Colors.white,
                    icon: Icon(
                      _playing
                          ? Icons.pause_circle_filled_rounded
                          : Icons.play_circle_fill_rounded,
                    ),
                  ),
                ),
                Positioned(
                  left: 14,
                  right: 14,
                  bottom: 10,
                  child: _BottomControls(
                    progress: _progress,
                    positionLabel: _formatDuration(
                      _controller?.value.position ??
                          const Duration(minutes: 8, seconds: 24),
                    ),
                    durationLabel: _formatDuration(
                      _controller?.value.duration ??
                          const Duration(minutes: 45, seconds: 16),
                    ),
                    speed: _speed,
                    source: _source,
                    subtitle: false,
                    danmaku: false,
                    onProgressChanged: _seek,
                    onSource: _showSourcePicker,
                    onSpeed: _showSpeedPicker,
                    onSubtitle: () => _showMessage('当前媒体未提供字幕轨道'),
                    onDanmaku: () => _showMessage('当前配置未提供弹幕服务'),
                    fullscreen: _fullscreen,
                    onFullscreen: _toggleFullscreen,
                    onCast: _showCastPicker,
                    onSettings: _showSettingsPanel,
                  ),
                ),
              ],
              Positioned(
                left: 8,
                top: MediaQuery.sizeOf(context).height * 0.42,
                child: IconButton(
                  key: const Key('player-lock'),
                  tooltip: _locked ? '解锁' : '锁定',
                  onPressed: () => setState(() {
                    _locked = !_locked;
                    _controlsVisible = true;
                  }),
                  color: Colors.white,
                  style: IconButton.styleFrom(backgroundColor: Colors.black45),
                  icon: Icon(_locked ? Icons.lock_rounded : Icons.lock_open),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _showSourcePicker() async {
    final lines = _playLines;
    if (lines.length < 2) {
      _showMessage('当前影片没有其他播放线路');
      return;
    }
    final value = await _showOptions(
      title: '播放线路',
      options: lines.map((line) => line.name).toList(),
    );
    if (value == null || !mounted) return;
    final index = lines.indexWhere((line) => line.name == value);
    if (index < 0 || index == _selectedLineIndex) return;
    await _switchPlayLine(index);
  }

  Future<void> _switchPlayLine(int index) async {
    final switchGeneration = ++_lineSwitchGeneration;
    final line = _playLines[index];
    if (line.episodes.isEmpty) {
      _showMessage('该线路没有可播放剧集');
      return;
    }
    var episodeIndex = line.episodes.indexWhere(
      (episode) => episode.name == _episodeLabel,
    );
    if (episodeIndex < 0) {
      episodeIndex = (_episode - 1).clamp(0, line.episodes.length - 1);
    }
    final episode = line.episodes[episodeIndex];
    if (episode.url.isEmpty) {
      _showMessage('该线路当前剧集没有播放地址');
      return;
    }
    final store = _store;
    try {
      var playback = widget.item.sourceKey.isEmpty
          ? PlaybackRequest(url: episode.url, headers: episode.headers)
          : await store!.resolvePlayback(
              item: widget.item,
              episode: episode,
              line: line.name,
            );
      if (!mounted || switchGeneration != _lineSwitchGeneration) return;
      if (playback.parseRequired) {
        final parser = store?.parsers
            .where((value) => value.type == 1)
            .firstOrNull;
        if (parser == null) {
          throw const FormatException('该线路需要网页解析，请返回详情页选择解析线路');
        }
        playback = await store!.resolvePlayback(
          item: widget.item,
          episode: episode,
          line: line.name,
          parser: parser,
        );
        if (!mounted || switchGeneration != _lineSwitchGeneration) return;
      }
      if (playback.parseRequired) {
        throw const FormatException('解析线路未返回可直接播放的地址');
      }
      if (!mounted || switchGeneration != _lineSwitchGeneration) return;
      final previous = _controller;
      if (previous?.value.isInitialized == true) {
        store?.savePlaybackProgress(
          widget.item,
          _episodeLabel,
          previous!.value.position.inMilliseconds / 1000,
        );
      }
      setState(() {
        _selectedLineIndex = index;
        _source = line.name;
        _episode = episodeIndex + 1;
        _currentEpisodeName = episode.name;
        _playback = playback;
        _playerError = '';
        _playerLoading = true;
        _progress = 0;
      });
      await _initializePlayer();
    } catch (error) {
      if (mounted && switchGeneration == _lineSwitchGeneration) {
        _showMessage('切换线路失败：$error');
      }
    }
  }

  Future<void> _showSpeedPicker() async {
    final value = await _showOptions(
      title: '播放速度',
      options: const ['0.75×', '1.0×', '1.25×', '1.5×', '2.0×'],
    );
    if (value == null || !mounted) return;
    final speed = double.parse(value.replaceAll('×', ''));
    await _controller?.setPlaybackSpeed(speed);
    if (mounted) setState(() => _speed = speed);
  }

  Future<void> _showCastPicker() async {
    final playback = _playback;
    final store = _store;
    if (playback == null || store == null) {
      _showMessage('当前演示内容没有可投屏地址');
      return;
    }
    try {
      if (Platform.isIOS) {
        await store.cast.showAirPlayPicker();
        return;
      }
      _showMessage('正在搜索局域网投屏设备…');
      final devices = await store.cast.discoverDlna();
      if (!mounted) return;
      if (devices.isEmpty) {
        _showMessage('未发现 DLNA 设备');
        return;
      }
      final selected = await showModalBottomSheet(
        context: context,
        backgroundColor: const Color(0xFF1D1D1D),
        showDragHandle: true,
        builder: (context) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              const ListTile(
                title: Text('投屏设备', style: TextStyle(color: Colors.white)),
              ),
              for (final device in devices)
                ListTile(
                  leading: const Icon(Icons.tv, color: Colors.white),
                  title: Text(
                    device.name,
                    style: const TextStyle(color: Colors.white),
                  ),
                  onTap: () => Navigator.pop(context, device),
                ),
            ],
          ),
        ),
      );
      if (selected != null) {
        await store.cast.playDlna(
          device: selected,
          mediaUrl: playback.url,
          title: '${widget.item.title} $_episodeLabel',
        );
        if (mounted) _showMessage('已投屏到 ${selected.name}');
      }
    } catch (error) {
      if (mounted) _showMessage('投屏失败：$error');
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
  }

  void _startLongPressSpeed() {
    if (!_longPressSpeedEnabled || _locked) return;
    _speedBeforeLongPress = _speed;
    unawaited(_controller?.setPlaybackSpeed(2));
    setState(() => _speed = 2);
  }

  void _stopLongPressSpeed() {
    if (!_longPressSpeedEnabled || _locked) return;
    final speed = _speedBeforeLongPress;
    unawaited(_controller?.setPlaybackSpeed(speed));
    setState(() => _speed = speed);
  }

  Future<void> _seek(double value) async {
    final controller = _controller;
    if (controller == null || !controller.value.isInitialized) {
      setState(() => _progress = value);
      return;
    }
    final milliseconds = (controller.value.duration.inMilliseconds * value)
        .round();
    await controller.seekTo(Duration(milliseconds: milliseconds));
  }

  static String _formatDuration(Duration value) {
    final hours = value.inHours;
    final minutes = value.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = value.inSeconds.remainder(60).toString().padLeft(2, '0');
    return hours > 0 ? '$hours:$minutes:$seconds' : '$minutes:$seconds';
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _showSettingsPanel() async {
    final setting = await _showOptions(
      title: '播放设置',
      options: ['画面比例 · $_scaleMode', '定时关闭 · $_sleepTimer'],
    );
    if (setting == null || !mounted) return;
    if (setting.startsWith('画面比例')) {
      await _selectSetting(
        title: '画面比例',
        options: const ['适应屏幕', '铺满屏幕', '原始比例', '16:9'],
        onSelected: (value) {
          _scaleMode = value;
          _store?.setSetting('scaleMode', value);
        },
      );
    } else if (setting.startsWith('定时关闭')) {
      await _selectSetting(
        title: '定时关闭',
        options: const ['关闭', '15 分钟', '30 分钟', '播完本集'],
        onSelected: _configureSleepTimer,
      );
    }
  }

  void _configureSleepTimer(String value) {
    _sleepTimerHandle?.cancel();
    _sleepTimerHandle = null;
    _sleepTimer = value;
    final duration = switch (value) {
      '15 分钟' => const Duration(minutes: 15),
      '30 分钟' => const Duration(minutes: 30),
      _ => null,
    };
    if (duration != null) {
      _sleepTimerHandle = Timer(duration, _stopForSleepTimer);
    }
  }

  Future<void> _stopForSleepTimer() async {
    _sleepTimerHandle = null;
    await _controller?.pause();
    if (!mounted) return;
    setState(() => _sleepTimer = '关闭');
    _showMessage('定时关闭已执行');
  }

  Future<void> _selectSetting({
    required String title,
    required List<String> options,
    required ValueChanged<String> onSelected,
  }) async {
    final value = await _showOptions(title: title, options: options);
    if (value != null && mounted) setState(() => onSelected(value));
  }

  Future<void> _toggleFullscreen() async {
    final fullscreen = !_fullscreen;
    if (fullscreen) {
      await Future.wait([
        SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky),
        SystemChrome.setPreferredOrientations(const [
          DeviceOrientation.landscapeLeft,
          DeviceOrientation.landscapeRight,
        ]),
      ]);
    } else {
      await _restoreSystemUi();
    }
    if (mounted) setState(() => _fullscreen = fullscreen);
  }

  Future<void> _restoreSystemUi() async {
    await Future.wait([
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge),
      SystemChrome.setPreferredOrientations(const []),
    ]);
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
}

class _VideoCanvas extends StatelessWidget {
  const _VideoCanvas({
    required this.item,
    required this.controller,
    required this.loading,
    required this.error,
    required this.fit,
    required this.forcedAspectRatio,
  });

  final MediaItem item;
  final VideoPlayerController? controller;
  final bool loading;
  final String error;
  final BoxFit fit;
  final double? forcedAspectRatio;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: RadialGradient(
          colors: [Color(item.color).withValues(alpha: 0.36), Colors.black],
          radius: 1.15,
        ),
      ),
      child: Stack(
        alignment: Alignment.center,
        children: [
          if (controller?.value.isInitialized == true)
            Positioned.fill(
              child: FittedBox(
                fit: fit,
                child: SizedBox(
                  width: forcedAspectRatio == null
                      ? controller!.value.size.width
                      : 1600,
                  height: forcedAspectRatio == null
                      ? controller!.value.size.height
                      : 1600 / forcedAspectRatio!,
                  child: VideoPlayer(controller!),
                ),
              ),
            )
          else
            Icon(
              Icons.movie_filter_outlined,
              color: Color(item.color).withValues(alpha: 0.7),
              size: 100,
            ),
          if (loading) const CircularProgressIndicator(color: Colors.white),
          if (error.isNotEmpty)
            Container(
              constraints: const BoxConstraints(maxWidth: 420),
              padding: const EdgeInsets.all(16),
              color: Colors.black54,
              child: Text(
                error,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white),
              ),
            ),
        ],
      ),
    );
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({
    required this.title,
    required this.onBack,
    required this.onMore,
  });

  final String title;
  final VoidCallback onBack;
  final VoidCallback onMore;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: 0,
      right: 0,
      top: 0,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
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
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: Colors.white, fontSize: 16),
              ),
            ),
            IconButton(
              onPressed: onMore,
              color: Colors.white,
              icon: const Icon(Icons.more_vert),
            ),
          ],
        ),
      ),
    );
  }
}

class _BottomControls extends StatelessWidget {
  const _BottomControls({
    required this.progress,
    required this.positionLabel,
    required this.durationLabel,
    required this.speed,
    required this.source,
    required this.subtitle,
    required this.danmaku,
    required this.fullscreen,
    required this.onProgressChanged,
    required this.onSource,
    required this.onSpeed,
    required this.onSubtitle,
    required this.onDanmaku,
    required this.onFullscreen,
    required this.onCast,
    required this.onSettings,
  });

  final double progress;
  final String positionLabel;
  final String durationLabel;
  final double speed;
  final String source;
  final bool subtitle;
  final bool danmaku;
  final bool fullscreen;
  final ValueChanged<double> onProgressChanged;
  final VoidCallback onSource;
  final VoidCallback onSpeed;
  final VoidCallback onSubtitle;
  final VoidCallback onDanmaku;
  final VoidCallback onFullscreen;
  final VoidCallback onCast;
  final VoidCallback onSettings;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.64),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(10, 6, 10, 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Text(
                  positionLabel,
                  style: const TextStyle(color: Colors.white70),
                ),
                Expanded(
                  child: Slider(
                    value: progress,
                    activeColor: Theme.of(context).colorScheme.primary,
                    inactiveColor: Colors.white30,
                    onChanged: onProgressChanged,
                  ),
                ),
                Text(
                  durationLabel,
                  style: const TextStyle(color: Colors.white70),
                ),
                const SizedBox(width: 4),
                IconButton(
                  key: const Key('player-fullscreen'),
                  tooltip: fullscreen ? '退出全屏' : '全屏',
                  visualDensity: VisualDensity.compact,
                  color: Colors.white,
                  onPressed: onFullscreen,
                  icon: Icon(
                    fullscreen ? Icons.fullscreen_exit : Icons.fullscreen,
                  ),
                ),
              ],
            ),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _PlayerAction(
                    label: source,
                    icon: Icons.alt_route,
                    onTap: onSource,
                  ),
                  _PlayerAction(
                    key: const Key('player-speed'),
                    label: '${speed.toStringAsFixed(speed == 1 ? 1 : 2)}×',
                    icon: Icons.speed,
                    onTap: onSpeed,
                  ),
                  _PlayerAction(
                    label: '字幕',
                    icon: subtitle ? Icons.subtitles : Icons.subtitles_off,
                    active: subtitle,
                    onTap: onSubtitle,
                  ),
                  _PlayerAction(
                    label: '弹幕',
                    icon: danmaku
                        ? Icons.chat_bubble
                        : Icons.chat_bubble_outline,
                    active: danmaku,
                    onTap: onDanmaku,
                  ),
                  _PlayerAction(label: '投屏', icon: Icons.cast, onTap: onCast),
                  _PlayerAction(
                    label: '设置',
                    icon: Icons.settings_outlined,
                    onTap: onSettings,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PlayerAction extends StatelessWidget {
  const _PlayerAction({
    required this.label,
    required this.icon,
    required this.onTap,
    this.active = false,
    super.key,
  });

  final String label;
  final IconData icon;
  final VoidCallback onTap;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return TextButton.icon(
      onPressed: onTap,
      style: TextButton.styleFrom(
        foregroundColor: active
            ? Theme.of(context).colorScheme.primary
            : Colors.white,
      ),
      icon: Icon(icon, size: 18),
      label: Text(label),
    );
  }
}
