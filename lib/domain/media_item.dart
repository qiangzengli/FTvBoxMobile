class MediaItem {
  const MediaItem({
    required this.id,
    required this.title,
    required this.note,
    required this.year,
    required this.category,
    required this.rating,
    required this.description,
    required this.director,
    required this.actors,
    required this.episodes,
    required this.color,
    this.sourceKey = '',
    this.detailId = '',
    this.posterUrl = '',
    this.playLines = const [],
    this.headers = const {},
  });

  final String id;
  final String title;
  final String note;
  final String year;
  final String category;
  final String rating;
  final String description;
  final String director;
  final String actors;
  final int episodes;
  final int color;
  final String sourceKey;
  final String detailId;
  final String posterUrl;
  final List<MediaPlayLine> playLines;
  final Map<String, String> headers;

  String get storageId => sourceKey.isEmpty ? id : '$sourceKey::$id';

  MediaItem copyWith({
    String? note,
    String? rating,
    String? description,
    String? director,
    String? actors,
    int? episodes,
    String? posterUrl,
    List<MediaPlayLine>? playLines,
    Map<String, String>? headers,
  }) {
    return MediaItem(
      id: id,
      title: title,
      note: note ?? this.note,
      year: year,
      category: category,
      rating: rating ?? this.rating,
      description: description ?? this.description,
      director: director ?? this.director,
      actors: actors ?? this.actors,
      episodes: episodes ?? this.episodes,
      color: color,
      sourceKey: sourceKey,
      detailId: detailId,
      posterUrl: posterUrl ?? this.posterUrl,
      playLines: playLines ?? this.playLines,
      headers: headers ?? this.headers,
    );
  }

  Map<String, Object?> toJson() => {
    'id': id,
    'title': title,
    'note': note,
    'year': year,
    'category': category,
    'rating': rating,
    'description': description,
    'director': director,
    'actors': actors,
    'episodes': episodes,
    'color': color,
    'sourceKey': sourceKey,
    'detailId': detailId,
    'posterUrl': posterUrl,
    'playLines': playLines.map((line) => line.toJson()).toList(),
    'headers': headers,
  };

  factory MediaItem.fromJson(Map<String, Object?> json) {
    final rawLines = json['playLines'];
    final rawHeaders = json['headers'];
    return MediaItem(
      id: json['id']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      note: json['note']?.toString() ?? '',
      year: json['year']?.toString() ?? '',
      category: json['category']?.toString() ?? '',
      rating: json['rating']?.toString() ?? '',
      description: json['description']?.toString() ?? '',
      director: json['director']?.toString() ?? '',
      actors: json['actors']?.toString() ?? '',
      episodes: int.tryParse(json['episodes']?.toString() ?? '') ?? 1,
      color: int.tryParse(json['color']?.toString() ?? '') ?? 0xFF48627A,
      sourceKey: json['sourceKey']?.toString() ?? '',
      detailId: json['detailId']?.toString() ?? '',
      posterUrl: json['posterUrl']?.toString() ?? '',
      playLines: rawLines is List
          ? rawLines
                .whereType<Map>()
                .map(
                  (line) =>
                      MediaPlayLine.fromJson(Map<String, Object?>.from(line)),
                )
                .toList()
          : const [],
      headers: rawHeaders is Map
          ? rawHeaders.map((key, value) => MapEntry('$key', '$value'))
          : const {},
    );
  }
}

class MediaPlayLine {
  const MediaPlayLine({required this.name, required this.episodes});

  final String name;
  final List<MediaEpisode> episodes;

  Map<String, Object?> toJson() => {
    'name': name,
    'episodes': episodes.map((episode) => episode.toJson()).toList(),
  };

  factory MediaPlayLine.fromJson(Map<String, Object?> json) {
    final rawEpisodes = json['episodes'];
    return MediaPlayLine(
      name: json['name']?.toString() ?? '默认线路',
      episodes: rawEpisodes is List
          ? rawEpisodes
                .whereType<Map>()
                .map(
                  (episode) =>
                      MediaEpisode.fromJson(Map<String, Object?>.from(episode)),
                )
                .toList()
          : const [],
    );
  }
}

class MediaEpisode {
  const MediaEpisode({
    required this.name,
    required this.url,
    this.headers = const {},
  });

  final String name;
  final String url;
  final Map<String, String> headers;

  Map<String, Object?> toJson() => {
    'name': name,
    'url': url,
    'headers': headers,
  };

  factory MediaEpisode.fromJson(Map<String, Object?> json) {
    final rawHeaders = json['headers'];
    return MediaEpisode(
      name: json['name']?.toString() ?? '',
      url: json['url']?.toString() ?? '',
      headers: rawHeaders is Map
          ? rawHeaders.map((key, value) => MapEntry('$key', '$value'))
          : const {},
    );
  }
}
