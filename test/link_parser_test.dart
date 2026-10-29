import 'package:flutter_test/flutter_test.dart';
import 'package:xray_vpn/core/services/link_parser.dart';

void main() {
  test('parses a vless ws+tls link', () {
    final c = LinkParser.parse(
        'vless://11111111-1111-1111-1111-111111111111@example.com:443?type=ws&security=tls&sni=example.com&path=%2Fws#My%20Node')!;
    expect(c.host, 'example.com');
    expect(c.port, 443);
    expect(c.name, 'My Node');
    expect(c.protocol, 'VLESS');
    expect(c.outbound!['streamSettings']['network'], 'ws');
  });

  test('parses a SIP002 shadowsocks link', () {
    final c = LinkParser.parse('ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@1.2.3.4:8388#SS')!;
    expect(c.host, '1.2.3.4');
    expect(c.port, 8388);
    expect(c.outbound!['settings']['servers'][0]['method'], 'aes-256-gcm');
    expect(c.outbound!['settings']['servers'][0]['password'], 'password');
  });

  test('rejects garbage', () {
    expect(LinkParser.parse('hello world'), isNull);
  });
}
