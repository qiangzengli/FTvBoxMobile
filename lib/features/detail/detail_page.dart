import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/features/player/player_page.dart';
import 'package:flutter/material.dart';

class DetailPage extends StatefulWidget {
  const DetailPage({required this.item, super.key});

  final MediaItem item;

  @override
  State<DetailPage> createState() => _DetailPageState();
}

class _DetailPageState extends State<DetailPage> {
  int _sourceIndex = 0;
  int _episode = 1;
  int _episodePage = 0;

  MediaItem get item => widget.item;

  void _play(BuildContext context) {
    FBoxScope.of(context).recordWatch(item);
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PlayerPage(
          item: item,
          episode: _episode,
          source: _sourceIndex == 0 ? '高清线路' : '备用线路',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final store = FBoxScope.of(context);
    final favorite = store.isFavorite(item);
    return Scaffold(
      key: const Key('detail-page'),
      appBar: AppBar(
        title: const Text('影片详情'),
        actions: [
          PopupMenuButton<String>(
            onSelected: (value) => _showMessage('$value（本地演示）'),
            itemBuilder: (_) => const [
              PopupMenuItem(value: '分享影片', child: Text('分享')),
              PopupMenuItem(value: '复制片名', child: Text('复制片名')),
              PopupMenuItem(value: '反馈片源', child: Text('反馈片源')),
            ],
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(14, 4, 14, 28),
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 124,
                height: 174,
                decoration: BoxDecoration(
                  color: Color(item.color),
                  borderRadius: BorderRadius.circular(12),
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      Color(item.color),
                      Color(item.color).withValues(alpha: 0.62),
                    ],
                  ),
                ),
                child: const Icon(
                  Icons.movie_filter_outlined,
                  color: Colors.white54,
                  size: 48,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.title,
                      style: const TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 10),
                    Text('${item.year} · ${item.category} · 中国大陆'),
                    const SizedBox(height: 8),
                    Text('导演：${item.director}'),
                    const SizedBox(height: 8),
                    Text(
                      '主演：${item.actors}',
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 9),
                    Text(
                      '豆瓣 ${item.rating}',
                      style: const TextStyle(
                        color: Color(0xFFFF7800),
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  key: const Key('detail-play'),
                  onPressed: () => _play(context),
                  icon: const Icon(Icons.play_arrow_rounded),
                  label: const Text('立即播放'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Theme.of(context).colorScheme.primary,
                    foregroundColor: Colors.white,
                    elevation: 0,
                    minimumSize: const Size.fromHeight(44),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              OutlinedButton.icon(
                key: const Key('detail-favorite'),
                onPressed: () => store.toggleFavorite(item),
                icon: Icon(
                  favorite ? Icons.star_rounded : Icons.star_border_rounded,
                ),
                label: Text(favorite ? '已收藏' : '收藏'),
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size(112, 44),
                  side: BorderSide(
                    color: favorite
                        ? Theme.of(context).colorScheme.primary
                        : Theme.of(context).dividerColor,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _showActionSheet(
                    title: '投屏设备',
                    items: const ['客厅电视（演示）', '卧室电视（演示）'],
                  ),
                  icon: const Icon(Icons.cast_rounded, size: 18),
                  label: const Text('投屏'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _showActionSheet(
                    title: '缓存选集',
                    items: const ['缓存当前集', '缓存全部'],
                  ),
                  icon: const Icon(Icons.download_outlined, size: 18),
                  label: const Text('下载'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _showActionSheet(
                    title: '解析方式',
                    items: const ['自动解析', '嗅探解析', '原始地址'],
                  ),
                  icon: const Icon(Icons.tune_rounded, size: 18),
                  label: const Text('解析'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          const Text(
            '简介',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          Text(
            item.description,
            style: TextStyle(
              height: 1.55,
              color: Theme.of(context).colorScheme.onSurface
                  .withValues(alpha: 0.72),
            ),
          ),
          const SizedBox(height: 20),
          Row(
            children: [
              const Text(
                '线路',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
              ),
              const Spacer(),
              TextButton.icon(
                key: const Key('detail-quick-source'),
                onPressed: () =>
                    setState(() => _sourceIndex = 1 - _sourceIndex),
                label: const Text('快捷换线'),
                icon: const Icon(Icons.swap_horiz, size: 18),
              ),
            ],
          ),
          Wrap(
            spacing: 8,
            children: [
              ChoiceChip(
                key: const Key('detail-source-0'),
                label: const Text('高清线路'),
                selected: _sourceIndex == 0,
                onSelected: (_) => setState(() => _sourceIndex = 0),
              ),
              ChoiceChip(
                key: const Key('detail-source-1'),
                label: const Text('备用线路'),
                selected: _sourceIndex == 1,
                onSelected: (_) => setState(() => _sourceIndex = 1),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              const Text(
                '选集',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
              ),
              const Spacer(),
              Text('${item.episodes} 集'),
            ],
          ),
          const SizedBox(height: 10),
          if (item.episodes > 30) ...[
            SizedBox(
              height: 34,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: (item.episodes + 29) ~/ 30,
                separatorBuilder: (_, _) => const SizedBox(width: 8),
                itemBuilder: (context, index) {
                  final start = index * 30 + 1;
                  final end = (start + 29).clamp(1, item.episodes);
                  return ChoiceChip(
                    label: Text('$start-$end'),
                    selected: _episodePage == index,
                    onSelected: (_) => setState(() => _episodePage = index),
                  );
                },
              ),
            ),
            const SizedBox(height: 10),
          ],
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 6,
              mainAxisSpacing: 7,
              crossAxisSpacing: 7,
              childAspectRatio: 1.3,
            ),
            itemCount: (item.episodes - _episodePage * 30).clamp(1, 30),
            itemBuilder: (context, index) {
              final episode = _episodePage * 30 + index + 1;
              return OutlinedButton(
                key: Key('detail-episode-$episode'),
                onPressed: () {
                  setState(() => _episode = episode);
                  _play(context);
                },
                style: OutlinedButton.styleFrom(
                  padding: EdgeInsets.zero,
                  backgroundColor: _episode == episode
                      ? Theme.of(context).colorScheme.primary
                            .withValues(alpha: 0.12)
                      : null,
                  side: BorderSide(
                    color: _episode == episode
                        ? Theme.of(context).colorScheme.primary
                        : Theme.of(context).dividerColor,
                  ),
                ),
                child: Text('$episode'),
              );
            },
          ),
        ],
      ),
    );
  }

  Future<void> _showActionSheet({
    required String title,
    required List<String> items,
  }) async {
    final selected = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              title: Text(
                title,
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
            ...items.map(
              (item) => ListTile(
                title: Text(item),
                onTap: () => Navigator.pop(context, item),
              ),
            ),
          ],
        ),
      ),
    );
    if (selected != null && mounted) _showMessage('$selected（本地演示）');
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
}
