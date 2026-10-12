import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/data/demo_catalog.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/features/detail/detail_page.dart';
import 'package:fbox/widgets/media_poster_card.dart';
import 'package:flutter/material.dart';

enum LibraryKind { history, favorites }

class LibraryPage extends StatelessWidget {
  const LibraryPage({required this.kind, super.key});

  final LibraryKind kind;

  @override
  Widget build(BuildContext context) {
    final store = FBoxScope.of(context);
    final ids = kind == LibraryKind.history
        ? store.historyIds
        : store.favoriteIds;
    final items = ids.map(DemoCatalog.byId).toList();
    final title = kind == LibraryKind.history ? '观看历史' : '收藏';
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          if (items.isNotEmpty)
            TextButton(onPressed: () {}, child: const Text('编辑')),
        ],
      ),
      body: items.isEmpty
          ? _EmptyLibrary(kind: kind)
          : GridView.builder(
              padding: const EdgeInsets.all(10),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                childAspectRatio: 0.72,
                mainAxisSpacing: 6,
                crossAxisSpacing: 6,
              ),
              itemCount: items.length,
              itemBuilder: (context, index) => MediaPosterCard(
                item: items[index],
                onTap: () => _openDetail(context, items[index]),
              ),
            ),
    );
  }

  void _openDetail(BuildContext context, MediaItem item) {
    Navigator.of(context)
        .push(MaterialPageRoute<void>(builder: (_) => DetailPage(item: item)));
  }
}

class _EmptyLibrary extends StatelessWidget {
  const _EmptyLibrary({required this.kind});

  final LibraryKind kind;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            kind == LibraryKind.history
                ? Icons.history_rounded
                : Icons.star_border_rounded,
            size: 68,
            color: Theme.of(context).colorScheme.onSurface
                .withValues(alpha: 0.18),
          ),
          const SizedBox(height: 14),
          Text(
            kind == LibraryKind.history ? '暂无观看记录' : '暂无收藏内容',
            style: TextStyle(
              color: Theme.of(context).colorScheme.onSurface
                  .withValues(alpha: 0.48),
            ),
          ),
        ],
      ),
    );
  }
}
