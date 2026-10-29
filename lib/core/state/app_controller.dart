import 'package:shared_preferences/shared_preferences.dart';

import 'app_settings.dart';
import 'config_store.dart';
import 'log_service.dart';
import 'vpn_controller.dart';

class AppController {
  AppController._(this.settings, this.configs, this.logs, this.vpn);

  final AppSettings settings;
  final ConfigStore configs;
  final LogService logs;
  final VpnController vpn;

  static Future<AppController> create() async {
    final prefs = await SharedPreferences.getInstance();
    final settings = await AppSettings.load(prefs);
    final configs = await ConfigStore.load(prefs);
    final logs = LogService();
    final vpn = VpnController(settings: settings, configs: configs, logs: logs);
    final c = AppController._(settings, configs, logs, vpn);
    logs.info('app', 'XRay VPN started');
    if (settings.autoConnect && configs.selected != null) {
      Future.delayed(const Duration(milliseconds: 800), vpn.connect);
    }
    return c;
  }
}
