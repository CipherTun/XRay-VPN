import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app.dart';
import '../../core/models/server_config.dart';
import '../../core/services/net_tools.dart';
import '../../core/services/ssh_socks_bridge.dart';
import '../../core/theme/app_theme.dart';
import '../../core/widgets/glass.dart';

class ConfigsPage extends StatefulWidget {
  const ConfigsPage({super.key});

  @override
  State<ConfigsPage> createState() => _ConfigsPageState();
}

class _ConfigsPageState extends State<ConfigsPage> {
  bool _testing = false;

  Future<void> _testAll() async {
    final store = AppScope.of(context).configs;
    setState(() => _testing = true);
    await NetTools.testAll(store.items, onUpdate: store.notify);
    if (mounted) setState(() => _testing = false);
  }

  @override
  Widget build(BuildContext context) {
    final c = AppScope.of(context);
    final x = context.x;
    return SafeArea(
      bottom: false,
      child: ListenableBuilder(
        listenable: Listenable.merge([c.configs, c.vpn]),
        builder: (context, _) {
          final items = c.configs.items;
          return Column(children: [
            PageHeader(
              title: 'Configs',
              subtitle: '${items.length} servers \u00b7 ${c.configs.subscriptions.length} subscriptions',
              actions: [
                GlassIconButton(
                  icon: _testing ? Icons.hourglass_top_rounded : Icons.speed_rounded,
                  tooltip: 'Test latency',
                  onTap: _testing ? () {} : _testAll,
                ),
                GlassIconButton(icon: Icons.add_rounded, tooltip: 'Add', onTap: () => _showAdd(context)),
              ],
            ),
            if (c.configs.subscriptions.isNotEmpty)
              SizedBox(
                height: 44,
                child: ListView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 18),
                  children: [
                    for (final s in c.configs.subscriptions)
                      Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: GlassCard(
                          radius: 22,
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                          onTap: () async {
                            try {
                              final n = await c.configs.refreshSubscription(s);
                              if (context.mounted) toast(context, 'Updated ${s.name}: $n servers');
                            } catch (e) {
                              if (context.mounted) toast(context, 'Update failed: $e');
                            }
                          },
                          onLongPress: () => c.configs.removeSubscription(s),
                          child: Row(children: [
                            Icon(Icons.rss_feed_rounded, size: 16, color: x.accent),
                            const SizedBox(width: 6),
                            Text(s.name, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                          ]),
                        ),
                      ),
                  ],
                ),
              ),
            Expanded(
              child: items.isEmpty
                  ? _Empty(onAdd: () => _showAdd(context))
                  : ListView.builder(
                      padding: const EdgeInsets.fromLTRB(18, 8, 18, 120),
                      itemCount: items.length,
                      itemBuilder: (context, i) => _ConfigTile(config: items[i]),
                    ),
            ),
          ]);
        },
      ),
    );
  }

  void _showAdd(BuildContext context) {
    final c = AppScope.of(context);
    showGlassSheet(context, (ctx) {
      Widget row(IconData i, String t, String s, VoidCallback f) => ListTile(
            leading: Container(
              padding: const EdgeInsets.all(9),
              decoration: BoxDecoration(color: ctx.x.accent.withValues(alpha: 0.16), borderRadius: BorderRadius.circular(12)),
              child: Icon(i, color: ctx.x.accent),
            ),
            title: Text(t, style: const TextStyle(fontWeight: FontWeight.w700)),
            subtitle: Text(s, style: TextStyle(color: ctx.x.muted, fontSize: 12.5)),
            onTap: () {
              Navigator.pop(ctx);
              f();
            },
          );
      return ListView(shrinkWrap: true, padding: const EdgeInsets.fromLTRB(8, 12, 8, 16), children: [
        row(Icons.content_paste_rounded, 'Paste from clipboard', 'vless:// vmess:// trojan:// ss:// or a subscription', () async {
          final d = await Clipboard.getData('text/plain');
          final n = c.configs.importText(d?.text ?? '');
          if (context.mounted) toast(context, n == 0 ? 'No valid links found in clipboard' : 'Imported $n config${n == 1 ? '' : 's'}');
        }),
        row(Icons.link_rounded, 'Enter link manually', 'Paste one or more share links', () => _linkDialog(context)),
        row(Icons.terminal_rounded, 'SSH tunnel', 'DartSSH2 \u00b7 password or private key', () => _sshSheet(context)),
        row(Icons.rss_feed_rounded, 'Subscription URL', 'Import and auto-refresh a server list', () => _subSheet(context)),
      ]);
    });
  }

  void _linkDialog(BuildContext context) {
    final c = AppScope.of(context);
    final ctl = TextEditingController();
    showGlassSheet(context, (ctx) => Padding(
          padding: const EdgeInsets.all(20),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Add share links', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
            const SizedBox(height: 14),
            TextField(controller: ctl, minLines: 3, maxLines: 6, decoration: const InputDecoration(hintText: 'vless://...\nvmess://...')),
            const SizedBox(height: 14),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () {
                  final n = c.configs.importText(ctl.text);
                  Navigator.pop(ctx);
                  toast(context, n == 0 ? 'No valid links found' : 'Imported $n config${n == 1 ? '' : 's'}');
                },
                child: const Text('Import'),
              ),
            ),
          ]),
        ));
  }

  void _subSheet(BuildContext context) {
    final c = AppScope.of(context);
    final name = TextEditingController(text: 'Subscription');
    final url = TextEditingController();
    showGlassSheet(context, (ctx) {
      var busy = false;
      return StatefulBuilder(builder: (ctx, set) => Padding(
            padding: const EdgeInsets.all(20),
            child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Add subscription', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
              const SizedBox(height: 14),
              TextField(controller: name, decoration: const InputDecoration(labelText: 'Name')),
              const SizedBox(height: 10),
              TextField(controller: url, keyboardType: TextInputType.url, decoration: const InputDecoration(labelText: 'URL (https://...)')),
              const SizedBox(height: 14),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: busy
                      ? null
                      : () async {
                          set(() => busy = true);
                          try {
                            final n = await c.configs.addSubscription(name.text.trim().isEmpty ? 'Subscription' : name.text.trim(), url.text.trim());
                            if (ctx.mounted) Navigator.pop(ctx);
                            if (context.mounted) toast(context, 'Imported $n servers');
                          } catch (e) {
                            set(() => busy = false);
                            if (context.mounted) toast(context, 'Failed: $e');
                          }
                        },
                  child: Text(busy ? 'Fetching...' : 'Add'),
                ),
              ),
            ]),
          ));
    });
  }

  void _sshSheet(BuildContext context, [ServerConfig? edit]) {
    final c = AppScope.of(context);
    final name = TextEditingController(text: edit?.name ?? '');
    final host = TextEditingController(text: edit?.host ?? '');
    final port = TextEditingController(text: '${edit?.port ?? 22}');
    final user = TextEditingController(text: edit?.username ?? '');
    final pass = TextEditingController(text: edit?.password ?? '');
    final key = TextEditingController(text: edit?.privateKey ?? '');
    final phrase = TextEditingController(text: edit?.passphrase ?? '');

    ServerConfig build() => ServerConfig(
          id: edit?.id ?? DateTime.now().microsecondsSinceEpoch.toRadixString(36),
          name: name.text.trim().isEmpty ? host.text.trim() : name.text.trim(),
          kind: ConfigKind.ssh,
          protocol: 'SSH',
          host: host.text.trim(),
          port: int.tryParse(port.text.trim()) ?? 22,
          username: user.text.trim(),
          password: pass.text,
          privateKey: key.text,
          passphrase: phrase.text,
        );

    showGlassSheet(context, (ctx) {
      var testing = false;
      return StatefulBuilder(builder: (ctx, set) => SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('SSH tunnel', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
              const SizedBox(height: 4),
              Text('Traffic is carried over your SSH server with DartSSH2.', style: TextStyle(color: ctx.x.muted, fontSize: 12.5)),
              const SizedBox(height: 14),
              TextField(controller: name, decoration: const InputDecoration(labelText: 'Name (optional)')),
              const SizedBox(height: 10),
              Row(children: [
                Expanded(flex: 3, child: TextField(controller: host, decoration: const InputDecoration(labelText: 'Host'))),
                const SizedBox(width: 10),
                Expanded(child: TextField(controller: port, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Port'))),
              ]),
              const SizedBox(height: 10),
              TextField(controller: user, decoration: const InputDecoration(labelText: 'Username')),
              const SizedBox(height: 10),
              TextField(controller: pass, obscureText: true, decoration: const InputDecoration(labelText: 'Password')),
              const SizedBox(height: 10),
              TextField(controller: key, minLines: 2, maxLines: 5, decoration: const InputDecoration(labelText: 'Private key (PEM, optional)')),
              const SizedBox(height: 10),
              TextField(controller: phrase, obscureText: true, decoration: const InputDecoration(labelText: 'Key passphrase (optional)')),
              const SizedBox(height: 16),
              Row(children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: testing
                        ? null
                        : () async {
                            set(() => testing = true);
                            final b = SshSocksBridge();
                            try {
                              await b.start(build());
                              if (context.mounted) toast(context, 'SSH login OK');
                            } catch (e) {
                              if (context.mounted) toast(context, 'SSH failed: $e');
                            } finally {
                              await b.stop();
                              set(() => testing = false);
                            }
                          },
                    child: Text(testing ? 'Testing...' : 'Test'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: () {
                      if (host.text.trim().isEmpty || user.text.trim().isEmpty) {
                        toast(context, 'Host and username are required');
                        return;
                      }
                      final cfg = build();
                      if (edit != null) c.configs.remove(edit);
                      c.configs.add(cfg, select: true);
                      Navigator.pop(ctx);
                    },
                    child: const Text('Save'),
                  ),
                ),
              ]),
            ]),
          ));
    });
  }
}

