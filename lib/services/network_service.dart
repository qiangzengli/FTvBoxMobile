import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:http/io_client.dart';

class NetworkSettings {
  const NetworkSettings({
    this.proxy = '',
    this.dohEndpoint = '',
    this.userAgent = 'okhttp/3.15',
    this.timeout = const Duration(seconds: 20),
  });

  final String proxy;
  final String dohEndpoint;
  final String userAgent;
  final Duration timeout;
}

class NetworkService {
  NetworkService({NetworkSettings initialSettings = const NetworkSettings()})
    : _settings = initialSettings;

  NetworkSettings _settings;

  void updateSettings(NetworkSettings value) {
    _settings = value;
  }

  Future<String> getText(
    Uri uri, {
    Map<String, String> headers = const {},
    Duration? timeout,
  }) async {
    final response = await getBytes(uri, headers: headers, timeout: timeout);
    return _decode(response.bytes, response.headers['content-type']);
  }

  Future<String> getDirectText(
    Uri uri, {
    Map<String, String> headers = const {},
    Duration? timeout,
  }) async {
    final response = await getBytes(
      uri,
      headers: headers,
      timeout: timeout,
      bypassProxy: true,
    );
    return _decode(response.bytes, response.headers['content-type']);
  }

  Future<String> getPublicText(
    Uri uri, {
    Map<String, String> headers = const {},
    Duration? timeout,
  }) async {
    if (!{'http', 'https'}.contains(uri.scheme) || uri.host.isEmpty) {
      throw ArgumentError.value(uri, 'uri', '只支持具有主机名的 HTTP(S) 地址');
    }
    final client = _createPublicClient();
    try {
      final response = await client
          .get(
            uri,
            headers: {
              HttpHeaders.userAgentHeader: _settings.userAgent,
              HttpHeaders.acceptHeader: '*/*',
              ...headers,
            },
          )
          .timeout(timeout ?? _settings.timeout);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw HttpException(
          'HTTP ${response.statusCode}: ${response.reasonPhrase ?? uri}',
          uri: uri,
        );
      }
      return _decode(response.bodyBytes, response.headers['content-type']);
    } finally {
      client.close();
    }
  }

  Future<NetworkResponse> getBytes(
    Uri uri, {
    Map<String, String> headers = const {},
    Duration? timeout,
    bool bypassProxy = false,
  }) async {
    final client = _createClient(forceDirect: bypassProxy);
    try {
      final response = await client
          .get(
            uri,
            headers: {
              HttpHeaders.userAgentHeader: _settings.userAgent,
              HttpHeaders.acceptHeader: '*/*',
              ...headers,
            },
          )
          .timeout(timeout ?? _settings.timeout);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw HttpException(
          'HTTP ${response.statusCode}: ${response.reasonPhrase ?? uri}',
          uri: uri,
        );
      }
      return NetworkResponse(
        bytes: response.bodyBytes,
        headers: response.headers,
        statusCode: response.statusCode,
      );
    } finally {
      client.close();
    }
  }

  Future<String> postText(
    Uri uri, {
    required String body,
    Map<String, String> headers = const {},
    Duration? timeout,
    bool bypassProxy = false,
  }) async {
    final client = _createClient(forceDirect: bypassProxy);
    try {
      final response = await client
          .post(
            uri,
            body: body,
            headers: {
              HttpHeaders.userAgentHeader: _settings.userAgent,
              ...headers,
            },
          )
          .timeout(timeout ?? _settings.timeout);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw HttpException(
          'HTTP ${response.statusCode}: ${response.reasonPhrase ?? uri}',
          uri: uri,
        );
      }
      return _decode(response.bodyBytes, response.headers['content-type']);
    } finally {
      client.close();
    }
  }

  Future<StreamedNetworkResponse> stream(
    Uri uri, {
    Map<String, String> headers = const {},
    Duration? timeout,
  }) async {
    final client = _createClient();
    final outbound = http.Request('GET', uri)..headers.addAll(headers);
    outbound.headers.putIfAbsent(
      HttpHeaders.userAgentHeader,
      () => _settings.userAgent,
    );
    late final http.StreamedResponse request;
    try {
      request = await client
          .send(outbound)
          .timeout(timeout ?? _settings.timeout);
    } catch (_) {
      client.close();
      rethrow;
    }
    if (request.statusCode < 200 || request.statusCode >= 300) {
      client.close();
      throw HttpException('HTTP ${request.statusCode}', uri: uri);
    }
    return StreamedNetworkResponse(
      stream: request.stream,
      contentLength: request.contentLength,
      headers: request.headers,
      close: client.close,
    );
  }

  Future<List<InternetAddress>> resolveHost(
    String host, {
    String? dohEndpoint,
  }) async {
    final configuredEndpoint = dohEndpoint ?? _settings.dohEndpoint;
    if (configuredEndpoint.isEmpty) return InternetAddress.lookup(host);
    final endpoint = Uri.parse(configuredEndpoint);
    final queryUri = endpoint.replace(
      queryParameters: {...endpoint.queryParameters, 'name': host, 'type': 'A'},
    );
    final client = _createClient(enableDoh: false);
    late final http.Response response;
    try {
      response = await client
          .get(queryUri, headers: const {'accept': 'application/dns-json'})
          .timeout(_settings.timeout);
    } finally {
      client.close();
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw HttpException('DoH HTTP ${response.statusCode}', uri: queryUri);
    }
    final body = _decode(response.bodyBytes, response.headers['content-type']);
    final decoded = jsonDecode(body);
    if (decoded is! Map) return InternetAddress.lookup(host);
    final answers = decoded['Answer'];
    if (answers is! List) return InternetAddress.lookup(host);
    final addresses = answers
        .whereType<Map>()
        .map((answer) => answer['data']?.toString() ?? '')
        .where((value) => InternetAddress.tryParse(value) != null)
        .map(InternetAddress.new)
        .toList();
    return addresses.isEmpty ? InternetAddress.lookup(host) : addresses;
  }

  IOClient createClientForTesting() => _createClient();

  IOClient _createClient({bool enableDoh = true, bool forceDirect = false}) {
    final inner = HttpClient()
      ..connectionTimeout = _settings.timeout
      ..idleTimeout = const Duration(seconds: 15)
      ..badCertificateCallback = (cert, host, port) => true;
    final proxy = forceDirect ? '' : _settings.proxy.trim();
    if (proxy.isNotEmpty) {
      final normalized = proxy.contains('://') ? proxy : 'http://$proxy';
      final uri = Uri.tryParse(normalized);
      if (uri != null && uri.host.isNotEmpty) {
        inner.findProxy = (_) =>
            'PROXY ${uri.host}:${uri.port == 0 ? 8080 : uri.port}';
        if (uri.userInfo.isNotEmpty) {
          final pair = uri.userInfo.split(':');
          inner.addProxyCredentials(
            uri.host,
            uri.port == 0 ? 8080 : uri.port,
            '',
            HttpClientBasicCredentials(
              Uri.decodeComponent(pair.first),
              pair.length > 1
                  ? Uri.decodeComponent(pair.sublist(1).join(':'))
                  : '',
            ),
          );
        }
      }
    } else {
      inner.findProxy = (_) => 'DIRECT';
    }
    final dohEndpoint = _settings.dohEndpoint.trim();
    if (enableDoh && dohEndpoint.isNotEmpty) {
      final dohHost = Uri.tryParse(dohEndpoint)?.host ?? '';
      inner.connectionFactory = (uri, proxyHost, proxyPort) async {
        final host = proxyHost ?? uri.host;
        final port =
            proxyPort ??
            (uri.hasPort ? uri.port : (uri.scheme == 'https' ? 443 : 80));
        if (proxyHost != null) {
          return _startConnection(host, port);
        }
        Object target = host;
        if (host != dohHost && InternetAddress.tryParse(host) == null) {
          final addresses = await resolveHost(host, dohEndpoint: dohEndpoint);
          if (addresses.isEmpty) {
            throw SocketException('DoH 未返回 $host 的地址');
          }
          target = addresses.first;
        }
        return uri.scheme == 'https'
            ? _startSecureConnection(target, port, uri.host)
            : _startConnection(target, port);
      };
    }
    return IOClient(inner);
  }

  IOClient _createPublicClient() {
    final inner = HttpClient()
      ..connectionTimeout = _settings.timeout
      ..idleTimeout = const Duration(seconds: 15)
      ..findProxy = (_) => 'DIRECT';
    inner.badCertificateCallback = (cert, host, port) => true;
    inner.connectionFactory = (uri, proxyHost, proxyPort) async {
      if (proxyHost != null) {
        throw const SocketException('公网限定请求不允许通过代理转发');
      }
      final port = uri.hasPort ? uri.port : (uri.scheme == 'https' ? 443 : 80);
      final literal = InternetAddress.tryParse(uri.host);
      final addresses = literal == null
          ? await resolveHost(uri.host)
          : <InternetAddress>[literal];
      if (addresses.isEmpty ||
          addresses.any((address) => !_isPublicAddress(address))) {
        throw SocketException('拒绝连接非公网地址：${uri.host}');
      }
      final target = addresses.first;
      return uri.scheme == 'https'
          ? _startSecureConnection(target, port, uri.host)
          : _startConnection(target, port);
    };
    return IOClient(inner);
  }

  static bool _isPublicAddress(InternetAddress address) {
    final bytes = address.rawAddress;
    if (address.type == InternetAddressType.IPv4) {
      return _isPublicIpv4(bytes);
    }
    final isIpv4Mapped =
        bytes.length == 16 &&
        bytes.take(10).every((value) => value == 0) &&
        bytes[10] == 0xFF &&
        bytes[11] == 0xFF;
    if (isIpv4Mapped) return _isPublicIpv4(bytes.sublist(12));
    final isLoopback =
        bytes.take(15).every((value) => value == 0) && bytes.last == 1;
    final isPrivate = (bytes[0] & 0xFE) == 0xFC;
    final isLinkLocal = bytes[0] == 0xFE && (bytes[1] & 0xC0) == 0x80;
    final isMulticast = bytes[0] == 0xFF;
    final isUnspecified = bytes.every((value) => value == 0);
    final isDocumentation =
        bytes[0] == 0x20 &&
        bytes[1] == 0x01 &&
        bytes[2] == 0x0D &&
        bytes[3] == 0xB8;
    return !isLoopback &&
        !isPrivate &&
        !isLinkLocal &&
        !isMulticast &&
        !isUnspecified &&
        !isDocumentation;
  }

  static bool _isPublicIpv4(List<int> bytes) {
    final first = bytes[0];
    final second = bytes[1];
    final third = bytes[2];
    return first != 0 &&
        first != 10 &&
        first != 127 &&
        first < 224 &&
        !(first == 100 && second >= 64 && second <= 127) &&
        !(first == 169 && second == 254) &&
        !(first == 172 && second >= 16 && second <= 31) &&
        !(first == 192 && second == 168) &&
        !(first == 192 && second == 0 && third == 0) &&
        !(first == 192 && second == 0 && third == 2) &&
        !(first == 198 && (second == 18 || second == 19)) &&
        !(first == 198 && second == 51 && third == 100) &&
        !(first == 203 && second == 0 && third == 113);
  }

  Future<ConnectionTask<Socket>> _startConnection(Object host, int port) async {
    final task = await Socket.startConnect(host, port);
    final socket = task.socket.timeout(
      _settings.timeout,
      onTimeout: () {
        task.cancel();
        throw TimeoutException('连接 $host:$port 超时', _settings.timeout);
      },
    );
    return ConnectionTask.fromSocket(socket, task.cancel);
  }

  Future<ConnectionTask<Socket>> _startSecureConnection(
    Object target,
    int port,
    String certificateHost,
  ) async {
    final task = await Socket.startConnect(target, port);
    Socket? rawSocket;
    Future<Socket> connect() async {
      final socket = await task.socket.timeout(
        _settings.timeout,
        onTimeout: () {
          task.cancel();
          throw TimeoutException('连接 $target:$port 超时', _settings.timeout);
        },
      );
      rawSocket = socket;
      try {
        return await SecureSocket.secure(
          socket,
          host: certificateHost,
          onBadCertificate: (_) => true,
        ).timeout(
          _settings.timeout,
          onTimeout: () {
            socket.destroy();
            throw TimeoutException(
              'TLS 握手 $certificateHost:$port 超时',
              _settings.timeout,
            );
          },
        );
      } catch (_) {
        socket.destroy();
        rethrow;
      }
    }

    return ConnectionTask.fromSocket(connect(), () {
      rawSocket?.destroy();
      task.cancel();
    });
  }

  static String _decode(List<int> bytes, String? contentType) {
    final charset = RegExp(
      r'charset\s*=\s*([^;]+)',
      caseSensitive: false,
    ).firstMatch(contentType ?? '')?.group(1)?.trim().toLowerCase();
    if (charset == 'latin1' || charset == 'iso-8859-1') {
      return latin1.decode(bytes);
    }
    return utf8.decode(bytes, allowMalformed: true);
  }
}

class NetworkResponse {
  const NetworkResponse({
    required this.bytes,
    required this.headers,
    required this.statusCode,
  });

  final List<int> bytes;
  final Map<String, String> headers;
  final int statusCode;
}

class StreamedNetworkResponse {
  const StreamedNetworkResponse({
    required this.stream,
    required this.contentLength,
    required this.headers,
    required this.close,
  });

  final Stream<List<int>> stream;
  final int? contentLength;
  final Map<String, String> headers;
  final void Function() close;
}
