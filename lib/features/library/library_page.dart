import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/features/detail/detail_page.dart';
import 'package:fbox/widgets/media_poster_card.dart';
import 'package:flutter/material.dart';

enum LibraryKind { history, favorites }

class LibraryPage extends StatefulWidget {
  const LibraryPage({required this.kind, super.key});

  final LibraryKind kind;

  @override
  State<LibraryPage> createState() => _LibraryPageState();
}

class _LibraryPageState extends State<LibraryPage> {
  bool _editing = false;
  final Set<String> _selected = {};

  @override
  Widget build(BuildContext context) {
    final store = FBoxScope.of(context);
    final ids = widget.kind == LibraryKind.history
        ? store.historyIds
        : store.favoriteIds;
    final items = store.itemsForIds(ids);
    final title = widget.kind == LibraryKind.history ? '观看历史' : '收藏';
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          if (items.isNotEmpty)
            TextButton(
              key: const Key('library-edit'),
              onPressed: () => setState(() {
                _editing = !_editing;
                if (!_editing) _selected.clear();
              }),
              child: Text(_editing ? '完成' : '编辑'),
            ),
        ],
      ),
      body: items.isEmpty
          ? _EmptyLibrary(kind: widget.kind)
          : GridView.builder(
              padding: const EdgeInsets.all(10),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                childAspectRatio: 0.72,
                mainAxisSpacing: 6,
                crossAxisSpacing: 6,
              ),
              itemCount: items.length,
              itemBuilder: (context, index) {
                final item = items[index];
                final selected = _selected.contains(item.storageId);
                return Stack(
                  fit: StackFit.expand,
                  children: [
                    MediaPosterCard(
                      item: item,
                      onTap: () => _editing
                          ? _toggleSelected(item.storageId)
                          : _openDetail(context, item),
                    ),
                    if (_editing)
                      Positioned(
                        right: 7,
                        top: 7,
                        child: IgnorePointer(
                          child: CircleAvatar(
                            radius: 12,
                            backgroundColor: selected
                                ? Theme.of(context).colorScheme.primary
                                : Colors.black45,
                            child: Icon(
                              selected ? Icons.check : Icons.circle_outlined,
                              color: Colors.white,
                              size: 16,
                            ),
                          ),
                        ),
                      ),
                  ],
                );
              },
            ),
      bottomNavigationBar: _editing && items.isNotEmpty
          ? SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
                child: Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        key: const Key('library-clear-all'),
                        onPressed: () => _clearAll(store),
                        child: const Text('清空全部'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton(
                        key: const Key('library-delete-selected'),
                        onPressed: _selected.isEmpty
                            ? null
                            : () => _deleteSelected(store),
                        child: Text('删除（${_selected.length}）'),
                      ),
                    ),
                  ],
                ),
              ),
            )
          : null,
    );
  }

  void _toggleSelected(String id) {
    setState(() {
      if (!_selected.add(id)) _selected.remove(id);
    });
  }

  void _deleteSelected(FBoxStore store) {
    if (widget.kind == LibraryKind.history) {
      store.removeHistory(_selected);
    } else {
      store.removeFavorites(_selected);
    }
    setState(() {
      _selected.clear();
      _editing = false;
    });
  }

  void _clearAll(FBoxStore store) {
    if (widget.kind == LibraryKind.history) {
      store.clearHistory();
    } else {
      store.clearFavorites();
    }
    setState(() {
      _selected.clear();
      _editing = false;
    });
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
