import 'package:fbox/domain/media_item.dart';
import 'package:flutter/material.dart';

class MediaPosterCard extends StatelessWidget {
  const MediaPosterCard({
    required this.item,
    required this.onTap,
    this.cardKey,
    super.key,
  });

  final MediaItem item;
  final VoidCallback onTap;
  final Key? cardKey;

  @override
  Widget build(BuildContext context) {
    final color = Color(item.color);
    return Material(
      key: cardKey,
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(14),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Stack(
          fit: StackFit.expand,
          children: [
            DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [color, color.withValues(alpha: 0.62)],
                ),
              ),
            ),
            if (item.posterUrl.isNotEmpty)
              Image.network(
                item.posterUrl,
                fit: BoxFit.cover,
                headers: item.headers,
                errorBuilder: (_, _, _) => _PosterFallback(color: color),
              )
            else
              _PosterFallback(color: color),
            Align(
              alignment: Alignment.topLeft,
              child: Container(
                padding: const EdgeInsets.all(5),
                color: const Color(0xFFFF4600),
                child: Text(
                  item.year,
                  style: const TextStyle(color: Colors.white, fontSize: 10),
                ),
              ),
            ),
            Align(
              alignment: Alignment.bottomCenter,
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.fromLTRB(8, 20, 8, 7),
                decoration: const BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [Colors.transparent, Color(0xD9000000)],
                  ),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      item.note,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: Colors.white, fontSize: 11),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      item.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: Colors.white, fontSize: 13),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PosterFallback extends StatelessWidget {
  const _PosterFallback({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Icon(
        Icons.movie_filter_outlined,
        color: Colors.white.withValues(alpha: 0.45),
        size: 38,
      ),
    );
  }
}
