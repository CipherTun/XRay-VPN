enum ConfigKind { xray, ssh }

class ServerConfig {
  ServerConfig({
    required this.id,
    required this.name,
    required this.kind,
    required this.protocol,
    required this.host,
    required this.port,
    this.link,
    this.outbound,
    this.username,
    this.password,
    this.privateKey,
    this.passphrase,
    this.subscriptionId,
    this.latencyMs,
    DateTime? added,
  }) : added = added ?? DateTime.now();

  final String id;
  String name;
  final ConfigKind kind;
  final String protocol;
  final String host;
  final int port;

  /// Original share link (xray configs).
  final String? link;

  /// Ready-to-use Xray outbound (xray configs).
  final Map<String, dynamic>? outbound;

  // SSH tunnel credentials.
  String? username;
  String? password;
  String? privateKey;
  String? passphrase;

  String? subscriptionId;
  int? latencyMs; // -1 = failed
  final DateTime added;

  String get address => '$host:$port';

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'kind': kind.name,
        'protocol': protocol,
        'host': host,
        'port': port,
        'link': link,
        'outbound': outbound,
        'username': username,
        'password': password,
        'privateKey': privateKey,
        'passphrase': passphrase,
        'subscriptionId': subscriptionId,
        'latencyMs': latencyMs,
        'added': added.toIso8601String(),
      };

  factory ServerConfig.fromJson(Map<String, dynamic> j) => ServerConfig(
        id: j['id'],
        name: j['name'],
        kind: ConfigKind.values.firstWhere((k) => k.name == j['kind'], orElse: () => ConfigKind.xray),
        protocol: j['protocol'] ?? 'XRAY',
        host: j['host'],
        port: j['port'],
        link: j['link'],
        outbound: (j['outbound'] as Map?)?.cast<String, dynamic>(),
        username: j['username'],
        password: j['password'],
        privateKey: j['privateKey'],
        passphrase: j['passphrase'],
        subscriptionId: j['subscriptionId'],
        latencyMs: j['latencyMs'],
        added: DateTime.tryParse(j['added'] ?? ''),
      );
}

class Subscription {
  Subscription({required this.id, required this.name, required this.url, this.updated});
  final String id;
  String name;
  final String url;
  DateTime? updated;

  Map<String, dynamic> toJson() => {'id': id, 'name': name, 'url': url, 'updated': updated?.toIso8601String()};
  factory Subscription.fromJson(Map<String, dynamic> j) =>
      Subscription(id: j['id'], name: j['name'], url: j['url'], updated: DateTime.tryParse(j['updated'] ?? ''));
}
