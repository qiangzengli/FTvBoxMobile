import 'dart:async';

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/data/demo_catalog.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:fbox/features/detail/detail_page.dart';
import 'package:fbox/features/library/library_page.dart';
import 'package:fbox/features/search/search_page.dart';
import 'package:fbox/features/subscription/subscription_page.dart';
import 'package:fbox/widgets/media_poster_card.dart';
import 'package:flutter/material.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  String _category = '推荐';
  String _source = 'FBox';
  Map<String, String> _filters = const {};

  void _openDetail(MediaItem item) {
    Navigator.of(context)
        .push(MaterialPageRoute<void>(builder: (_) => DetailPage(item: item)));
  }

  @override
  Widget build(BuildContext context) {
    final store = FBoxScope.of(context);
    final colors = Theme.of(context).colorScheme;
    final items = store.currentSource == null
        ? DemoCatalog.category(_category)
        : store.catalog;
    final layout = store.settings['homeLayout']?.toString() ?? '3 列 · 海报模式';
    final columns = layout.startsWith('2')
        ? 2
        : layout == '列表模式'
        ? 1
        : 3;
    final aspectRatio = columns == 1
        ? 2.7
        : columns == 2
        ? 0.76
        : 0.72;
    final sourceLabel = store.currentSource?.name ?? _source;
    return SafeArea(
      bottom: false,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(10, 16, 10, 0),
            child: Row(
              children: [
                InkWell(
                  borderRadius: BorderRadius.circular(8),
                  onTap: _showSources,
                  onLongPress: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const SubscriptionPage(),
                    ),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 2,
                      vertical: 8,
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 90),
                          child: Text(
                            sourceLabel,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                        const SizedBox(width: 3),
                        const Icon(Icons.keyboard_arrow_down, size: 18),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Material(
                    key: const Key('home-search'),
                    color: Theme.of(context).cardColor,
                    borderRadius: BorderRadius.circular(18),
                    clipBehavior: Clip.antiAlias,
                    child: InkWell(
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => const SearchPage(),
                        ),
                      ),
                      child: SizedBox(
                        height: 36,
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          child: Row(
                            children: [
                              Text(
                                '搜索',
                                style: TextStyle(
                                  color: colors.onSurface.withValues(
                                    alpha: 0.45,
                                  ),
                                  fontSize: 14,
                                ),
                              ),
                              const Spacer(),
                              Icon(
                                Icons.search,
                                size: 18,
                                color: colors.onSurface.withValues(alpha: 0.55),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                IconButton(
                  visualDensity: VisualDensity.compact,
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) =>
                          const LibraryPage(kind: LibraryKind.favorites),
                    ),
                  ),
                  icon: const Icon(Icons.star_border_rounded),
                ),
                IconButton(
                  visualDensity: VisualDensity.compact,
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) =>
                          const LibraryPage(kind: LibraryKind.history),
                    ),
                  ),
                  icon: const Icon(Icons.history_rounded),
                ),
              ],
            ),
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              Expanded(
                child: _CategoryStrip(
                  labels: store.categories,
                  selected: _category,
                  onSelected: (value) => _selectCategory(store, value),
                ),
              ),
              if (store.filtersForCategory(_category).isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(right: 6),
                  child: Badge.count(
                    count: _filters.values
                        .where((value) => value.isNotEmpty)
                        .length,
                    isLabelVisible: _filters.values.any(
                      (value) => value.isNotEmpty,
                    ),
                    child: IconButton(
                      key: const Key('home-filter'),
                      tooltip: '筛选',
                      onPressed: () => _showFilters(store),
                      icon: const Icon(Icons.filter_list_rounded),
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 10),
          Expanded(
            child: store.catalogLoading
                ? const Center(child: CircularProgressIndicator())
                : store.catalogError.isNotEmpty && items.isEmpty
                ? _LoadError(
                    message: store.catalogError,
                    onRetry: () => _selectCategory(store, _category),
                  )
                : NotificationListener<ScrollNotification>(
                    onNotification: (notification) {
                      if (notification.metrics.extentAfter < 360 &&
                          store.catalogHasMore &&
                          !store.catalogLoadingMore) {
                        unawaited(
                          store
                              .loadNextCategoryPage(
                                _category,
                                filters: _filters,
                              )
                              .catchError(_showLoadMoreError),
                        );
                      }
                      return false;
                    },
                    child: GridView.builder(
                      padding: const EdgeInsets.fromLTRB(8, 0, 8, 12),
                      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: columns,
                        childAspectRatio: aspectRatio,
                        mainAxisSpacing: 4,
                        crossAxisSpacing: 4,
                      ),
                      itemCount:
                          items.length + (store.catalogLoadingMore ? 1 : 0),
                      itemBuilder: (context, index) {
                        if (index == items.length) {
                          return const Center(
                            child: CircularProgressIndicator(),
                          );
                        }
                        return MediaPosterCard(
                          item: items[index],
                          onTap: () => _openDetail(items[index]),
                        );
                      },
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Future<void> _showSources() async {
    final store = FBoxScope.of(context);
    final value = await showModalBottomSheet<String>(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (context) => SafeArea(
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(context).height * 0.72,
          ),
          child: ListView(
            shrinkWrap: true,
            padding: const EdgeInsets.fromLTRB(16, 18, 16, 12),
            children: [
              const Text(
                '选择站源',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 10),
              ...[
                (key: 'demo', name: 'FBox', supported: true),
                ...store.sources.map(
                  (source) => (
                    key: source.identity,
                    name: source.name,
                    supported: source.isSupported,
                  ),
                ),
              ].map(
                (source) => ListTile(
                  title: Text(source.name),
                  subtitle: source.supported
                      ? null
                      : const Text('仅 Android Spider，iOS 不兼容'),
                  trailing:
                      (source.key == 'demo' && store.currentSource == null) ||
                          source.key == store.currentSourceKey
                      ? Icon(
                          Icons.check,
                          color: Theme.of(context).colorScheme.primary,
                        )
                      : null,
                  onTap: () => Navigator.pop(context, source.key),
                ),
              ),
              const Divider(),
              ListTile(
                leading: const Icon(Icons.settings_outlined),
                title: const Text('订阅管理'),
                onTap: () {
                  Navigator.pop(context);
                  Navigator.of(this.context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const SubscriptionPage(),
                    ),
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
    if (value == null || !mounted) return;
    try {
      await store.selectSource(value);
      if (mounted) {
        setState(() {
          _source = store.currentSource?.name ?? 'FBox';
          _category = '推荐';
          _filters = const {};
        });
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  void _showLoadMoreError(Object error) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text('加载下一页失败：$error')));
  }

  Future<void> _selectCategory(FBoxStore store, String value) async {
    setState(() {
      _category = value;
      _filters = const {};
    });
    try {
      await store.loadCategory(value);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('加载分类失败：$error')));
      }
    }
  }

  Future<void> _showFilters(FBoxStore store) async {
    final definitions = store.filtersForCategory(_category);
    final selected = await showModalBottomSheet<Map<String, String>>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (context) =>
          _FilterSheet(definitions: definitions, selected: _filters),
    );
    if (selected == null || !mounted) return;
    setState(() => _filters = selected);
    try {
      await store.loadCategory(_category, filters: selected);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('应用筛选失败：$error')));
      }
    }
  }
}

class _FilterSheet extends StatefulWidget {
  const _FilterSheet({required this.definitions, required this.selected});

  final List<SourceFilter> definitions;
  final Map<String, String> selected;

  @override
  State<_FilterSheet> createState() => _FilterSheetState();
}

class _FilterSheetState extends State<_FilterSheet> {
  late final Map<String, String> _selected = Map.of(widget.selected);

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          18,
          18,
          18,
          16 + MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Text(
                  '分类筛选',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                ),
                const Spacer(),
                TextButton(
                  onPressed: () => setState(_selected.clear),
                  child: const Text('重置'),
                ),
              ],
            ),
            Flexible(
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    for (final definition in widget.definitions) ...[
                      const SizedBox(height: 12),
                      Text(
                        definition.name,
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 4,
                        children: [
                          for (final option in definition.options)
                            ChoiceChip(
                              label: Text(option.label),
                              selected:
                                  (_selected[definition.key] ?? '') ==
                                  option.value,
                              onSelected: (_) => setState(() {
                                if (option.value.isEmpty) {
                                  _selected.remove(definition.key);
                                } else {
                                  _selected[definition.key] = option.value;
                                }
                              }),
                            ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: 18),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () => Navigator.pop(context, _selected),
                child: const Text('应用筛选'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CategoryStrip extends StatelessWidget {
  const _CategoryStrip({
    required this.labels,
    required this.selected,
    required this.onSelected,
  });

  final List<String> labels;
  final String selected;
  final ValueChanged<String> onSelected;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 40,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        scrollDirection: Axis.horizontal,
        itemCount: labels.length,
        separatorBuilder: (_, _) => const SizedBox(width: 24),
        itemBuilder: (context, index) {
          final label = labels[index];
          final active = label == selected;
          return InkWell(
            key: Key('category-$label'),
            onTap: () => onSelected(label),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  label,
                  style: TextStyle(
                    color: active
                        ? Theme.of(context).colorScheme.primary
                        : Theme.of(context).colorScheme.onSurface,
                    fontSize: active ? 16 : 14,
                    fontWeight: active ? FontWeight.w600 : FontWeight.normal,
                  ),
                ),
                const SizedBox(height: 4),
                AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  width: active ? 18 : 0,
                  height: 2,
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.primary,
                    borderRadius: BorderRadius.circular(1),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _LoadError extends StatelessWidget {
  const _LoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_outlined, size: 48),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            FilledButton(onPressed: onRetry, child: const Text('重试')),
          ],
        ),
      ),
    );
  }
}
