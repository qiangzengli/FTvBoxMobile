import 'package:flutter/material.dart';

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

  static const groups = ['央视频道', '卫视频道', '数字频道'];
  static const channels = [
    ['CCTV-1 综合', 'CCTV-2 财经', 'CCTV-5 体育', 'CCTV-6 电影'],
    ['湖南卫视', '东方卫视', '浙江卫视', '江苏卫视'],
    ['风云足球', '世界地理', '第一剧场', '怀旧剧场'],
  ];

  String get _currentChannel => channels[_group][_channel];

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
                const _LiveCanvas(),
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
                      onPressed: () => setState(() => _playing = !_playing),
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
                        onGroup: (index) => setState(() {
                          _group = index;
                          _channel = 0;
                        }),
                        onChannel: (index) => setState(() => _channel = index),
                      ),
                    ),
                  Positioned(
                    left: 12,
                    right: 12,
                    bottom: 10,
                    child: _LiveBottomBar(
                      channel: _currentChannel,
                      channelsVisible: _channelsVisible,
                      onChannels: () =>
                          setState(() => _channelsVisible = !_channelsVisible),
                      onEpg: () => _showOptions(
                        title: '节目单',
                        options: const [
                          '20:00  黄金档特别节目',
                          '21:30  晚间新闻',
                          '22:10  精品剧场',
                        ],
                      ),
                      onCast: () => _showOptions(
                        title: '投屏设备',
                        options: const ['客厅电视（演示）', '卧室电视（演示）'],
                      ),
                      onSettings: () => _showOptions(
                        title: '直播设置',
                        options: const ['画面比例', '线路选择', '解码方式'],
                      ),
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
    final source = await _showOptions(
      title: '直播源',
      options: const ['演示直播源', '备用直播源', '本地播放列表'],
    );
    if (source != null && mounted) setState(() => _source = source);
  }
}

class _LiveCanvas extends StatelessWidget {
  const _LiveCanvas();

  @override
  Widget build(BuildContext context) {
    return const DecoratedBox(
      decoration: BoxDecoration(
        gradient: RadialGradient(
          colors: [Color(0xFF24324F), Colors.black],
          radius: 1.15,
        ),
      ),
      child: Center(
        child: Icon(Icons.live_tv_rounded, color: Colors.white24, size: 94),
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
    required this.channelsVisible,
    required this.onChannels,
    required this.onEpg,
    required this.onCast,
    required this.onSettings,
  });

  final String channel;
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
                const Text(
                  '20:00  黄金档特别节目',
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
