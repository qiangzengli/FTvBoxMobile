import 'package:fbox/domain/media_item.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:fbox/features/player/player_page.dart';
import 'package:flutter/material.dart';

class UrlPlayPage extends StatefulWidget {
  const UrlPlayPage({super.key});

  @override
  State<UrlPlayPage> createState() => _UrlPlayPageState();
}

class _UrlPlayPageState extends State<UrlPlayPage> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final value = _controller.text.trim();
    final uri = Uri.tryParse(value);
    if (uri == null ||
        !{'http', 'https', 'file', 'rtsp', 'rtmp'}.contains(uri.scheme)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请输入有效的 HTTP、HTTPS、文件或直播地址')),
      );
      return;
    }
    final title = uri.pathSegments.isEmpty || uri.pathSegments.last.isEmpty
        ? uri.host
        : Uri.decodeComponent(uri.pathSegments.last);
    final item = MediaItem(
      id: 'url-${value.hashCode}',
      title: title,
      note: '链接播放',
      year: '',
      category: '链接',
      rating: '',
      description: value,
      director: '',
      actors: '',
      episodes: 1,
      color: 0xFF48627A,
      playLines: [
        MediaPlayLine(
          name: '原始地址',
          episodes: [MediaEpisode(name: '链接', url: value)],
        ),
      ],
    );
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PlayerPage(
          item: item,
          episodeName: '链接',
          source: '原始地址',
          playback: PlaybackRequest(url: value),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('播放链接')),
      body: ListView(
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        padding: const EdgeInsets.all(18),
        children: [
          const Text(
            '请输入视频地址',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
          Text(
            '支持 HTTP、HTTPS、M3U8 等常见媒体链接',
            style: TextStyle(
              color: Theme.of(context).colorScheme.onSurface
                  .withValues(alpha: 0.55),
            ),
          ),
          const SizedBox(height: 22),
          TextField(
            controller: _controller,
            maxLines: 4,
            decoration: InputDecoration(
              hintText: 'https://example.com/video.m3u8',
              filled: true,
              fillColor: Theme.of(context).cardColor,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide.none,
              ),
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _submit,
              icon: const Icon(Icons.play_arrow_rounded),
              label: const Text('播放'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.primary,
                foregroundColor: Colors.white,
                minimumSize: const Size.fromHeight(46),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
