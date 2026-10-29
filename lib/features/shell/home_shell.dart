import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app.dart';
import '../../core/theme/app_theme.dart';
import '../../core/widgets/glass.dart';
import '../configs/configs_page.dart';
import '../dashboard/dashboard_page.dart';
import '../logs/logs_page.dart';
import '../tools/tools_page.dart';

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _i = 0;

  void _go(int i) {
    if (AppScope.of(context).settings.haptics) HapticFeedback.selectionClick();
    setState(() => _i = i);
  }

  @override
  Widget build(BuildContext context) {
    return AuroraBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        extendBody: true,
        body: IndexedStack(index: _i, children: [
          DashboardPage(onOpenConfigs: () => _go(1)),
          const ConfigsPage(),
          const LogsPage(),
          const ToolsPage(),
        ]),
        bottomNavigationBar: _GlassNavBar(index: _i, onChanged: _go),
      ),
    );
  }
}

class _GlassNavBar extends StatelessWidget {
  const _GlassNavBar({required this.index, required this.onChanged});
  final int index;
  final ValueChanged<int> onChanged;

  static const _items = [
    (Icons.space_dashboard_rounded, 'Dashboard'),
    (Icons.hub_rounded, 'Configs'),
    (Icons.terminal_rounded, 'Logs'),
    (Icons.handyman_rounded, 'Tools'),
  ];

  @override
  Widget build(BuildContext context) {
    final x = context.x;
    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 0, 18, 12),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(32),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: x.blur + 6, sigmaY: x.blur + 6),
            child: Container(
              height: 70,
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: x.isDark ? Colors.white.withValues(alpha: 0.08) : Colors.white.withValues(alpha: 0.65),
                borderRadius: BorderRadius.circular(32),
                border: Border.all(color: x.glassBorder),
              ),
              child: Row(children: [
                for (var i = 0; i < _items.length; i++)
                  Expanded(
                    child: GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTap: () => onChanged(i),
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 280),
                        curve: Curves.easeOutCubic,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(26),
                          gradient: i == index
                              ? LinearGradient(colors: [x.accent.withValues(alpha: 0.30), x.accent2.withValues(alpha: 0.16)])
                              : null,
                          border: i == index ? Border.all(color: x.accent.withValues(alpha: 0.45)) : null,
                        ),
                        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                          Icon(_items[i].$1, size: 23, color: i == index ? x.accent : x.muted),
                          const SizedBox(height: 3),
                          Text(_items[i].$2,
                              style: TextStyle(
                                fontSize: 10.5,
                                fontWeight: i == index ? FontWeight.w800 : FontWeight.w500,
                                color: i == index ? x.text : x.muted,
                              )),
                        ]),
                      ),
                    ),
                  ),
              ]),
            ),
          ),
        ),
      ),
    );
  }
}
