import 'dart:convert';

import '../models/server_config.dart';

/// Parses vless://, vmess://, trojan:// and ss:// share links (and base64
/// subscription bodies) into [ServerConfig]s holding a ready Xray outbound.
class LinkParser {
  static int _n = 0;
  static String _id() => '${DateTime.now().microsecondsSinceEpoch.toRadixString(36)}${(_n++).toRadixString(36)}';

  static List<ServerConfig> parseText(String input) {
    var text = input.trim();
    if (text.isEmpty) return [];
    if (!text.contains('://')) {
      final d = _b64(text);
      if (d != null) text = d;
    }
    final out = <ServerConfig>[];
    for (final raw in text.split(RegExp(r'\s+'))) {
      final c = parse(raw.trim());
      if (c != null) out.add(c);
    }
    return out;
  }

  static ServerConfig? parse(String link) {
    final l = link.trim();
    try {
      if (l.startsWith('vless://')) return _vless(l);
      if (l.startsWith('vmess://')) return _vmess(l);
      if (l.startsWith('trojan://')) return _trojan(l);
      if (l.startsWith('ss://')) return _ss(l);
    } catch (_) {}
    return null;
  }

  static String? _b64(String s) {
    try {
      final t = s.replaceAll(RegExp(r'\s'), '').replaceAll('-', '+').replaceAll('_', '/');
      return utf8.decode(base64.decode(base64.normalize(t)));
    } catch (_) {
      return null;
    }
  }

  static String _dec(String s) {
    try {
      return Uri.decodeComponent(s);
    } catch (_) {
      return s;
    }
  }

  static ServerConfig _mk(String name, String proto, String host, int port, String link, Map<String, dynamic> out) =>
      ServerConfig(
        id: _id(),
        name: name.isEmpty ? host : name,
        kind: ConfigKind.xray,
        protocol: proto,
        host: host,
        port: port,
        link: link,
        outbound: out,
      );

  static ServerConfig _vless(String l) {
    final u = Uri.parse(l);
    final q = u.queryParameters;
    final flow = q['flow'] ?? '';
    final out = {
      'protocol': 'vless',
      'settings': {
        'vnext': [
          {
            'address': u.host,
            'port': u.port,
            'users': [
              {
                'id': _dec(u.userInfo),
                'encryption': q['encryption'] ?? 'none',
                if (flow.isNotEmpty) 'flow': flow,
              }
            ]
          }
        ]
      },
      'streamSettings': stream(q, u.host, 'none'),
    };
    return _mk(_dec(u.fragment), 'VLESS', u.host, u.port, l, out);
  }

  static ServerConfig _vmess(String l) {
    final body = _b64(l.substring(8).trim())!;
    final j = jsonDecode(body) as Map<String, dynamic>;
    String s(String k) => '${j[k] ?? ''}';
    final q = <String, String>{
      'type': s('net').isEmpty ? 'tcp' : s('net'),
      'security': s('tls') == 'tls' ? 'tls' : 'none',
      'sni': s('sni'),
      'host': s('host'),
      'path': s('path'),
      'fp': s('fp'),
      'alpn': s('alpn'),
      'mode': s('type'),
    };
    final host = s('add');
    final port = int.parse(s('port'));
    final out = {
      'protocol': 'vmess',
      'settings': {
        'vnext': [
          {
            'address': host,
            'port': port,
            'users': [
              {
                'id': s('id'),
                'alterId': int.tryParse(s('aid')) ?? 0,
                'security': s('scy').isEmpty ? 'auto' : s('scy'),
              }
            ]
          }
        ]
      },
      'streamSettings': stream(q, host, 'none'),
    };
    return _mk(s('ps'), 'VMESS', host, port, l, out);
  }

