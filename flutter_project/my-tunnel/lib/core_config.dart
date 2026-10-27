import 'dart:convert';

import 'config_tweaks.dart';
import 'models.dart';
import 'native.dart';

export 'config_tweaks.dart' show lanBypassSubnets;

// Minimal Xray config shell. Share-link parsing is delegated to the official
// libXray convertShareLinksToXrayJson API rather than a second, divergent parser.
Map<String, dynamic> _templateMap() => <String, dynamic>{
      'log': <String, dynamic>{'loglevel': 'warning'},
      'inbounds': <Map<String, dynamic>>[
        <String, dynamic>{
          'tag': 'socks-in',
          'listen': '127.0.0.1',
          'port': 10808,
          'protocol': 'socks',
          'settings': <String, dynamic>{'auth': 'noauth', 'udp': true},
        }
      ],
      'outbounds': <Map<String, dynamic>>[
        <String, dynamic>{'tag': 'proxy', 'protocol': 'freedom'},
        <String, dynamic>{'tag': 'direct', 'protocol': 'freedom'},
        <String, dynamic>{'tag': 'block', 'protocol': 'blackhole'},
      ],
    };

String _withOutbound(Map<String, dynamic> outbound) {
  final m = _templateMap();
  final outs = (m['outbounds'] as List).cast<Map<String, dynamic>>();
  final idx = outs.indexWhere((o) => o['tag'] == 'proxy');
  outs[idx] = outbound;
  m['outbounds'] = outs;
  return jsonEncode(m);
}

String patchDns(String cfg, AppSettings s, {bool tcpOnly = false}) =>
    patchDnsJson(cfg, s.dnsServers, tcpOnly: tcpOnly);

String _finish(String cfg, AppSettings s, int inboundPort) =>
    applyLogLevel(applyInbound(cfg, port: inboundPort, listenAll: s.shareHotspot), s.logLevel);

String _tweak(String cfg, AppSettings s) => applyTweaks(
      cfg,
      fragment: s.fragment,
      fragLength: s.fragLength,
      fragInterval: s.fragInterval,
      mux: s.mux,
      muxConcurrency: s.muxConcurrency,
      fingerprint: s.fingerprint,
      blockQuic: s.blockQuic,
    );

Future<String> _configFromShareLink(String link, AppSettings s) async {
  final parsed = await Native.convertShareLinkToXrayJson(link);
  final outbounds = (parsed['outbounds'] as List?)
      ?.whereType<Map>()
      .map((e) => Map<String, dynamic>.from(e))
      .toList();
  if (outbounds == null || outbounds.isEmpty) {
    throw 'libXray did not return a valid outbound for this share link';
  }
  final cfg = _templateMap();
  cfg['outbounds'] = <Map<String, dynamic>>[
    ...outbounds,
    <String, dynamic>{'tag': 'direct', 'protocol': 'freedom'},
    <String, dynamic>{'tag': 'block', 'protocol': 'blackhole'},
  ];
  // Keep the first parsed outbound as the app's selected proxy outbound.
  final first = Map<String, dynamic>.from(outbounds.first);
  first['tag'] = 'proxy';
  cfg['outbounds']![0] = first;
  return _finish(
    _tweak(overrideAddress(patchDns(jsonEncode(cfg), s), ''), s),
    s,
    s.socksPort,
  );
}

Future<String> buildXrayConfig(Profile p, AppSettings s) async {
  switch (p.s('mode')) {
    case 'json':
      final raw = p.s('json').trim();
      try {
        jsonDecode(raw);
      } catch (e) {
        throw 'Invalid JSON: $e';
      }
      return raw;
    case 'link':
      final cfg = await _configFromShareLink(p.s('link').trim(), s);
      return overrideAddress(cfg, p.connectAddr);
    default:
      final cfg = await _configFromShareLink(buildShareLink(p.data, p.name), s);
      return overrideAddress(cfg, p.connectAddr);
  }
}

/// HTTP / HTTPS / SOCKS5 upstream proxy as the Xray outbound.
String buildProxyConfig(Profile p, AppSettings s) => _finish(
    _tweak(overrideAddress(patchDns(_withOutbound(buildProxyOutbound(p.data)), s), p.connectAddr), s),
    s,
    s.socksPort);

/// entry -> exit chain of two Xray profiles.
Future<String> buildChainConfig(Profile entry, Profile exit, AppSettings s) async {
  for (final p in [entry, exit]) {
    if (p.type != ProfileType.v2ray || p.s('mode') == 'json') {
      throw 'Chains use link/manual V2Ray profiles only (${p.name} is not)';
    }
  }
  Future<String> raw(Profile p) async {
    final link = p.s('mode') == 'link' ? p.s('link').trim() : buildShareLink(p.data, p.name);
    return overrideAddress(patchDns(await _configFromShareLink(link, s), s), p.connectAddr);
  }

  return _finish(_tweak(chainConfigs(await raw(exit), await raw(entry)), s), s, s.socksPort);
}

String buildWireguardConfig(String conf, AppSettings s) =>
    _finish(patchDns(_withOutbound(wgToOutbound(conf)), s), s, s.socksPort);

/// Xray config forwarding to the app's real SSH-backed local SOCKS listener.
String buildSocksConfig(int port, AppSettings s) {
  final cfg = _templateMap();
  cfg['inbounds'] = <Map<String, dynamic>>[
    <String, dynamic>{
      'tag': 'socks-in',
      'listen': '127.0.0.1',
      'port': port >= 65535 ? port - 1 : port + 1,
      'protocol': 'socks',
      'settings': <String, dynamic>{'auth': 'noauth', 'udp': true},
    }
  ];
  cfg['outbounds'] = <Map<String, dynamic>>[
    <String, dynamic>{
      'tag': 'proxy',
      'protocol': 'socks',
      'settings': <String, dynamic>{
        'servers': <Map<String, dynamic>>[
          <String, dynamic>{'address': '127.0.0.1', 'port': port},
        ],
      },
    },
    <String, dynamic>{'tag': 'direct', 'protocol': 'freedom'},
    <String, dynamic>{'tag': 'block', 'protocol': 'blackhole'},
  ];
  // The inbound and upstream SOCKS ports must be distinct.
  return _finish(patchDns(jsonEncode(cfg), s, tcpOnly: true), s,
      port >= 65535 ? port - 1 : port + 1);
}

String? shareLinkOf(Profile p) {
  if (p.type != ProfileType.v2ray) return null;
  switch (p.s('mode')) {
    case 'link':
      return p.s('link').trim();
    case 'json':
      return null;
    default:
      try {
        return buildShareLink(p.data, p.name);
      } catch (_) {
        return null;
      }
  }
}
