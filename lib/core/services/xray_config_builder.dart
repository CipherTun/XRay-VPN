import '../models/server_config.dart';
import '../state/app_settings.dart';

/// Builds the complete Xray-core JSON (TUN inbound + local proxies + routing).
class XrayConfigBuilder {
  static const privateCidrs = [
    '10.0.0.0/8',
    '172.16.0.0/12',
    '192.168.0.0/16',
    '169.254.0.0/16',
    '127.0.0.0/8',
    '100.64.0.0/10',
    'fc00::/7',
    'fe80::/10',
  ];

  static Map<String, dynamic> build({
    required ServerConfig config,
    required AppSettings settings,
    required int socksPort,
    required int httpPort,
    required int metricsPort,
    required String errorLogPath,
    int? sshSocksPort,
  }) {
    final ssh = config.kind == ConfigKind.ssh;

    final Map<String, dynamic> proxy;
    if (ssh) {
      proxy = {
        'tag': 'proxy',
        'protocol': 'socks',
        'settings': {
          'servers': [
            {'address': '127.0.0.1', 'port': sshSocksPort}
          ]
        },
      };
    } else {
      proxy = {...config.outbound!, 'tag': 'proxy'};
      final proto = proxy['protocol'];
      final stream = (proxy['streamSettings'] as Map?) ?? const {};
      final users = ((proxy['settings'] as Map?)?['vnext'] as List?)?.first?['users'] as List?;
      final hasFlow = users != null && users.isNotEmpty && (users.first['flow'] ?? '').toString().isNotEmpty;
      final muxOk = settings.mux && !hasFlow && stream['network'] != 'xhttp' &&
          (proto == 'vless' || proto == 'vmess' || proto == 'trojan');
      if (muxOk) proxy['mux'] = {'enabled': true, 'concurrency': 8};
    }

    final sniffing = {
      'enabled': true,
      'destOverride': ['http', 'tls', 'quic'],
      'routeOnly': true,
    };

    final rules = <Map<String, dynamic>>[
      {'type': 'field', 'port': '53', 'outboundTag': 'dns-out'},
    ];
    if (settings.bypassLan) {
      rules.add({'type': 'field', 'ip': privateCidrs, 'outboundTag': 'direct'});
    }
    final domains = <String>[];
    final ips = <String>[];
    for (final raw in settings.bypassList.split(RegExp(r'[\n,;\s]+'))) {
      final e = raw.trim();
      if (e.isEmpty) continue;
      if (RegExp(r'^[\d.]+(/\d+)?$').hasMatch(e) || (e.contains(':') && RegExp(r'^[0-9a-fA-F:]+(/\d+)?$').hasMatch(e))) {
        ips.add(e);
      } else {
        domains.add(e.startsWith('domain:') ? e : 'domain:$e');
      }
    }
    if (domains.isNotEmpty) rules.add({'type': 'field', 'domain': domains, 'outboundTag': 'direct'});
    if (ips.isNotEmpty) rules.add({'type': 'field', 'ip': ips, 'outboundTag': 'direct'});
    if (settings.blockQuic) {
      rules.add({'type': 'field', 'network': 'udp', 'port': '443', 'outboundTag': 'block'});
    }

    return {
      'log': {'loglevel': settings.logLevel, 'access': 'none', 'error': errorLogPath},
      'stats': {},
      'metrics': {'listen': '127.0.0.1:$metricsPort'},
      'policy': {
        'system': {
          'statsOutboundUplink': true,
          'statsOutboundDownlink': true,
        }
      },
      'dns': {
        'servers': dnsServers(settings, forceTcp: ssh),
        'queryStrategy': settings.ipv6 ? 'UseIP' : 'UseIPv4',
      },
      'inbounds': [
        {
          'tag': 'tun-in',
          'protocol': 'tun',
          'settings': {'name': 'xray0'},
          'sniffing': sniffing,
        },
        {
          'tag': 'socks-in',
          'listen': '127.0.0.1',
          'port': socksPort,
          'protocol': 'socks',
          'settings': {'udp': true},
          'sniffing': sniffing,
        },
        {
          'tag': 'http-in',
          'listen': '127.0.0.1',
          'port': httpPort,
          'protocol': 'http',
          'sniffing': sniffing,
        },
      ],
      'outbounds': [
        proxy,
        {'tag': 'direct', 'protocol': 'freedom'},
        {'tag': 'block', 'protocol': 'blackhole'},
        {'tag': 'dns-out', 'protocol': 'dns'},
      ],
      'routing': {'domainStrategy': 'AsIs', 'rules': rules},
    };
  }

  /// DNS server entries Xray's internal resolver uses (always via the proxy).
  static List<String> dnsServers(AppSettings s, {bool forceTcp = false}) {
    String map(String ip) {
      if (ip.contains('://')) return ip;
      final mode = (forceTcp && s.dnsMode == 'udp') ? 'tcp' : s.dnsMode;
      return switch (mode) {
        'tcp' => 'tcp://$ip',
        'doh' => 'https://$ip/dns-query',
        _ => ip,
      };
    }

    return [s.dns1, s.dns2].where((e) => e.trim().isNotEmpty).map((e) => map(e.trim())).toList();
  }

  /// Plain IPs handed to the OS VPN builder as the tunnel DNS.
  static List<String> vpnDnsIps(AppSettings s) => [s.dns1, s.dns2]
      .map((e) => e.trim())
      .where((e) => RegExp(r'^[\d.:a-fA-F]+$').hasMatch(e))
      .toList();
}
