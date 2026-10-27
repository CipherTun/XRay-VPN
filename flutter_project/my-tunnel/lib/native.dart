import 'dart:convert';

import 'package:flutter/services.dart';

class AppInfo {
  const AppInfo(this.label, this.package);
  final String label;
  final String package;
}

/// Bridge to MainActivity.kt (installed in CI by tools/patch_android.py).
class Native {
  static const _c = MethodChannel('xyz.xray.vpn/native');
  static const ownPackage = 'xyz.xray.vpn';

  static Future<void> moveToBack() async {
    try {
      await _c.invokeMethod('moveToBack');
    } catch (_) {}
  }

  static void Function(String link)? _onLink;

  /// Receives links that open the app while it is already running.
  static void setLinkHandler(void Function(String link) handler) {
    _onLink = handler;
    _c.setMethodCallHandler((call) async {
      if (call.method == 'onLink' && call.arguments is String) {
        _onLink?.call(call.arguments as String);
      }
      return null;
    });
  }

  /// The link that launched the app (vless:// ...), once.
  static Future<String?> takeLink() async {
    try {
      return await _c.invokeMethod<String>('takeLink');
    } catch (_) {
      return null;
    }
  }

  static Future<List<AppInfo>> installedApps() async {
    final r = await _c.invokeMethod<List<dynamic>>('installedApps') ?? const [];
    return [
      for (final e in r)
        AppInfo((e as Map)['label'].toString(), e['package'].toString()),
    ];
  }

