import 'dart:collection';

import 'package:fbox/domain/media_item.dart';
import 'package:flutter/material.dart';

class FBoxStore extends ChangeNotifier {
  ThemeMode get themeMode => _themeMode;
  ThemeMode _themeMode = ThemeMode.system;

  UnmodifiableSetView<String> get favoriteIds =>
      UnmodifiableSetView(_favorites);
  final Set<String> _favorites = {};

  UnmodifiableListView<String> get historyIds => UnmodifiableListView(_history);
  final List<String> _history = [];

  bool isFavorite(MediaItem item) => _favorites.contains(item.id);

  void toggleFavorite(MediaItem item) {
    if (!_favorites.add(item.id)) _favorites.remove(item.id);
    notifyListeners();
  }

  void recordWatch(MediaItem item) {
    _history.remove(item.id);
    _history.insert(0, item.id);
    notifyListeners();
  }

  void setThemeMode(ThemeMode value) {
    if (_themeMode == value) return;
    _themeMode = value;
    notifyListeners();
  }
}

class FBoxScope extends InheritedNotifier<FBoxStore> {
  const FBoxScope({required FBoxStore store, required super.child, super.key})
    : super(notifier: store);

  static FBoxStore of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<FBoxScope>();
    assert(scope != null, 'FBoxScope is missing above this context.');
    return scope!.notifier!;
  }
}
