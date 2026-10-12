import 'dart:convert';

import 'package:fbox/domain/source_models.dart';
import 'package:fbox/services/network_service.dart';

class LiveEpgService {
  const LiveEpgService(this.network);

  final NetworkService network;

  Future<List<LiveProgram>> load({
    required LiveSource source,
    required LiveChannel channel,
    DateTime? date,
  }) async {
    final template = source.epg.trim();
    if (template.isEmpty) return const [];
    final requestedDate = date ?? DateTime.now();
    final dateValue = _date(requestedDate);
    final channelValue = channel.epgId.isNotEmpty
        ? channel.epgId
        : channel.name;
    final hasNamePlaceholder = template.contains('{name}');
    final hasDatePlaceholder = template.contains('{date}');
    var location = template
        .replaceAll('{name}', Uri.encodeQueryComponent(channelValue))
        .replaceAll('{date}', Uri.encodeQueryComponent(dateValue));
    var uri = Uri.parse(location);
    if (!hasNamePlaceholder || !hasDatePlaceholder) {
      uri = uri.replace(
        queryParameters: {
          ...uri.queryParameters,
          if (!hasNamePlaceholder) 'ch': channelValue,
          if (!hasDatePlaceholder) 'date': dateValue,
        },
      );
      location = uri.toString();
    }
    final body = await network.getText(
      Uri.parse(location),
      headers: source.headers,
    );
    return _parse(body);
  }

  static List<LiveProgram> _parse(String input) {
    final decoded = jsonDecode(input);
    final rows = _programRows(decoded);
    return rows
        .whereType<Map>()
        .map((row) {
          final values = Map<Object?, Object?>.from(row);
          return LiveProgram(
            start: _value(values, const ['start', 'starttime', 'begin']),
            end: _value(values, const ['end', 'endtime', 'stop']),
            title: _value(values, const ['title', 'name', 'program']),
          );
        })
        .where((program) => program.title.isNotEmpty)
        .toList(growable: false);
  }

  static List<Object?> _programRows(Object? value) {
    if (value is List) return value;
    if (value is! Map) return const [];
    for (final key in const ['epg_data', 'data', 'list']) {
      final nested = value[key];
      final rows = _programRows(nested);
      if (rows.isNotEmpty) return rows;
    }
    return const [];
  }

  static String _value(Map<Object?, Object?> row, List<String> keys) {
    for (final key in keys) {
      final value = row[key]?.toString().trim() ?? '';
      if (value.isNotEmpty) return value;
    }
    return '';
  }

  static String _date(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-'
      '${value.month.toString().padLeft(2, '0')}-'
      '${value.day.toString().padLeft(2, '0')}';
}
