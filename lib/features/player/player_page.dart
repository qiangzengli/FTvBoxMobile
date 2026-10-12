import 'package:fbox/domain/media_item.dart';
import 'package:flutter/material.dart';

class PlayerPage extends StatefulWidget {
  const PlayerPage({required this.item, super.key});

  final MediaItem item;

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> {
  bool _playing = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            Center(
              child: Icon(
                Icons.movie_filter_outlined,
                color: Color(widget.item.color).withValues(alpha: 0.7),
                size: 100,
              ),
            ),
            Positioned(
              left: 4,
              top: 4,
              child: IconButton(
                onPressed: () => Navigator.of(context).pop(),
                color: Colors.white,
                icon: const Icon(Icons.arrow_back),
              ),
            ),
            Positioned(
              left: 52,
              right: 52,
              top: 15,
              child: Text(
                widget.item.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white, fontSize: 16),
              ),
            ),
            Center(
              child: IconButton(
                onPressed: () => setState(() => _playing = !_playing),
                iconSize: 68,
                color: Colors.white,
                icon: Icon(
                  _playing
                      ? Icons.pause_circle_filled_rounded
                      : Icons.play_circle_fill_rounded,
                ),
              ),
            ),
            const Positioned(
              left: 18,
              right: 18,
              bottom: 28,
              child: Column(
                children: [
                  LinearProgressIndicator(
                    value: 0.18,
                    color: Color(0xFF567DF4),
                    backgroundColor: Colors.white30,
                  ),
                  SizedBox(height: 8),
                  Row(
                    children: [
                      Text('08:24', style: TextStyle(color: Colors.white)),
                      Spacer(),
                      Text('45:16', style: TextStyle(color: Colors.white)),
                      SizedBox(width: 14),
                      Icon(Icons.fullscreen, color: Colors.white),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
