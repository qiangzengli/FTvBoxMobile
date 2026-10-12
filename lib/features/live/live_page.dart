import 'package:fbox/app/fbox_theme.dart';
import 'package:flutter/material.dart';

class LivePage extends StatefulWidget {
  const LivePage({super.key});

  @override
  State<LivePage> createState() => _LivePageState();
}

class _LivePageState extends State<LivePage> {
  int _group = 0;
  int _channel = 0;

  static const groups = ['央视频道', '卫视频道', '数字频道'];
  static const channels = [
    ['CCTV-1 综合', 'CCTV-2 财经', 'CCTV-5 体育', 'CCTV-6 电影'],
    ['湖南卫视', '东方卫视', '浙江卫视', '江苏卫视'],
    ['风云足球', '世界地理', '第一剧场', '怀旧剧场'],
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('直播')),
      body: Column(
        children: [
          Container(
            height: 220,
            width: double.infinity,
            color: Colors.black,
            child: Stack(
              alignment: Alignment.center,
              children: [
                Icon(
                  Icons.live_tv_rounded,
                  color: Colors.white.withValues(alpha: 0.25),
                  size: 72,
                ),
                const Positioned(
                  left: 14,
                  top: 12,
                  child: Text('直播预览', style: TextStyle(color: Colors.white70)),
                ),
                const Icon(
                  Icons.play_circle_fill_rounded,
                  color: Colors.white70,
                  size: 54,
                ),
              ],
            ),
          ),
          Container(
            height: 96,
            margin: const EdgeInsets.all(10),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: Theme.of(context).cardColor,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Row(
              children: [
                SizedBox(
                  width: 110,
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        channels[_group][_channel],
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 6),
                      const Text('正在直播', style: TextStyle(fontSize: 12)),
                    ],
                  ),
                ),
                const VerticalDivider(),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text('20:00  黄金档特别节目'),
                      SizedBox(height: 8),
                      Text('21:30  晚间新闻', style: TextStyle(fontSize: 12)),
                    ],
                  ),
                ),
                const Icon(Icons.cast_rounded),
                const SizedBox(width: 12),
                const Icon(Icons.settings_outlined),
              ],
            ),
          ),
          Expanded(
            child: Container(
              margin: const EdgeInsets.fromLTRB(10, 0, 10, 10),
              decoration: BoxDecoration(
                color: Theme.of(context).cardColor,
                borderRadius: BorderRadius.circular(14),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: ListView.builder(
                      padding: const EdgeInsets.all(10),
                      itemCount: groups.length,
                      itemBuilder: (context, index) => _LiveChoice(
                        label: groups[index],
                        selected: _group == index,
                        onTap: () => setState(() {
                          _group = index;
                          _channel = 0;
                        }),
                      ),
                    ),
                  ),
                  const VerticalDivider(width: 1),
                  Expanded(
                    child: ListView.builder(
                      padding: const EdgeInsets.all(10),
                      itemCount: channels[_group].length,
                      itemBuilder: (context, index) => _LiveChoice(
                        label: channels[_group][index],
                        selected: _channel == index,
                        onTap: () => setState(() => _channel = index),
                      ),
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
      padding: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: selected
            ? FBoxTheme.primary.withValues(alpha: 0.14)
            : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
        child: ListTile(
          dense: true,
          onTap: onTap,
          title: Text(
            label,
            style: TextStyle(
              color: selected ? FBoxTheme.primary : null,
              fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
            ),
          ),
        ),
      ),
    );
  }
}
