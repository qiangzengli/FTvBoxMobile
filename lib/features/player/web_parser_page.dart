import 'dart:async';
import 'dart:convert';

import 'package:fbox/domain/source_models.dart';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

class WebParserPage extends StatefulWidget {
  const WebParserPage({
    required this.parser,
    required this.targetUrl,
    super.key,
  });

  final ParseSource parser;
  final String targetUrl;

  @override
  State<WebParserPage> createState() => _WebParserPageState();
}

class _WebParserPageState extends State<WebParserPage> {
  late final WebViewController _controller;
  late final String _parseUrl;
  Timer? _probeTimer;
  int _progress = 0;
  String _status = '正在打开解析页面…';
  bool _completed = false;

  @override
  void initState() {
    super.initState();
    final parserUrl = widget.parser.requestUrl;
    _parseUrl = parserUrl.contains('{url}')
        ? parserUrl.replaceAll('{url}', Uri.encodeComponent(widget.targetUrl))
        : '$parserUrl${Uri.encodeComponent(widget.targetUrl)}';
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(Colors.black)
      ..setNavigationDelegate(
        NavigationDelegate(
          onProgress: (value) => setState(() => _progress = value),
          onPageFinished: (_) {
            if (!mounted) return;
            setState(() => _status = '页面已加载，正在嗅探媒体地址…');
            _startProbe();
          },
          onWebResourceError: (error) {
            if (mounted && error.isForMainFrame == true) {
              setState(() => _status = '解析页面加载失败：${error.description}');
            }
          },
          onNavigationRequest: (request) {
            if (_isMediaUrl(request.url)) {
              _finish(request.url);
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
        ),
      )
      ..loadRequest(Uri.parse(_parseUrl), headers: widget.parser.ext);
  }

  void _startProbe() {
    _probeTimer ??= Timer.periodic(const Duration(milliseconds: 800), (_) {
      unawaited(_probe());
    });
  }

  Future<void> _probe() async {
    if (_completed) return;
    try {
      final result = await _controller.runJavaScriptReturningResult('''
        (() => {
          const urls = [];
          document.querySelectorAll('video, source').forEach((node) => {
            if (node.src) urls.push(node.src);
          });
          performance.getEntriesByType('resource').forEach((entry) => {
            if (entry.name) urls.push(entry.name);
          });
          return JSON.stringify([...new Set(urls)]);
        })()
      ''');
      var value = result.toString();
      if (value.startsWith('"')) value = jsonDecode(value) as String;
      final decoded = jsonDecode(value);
      if (decoded is! List) return;
      for (final candidate in decoded.map((entry) => entry.toString())) {
        if (_isMediaUrl(candidate)) {
          _finish(candidate);
          return;
        }
      }
    } on Object {
      // A page can temporarily reject JS while navigating; the next probe retries.
    }
  }

  bool _isMediaUrl(String value) {
    final lower = value.toLowerCase();
    if (!lower.startsWith('http://') && !lower.startsWith('https://')) {
      return false;
    }
    return lower.contains('.m3u8') ||
        lower.contains('.mp4') ||
        lower.contains('.flv') ||
        lower.contains('.mpd') ||
        lower.contains('mime=video') ||
        lower.contains('content-type=video');
  }

  void _finish(String url) {
    if (_completed || !mounted) return;
    _completed = true;
    _probeTimer?.cancel();
    Navigator.pop(
      context,
      PlaybackRequest(
        url: url,
        headers: {'Referer': _parseUrl, ...widget.parser.ext},
      ),
    );
  }

  @override
  void dispose() {
    _probeTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.parser.name)),
      body: Column(
        children: [
          if (_progress < 100) LinearProgressIndicator(value: _progress / 100),
          Material(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: ListTile(
              dense: true,
              leading: const Icon(Icons.radar_rounded),
              title: Text(_status),
              subtitle: const Text('检测到可播放的媒体请求后会自动返回播放器'),
            ),
          ),
          Expanded(child: WebViewWidget(controller: _controller)),
        ],
      ),
    );
  }
}