  static Future<bool> ignoringBatteryOptimizations() async {
    try {
      return await _c.invokeMethod<bool>('isIgnoringBatteryOptimizations') ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<bool> requestIgnoreBatteryOptimizations() async {
    try {
      return await _c.invokeMethod<bool>('requestIgnoreBatteryOptimizations') ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<bool> openVpnSettings() async {
    try {
      return await _c.invokeMethod<bool>('openVpnSettings') ?? false;
    } catch (_) {
      return false;
    }
  }


  /// Parse a share link using libXray's official API (apiVersion 3).
  static Future<Map<String, dynamic>> convertShareLinkToXrayJson(String link) async {
    try {
      final raw = await _c.invokeMethod<String>('convertShareLink', {'text': link});
      if (raw == null || raw.isEmpty) throw 'libXray returned an empty conversion response';
      final envelope = jsonDecode(raw) as Map<String, dynamic>;
      if (envelope['success'] != true) {
        // Upstream libXray deliberately does not support legacy VMessQrCode URIs.
        // Preserve compatibility by translating that legacy format explicitly.
        if (link.trim().toLowerCase().startsWith('vmess://')) return _legacyVmess(link);
        throw 'libXray rejected the share link: ${envelope['error'] ?? 'unknown conversion error'}';
      }
      final data = envelope['data'];
      if (data is! Map || data['outbounds'] is! List) {
        throw 'libXray returned no usable outbound for this share link';
      }
      return Map<String, dynamic>.from(data);
    } catch (e) {
      if (link.trim().toLowerCase().startsWith('vmess://')) return _legacyVmess(link);
      rethrow;
    }
  }

  static Map<String, dynamic> _legacyVmess(String link) {
    try {
      final encoded = link.trim().substring('vmess://'.length).split('#').first;
      final normalized = base64.normalize(encoded.replaceAll('-', '+').replaceAll('_', '/'));
      final decoded = jsonDecode(utf8.decode(base64.decode(normalized))) as Map<String, dynamic>;
      String str(String key, [String fallback = '']) => (decoded[key] ?? fallback).toString();
      final address = str('add').trim();
      final id = str('id').trim();
      final port = int.tryParse(str('port'));
      if (address.isEmpty || id.isEmpty || port == null || port < 1 || port > 65535) {
        throw 'Legacy VMess link is missing a valid address, UUID or port';
      }
      final network = str('net', 'tcp').toLowerCase();
      final outbound = <String, dynamic>{
        'tag': 'proxy',
        'protocol': 'vmess',
        'settings': <String, dynamic>{
          'vnext': <Map<String, dynamic>>[
            <String, dynamic>{
              'address': address,
              'port': port,
              'users': <Map<String, dynamic>>[
                <String, dynamic>{'id': id, 'alterId': int.tryParse(str('aid', '0')) ?? 0, 'security': str('scy', 'auto')},
              ],
            },
          ],
        },
        'streamSettings': <String, dynamic>{'network': network, 'security': str('tls') == 'tls' ? 'tls' : 'none'},
      };
      final stream = outbound['streamSettings'] as Map<String, dynamic>;
      if (stream['security'] == 'tls') {
        stream['tlsSettings'] = <String, dynamic>{
          'serverName': str('sni').isNotEmpty ? str('sni') : address,
          if (str('alpn').isNotEmpty) 'alpn': str('alpn').split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toList(),
          if (str('fp').isNotEmpty) 'fingerprint': str('fp'),
        };
      }
      if (network == 'ws') {
        stream['wsSettings'] = <String, dynamic>{
          'path': str('path', '/'),
          'headers': <String, dynamic>{if (str('host').isNotEmpty) 'Host': str('host')},
        };
      } else if (network == 'grpc') {
        stream['grpcSettings'] = <String, dynamic>{'serviceName': str('path'), 'multiMode': false};
      } else if (!const ['tcp', 'kcp', 'httpupgrade', 'xhttp'].contains(network)) {
        throw 'Unsupported legacy VMess transport: $network';
      }
      return <String, dynamic>{'outbounds': <Map<String, dynamic>>[outbound]};
    } catch (e) {
      throw 'Invalid legacy VMess share link: $e';
    }
  }

  static Future<bool> requestXrayPermission() async {
    return await _c.invokeMethod<bool>('requestXrayPermission') ?? false;
  }

  /// Start the native libXray engine. Full-device mode creates an Android TUN;
  /// proxy-only mode starts only the configured local proxy inbound.
  static Future<void> startXray({
    required String config,
    required bool proxyOnly,
    required List<String> disallowedApps,
    required bool bypassLan,
    required List<String> includedRoutes,
  }) async {
    await _c.invokeMethod<void>('startXray', <String, dynamic>{
      'config': config,
      'proxyOnly': proxyOnly,
      'disallowedApps': disallowedApps,
      'bypassLan': bypassLan,
      'includeRoutes': includedRoutes,
    });
  }

  static Future<void> stopXray() async {
    await _c.invokeMethod<void>('stopXray');
  }

  static Future<int> pingXray(String config, {String url = 'https://cp.cloudflare.com/generate_204'}) async {
    final raw = await _c.invokeMethod<String>('pingXray', <String, dynamic>{'config': config, 'url': url});
    if (raw == null) return -1;
    final envelope = jsonDecode(raw) as Map<String, dynamic>;
    if (envelope['success'] != true) return -1;
    final data = envelope['data'];
    if (data is! Map || data['results'] is! List || (data['results'] as List).isEmpty) return -1;
    final item = (data['results'] as List).first;
    if (item is! Map || item['success'] != true) return -1;
    return (item['delay'] as num?)?.toInt() ?? -1;
  }

  static Future<String> xrayVersion() async {
    final raw = await _c.invokeMethod<String>('xrayVersion');
    if (raw == null) throw 'libXray version query returned no response';
    final envelope = jsonDecode(raw) as Map<String, dynamic>;
    if (envelope['success'] != true) throw 'libXray version query failed: ${envelope['error']}';
    final data = envelope['data'];
    return data is Map ? (data['version']?.toString() ?? 'unknown') : 'unknown';
  }

  // ---------- Protocol native engine hooks ----------
  static Future<bool> startDnstt(Map<String, dynamic> params) async {
    // Fail closed: never report success for an engine that is not bundled.
    return false;
  }

  static Future<void> stopDnstt() async {
    try {
      await _c.invokeMethod('stopDnstt');
    } catch (_) {}
  }

  static Future<bool> startSlowDns(Map<String, dynamic> params) async {
    // Fail closed: never report success for an engine that is not bundled.
    return false;
  }

  static Future<void> stopSlowDns() async {
    try {
      await _c.invokeMethod('stopSlowDns');
    } catch (_) {}
  }

  static Future<bool> startOpenConnect(Map<String, dynamic> params) async {
    // Fail closed: never report success for an engine that is not bundled.
    return false;
  }

  static Future<void> stopOpenConnect() async {
    try {
      await _c.invokeMethod('stopOpenConnect');
    } catch (_) {}
  }

  static Future<bool> startPsiphon(Map<String, dynamic> params) async {
    // Fail closed: never report success for an engine that is not bundled.
    return false;
  }

  static Future<void> stopPsiphon() async {
    try {
      await _c.invokeMethod('stopPsiphon');
    } catch (_) {}
  }

  static Future<void> stopAllDaemons() async {
    try {
      await _c.invokeMethod('stopAllDaemons');
    } catch (_) {}
  }
}
