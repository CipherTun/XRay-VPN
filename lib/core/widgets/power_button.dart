import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../state/vpn_controller.dart';
import '../theme/app_theme.dart';

class PowerButton extends StatefulWidget {
  const PowerButton({super.key, required this.state, required this.onTap});
  final VpnState state;
  final VoidCallback onTap;

  @override
  State<PowerButton> createState() => _PowerButtonState();
}

class _PowerButtonState extends State<PowerButton> with SingleTickerProviderStateMixin {
  late final AnimationController _c =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 2800))..repeat();

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final x = context.x;
    final on = widget.state == VpnState.connected;
    final busy = widget.state == VpnState.connecting || widget.state == VpnState.disconnecting;

    return GestureDetector(
      onTap: widget.onTap,
      behavior: HitTestBehavior.opaque,
      child: SizedBox(
        width: 280,
        height: 280,
        child: AnimatedBuilder(
          animation: _c,
          builder: (context, _) {
            final t = _c.value;
            return Stack(alignment: Alignment.center, children: [
              if (on || busy)
                for (final o in const [0.0, 0.5])
                  _ripple((t + o) % 1, x.accent, on ? 0.30 : 0.22),
              // rotating arc while busy
              if (busy)
                Transform.rotate(
                  angle: t * 2 * math.pi,
                  child: SizedBox(
                    width: 214,
                    height: 214,
                    child: CircularProgressIndicator(
                      value: 0.28,
                      strokeWidth: 3,
                      strokeCap: StrokeCap.round,
                      valueColor: AlwaysStoppedAnimation(x.accent),
                      backgroundColor: Colors.transparent,
                    ),
                  ),
                ),
              AnimatedContainer(
                duration: const Duration(milliseconds: 450),
                curve: Curves.easeOutCubic,
                width: 190,
                height: 190,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: on
                      ? x.accentGradient
                      : LinearGradient(
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                          colors: [x.glass.withValues(alpha: x.isDark ? 0.14 : 0.8), x.glass],
                        ),
                  border: Border.all(color: on ? Colors.white.withValues(alpha: 0.4) : x.glassBorder, width: 1.6),
                  boxShadow: [
                    BoxShadow(
                      color: x.accent.withValues(alpha: on ? 0.50 : 0.12),
                      blurRadius: on ? 60 : 30,
                      spreadRadius: on ? 6 : 0,
                    ),
                  ],
                ),
                child: Center(
                  child: Icon(
                    Icons.power_settings_new_rounded,
                    size: 78,
                    color: on ? x.onAccent : (busy ? x.accent : x.text.withValues(alpha: 0.85)),
                  ),
                ),
              ),
            ]);
          },
        ),
      ),
    );
  }

  Widget _ripple(double t, Color c, double a) => Opacity(
        opacity: ((1 - t) * a).clamp(0, 1),
        child: Container(
          width: 190 + t * 90,
          height: 190 + t * 90,
          decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: c, width: 2)),
        ),
      );
}
