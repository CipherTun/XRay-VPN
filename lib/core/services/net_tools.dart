import 'dart:async';
import 'dart:convert';
import 'dart:io';

import '../models/server_config.dart';

class NetTools {
  /// TCP connect time in ms, or -1 on failure/timeout.
  static Future<int> tcpPing(String host, int port, {Duration timeout = const Duration(seconds: 4)}) async {
    final sw = Stopwatch()..start();
    try {
      final s = await Socket.connect(host, port, timeout: timeout);
      sw.stop();
      s.destroy();
      return sw.elapsedMilliseconds;
    } catch (_) {
      return -1;
    }
  }

  /// Downloads a subscription URL (direct, not via the tunnel) and returns the body.
  static Future<String> fetch(String url, {int? proxyPort}) async {
    final c = HttpClient()..connectionTimeout = const Duration(seconds: 12);
    c.findProxy = (_) => proxyPort == null ? 'DIRECT' : 'PROXY 127.0.0.1:$proxyPort';
    c.userAgent = 'XRayVPN/1.0';
    try {
      final req = await c.getUrl(Uri.parse(url));
      final res = await req.close().timeout(const Duration(seconds: 20));
      if (res.statusCode != 200) throw HttpException('HTTP ${res.statusCode}');
      return await res.transform(utf8.decoder).join();
    } finally {
      c.close(force: true);
    }
  }

  /// Public IP / geo lookup. Routed through the local HTTP inbound when connected.
  static Future<Map<String, dynamic>> ipInfo({int? proxyPort}) async {
    final body = await fetch('https://ipwho.is/', proxyPort: proxyPort);
    return jsonDecode(body) as Map<String, dynamic>;
  }

  /// Downloads [bytes] through the proxy and reports Mbit/s.
  static Future<double> speedTest({int? proxyPort, int bytes = 25 * 1000 * 1000, void Function(double mbps)? onProgress}) async {
    final c = HttpClient()..connectionTimeout = const Duration(seconds: 10);
    c.findProxy = (_) => proxyPort == null ? 'DIRECT' : 'PROXY 127.0.0.1:$proxyPort';
    try {
      final req = await c.getUrl(Uri.parse('https://speed.cloudflare.com/__down?bytes=$bytes'));
      final res = await req.close();
      final sw = Stopwatch()..start();
      var got = 0;
      await for (final chunk in res) {
        got += chunk.length;
        if (sw.elapsedMilliseconds > 250) onProgress?.call(got * 8 / 1e6 / (sw.elapsedMilliseconds / 1000));
      }
      return got * 8 / 1e6 / (sw.elapsedMilliseconds / 1000);
    } finally {
      c.close(force: true);
    }
  }

  static Future<void> testAll(List<ServerConfig> list, {void Function()? onUpdate}) async {
    const batch = 6;
    for (var i = 0; i < list.length; i += batch) {
      await Future.wait(list.skip(i).take(batch).map((c) async {
        c.latencyMs = await tcpPing(c.host, c.port);
        onUpdate?.call();
      }));
    }
  }
}
