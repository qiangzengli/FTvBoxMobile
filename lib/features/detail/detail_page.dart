import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/app/fbox_theme.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/features/player/player_page.dart';
import 'package:flutter/material.dart';

class DetailPage extends StatelessWidget {
  const DetailPage({required this.item, super.key});

  final MediaItem item;

  void _play(BuildContext context) {
    FBoxScope.of(context).recordWatch(item);
    Navigator.of(context)
        .push(MaterialPageRoute<void>(builder: (_) => PlayerPage(item: item)));
  }

  @override
  Widget build(BuildContext context) {
    final store = FBoxScope.of(context);
    final favorite = store.isFavorite(item);
    return Scaffold(
      appBar: AppBar(
        title: const Text('影片详情'),
        actions: [
          IconButton(onPressed: () {}, icon: const Icon(Icons.more_vert)),
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
                    backgroundColor: FBoxTheme.primary,
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
                        ? FBoxTheme.primary
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
                  onPressed: () {},
                  icon: const Icon(Icons.cast_rounded, size: 18),
                  label: const Text('投屏'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () {},
                  icon: const Icon(Icons.download_outlined, size: 18),
                  label: const Text('下载'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () {},
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
                onPressed: () {},
                label: const Text('快捷换线'),
                icon: const Icon(Icons.swap_horiz, size: 18),
              ),
            ],
          ),
          Wrap(
            spacing: 8,
            children: const [
              ChoiceChip(label: Text('高清线路'), selected: true),
              ChoiceChip(label: Text('备用线路'), selected: false),
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
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 6,
              mainAxisSpacing: 7,
              crossAxisSpacing: 7,
              childAspectRatio: 1.3,
            ),
            itemCount: item.episodes.clamp(1, 18),
            itemBuilder: (context, index) => OutlinedButton(
              onPressed: () => _play(context),
              style: OutlinedButton.styleFrom(
                padding: EdgeInsets.zero,
                side: BorderSide(color: Theme.of(context).dividerColor),
              ),
              child: Text('${index + 1}'),
            ),
          ),
        ],
      ),
    );
  }
}
