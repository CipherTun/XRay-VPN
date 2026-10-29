import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:dartssh2/dartssh2.dart';

import '../models/server_config.dart';

/// Opens an SSH session with DartSSH2 and exposes it as a local SOCKS5 proxy
/// (TCP CONNECT). Xray's `proxy` outbound then points at this port, so the
/// whole device is tunneled over SSH via the TUN interface.
class SshSocksBridge {
  SSHClient? _client;
  ServerSocket? _server;
  bool _closing = false;

  Future<int> start(
    ServerConfig c, {
    void Function(String msg)? log,
    void Function()? onClosed,
  }) async {
    log?.call('Connecting to ${c.host}:${c.port} as ${c.username}');
    final socket = await SSHSocket.connect(c.host, c.port, timeout: const Duration(seconds: 15));

    List<SSHKeyPair>? keys;
    final pem = c.privateKey?.trim() ?? '';
    if (pem.isNotEmpty) {
      final pass = (c.passphrase ?? '').isEmpty ? null : c.passphrase;
      keys = SSHKeyPair.fromPem(pem, pass);
    }
    final pw = c.password ?? '';

    final client = SSHClient(
      socket,
      username: c.username ?? 'root',
      identities: keys,
      onPasswordRequest: pw.isNotEmpty ? () => pw : null,
      keepAliveInterval: const Duration(seconds: 20),
    );
    _client = client;
    await client.authenticated.timeout(const Duration(seconds: 20));
    log?.call('SSH authenticated (${client.remoteVersion ?? 'unknown server'})');

    client.done.then((_) {
      if (!_closing) {
        log?.call('SSH session closed by server');
        onClosed?.call();
      }
    }).catchError((Object e) {
      if (!_closing) {
        log?.call('SSH session error: $e');
        onClosed?.call();
      }
    });

    final server = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
    _server = server;
    server.listen(_onClient);
    log?.call('SSH SOCKS5 bridge on 127.0.0.1:${server.port}');
    return server.port;
  }

  Future<void> stop() async {
    _closing = true;
    try {
      await _server?.close();
    } catch (_) {}
    try {
      _client?.close();
    } catch (_) {}
    _server = null;
    _client = null;
  }

  void _onClient(Socket s) {
    final client = _client;
    if (client == null) {
      s.destroy();
      return;
    }
    s.setOption(SocketOption.tcpNoDelay, true);
    var state = 0; // 0 greeting, 1 request, 2 piping, 3 connecting
    final buf = <int>[];
    SSHForwardChannel? ch;
    late StreamSubscription<Uint8List> sub;

    void reply(int code) =>
        s.add([0x05, code, 0x00, 0x01, 0, 0, 0, 0, 0, 0]);

    sub = s.listen(
      (data) async {
        if (state == 2) {
          ch!.sink.add(data);
          return;
        }
        buf.addAll(data);
        if (state == 0) {
          if (buf.length < 2) return;
          final n = buf[1];
          if (buf.length < 2 + n) return;
          buf.removeRange(0, 2 + n);
          s.add([0x05, 0x00]);
          state = 1;
        }
        if (state == 1) {
          if (buf.length < 5) return;
          final cmd = buf[1];
          final atyp = buf[3];
          String host;
          int portOff;
          if (atyp == 1) {
            if (buf.length < 10) return;
            host = buf.sublist(4, 8).join('.');
            portOff = 8;
          } else if (atyp == 3) {
            final len = buf[4];
            if (buf.length < 5 + len + 2) return;
            host = String.fromCharCodes(buf.sublist(5, 5 + len));
            portOff = 5 + len;
          } else if (atyp == 4) {
            if (buf.length < 22) return;
            final b = buf.sublist(4, 20);
            final parts = <String>[];
            for (var i = 0; i < 16; i += 2) {
              parts.add(((b[i] << 8) | b[i + 1]).toRadixString(16));
            }
            host = parts.join(':');
            portOff = 20;
          } else {
            reply(0x08);
            s.destroy();
            return;
          }
          final port = (buf[portOff] << 8) | buf[portOff + 1];
          final rest = buf.sublist(portOff + 2);
          buf.clear();
          if (cmd != 0x01) {
            reply(0x07); // only CONNECT is supported over SSH
            s.destroy();
            return;
          }
          state = 3;
          sub.pause();
          try {
            final channel = await client.forwardLocal(host, port);
            ch = channel;
            reply(0x00);
            channel.stream.listen(
              (d) => s.add(d),
              onDone: () => s.close(),
              onError: (_) => s.destroy(),
              cancelOnError: true,
            );
            if (rest.isNotEmpty) channel.sink.add(Uint8List.fromList(rest));
            state = 2;
            sub.resume();
          } catch (_) {
            reply(0x05);
            s.destroy();
          }
        }
      },
      onDone: () {
        try {
          ch?.sink.close();
        } catch (_) {}
      },
      onError: (_) {
        try {
          ch?.destroy();
        } catch (_) {}
        s.destroy();
      },
      cancelOnError: true,
    );
  }
}
