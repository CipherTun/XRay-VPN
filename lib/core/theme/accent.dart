import 'package:flutter/material.dart';

class AccentPreset {
  const AccentPreset(this.id, this.name, this.primary, this.secondary,
      {this.darkPrimary, this.darkSecondary});
  final String id;
  final String name;
  final Color primary;
  final Color secondary;
  final Color? darkPrimary;
  final Color? darkSecondary;

  (Color, Color) resolve(bool dark) => dark
      ? (darkPrimary ?? primary, darkSecondary ?? secondary)
      : (primary, secondary);
}

/// First entry is the default: soft turquoise on grey-blue / black.
const accentPresets = <AccentPreset>[
  AccentPreset('glacier', 'Glacier', Color(0xFF5DB8B1), Color(0xFF7FA6C9)),
  AccentPreset('blue', 'Blue', Color(0xFF4C8DFF), Color(0xFF8AB4FF)),
  AccentPreset('black', 'Black', Color(0xFF1B2028), Color(0xFF55606F),
      darkPrimary: Color(0xFFD5DBE4), darkSecondary: Color(0xFF7C8796)),
  AccentPreset('green', 'Green', Color(0xFF3FBF86), Color(0xFF9BE3B8)),
  AccentPreset('pink', 'Pink', Color(0xFFEC6AA6), Color(0xFFF7A6C8)),
  AccentPreset('teal', 'Teal', Color(0xFF14B8A6), Color(0xFF5EEAD4)),
];

AccentPreset presetById(String id) =>
    accentPresets.firstWhere((p) => p.id == id, orElse: () => accentPresets.first);
