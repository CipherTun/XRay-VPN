import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

import '../models/server_config.dart';
import '../services/platform_vpn.dart';
import '../services/ssh_socks_bridge.dart';
import '../services/xray_config_builder.dart';
import 'app_settings.dart';
import 'config_store.dart';
import 'log_service.dart';

enum VpnState { disconnected, connecting, connected, disconnecting }

class VpnController extends ChangeNotifier {
  VpnController({required this.settings, required this.configs, required this.logs}) {
    _sub = platform.events.listen(_onEvent, onError: (_) {});
    platform.status().then((s) {
      if (s == 'connected') {
        logs.info('app', 'VPN service already running');
      }
    }).catchError((_) {});
  }

  final AppSettings settings;
  final ConfigStore configs;
  final LogService logs;
  final PlatformVpn platform = PlatformVpn();

  VpnState state = VpnState.disconnected;
  String? lastError;
  ServerConfig? active;

  // live stats
  int downTotal = 0, upTotal = 0;
  double downSpeed = 0, upSpeed = 0;
  final List<double> downHistory = List.filled(60, 0, growable: true);
  final List<double> upHistory = List.filled(60, 0, growable: true);
  DateTime? connectedAt;
  int httpPort = 0;

  StreamSubscription? _sub;
  SshSocksBridge? _bridge;
  Timer? _statsTimer;
  Timer? _logTimer;
  Completer<void>? _connecting;
  bool _userStop = false;
  int _retries = 0;
  String _logPath = '';
  int _logOffset = 0;
  int _metricsPort = 0;
  int _lastDown = 0, _lastUp = 0;

  bool get isConnected => state == VpnState.connected;
  bool get isBusy => state == VpnState.connecting || state == VpnState.disconnecting;

  Duration get uptime => connectedAt == null ? Duration.zero : DateTime.now().difference(connectedAt!);

  void _set(VpnState s) {
    state = s;
    notifyListeners();
  }

  Future<void> toggle() async {
    if (isBusy) return;
    if (isConnected) {
      await disconnect();
    } else {
      await connect();
    }
  }

  Future<List<int>> _freePorts(int n) async {
    final servers = <ServerSocket>[];
    for (var i = 0; i < n; i++) {
      servers.add(await ServerSocket.bind(InternetAddress.loopbackIPv4, 0));
    }
    final ports = servers.map((s) => s.port).toList();
    for (final s in servers) {
      await s.close();
    }
    return ports;
  }

  Future<void> connect() async {
    if (state != VpnState.disconnected) return;
    final cfg = configs.selected;
    if (cfg == null) {
      lastError = 'Add a config first';
      notifyListeners();
      return;
    }
    lastError = null;
    _userStop = false;
    active = cfg;
    _set(VpnState.connecting);
    logs.info('app', 'Connecting to "${cfg.name}" (${cfg.protocol})');
    try {
      if (!await platform.prepare()) throw 'VPN permission was not granted';

      int? sshPort;
      if (cfg.kind == ConfigKind.ssh) {
        _bridge = SshSocksBridge();
        sshPort = await _bridge!.start(
          cfg,
          log: (m) => logs.info('ssh', m),
          onClosed: _onBridgeClosed,
        );
      }

      final ports = await _freePorts(3);
      httpPort = ports[1];
      _metricsPort = ports[2];
      final dir = await platform.filesDir();
      _logPath = '$dir/xray_error.log';
      try {
        File(_logPath).writeAsStringSync('');
      } catch (_) {}
      _logOffset = 0;

      final json = XrayConfigBuilder.build(
        config: cfg,
        settings: settings,
        socksPort: ports[0],
        httpPort: httpPort,
        metricsPort: _metricsPort,
        errorLogPath: _logPath,
        sshSocksPort: sshPort,
      );

      _connecting = Completer<void>();
      await platform.start(configJson: jsonEncode(json), options: {
        'dns': XrayConfigBuilder.vpnDnsIps(settings),
        'mtu': settings.mtu,
        'ipv6': settings.ipv6,
        'splitMode': settings.splitMode,
        'apps': settings.splitApps,
        'session': 'XRay VPN - ${cfg.name}',
      });
      await _connecting!.future.timeout(const Duration(seconds: 30));
    } catch (e) {
      lastError = '$e';
      logs.error('app', 'Connect failed: $e');
      try {
        await platform.stop();
      } catch (_) {}
      await _cleanup();
      _set(VpnState.disconnected);
    }
  }

  Future<void> disconnect() async {
    _userStop = true;
    _retries = 0;
    _set(VpnState.disconnecting);
    logs.info('app', 'Disconnecting');
    try {
      await platform.stop();
    } catch (e) {
      logs.error('app', 'Stop failed: $e');
    }
    await Future.delayed(const Duration(milliseconds: 600));
    if (state == VpnState.disconnecting) {
      await _cleanup();
      _set(VpnState.disconnected);
    }
  }

