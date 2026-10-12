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
}
