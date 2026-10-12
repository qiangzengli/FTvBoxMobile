import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

class FBoxStorage {
  FBoxStorage._(this._preferences);

  final SharedPreferences _preferences;

  static Future<FBoxStorage> open() async =>
      FBoxStorage._(await SharedPreferences.getInstance());

  String? readString(String key) => _preferences.getString(key);

  bool? readBool(String key) => _preferences.getBool(key);

  int? readInt(String key) => _preferences.getInt(key);

  double? readDouble(String key) => _preferences.getDouble(key);

  List<String> readStringList(String key) =>
      _preferences.getStringList(key) ?? const [];

  Map<String, dynamic>? readJsonObject(String key) {
    final value = readString(key);
    if (value == null || value.isEmpty) return null;
    try {
      final decoded = jsonDecode(value);
      return decoded is Map ? Map<String, dynamic>.from(decoded) : null;
    } on FormatException {
      return null;
    }
  }

  List<dynamic> readJsonList(String key) {
    final value = readString(key);
    if (value == null || value.isEmpty) return const [];
    try {
      final decoded = jsonDecode(value);
      return decoded is List ? decoded : const [];
    } on FormatException {
      return const [];
    }
  }

  Future<void> writeString(String key, String value) =>
      _preferences.setString(key, value);

  Future<void> writeBool(String key, bool value) =>
      _preferences.setBool(key, value);

  Future<void> writeInt(String key, int value) =>
      _preferences.setInt(key, value);

  Future<void> writeDouble(String key, double value) =>
      _preferences.setDouble(key, value);

  Future<void> writeStringList(String key, Iterable<String> values) =>
      _preferences.setStringList(key, values.toList());

  Future<void> writeJson(String key, Object? value) =>
      _preferences.setString(key, jsonEncode(value));

  Future<void> remove(String key) => _preferences.remove(key);
}