  void _onEvent(Map<String, dynamic> e) {
    switch (e['type']) {
      case 'status':
        final st = e['state'] as String;
        if (st == 'connected') {
          connectedAt = DateTime.now();
          _retries = 0;
          _startTimers();
          _set(VpnState.connected);
          logs.info('app', 'Connected');
          if (_connecting?.isCompleted == false) _connecting!.complete();
        } else if (st == 'connecting') {
          if (state == VpnState.disconnected) _set(VpnState.connecting);
        } else if (st == 'disconnected') {
          final was = state;
          _cleanup().then((_) {
            _set(VpnState.disconnected);
            if (_connecting?.isCompleted == false) _connecting!.completeError('Core stopped before connecting');
            if (was == VpnState.connected && !_userStop && settings.autoReconnect) _tryReconnect();
          });
        }
      case 'error':
        lastError = '${e['message']}';
        logs.error('core', lastError!);
        if (_connecting?.isCompleted == false) _connecting!.completeError(lastError!);
        notifyListeners();
    }
  }

  void _onBridgeClosed() {
    logs.warn('ssh', 'SSH session dropped');
    if (state == VpnState.connected || state == VpnState.connecting) {
      platform.stop().catchError((_) {});
    }
  }

  void _tryReconnect() {
    if (_retries >= 3) return;
    _retries++;
    logs.warn('app', 'Connection lost - reconnecting (attempt $_retries/3)');
    Future.delayed(Duration(seconds: 2 * _retries), () {
      if (state == VpnState.disconnected && !_userStop) connect();
    });
  }

  void _startTimers() {
    _statsTimer?.cancel();
    _logTimer?.cancel();
    downTotal = upTotal = 0;
    _lastDown = _lastUp = 0;
    _statsTimer = Timer.periodic(const Duration(seconds: 1), (_) => _pollStats());
    _logTimer = Timer.periodic(const Duration(seconds: 1), (_) => _tailLog());
  }

  Future<void> _pollStats() async {
    final c = HttpClient()..connectionTimeout = const Duration(seconds: 1);
    c.findProxy = (_) => 'DIRECT';
    try {
      final req = await c.getUrl(Uri.parse('http://127.0.0.1:$_metricsPort/debug/vars'));
      final res = await req.close().timeout(const Duration(seconds: 2));
      final j = jsonDecode(await res.transform(utf8.decoder).join()) as Map<String, dynamic>;
      final p = ((j['stats'] as Map?)?['outbound'] as Map?)?['proxy'] as Map?;
      final d = (p?['downlink'] as num?)?.toInt() ?? _lastDown;
      final u = (p?['uplink'] as num?)?.toInt() ?? _lastUp;
      downSpeed = (d - _lastDown).clamp(0, 1 << 40).toDouble();
      upSpeed = (u - _lastUp).clamp(0, 1 << 40).toDouble();
      _lastDown = d;
      _lastUp = u;
      downTotal = d;
      upTotal = u;
      downHistory..removeAt(0)..add(downSpeed);
      upHistory..removeAt(0)..add(upSpeed);
      notifyListeners();
    } catch (_) {
    } finally {
      c.close(force: true);
    }
  }

  void _tailLog() {
    try {
      final f = File(_logPath);
      if (!f.existsSync()) return;
      final len = f.lengthSync();
      if (len < _logOffset) _logOffset = 0;
      if (len == _logOffset) return;
      final raf = f.openSync();
      raf.setPositionSync(_logOffset);
      final bytes = raf.readSync(len - _logOffset);
      raf.closeSync();
      _logOffset = len;
      for (final line in utf8.decode(bytes, allowMalformed: true).split('\n')) {
        final t = line.trim();
        if (t.isEmpty) continue;
        final lv = t.contains('[Error]')
            ? LogLevel.error
            : t.contains('[Warning]')
                ? LogLevel.warning
                : t.contains('[Debug]')
                    ? LogLevel.debug
                    : LogLevel.info;
        logs.add(lv, 'xray', t);
      }
    } catch (_) {}
  }

  Future<void> _cleanup() async {
    _statsTimer?.cancel();
    _logTimer?.cancel();
    _statsTimer = _logTimer = null;
    await _bridge?.stop();
    _bridge = null;
    connectedAt = null;
    downSpeed = upSpeed = 0;
    downHistory.fillRange(0, downHistory.length, 0);
    upHistory.fillRange(0, upHistory.length, 0);
  }

  @override
  void dispose() {
    _sub?.cancel();
    _statsTimer?.cancel();
    _logTimer?.cancel();
    super.dispose();
  }
}
