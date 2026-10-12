import 'package:fbox/domain/media_item.dart';
import 'package:flutter/material.dart';

class PlayerPage extends StatefulWidget {
  const PlayerPage({
    required this.item,
    this.episode = 1,
    this.source = '高清线路',
    super.key,
  });

  final MediaItem item;
  final int episode;
  final String source;

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> {
  bool _playing = false;
  bool _controlsVisible = true;
  bool _locked = false;
  bool _danmaku = true;
  bool _subtitle = false;
  double _progress = 0.18;
  double _speed = 1;
  late String _source = widget.source;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: GestureDetector(
          key: const Key('player-surface'),
          behavior: HitTestBehavior.opaque,
          onTap: () {
            if (!_locked) {
              setState(() => _controlsVisible = !_controlsVisible);
            }
          },
          child: Stack(
            fit: StackFit.expand,
            children: [
              _VideoCanvas(item: widget.item, danmaku: _danmaku),
              if (_controlsVisible && !_locked) ...[
                _TopBar(
                  title: '${widget.item.title} · 第 ${widget.episode} 集',
                  onBack: () => Navigator.pop(context),
                  onMore: () => _showOptions(
                    title: '播放设置',
                    options: const ['画面比例', '解码方式', '音轨', '定时关闭'],
                  ),
                ),
                Center(
                  child: IconButton(
                    key: const Key('player-play-toggle'),
                    onPressed: () => setState(() => _playing = !_playing),
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
                    speed: _speed,
                    source: _source,
                    subtitle: _subtitle,
                    danmaku: _danmaku,
                    onProgressChanged: (value) =>
                        setState(() => _progress = value),
                    onSource: _showSourcePicker,
                    onSpeed: _showSpeedPicker,
                    onSubtitle: () => setState(() => _subtitle = !_subtitle),
                    onDanmaku: () => setState(() => _danmaku = !_danmaku),
                    onCast: () => _showOptions(
                      title: '投屏设备',
                      options: const ['客厅电视（演示）', '卧室电视（演示）'],
                    ),
                    onSettings: () => _showOptions(
                      title: '播放设置',
                      options: const ['画面比例', '解码方式', '音轨', '定时关闭'],
                    ),
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
    final value = await _showOptions(
      title: '播放线路',
      options: const ['高清线路', '备用线路', '解析线路'],
    );
    if (value != null && mounted) setState(() => _source = value);
  }

  Future<void> _showSpeedPicker() async {
    final value = await _showOptions(
      title: '播放速度',
      options: const ['0.75×', '1.0×', '1.25×', '1.5×', '2.0×'],
    );
    if (value == null || !mounted) return;
    setState(() => _speed = double.parse(value.replaceAll('×', '')));
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
  const _VideoCanvas({required this.item, required this.danmaku});

  final MediaItem item;
  final bool danmaku;

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
          Icon(
            Icons.movie_filter_outlined,
            color: Color(item.color).withValues(alpha: 0.7),
            size: 100,
          ),
          if (danmaku) ...const [
            Positioned(
              top: 120,
              left: 40,
              child: Text('这个镜头太棒了', style: _danmakuStyle),
            ),
            Positioned(
              top: 170,
              right: 24,
              child: Text('FBox 弹幕演示 →', style: _danmakuStyle),
            ),
          ],
        ],
      ),
    );
  }
}

const _danmakuStyle = TextStyle(
  color: Colors.white,
  fontSize: 12,
  shadows: [Shadow(color: Colors.black, blurRadius: 3)],
);

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
    required this.speed,
    required this.source,
    required this.subtitle,
    required this.danmaku,
    required this.onProgressChanged,
    required this.onSource,
    required this.onSpeed,
    required this.onSubtitle,
    required this.onDanmaku,
    required this.onCast,
    required this.onSettings,
  });

  final double progress;
  final double speed;
  final String source;
  final bool subtitle;
  final bool danmaku;
  final ValueChanged<double> onProgressChanged;
  final VoidCallback onSource;
  final VoidCallback onSpeed;
  final VoidCallback onSubtitle;
  final VoidCallback onDanmaku;
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
                const Text('08:24', style: TextStyle(color: Colors.white70)),
                Expanded(
                  child: Slider(
                    value: progress,
                    activeColor: Theme.of(context).colorScheme.primary,
                    inactiveColor: Colors.white30,
                    onChanged: onProgressChanged,
                  ),
                ),
                const Text('45:16', style: TextStyle(color: Colors.white70)),
                const SizedBox(width: 4),
                const Icon(Icons.fullscreen, color: Colors.white),
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
