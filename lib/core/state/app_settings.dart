import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AppSettings extends ChangeNotifier {
  AppSettings._(this._prefs);
  final SharedPreferences _prefs;
  static const _key = 'settings_v1';

  // Appearance
  int themeMode = 2; // 0 system, 1 light, 2 dark
  String accentId = 'glacier';
  int customPrimary = 0xFF7C5CFF;
  int customSecondary = 0xFFB9A8FF;
  int customBg = 0; // 0 = none
  double blur = 24;
  bool amoled = false;
  bool haptics = true;

  // Tunneling
  String dnsMode = 'udp'; // udp | tcp | doh
  String dns1 = '1.1.1.1';
  String dns2 = '8.8.8.8';
  bool ipv6 = false;
  int mtu = 1500;
  bool bypassLan = true;
  bool blockQuic = true;
  bool mux = false;
  String bypassList = '';
  String splitMode = 'all'; // all | allow | deny
  List<String> splitApps = [];

  // Connection
  String logLevel = 'warning';
  bool autoConnect = false;
  bool autoReconnect = true;

  static Future<AppSettings> load(SharedPreferences prefs) async {
    final s = AppSettings._(prefs);
    final raw = prefs.getString(_key);
    if (raw != null) {
      try {
        s._apply(jsonDecode(raw) as Map<String, dynamic>);
      } catch (_) {}
    }
    return s;
  }

  void _apply(Map<String, dynamic> j) {
    themeMode = j['themeMode'] ?? themeMode;
    accentId = j['accentId'] ?? accentId;
    customPrimary = j['customPrimary'] ?? customPrimary;
    customSecondary = j['customSecondary'] ?? customSecondary;
    customBg = j['customBg'] ?? customBg;
    blur = (j['blur'] ?? blur).toDouble();
    amoled = j['amoled'] ?? amoled;
    haptics = j['haptics'] ?? haptics;
    dnsMode = j['dnsMode'] ?? dnsMode;
    dns1 = j['dns1'] ?? dns1;
    dns2 = j['dns2'] ?? dns2;
    ipv6 = j['ipv6'] ?? ipv6;
    mtu = j['mtu'] ?? mtu;
    bypassLan = j['bypassLan'] ?? bypassLan;
    blockQuic = j['blockQuic'] ?? blockQuic;
    mux = j['mux'] ?? mux;
    bypassList = j['bypassList'] ?? bypassList;
    splitMode = j['splitMode'] ?? splitMode;
    splitApps = ((j['splitApps'] ?? const []) as List).cast<String>();
    logLevel = j['logLevel'] ?? logLevel;
    autoConnect = j['autoConnect'] ?? autoConnect;
    autoReconnect = j['autoReconnect'] ?? autoReconnect;
  }

  Map<String, dynamic> toJson() => {
        'themeMode': themeMode,
        'accentId': accentId,
        'customPrimary': customPrimary,
        'customSecondary': customSecondary,
        'customBg': customBg,
        'blur': blur,
        'amoled': amoled,
        'haptics': haptics,
        'dnsMode': dnsMode,
        'dns1': dns1,
        'dns2': dns2,
        'ipv6': ipv6,
        'mtu': mtu,
        'bypassLan': bypassLan,
        'blockQuic': blockQuic,
        'mux': mux,
        'bypassList': bypassList,
        'splitMode': splitMode,
        'splitApps': splitApps,
        'logLevel': logLevel,
        'autoConnect': autoConnect,
        'autoReconnect': autoReconnect,
      };

  /// Mutate and persist in one go.
  void update(void Function(AppSettings s) fn) {
    fn(this);
    _prefs.setString(_key, jsonEncode(toJson()));
    notifyListeners();
  }
}
