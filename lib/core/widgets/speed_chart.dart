import 'dart:math' as math;

import 'package:flutter/material.dart';

class SpeedChart extends StatelessWidget {
  const SpeedChart({super.key, required this.down, required this.up, required this.c1, required this.c2});
  final List<double> down, up;
  final Color c1, c2;

  @override
  Widget build(BuildContext context) => CustomPaint(
        painter: _Painter(List.of(down), List.of(up), c1, c2),
        size: Size.infinite,
      );
}

class _Painter extends CustomPainter {
  _Painter(this.down, this.up, this.c1, this.c2);
  final List<double> down, up;
  final Color c1, c2;

  @override
  void paint(Canvas canvas, Size size) {
    final peak = math.max(1024.0, [...down, ...up].fold<double>(0, math.max));
    _draw(canvas, size, up, c2, peak);
    _draw(canvas, size, down, c1, peak);
  }

  void _draw(Canvas canvas, Size size, List<double> d, Color c, double peak) {
    if (d.length < 2) return;
    final dx = size.width / (d.length - 1);
    double y(int i) => size.height - 4 - (d[i] / peak) * (size.height - 10);
    final line = Path()..moveTo(0, y(0));
    for (var i = 1; i < d.length; i++) {
      final mx = (i - 0.5) * dx;
      line.cubicTo(mx, y(i - 1), mx, y(i), i * dx, y(i));
    }
    final fill = Path.from(line)
      ..lineTo(size.width, size.height)
      ..lineTo(0, size.height)
      ..close();
    canvas.drawPath(
      fill,
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [c.withValues(alpha: 0.30), c.withValues(alpha: 0)],
        ).createShader(Offset.zero & size),
    );
    canvas.drawPath(
      line,
      Paint()
        ..color = c
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.2
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(covariant _Painter old) => true;
}