class _Empty extends StatelessWidget {
  const _Empty({required this.onAdd});
  final VoidCallback onAdd;

  @override
  Widget build(BuildContext context) {
    final x = context.x;
    return Center(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Icon(Icons.hub_rounded, size: 64, color: x.muted.withValues(alpha: 0.5)),
        const SizedBox(height: 14),
        const Text('No configs yet', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
        const SizedBox(height: 4),
        Text('Import a link, subscription or SSH server', style: TextStyle(color: x.muted)),
        const SizedBox(height: 18),
        FilledButton.icon(onPressed: onAdd, icon: const Icon(Icons.add_rounded), label: const Text('Add config')),
      ]),
    );
  }
}

class _ConfigTile extends StatelessWidget {
  const _ConfigTile({required this.config});
  final ServerConfig config;

  @override
  Widget build(BuildContext context) {
    final c = AppScope.of(context);
    final x = context.x;
    final selected = c.configs.selected?.id == config.id;
    final ms = config.latencyMs;
    final msColor = ms == null ? x.muted : (ms < 0 ? x.danger : (ms < 250 ? x.success : x.warning));
    return Dismissible(
      key: ValueKey(config.id),
      direction: DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 24),
        margin: const EdgeInsets.only(bottom: 10),
        decoration: BoxDecoration(color: x.danger.withValues(alpha: 0.25), borderRadius: BorderRadius.circular(24)),
        child: Icon(Icons.delete_rounded, color: x.danger),
      ),
      onDismissed: (_) => c.configs.remove(config),
      child: GlassCard(
        margin: const EdgeInsets.only(bottom: 10),
        tint: selected ? LinearGradient(colors: [x.accent.withValues(alpha: 0.20), x.accent2.withValues(alpha: 0.06)]) : null,
        onTap: () {
          if (c.vpn.isConnected || c.vpn.isBusy) {
            toast(context, 'Disconnect first to switch config');
            return;
          }
          c.configs.select(config);
        },
        onLongPress: () => _menu(context),
        child: Row(children: [
          AnimatedContainer(
            duration: const Duration(milliseconds: 250),
            width: 22,
            height: 22,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: selected ? x.accentGradient : null,
              border: Border.all(color: selected ? Colors.transparent : x.muted.withValues(alpha: 0.5), width: 1.6),
            ),
            child: selected ? Icon(Icons.check_rounded, size: 15, color: x.onAccent) : null,
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(config.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15.5)),
              const SizedBox(height: 4),
              Row(children: [
                Chip2(config.protocol, color: config.kind == ConfigKind.ssh ? x.accent2 : x.accent),
                if (config.outbound?['streamSettings']?['network'] != null) ...[
                  const SizedBox(width: 6),
                  Chip2('${config.outbound!['streamSettings']['network']}'.toUpperCase(), color: x.muted),
                ],
                const SizedBox(width: 8),
                Expanded(child: Text(config.address, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: x.muted, fontSize: 12))),
              ]),
            ]),
          ),
          const SizedBox(width: 10),
          Text(ms == null ? '--' : (ms < 0 ? 'fail' : '$ms ms'), style: TextStyle(color: msColor, fontWeight: FontWeight.w700, fontSize: 12.5)),
        ]),
      ),
    );
  }

  void _menu(BuildContext context) {
    final c = AppScope.of(context);
    showGlassSheet(context, (ctx) => ListView(shrinkWrap: true, padding: const EdgeInsets.fromLTRB(8, 12, 8, 16), children: [
          if (config.link != null)
            ListTile(
              leading: const Icon(Icons.copy_rounded),
              title: const Text('Copy share link'),
              onTap: () {
                Clipboard.setData(ClipboardData(text: config.link!));
                Navigator.pop(ctx);
                toast(context, 'Link copied');
              },
            ),
          ListTile(
            leading: const Icon(Icons.edit_rounded),
            title: const Text('Rename'),
            onTap: () {
              Navigator.pop(ctx);
              final t = TextEditingController(text: config.name);
              showGlassSheet(context, (c2) => Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(mainAxisSize: MainAxisSize.min, children: [
                      TextField(controller: t, autofocus: true, decoration: const InputDecoration(labelText: 'Name')),
                      const SizedBox(height: 14),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: () {
                            config.name = t.text.trim().isEmpty ? config.name : t.text.trim();
                            c.configs.update(config);
                            Navigator.pop(c2);
                          },
                          child: const Text('Save'),
                        ),
                      ),
                    ]),
                  ));
            },
          ),
          ListTile(
            leading: const Icon(Icons.speed_rounded),
            title: const Text('Test latency'),
            onTap: () async {
              Navigator.pop(ctx);
              config.latencyMs = await NetTools.tcpPing(config.host, config.port);
              c.configs.update(config);
            },
          ),
          ListTile(
            leading: Icon(Icons.delete_rounded, color: ctx.x.danger),
            title: Text('Delete', style: TextStyle(color: ctx.x.danger)),
            onTap: () {
              Navigator.pop(ctx);
              c.configs.remove(config);
            },
          ),
        ]));
  }
}
