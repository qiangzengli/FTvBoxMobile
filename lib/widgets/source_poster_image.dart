import 'dart:typed_data';

import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/services/network_service.dart';
import 'package:flutter/material.dart';

/// Loads poster images through the same proxy and DNS settings as source data.
class SourcePosterImage extends StatelessWidget {
  const SourcePosterImage({
    required this.url,
    required this.fallback,
    this.headers = const {},
    this.fit = BoxFit.cover,
    super.key,
  });

  final String url;
  final Map<String, String> headers;
  final BoxFit fit;
  final Widget fallback;

  static final Map<String, Future<Uint8List>> _pending = {};

  static Future<Uint8List> _load(
    NetworkService network,
    String url,
    Map<String, String> headers,
  ) {
    final key =
        '$url|${headers.entries.map((e) => '${e.key}:${e.value}').join('|')}';
    final existing = _pending[key];
    if (existing != null) return existing;
    if (_pending.length >= 128) _pending.remove(_pending.keys.first);
    final request = network.getBytes(Uri.parse(url), headers: headers).then((
      response,
    ) {
      if (response.bytes.isEmpty) throw const FormatException('封面数据为空');
      return Uint8List.fromList(response.bytes);
    });
    _pending[key] = request;
    request.catchError((Object _) {
      if (identical(_pending[key], request)) _pending.remove(key);
      return Uint8List(0);
    });
    return request;
  }

  @override
  Widget build(BuildContext context) {
    if (url.trim().isEmpty) return fallback;
    final normalized = url.startsWith('//') ? 'https:$url' : url;
    final network = FBoxScope.maybeOf(context)?.network;
    if (network == null) {
      return Image.network(
        normalized,
        fit: fit,
        headers: headers,
        errorBuilder: (_, _, _) => fallback,
      );
    }
    return FutureBuilder<Uint8List>(
      future: _load(network, normalized, headers),
      builder: (context, snapshot) =>
          snapshot.hasData && snapshot.data!.isNotEmpty
          ? Image.memory(
              snapshot.data!,
              fit: fit,
              errorBuilder: (_, _, _) => fallback,
            )
          : fallback,
    );
  }
}
