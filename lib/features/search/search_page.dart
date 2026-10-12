import 'package:fbox/data/demo_catalog.dart';
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
  String _query = '';

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _open(MediaItem item) {
    Navigator.of(context)
        .push(MaterialPageRoute<void>(builder: (_) => DetailPage(item: item)));
  }

  @override
  Widget build(BuildContext context) {
    final results = DemoCatalog.search(_query);
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
                onChanged: (value) => setState(() => _query = value),
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  hintText: '请输入影片名称',
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: _query.isEmpty
                      ? null
                      : IconButton(
                          onPressed: () {
                            _controller.clear();
                            setState(() => _query = '');
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
            if (_query.isEmpty) ...[
              const Padding(
                padding: EdgeInsets.fromLTRB(18, 8, 18, 10),
                child: Text(
                  '热门搜索',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 14),
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: ['庆余年', '繁花', '沙丘', '歌手', '凡人修仙传']
                      .map(
                        (word) => ActionChip(
                          label: Text(word),
                          backgroundColor: Theme.of(context).cardColor,
                          onPressed: () {
                            _controller.text = word;
                            _controller.selection = TextSelection.collapsed(
                              offset: word.length,
                            );
                            setState(() => _query = word);
                          },
                        ),
                      )
                      .toList(),
                ),
              ),
              const Padding(
                padding: EdgeInsets.fromLTRB(18, 20, 18, 8),
                child: Text(
                  '热门推荐',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                ),
              ),
            ] else
              Padding(
                padding: const EdgeInsets.fromLTRB(18, 4, 18, 8),
                child: Text(
                  '搜索结果  ${results.length}',
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            Expanded(
              child: results.isEmpty
                  ? const Center(child: Text('暂无搜索结果'))
                  : ListView.separated(
                      padding: const EdgeInsets.fromLTRB(14, 4, 14, 16),
                      itemCount: results.length,
                      separatorBuilder: (_, _) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        final item = results[index];
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
                                color: Theme.of(context).colorScheme.onSurface
                                    .withValues(alpha: 0.55),
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
}
