import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app.dart';
import '../../core/services/net_tools.dart';
import '../../core/state/vpn_controller.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils.dart';
import '../../core/widgets/glass.dart';
import '../../core/widgets/power_button.dart';
import '../../core/widgets/speed_chart.dart';
import '../settings/settings_page.dart';

class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key, required this.onOpenConfigs});
  final VoidCallback onOpenConfigs;

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  VpnState? _last;
  Map<String, dynamic>? _ip;
  bool _ipLoading = false;
  bool _wired = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_wired) return;
    _wired = true;
    AppScope.of(context).vpn.addListener(_onVpn);
  }

  @override
  void dispose() {
    AppScope.of(context).vpn.removeListener(_onVpn);
    super.dispose();
  }

  void _onVpn() {
    final v = AppScope.of(context).vpn;
    if (v.state == _last) return;
    _last = v.state;
    if (v.state == VpnState.connected) {
      _fetchIp();
    } else if (v.state == VpnState.disconnected) {
      if (mounted) setState(() => _ip = null);
    }
  }

  Future<void> _fetchIp() async {
    final v = AppScope.of(context).vpn;
    setState(() => _ipLoading = true);
    await Future.delayed(const Duration(milliseconds: 900));
    try {
      final r = await NetTools.ipInfo(proxyPort: v.httpPort);
      if (mounted && v.isConnected) setState(() => _ip = r);
    } catch (_) {
      if (mounted) setState(() => _ip = null);
    } finally {
      if (mounted) setState(() => _ipLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = AppScope.of(context);
    final x = context.x;
    return SafeArea(
      bottom: false,
      child: ListenableBuilder(
        listenable: Listenable.merge([c.vpn, c.configs]),
        builder: (context, _) {
          final v = c.vpn;
          final sel = c.configs.selected;
          final status = switch (v.state) {
            VpnState.connected => 'Protected',
            VpnState.connecting => 'Connecting...',
            VpnState.disconnecting => 'Disconnecting...',
            VpnState.disconnected => 'Not connected',
          };
          final statusColor = v.isConnected ? x.success : (v.isBusy ? x.warning : x.muted);

          return ListView(
            padding: const EdgeInsets.only(bottom: 120),
            children: [
              PageHeader(
                title: 'XRay VPN',
                subtitle: 'Secure tunnel \u00b7 TUN mode',
                actions: [
                  GlassIconButton(
                    icon: Icons.tune_rounded,
                    tooltip: 'Settings',
                    onTap: () => Navigator.of(context).push(PageRouteBuilder(
                      opaque: false,
                      transitionDuration: const Duration(milliseconds: 320),
                      pageBuilder: (_, __, ___) => const SettingsPage(),
                      transitionsBuilder: (_, a, __, child) => FadeTransition(
                        opacity: CurvedAnimation(parent: a, curve: Curves.easeOut),
                        child: SlideTransition(
                          position: Tween(begin: const Offset(0, 0.04), end: Offset.zero).animate(CurvedAnimation(parent: a, curve: Curves.easeOutCubic)),
                          child: child,
                        ),
                      ),
                    )),
                  ),
                ],
              ),
              Center(
                child: PowerButton(
                  state: v.state,
                  onTap: () {
                    if (c.settings.haptics) HapticFeedback.mediumImpact();
                    v.toggle();
                  },
                ),
              ),
              Center(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 250),
                  child: Text(status,
                      key: ValueKey(status),
                      style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: statusColor, letterSpacing: -0.3)),
                ),
              ),
              const SizedBox(height: 4),
              Center(
                child: Text(
                  v.isConnected ? fmtDuration(v.uptime) : (v.lastError ?? 'Tap to connect'),
                  style: TextStyle(color: v.lastError != null && !v.isConnected ? x.danger : x.muted, fontSize: 13),
                  textAlign: TextAlign.center,
                ),
              ),
              const SizedBox(height: 22),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: GlassCard(
                  onTap: widget.onOpenConfigs,
                  child: Row(children: [
                    Container(
                      width: 46,
                      height: 46,
                      decoration: BoxDecoration(gradient: x.accentGradient, borderRadius: BorderRadius.circular(15)),
                      child: Icon(sel?.kind.name == 'ssh' ? Icons.terminal_rounded : Icons.bolt_rounded, color: x.onAccent),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(sel?.name ?? 'No config selected',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                        const SizedBox(height: 3),
                        Text(sel == null ? 'Tap to add your first config' : sel.address,
                            maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: x.muted, fontSize: 12.5)),
                      ]),
                    ),
                    if (sel != null) Chip2(sel.protocol),
                    const SizedBox(width: 6),
                    Icon(Icons.chevron_right_rounded, color: x.muted),
                  ]),
                ),
              ),
              const SizedBox(height: 12),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: Row(children: [
                  Expanded(child: _Stat(icon: Icons.south_rounded, label: 'Download', value: fmtSpeed(v.downSpeed), sub: fmtBytes(v.downTotal), color: x.accent)),
                  const SizedBox(width: 12),
                  Expanded(child: _Stat(icon: Icons.north_rounded, label: 'Upload', value: fmtSpeed(v.upSpeed), sub: fmtBytes(v.upTotal), color: x.accent2)),
                ]),
              ),
              const SizedBox(height: 12),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: GlassCard(
                  padding: const EdgeInsets.fromLTRB(16, 14, 16, 12),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      Text('Live traffic', style: TextStyle(color: x.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
                      const Spacer(),
                      _dot(x.accent),
                      Text(' down  ', style: TextStyle(color: x.muted, fontSize: 11)),
                      _dot(x.accent2),
                      Text(' up', style: TextStyle(color: x.muted, fontSize: 11)),
                    ]),
                    const SizedBox(height: 10),
                    SizedBox(height: 90, child: SpeedChart(down: v.downHistory, up: v.upHistory, c1: x.accent, c2: x.accent2)),
                  ]),
                ),
              ),
              const SizedBox(height: 12),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: GlassCard(
                  child: Row(children: [
                    Icon(Icons.public_rounded, color: x.accent),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text('Public IP', style: TextStyle(color: x.muted, fontSize: 12)),
                        const SizedBox(height: 2),
                        Text(
                          _ipLoading ? 'Checking...' : (_ip?['ip']?.toString() ?? (v.isConnected ? 'Unavailable' : '\u2014')),
                          style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                        ),
                        if (_ip != null)
                          Text('${_ip!['city'] ?? ''}, ${_ip!['country'] ?? ''}  \u00b7  ${(_ip!['connection'] as Map?)?['isp'] ?? ''}',
                              maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: x.muted, fontSize: 12)),
                      ]),
                    ),
                    if (v.isConnected) IconButton(onPressed: _fetchIp, icon: Icon(Icons.refresh_rounded, color: x.muted)),
                  ]),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _dot(Color c) => Container(width: 8, height: 8, decoration: BoxDecoration(color: c, shape: BoxShape.circle));
}

class _Stat extends StatelessWidget {
  const _Stat({required this.icon, required this.label, required this.value, required this.sub, required this.color});
  final IconData icon;
  final String label, value, sub;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final x = context.x;
    return GlassCard(
      padding: const EdgeInsets.all(14),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
            padding: const EdgeInsets.all(6),
            decoration: BoxDecoration(color: color.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(10)),
            child: Icon(icon, size: 16, color: color),
          ),
          const SizedBox(width: 8),
          Text(label, style: TextStyle(color: x.muted, fontSize: 12.5)),
        ]),
        const SizedBox(height: 10),
        Text(value, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17)),
        const SizedBox(height: 2),
        Text('Total $sub', style: TextStyle(color: x.muted, fontSize: 11.5)),
      ]),
    );
  }
}
