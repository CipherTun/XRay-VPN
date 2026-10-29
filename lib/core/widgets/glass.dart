import 'dart:ui';

import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// Full-screen gradient with soft accent-colored aurora blobs.
class AuroraBackground extends StatelessWidget {
  const AuroraBackground({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final x = context.x;
    final a = x.isDark ? 0.20 : 0.26;
    return Stack(fit: StackFit.expand, children: [
      DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [x.bgTop, x.bgBottom],
          ),
        ),
      ),
      Positioned(top: -140, left: -100, child: _Blob(color: x.accent, size: 380, alpha: a)),
      Positioned(bottom: 100, right: -140, child: _Blob(color: x.accent2, size: 420, alpha: a * 0.9)),
      child,
    ]);
  }
}

class _Blob extends StatelessWidget {
  const _Blob({required this.color, required this.size, required this.alpha});
  final Color color;
  final double size, alpha;

  @override
  Widget build(BuildContext context) => IgnorePointer(
        child: Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: RadialGradient(colors: [color.withValues(alpha: alpha), color.withValues(alpha: 0)]),
          ),
        ),
      );
}

/// Frosted-glass surface (backdrop blur + translucent fill + hairline border).
class GlassCard extends StatelessWidget {
  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.radius = 24,
    this.onTap,
    this.onLongPress,
    this.tint,
    this.margin,
  });
  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;
  final double radius;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final Gradient? tint;

  @override
  Widget build(BuildContext context) {
    final x = context.x;
    final r = BorderRadius.circular(radius);
    return Padding(
      padding: margin ?? EdgeInsets.zero,
      child: ClipRRect(
        borderRadius: r,
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: x.blur, sigmaY: x.blur),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: x.glass,
              gradient: tint,
              borderRadius: r,
              border: Border.all(color: x.glassBorder),
            ),
            child: Material(
              type: MaterialType.transparency,
              child: InkWell(
                borderRadius: r,
                onTap: onTap,
                onLongPress: onLongPress,
                child: Padding(padding: padding, child: child),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class GlassIconButton extends StatelessWidget {
  const GlassIconButton({super.key, required this.icon, required this.onTap, this.tooltip});
  final IconData icon;
  final VoidCallback onTap;
  final String? tooltip;

  @override
  Widget build(BuildContext context) {
    final x = context.x;
    return Tooltip(
      message: tooltip ?? '',
      child: GlassCard(
        radius: 16,
        padding: const EdgeInsets.all(11),
        onTap: onTap,
        child: Icon(icon, size: 22, color: x.text),
      ),
    );
  }
}

class PageHeader extends StatelessWidget {
  const PageHeader({super.key, required this.title, this.subtitle, this.actions = const []});
  final String title;
  final String? subtitle;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final x = context.x;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 12),
      child: Row(children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, letterSpacing: -0.6, color: x.text)),
            if (subtitle != null)
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Text(subtitle!, style: TextStyle(fontSize: 13, color: x.muted)),
              ),
          ]),
        ),
        for (final a in actions) Padding(padding: const EdgeInsets.only(left: 8), child: a),
      ]),
    );
  }
}

class Chip2 extends StatelessWidget {
  const Chip2(this.label, {super.key, this.color});
  final String label;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = color ?? context.x.accent;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3),
      decoration: BoxDecoration(
        color: c.withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: c.withValues(alpha: 0.35)),
      ),
      child: Text(label, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: c, letterSpacing: 0.3)),
    );
  }
}

void toast(BuildContext context, String msg) {
  final x = context.x;
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(
      content: Text(msg, style: TextStyle(color: x.text)),
      behavior: SnackBarBehavior.floating,
      backgroundColor: x.isDark ? const Color(0xFF1E2A36) : Colors.white,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16), side: BorderSide(color: x.glassBorder)),
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 96),
    ));
}

Future<T?> showGlassSheet<T>(BuildContext context, Widget Function(BuildContext) builder) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    barrierColor: Colors.black54,
    builder: (ctx) {
      final x = ctx.x;
      return Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewInsets.bottom),
        child: ClipRRect(
          borderRadius: const BorderRadius.vertical(top: Radius.circular(30)),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 30, sigmaY: 30),
            child: Container(
              constraints: BoxConstraints(maxHeight: MediaQuery.of(ctx).size.height * 0.92),
              decoration: BoxDecoration(
                color: x.isDark ? const Color(0xE6101820) : const Color(0xE8F2F6FA),
                border: Border(top: BorderSide(color: x.glassBorder)),
              ),
              child: SafeArea(
                top: false,
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  const SizedBox(height: 10),
                  Container(width: 40, height: 4, decoration: BoxDecoration(color: x.muted.withValues(alpha: 0.4), borderRadius: BorderRadius.circular(4))),
                  Flexible(child: builder(ctx)),
                ]),
              ),
            ),
          ),
        ),
      );
    },
  );
}
