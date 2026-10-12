import 'dart:async';
import 'dart:io';

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/domain/media_item.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:fbox/features/player/player_page.dart';
import 'package:fbox/features/player/web_parser_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class DetailPage extends StatefulWidget {
  const DetailPage({required this.item, super.key});

  final MediaItem item;

  @override
  State<DetailPage> createState() => _DetailPageState();
}

class _DetailPageState extends State<DetailPage> {
  late MediaItem _item = widget.item;
  int _sourceIndex = 0;
  int _episode = 1;
  int _episodePage = 0;
  bool _detailRequested = false;
  bool _loading = false;

  MediaItem get item => _item;

  List<MediaPlayLine> get _lines {
    if (item.playLines.isNotEmpty) return item.playLines;
    return [
      for (final name in const ['高清线路', '备用线路'])
        MediaPlayLine(
          name: name,
          episodes: [
            for (var index = 1; index <= item.episodes; index++)
              MediaEpisode(name: '$index', url: ''),
          ],
        ),
    ];
  }

  MediaPlayLine get _selectedLine =>
      _lines[_sourceIndex.clamp(0, _lines.length - 1)];

  MediaEpisode get _selectedEpisode {
    final episodes = _selectedLine.episodes;
    if (episodes.isEmpty) return const MediaEpisode(name: '1', url: '');
    return episodes[(_episode - 1).clamp(0, episodes.length - 1)];
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_detailRequested || item.sourceKey.isEmpty) return;
    _detailRequested = true;
    unawaited(_loadDetail());
  }

  Future<void> _loadDetail() async {
    setState(() => _loading = true);
    try {
      final detail = await FBoxScope.of(context).loadDetail(item);
      if (!mounted) return;
      setState(() {
        _item = detail;
        _sourceIndex = 0;
        _episode = 1;
        _episodePage = 0;
      });
    } catch (error) {
      if (mounted) _showMessage('详情加载失败：$error');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _play({ParseSource? parser}) async {
    final store = FBoxScope.of(context);
    store.recordWatch(item);
    final episode = _selectedEpisode;
    PlaybackRequest? playback;
    if (episode.url.isNotEmpty) {
      setState(() => _loading = true);
      try {
        playback = await _resolveSelectedPlayback(parser: parser);
        if (playback == null) return;
      } catch (error) {
        if (mounted) _showMessage('播放地址解析失败：$error');
        return;
      } finally {
        if (mounted) setState(() => _loading = false);
      }
    }
    if (!mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PlayerPage(
          item: item,
          episode: _episode,
          episodeName: item.playLines.isEmpty ? null : episode.name,
          source: _selectedLine.name,
          playback: playback,
          playLines: _lines,
          selectedLineIndex: _sourceIndex,
        ),
      ),
    );
  }

  Future<PlaybackRequest?> _resolveSelectedPlayback({
    ParseSource? parser,
  }) async {
    final store = FBoxScope.of(context);
    final episode = _selectedEpisode;
    var playback = await store.resolvePlayback(
      item: item,
      episode: episode,
      line: _selectedLine.name,
      parser: parser?.type == 0 ? null : parser,
    );
    ParseSource? selectedParser = parser;
    if (selectedParser == null && playback.parseRequired) {
      final compatible = store.parsers
          .where((value) => value.type == 0 || value.type == 1)
          .toList();
      if (compatible.isEmpty) {
        throw const FormatException('该地址需要解析，但当前配置没有可用解析线路');
      }
      selectedParser = compatible.first;
    }
    if (selectedParser?.type == 0) {
      if (!mounted) return null;
      return Navigator.of(context).push<PlaybackRequest>(
        MaterialPageRoute<PlaybackRequest>(
          builder: (_) =>
              WebParserPage(parser: selectedParser!, targetUrl: playback.url),
        ),
      );
    }
    if (selectedParser != null && parser == null && playback.parseRequired) {
      playback = await store.resolvePlayback(
        item: item,
        episode: episode,
        line: _selectedLine.name,
        parser: selectedParser,
      );
    }
    if (playback.parseRequired) {
      throw const FormatException('解析线路未返回可直接播放的地址');
    }
    return playback;
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
            onSelected: _handleMenu,
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'share', child: Text('分享')),
              PopupMenuItem(value: 'copy', child: Text('复制片名')),
              PopupMenuItem(value: 'feedback', child: Text('反馈片源')),
            ],
          ),
        ],
      ),
      body: Stack(
        children: [
          ListView(
            padding: const EdgeInsets.fromLTRB(14, 4, 14, 28),
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _DetailPoster(item: item),
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
                        if (item.rating.isNotEmpty)
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
                      onPressed: _play,
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
                      onPressed: _cast,
                      icon: const Icon(Icons.cast_rounded, size: 18),
                      label: const Text('投屏'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _download,
                      icon: const Icon(Icons.download_outlined, size: 18),
                      label: const Text('下载'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _chooseParser,
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
                    onPressed: _lines.length < 2
                        ? null
                        : () => setState(() {
                            _sourceIndex = (_sourceIndex + 1) % _lines.length;
                            _episode = 1;
                            _episodePage = 0;
                          }),
                    label: const Text('快捷换线'),
                    icon: const Icon(Icons.swap_horiz, size: 18),
                  ),
                ],
              ),
              Wrap(
                spacing: 8,
                children: [
                  for (var index = 0; index < _lines.length; index++)
                    ChoiceChip(
                      key: Key('detail-source-$index'),
                      label: Text(_lines[index].name),
                      selected: _sourceIndex == index,
                      onSelected: (_) => setState(() {
                        _sourceIndex = index;
                        _episode = 1;
                        _episodePage = 0;
                      }),
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
                  Text('${_selectedLine.episodes.length} 集'),
                ],
              ),
              const SizedBox(height: 10),
              if (_selectedLine.episodes.length > 30) ...[
                SizedBox(
                  height: 34,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    itemCount: (_selectedLine.episodes.length + 29) ~/ 30,
                    separatorBuilder: (_, _) => const SizedBox(width: 8),
                    itemBuilder: (context, index) {
                      final start = index * 30 + 1;
                      final end = (start + 29).clamp(
                        1,
                        _selectedLine.episodes.length,
                      );
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
                itemCount: (_selectedLine.episodes.length - _episodePage * 30)
                    .clamp(0, 30),
                itemBuilder: (context, index) {
                  final episode = _episodePage * 30 + index + 1;
                  final episodeData = _selectedLine.episodes[episode - 1];
                  return OutlinedButton(
                    key: Key('detail-episode-$episode'),
                    onPressed: () {
                      setState(() => _episode = episode);
                      unawaited(_play());
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
                    child: Text(
                      episodeData.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  );
                },
              ),
            ],
          ),
          if (_loading)
            const Positioned.fill(
              child: ColoredBox(
                color: Color(0x33000000),
                child: Center(child: CircularProgressIndicator()),
              ),
            ),
        ],
      ),
    );
  }

  Future<void> _download() async {
    final episode = _selectedEpisode;
    if (episode.url.isEmpty) {
      _showMessage('演示内容没有可下载的媒体地址');
      return;
    }
    final store = FBoxScope.of(context);
    setState(() => _loading = true);
    try {
      final playback = await _resolveSelectedPlayback();
      if (playback == null) return;
      final resolved = MediaEpisode(
        name: episode.name,
        url: playback.url,
        headers: playback.headers,
      );
      unawaited(
        store.downloads
            .start(item: item, episode: resolved)
            .then<void>((_) {})
            .catchError((Object error) {
              if (mounted) _showMessage('下载失败：$error');
            }),
      );
      _showMessage('已加入下载：${episode.name}');
    } catch (error) {
      _showMessage('无法开始下载：$error');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _chooseParser() async {
    final store = FBoxScope.of(context);
    if (_selectedEpisode.url.isEmpty) {
      _showMessage('演示内容不需要解析');
      return;
    }
    final parser = await showModalBottomSheet<ParseSource?>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            const ListTile(
              title: Text(
                '选择解析线路',
                style: TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
            ListTile(
              title: const Text('站源自动解析'),
              onTap: () => Navigator.pop(context),
            ),
            for (final parser in store.parsers)
              ListTile(
                title: Text(parser.name),
                subtitle: Text(
                  parser.type == 0 || parser.type == 1
                      ? parser.url
                      : '${parser.url} · APK Android 专用类型 ${parser.type}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                onTap: () => Navigator.pop(context, parser),
              ),
          ],
        ),
      ),
    );
    if (mounted) await _play(parser: parser);
  }

  Future<void> _cast() async {
    final episode = _selectedEpisode;
    if (episode.url.isEmpty) {
      _showMessage('演示内容没有可投屏的媒体地址');
      return;
    }
    final store = FBoxScope.of(context);
    setState(() => _loading = true);
    try {
      final playback = await _resolveSelectedPlayback();
      if (playback == null) return;
      if (Platform.isIOS) {
        if (!mounted) return;
        await Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => PlayerPage(
              item: item,
              episode: _episode,
              episodeName: episode.name,
              source: _selectedLine.name,
              playback: playback,
              playLines: _lines,
              selectedLineIndex: _sourceIndex,
              autoOpenCastPicker: true,
            ),
          ),
        );
        return;
      }
      final devices = await store.cast.discoverDlna();
      if (!mounted) return;
      if (devices.isEmpty) {
        _showMessage('局域网中未发现 DLNA 设备');
        return;
      }
      final device = await showModalBottomSheet(
        context: context,
        showDragHandle: true,
        builder: (context) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              const ListTile(title: Text('投屏设备')),
              for (final device in devices)
                ListTile(
                  leading: const Icon(Icons.tv),
                  title: Text(device.name),
                  onTap: () => Navigator.pop(context, device),
                ),
            ],
          ),
        ),
      );
      if (device != null) {
        await store.cast.playDlna(
          device: device,
          mediaUrl: playback.url,
          title: '${item.title} ${episode.name}',
        );
        if (mounted) _showMessage('已开始投屏到 ${device.name}');
      }
    } catch (error) {
      if (mounted) _showMessage('投屏失败：$error');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _handleMenu(String action) {
    switch (action) {
      case 'copy':
        Clipboard.setData(ClipboardData(text: item.title));
        _showMessage('片名已复制');
      case 'share':
        Clipboard.setData(
          ClipboardData(text: '${item.title}\n${item.description}'),
        );
        _showMessage('影片信息已复制，可粘贴分享');
      case 'feedback':
        _showMessage(
          '当前站源：${item.sourceKey.isEmpty ? '本地演示' : item.sourceKey}',
        );
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
}

class _DetailPoster extends StatelessWidget {
  const _DetailPoster({required this.item});

  final MediaItem item;

  @override
  Widget build(BuildContext context) {
    final fallback = DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color(item.color),
            Color(item.color).withValues(alpha: 0.62),
          ],
        ),
      ),
      child: const Center(
        child: Icon(
          Icons.movie_filter_outlined,
          color: Colors.white54,
          size: 48,
        ),
      ),
    );
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: SizedBox(
        width: 124,
        height: 174,
        child: item.posterUrl.isEmpty
            ? fallback
            : Image.network(
                item.posterUrl,
                fit: BoxFit.cover,
                headers: item.headers,
                errorBuilder: (_, _, _) => fallback,
              ),
      ),
    );
  }
}
