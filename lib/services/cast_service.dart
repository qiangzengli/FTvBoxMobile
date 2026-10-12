import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:fbox/services/network_service.dart';
import 'package:flutter/services.dart';
import 'package:xml/xml.dart';

class CastDevice {
  const CastDevice({
    required this.name,
    required this.location,
    required this.controlUrl,
  });

  final String name;
  final Uri location;
  final Uri controlUrl;
}

class CastService {
  CastService(this.network);

  final NetworkService network;
  static const _platform = MethodChannel('com.fbox.app/platform');

  Future<List<CastDevice>> discoverDlna({
    Duration timeout = const Duration(seconds: 3),
  }) async {
    final socket = await RawDatagramSocket.bind(InternetAddress.anyIPv4, 0);
    final locations = <String>{};
    final completer = Completer<void>();
    late StreamSubscription<RawSocketEvent> subscription;
    subscription = socket.listen((event) {
      if (event != RawSocketEvent.read) return;
      Datagram? packet;
      while ((packet = socket.receive()) != null) {
        final response = utf8.decode(packet!.data, allowMalformed: true);
        final match = RegExp(
          r'^location:\s*(.+)$',
          caseSensitive: false,
          multiLine: true,
        ).firstMatch(response);
        if (match != null) locations.add(match.group(1)!.trim());
      }
    });
    final message = utf8.encode(
      'M-SEARCH * HTTP/1.1\r\n'
      'HOST: 239.255.255.250:1900\r\n'
      'MAN: "ssdp:discover"\r\n'
      'MX: 2\r\n'
      'ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n',
    );
    socket.send(message, InternetAddress('239.255.255.250'), 1900);
    Timer(timeout, completer.complete);
    await completer.future;
    await subscription.cancel();
    socket.close();

    final devices = <CastDevice>[];
    for (final value in locations) {
      try {
        final location = Uri.parse(value);
        final body = await network.getText(location);
        final document = XmlDocument.parse(body);
        final name = document
            .findAllElements('friendlyName')
            .firstOrNull
            ?.innerText
            .trim();
        Uri? controlUrl;
        for (final service in document.findAllElements('service')) {
          final type = service
              .findElements('serviceType')
              .firstOrNull
              ?.innerText;
          if (type?.contains('AVTransport') != true) continue;
          final control = service
              .findElements('controlURL')
              .firstOrNull
              ?.innerText
              .trim();
          if (control != null && control.isNotEmpty) {
            controlUrl = location.resolve(control);
            break;
          }
        }
        if (controlUrl != null) {
          devices.add(
            CastDevice(
              name: name?.isNotEmpty == true ? name! : location.host,
              location: location,
              controlUrl: controlUrl,
            ),
          );
        }
      } on Object {
        // Ignore devices that disappear while discovery is in progress.
      }
    }
    return devices;
  }

  Future<void> playDlna({
    required CastDevice device,
    required String mediaUrl,
    required String title,
  }) async {
    final escapedUrl = const HtmlEscape(HtmlEscapeMode.element)
        .convert(mediaUrl);
    final escapedTitle = const HtmlEscape(HtmlEscapeMode.element)
        .convert(title);
    final metadata = const HtmlEscape(HtmlEscapeMode.element).convert(
      '<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" '
      'xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" '
      'xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">'
      '<item id="0" parentID="0" restricted="1">'
      '<dc:title>$escapedTitle</dc:title>'
      '<upnp:class>object.item.videoItem</upnp:class>'
      '<res>$escapedUrl</res></item></DIDL-Lite>',
    );
    await _soap(
      device,
      'SetAVTransportURI',
      '<InstanceID>0</InstanceID>'
          '<CurrentURI>$escapedUrl</CurrentURI>'
          '<CurrentURIMetaData>$metadata</CurrentURIMetaData>',
    );
    await _soap(device, 'Play', '<InstanceID>0</InstanceID><Speed>1</Speed>');
  }

  Future<void> pauseDlna(CastDevice device) =>
      _soap(device, 'Pause', '<InstanceID>0</InstanceID>');

  Future<void> stopDlna(CastDevice device) =>
      _soap(device, 'Stop', '<InstanceID>0</InstanceID>');

  Future<void> showAirPlayPicker() async {
    await _platform.invokeMethod<void>('showAirPlayPicker');
  }

  Future<void> _soap(CastDevice device, String action, String body) async {
    const namespace = 'urn:schemas-upnp-org:service:AVTransport:1';
    await network.postText(
      device.controlUrl,
      body:
          '<?xml version="1.0" encoding="utf-8"?>'
          '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" '
          's:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">'
          '<s:Body><u:$action xmlns:u="$namespace">$body</u:$action></s:Body>'
          '</s:Envelope>',
      headers: {
        HttpHeaders.contentTypeHeader: 'text/xml; charset="utf-8"',
        'SOAPACTION': '"$namespace#$action"',
      },
    );
  }
}
