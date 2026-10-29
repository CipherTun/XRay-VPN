import 'package:flutter/material.dart';

import '../state/app_settings.dart';
import 'accent.dart';

@immutable
class XColors extends ThemeExtension<XColors> {
  const XColors({
    required this.bgTop,
    required this.bgBottom,
    required this.glass,
    required this.glassBorder,
    required this.text,
    required this.muted,
    required this.accent,
    required this.accent2,
    required this.success,
    required this.warning,
    required this.danger,
    required this.blur,
    required this.isDark,
  });

  final Color bgTop, bgBottom, glass, glassBorder, text, muted;
  final Color accent, accent2, success, warning, danger;
  final double blur;
  final bool isDark;

  LinearGradient get accentGradient => LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [accent, accent2],
      );

  Color get onAccent =>
      ThemeData.estimateBrightnessForColor(accent) == Brightness.dark ? Colors.white : const Color(0xFF0B1117);

  @override
  XColors copyWith() => this;

  @override
  XColors lerp(ThemeExtension<XColors>? other, double t) => this;
}

extension XContext on BuildContext {
  XColors get x => Theme.of(this).extension<XColors>()!;
}

XColors buildColors(AppSettings s, Brightness b) {
  final dark = b == Brightness.dark;
  late final Color accent, accent2;
  if (s.accentId == 'custom') {
    accent = Color(s.customPrimary);
    accent2 = Color(s.customSecondary);
  } else {
    final r = presetById(s.accentId).resolve(dark);
    accent = r.$1;
    accent2 = r.$2;
  }

  Color top, bottom;
  if (dark) {
    if (s.customBg != 0) {
      top = Color(s.customBg);
      bottom = Color.lerp(top, accent, 0.14)!;
    } else if (s.amoled) {
      top = const Color(0xFF000000);
      bottom = const Color(0xFF070B0F);
    } else {
      top = const Color(0xFF090D12);
      bottom = const Color(0xFF1A2733);
    }
  } else {
    top = const Color(0xFFEAF0F5);
    bottom = const Color(0xFFD3DEE8);
  }

  return XColors(
    bgTop: top,
    bgBottom: bottom,
    glass: dark ? Colors.white.withValues(alpha: 0.07) : Colors.white.withValues(alpha: 0.58),
    glassBorder: dark ? Colors.white.withValues(alpha: 0.12) : Colors.white.withValues(alpha: 0.85),
    text: dark ? const Color(0xFFEAF0F6) : const Color(0xFF111A22),
    muted: dark ? const Color(0xFF8FA0B2) : const Color(0xFF5B6B7B),
    accent: accent,
    accent2: accent2,
    success: const Color(0xFF45C98B),
    warning: const Color(0xFFE9B44C),
    danger: const Color(0xFFEF6A6A),
    blur: s.blur,
    isDark: dark,
  );
}

ThemeData buildTheme(AppSettings s, Brightness b) {
  final c = buildColors(s, b);
  final scheme = ColorScheme.fromSeed(seedColor: c.accent, brightness: b).copyWith(
    primary: c.accent,
    secondary: c.accent2,
    surface: c.bgTop,
    onSurface: c.text,
  );
  return ThemeData(
    useMaterial3: true,
    brightness: b,
    colorScheme: scheme,
    scaffoldBackgroundColor: Colors.transparent,
    canvasColor: c.bgTop,
    splashFactory: InkSparkle.splashFactory,
    textTheme: Typography.material2021(platform: TargetPlatform.android)
        .black
        .apply(bodyColor: c.text, displayColor: c.text),
    dividerColor: c.glassBorder,
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith(
          (st) => st.contains(WidgetState.selected) ? c.onAccent : c.muted),
      trackColor: WidgetStateProperty.resolveWith(
          (st) => st.contains(WidgetState.selected) ? c.accent : c.glass),
      trackOutlineColor: WidgetStateProperty.all(c.glassBorder),
    ),
    sliderTheme: SliderThemeData(activeTrackColor: c.accent, thumbColor: c.accent, inactiveTrackColor: c.glass),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: c.glass,
      labelStyle: TextStyle(color: c.muted),
      hintStyle: TextStyle(color: c.muted),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide(color: c.glassBorder)),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide(color: c.glassBorder)),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide(color: c.accent, width: 1.4)),
    ),
    extensions: [c],
  );
}
