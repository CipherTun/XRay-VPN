import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'core/state/app_controller.dart';
import 'core/theme/app_theme.dart';
import 'features/shell/home_shell.dart';

class AppScope extends InheritedWidget {
  const AppScope({super.key, required this.controller, required super.child});
  final AppController controller;

  static AppController of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppScope>()!.controller;

  @override
  bool updateShouldNotify(AppScope oldWidget) => controller != oldWidget.controller;
}

class XRayApp extends StatelessWidget {
  const XRayApp({super.key, required this.controller});
  final AppController controller;

  @override
  Widget build(BuildContext context) {
    return AppScope(
      controller: controller,
      child: ListenableBuilder(
        listenable: controller.settings,
        builder: (context, _) {
          final s = controller.settings;
          final mode = switch (s.themeMode) {
            1 => ThemeMode.light,
            2 => ThemeMode.dark,
            _ => ThemeMode.system,
          };
          return MaterialApp(
            title: 'XRay VPN',
            debugShowCheckedModeBanner: false,
            themeMode: mode,
            theme: buildTheme(s, Brightness.light),
            darkTheme: buildTheme(s, Brightness.dark),
            builder: (context, child) {
              final dark = Theme.of(context).brightness == Brightness.dark;
              SystemChrome.setSystemUIOverlayStyle(SystemUiOverlayStyle(
                statusBarColor: Colors.transparent,
                systemNavigationBarColor: Colors.transparent,
                statusBarIconBrightness: dark ? Brightness.light : Brightness.dark,
                systemNavigationBarIconBrightness: dark ? Brightness.light : Brightness.dark,
              ));
              return child!;
            },
            home: const HomeShell(),
          );
        },
      ),
    );
  }
}
