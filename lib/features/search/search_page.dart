import 'dart:async';

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/features/detail/detail_page.dart';
import 'package:flutter/material.dart';

class SearchPage extends StatefulWidget {
  const SearchPage({super.key});

  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  final _controller = TextEditingController();
  final _scrollController = ScrollController();
  String _query = '';
  List<MediaItem> _results = const [];
  Timer? _debounce;
  int _requestId = 0;
  bool _loading = false;
  bool _loadingMore = false;
  bool _hasMore = true;
  int _page = 0;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _scrollController.dispose();
    _controller.dispose();
    super.dispose();
  }

  void _open(MediaItem item) {
    Navigator.of(
      context,
    ).push(MaterialPageRoute<void>(builder: (_) => DetailPage(item: item)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('快速搜索')),
      body: SafeArea(
        top: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 6, 14, 10),
              child: TextField(
                key: const Key('search-field'),
                controller: _controller,
                autofocus: true,
                onChanged: _onQueryChanged,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  hintText: '请输入影片名称',
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: _query.isEmpty
                      ? null
                      : IconButton(
                          onPressed: () {
                            _controller.clear();
                            _onQueryChanged('');
                          },
                          icon: const Icon(Icons.clear),
                        ),
                  filled: true,
                  fillColor: Theme.of(context).cardColor,
                  contentPadding: const EdgeInsets.symmetric(vertical: 0),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(22),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
            ),
            if (_query.isNotEmpty)
              Padding(
                padding: const EdgeInsets.fromLTRB(18, 4, 18, 8),
                child: Text(
                  '搜索结果  ${_results.length}',
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            Expanded(
              child: _loading && _results.isEmpty
                  ? const Center(child: CircularProgressIndicator())
                  : _results.isEmpty
                  ? Center(
                      child: Text(_query.isEmpty ? '输入关键词搜索已导入的站源' : '暂无搜索结果'),
                    )
                  : ListView.separated(
                      controller: _scrollController,
                      padding: const EdgeInsets.fromLTRB(14, 4, 14, 16),
                      itemCount: _results.length + (_loadingMore ? 1 : 0),
                      separatorBuilder: (_, _) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        if (index == _results.length) {
                          return const Padding(
                            padding: EdgeInsets.all(18),
                            child: Center(child: CircularProgressIndicator()),
                          );
                        }
                        final item = _results[index];
                        return ListTile(
                          key: Key('search-result-${item.id}'),
                          onTap: () => _open(item),
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 4,
                            vertical: 6,
                          ),
                          leading: Container(
                            width: 54,
                            height: 72,
                            decoration: BoxDecoration(
                              color: Color(item.color),
                              borderRadius: BorderRadius.circular(7),
                            ),
                            child: const Icon(
                              Icons.movie_filter_outlined,
                              color: Colors.white70,
                            ),
                          ),
                          title: Text(item.title),
                          subtitle: Padding(
                            padding: const EdgeInsets.only(top: 7),
                            child: Text(
                              '${item.year} · ${item.category} · ${item.note}',
                              style: TextStyle(
                                color: Theme.of(
                                  context,
                                ).colorScheme.onSurface.withValues(alpha: 0.55),
                              ),
                            ),
                          ),
                          trailing: Text(
                            item.rating,
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.primary,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }

  void _onQueryChanged(String value) {
    _debounce?.cancel();
    final requestId = ++_requestId;
    setState(() {
      _query = value;
      _page = 0;
      _hasMore = true;
      _loadingMore = false;
      _results = const [];
    });
    _debounce = Timer(
      const Duration(milliseconds: 350),
      () => _search(value, requestId: requestId),
    );
  }

  void _onScroll() {
    if (_scrollController.position.extentAfter > 320 ||
        _query.trim().isEmpty ||
        _loading ||
        _loadingMore ||
        !_hasMore) {
      return;
    }
    final requestId = ++_requestId;
    unawaited(_search(_query, append: true, requestId: requestId));
  }

  Future<void> _search(
    String query, {
    bool append = false,
    required int requestId,
  }) async {
    if (query.trim().isEmpty) {
      if (mounted) {
        setState(() {
          _results = const [];
          _loading = false;
          _loadingMore = false;
          _page = 0;
          _hasMore = false;
        });
      }
      return;
    }
    final page = append ? _page + 1 : 1;
    setState(() {
      if (append) {
        _loadingMore = true;
      } else {
        _loading = true;
      }
    });
    try {
      final results = await FBoxScope.of(context).search(query, page: page);
      if (mounted && requestId == _requestId) {
        setState(() {
          var addedResults = results.isNotEmpty;
          if (append) {
            final unique = {for (final item in _results) item.storageId: item};
            final previousCount = unique.length;
            for (final item in results) {
              unique[item.storageId] = item;
            }
            addedResults = unique.length > previousCount;
            _results = unique.values.toList(growable: false);
          } else {
            _results = results;
          }
          _page = page;
          _hasMore = addedResults;
        });
      }
    } catch (error) {
      if (mounted && requestId == _requestId) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('搜索失败：$error')));
      }
    } finally {
      if (mounted && requestId == _requestId) {
        setState(() {
          _loading = false;
          _loadingMore = false;
        });
      }
    }
  }
}