  static ServerConfig _trojan(String l) {
    final u = Uri.parse(l);
    final out = {
      'protocol': 'trojan',
      'settings': {
        'servers': [
          {'address': u.host, 'port': u.port, 'password': _dec(u.userInfo)}
        ]
      },
      'streamSettings': stream(u.queryParameters, u.host, 'tls'),
    };
    return _mk(_dec(u.fragment), 'TROJAN', u.host, u.port, l, out);
  }

  static ServerConfig _ss(String l) {
    var body = l.substring(5);
    var name = '';
    final hash = body.indexOf('#');
    if (hash >= 0) {
      name = _dec(body.substring(hash + 1));
      body = body.substring(0, hash);
    }
    final qi = body.indexOf('?');
    if (qi >= 0) body = body.substring(0, qi);
    body = body.replaceAll('/', '');

    String cred, hostPort;
    if (body.contains('@')) {
      final at = body.lastIndexOf('@');
      cred = body.substring(0, at);
      hostPort = body.substring(at + 1);
      cred = cred.contains(':') ? _dec(cred) : (_b64(cred) ?? cred);
    } else {
      final d = _b64(body)!;
      final at = d.lastIndexOf('@');
      cred = d.substring(0, at);
      hostPort = d.substring(at + 1);
    }
    final ci = cred.indexOf(':');
    final method = cred.substring(0, ci);
    final password = cred.substring(ci + 1);
    final pi = hostPort.lastIndexOf(':');
    var host = hostPort.substring(0, pi);
    if (host.startsWith('[') && host.endsWith(']')) host = host.substring(1, host.length - 1);
    final port = int.parse(hostPort.substring(pi + 1));
    final out = {
      'protocol': 'shadowsocks',
      'settings': {
        'servers': [
          {'address': host, 'port': port, 'method': method, 'password': password}
        ]
      },
      'streamSettings': {'network': 'tcp', 'security': 'none'},
    };
    return _mk(name, 'SHADOWSOCKS', host, port, l, out);
  }

  /// Builds Xray `streamSettings` from share-link query parameters.
  static Map<String, dynamic> stream(Map<String, String> q, String host, String defaultSecurity) {
    String? g(String k) => (q[k] == null || q[k]!.isEmpty) ? null : q[k];
    var net = g('type') ?? 'tcp';
    if (net == 'raw') net = 'tcp';
    final sec = g('security') ?? defaultSecurity;
    final s = <String, dynamic>{'network': net, 'security': sec};

    if (sec == 'tls') {
      s['tlsSettings'] = {
        'serverName': g('sni') ?? g('peer') ?? host,
        'fingerprint': g('fp') ?? 'chrome',
        if (g('alpn') != null) 'alpn': g('alpn')!.split(','),
        'allowInsecure': g('allowInsecure') == '1' || g('insecure') == '1',
      };
    } else if (sec == 'reality') {
      s['realitySettings'] = {
        'serverName': g('sni') ?? host,
        'fingerprint': g('fp') ?? 'chrome',
        'publicKey': g('pbk') ?? '',
        'shortId': g('sid') ?? '',
        'spiderX': g('spx') ?? '',
      };
    }

    switch (net) {
      case 'ws':
        s['wsSettings'] = {
          'path': g('path') ?? '/',
          if (g('host') != null) 'headers': {'Host': g('host')},
        };
      case 'grpc':
        s['grpcSettings'] = {
          'serviceName': g('serviceName') ?? g('path') ?? '',
          'multiMode': g('mode') == 'multi',
        };
      case 'xhttp':
        Object? extra;
        if (g('extra') != null) {
          try {
            extra = jsonDecode(g('extra')!);
          } catch (_) {}
        }
        s['xhttpSettings'] = {
          'path': g('path') ?? '/',
          if (g('host') != null) 'host': g('host'),
          'mode': g('mode') ?? 'auto',
          if (extra != null) 'extra': extra,
        };
      case 'httpupgrade':
        s['httpupgradeSettings'] = {
          'path': g('path') ?? '/',
          if (g('host') != null) 'host': g('host'),
        };
      default:
        break;
    }
    return s;
  }
}
