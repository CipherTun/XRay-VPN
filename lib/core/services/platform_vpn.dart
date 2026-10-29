import 'dart:async';

import 'package:flutter/services.dart';

class InstalledApp {
  InstalledApp(this.package, this.label, this.system);
  final String package;
  final String label;
  final bool system;
}

/// Thin wrapper around the native VPN layer (Android VpnService /
/// iOS NEPacketTunnelProvider). Both sides expose the same contract.
class PlatformVpn {
  static const _m = MethodChannel('io.surprise.vpn/vpn');
  static const _e = EventChannel('io.surprise.vpn/events');

  Stream<Map<String, dynamic>> get events =>
      _e.receiveBroadcastStream().map((e) => Map<String, dynamic>.from(e as Map));

  /// Shows the system VPN consent dialog when needed.
  Future<bool> prepare() async => (await _m.invokeMethod<bool>('prepare')) ?? false;

  Future<void> start({required String configJson, required Map<String, dynamic> options}) =>
      _m.invokeMethod('start', {'configJson': configJson, ...options});

  Future<void> stop() => _m.invokeMethod('stop');

  Future<String> status() async => (await _m.invokeMethod<String>('status')) ?? 'disconnected';

  Future<String> filesDir() async => (await _m.invokeMethod<String>('getFilesDir')) ?? '';

  Future<bool> requestNotifications() async =>
      (await _m.invokeMethod<bool>('requestNotifications')) ?? true;

  Future<void> openVpnSettings() => _m.invokeMethod('openVpnSettings');

  Future<void> openBatterySettings() => _m.invokeMethod('openBatterySettings');

  Future<List<InstalledApp>> installedApps() async {
    final r = await _m.invokeMethod<List<dynamic>>('getApps') ?? const [];
    return r
        .map((e) => Map<String, dynamic>.from(e as Map))
        .map((m) => InstalledApp(m['package'], m['label'], m['system'] == true))
        .toList()
      ..sort((a, b) => a.label.toLowerCase().compareTo(b.label.toLowerCase()));
  }
}
