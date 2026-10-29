import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/server_config.dart';
import '../services/link_parser.dart';
import '../services/net_tools.dart';

class ConfigStore extends ChangeNotifier {
  ConfigStore._(this._prefs);
  final SharedPreferences _prefs;

  final List<ServerConfig> items = [];
  final List<Subscription> subscriptions = [];
  String? selectedId;

  static Future<ConfigStore> load(SharedPreferences prefs) async {
    final s = ConfigStore._(prefs);
    try {
      final raw = prefs.getString('configs_v1');
      if (raw != null) {
        s.items.addAll((jsonDecode(raw) as List).map((e) => ServerConfig.fromJson(Map<String, dynamic>.from(e))));
      }
      final subs = prefs.getString('subs_v1');
      if (subs != null) {
        s.subscriptions.addAll((jsonDecode(subs) as List).map((e) => Subscription.fromJson(Map<String, dynamic>.from(e))));
      }
    } catch (_) {}
    s.selectedId = prefs.getString('selected_v1');
    return s;
  }

  ServerConfig? get selected {
    for (final c in items) {
      if (c.id == selectedId) return c;
    }
    return items.isEmpty ? null : items.first;
  }

  void _save() {
    _prefs.setString('configs_v1', jsonEncode(items.map((e) => e.toJson()).toList()));
    _prefs.setString('subs_v1', jsonEncode(subscriptions.map((e) => e.toJson()).toList()));
    if (selectedId != null) _prefs.setString('selected_v1', selectedId!);
    notifyListeners();
  }

  void select(ServerConfig c) {
    selectedId = c.id;
    _save();
  }

  void add(ServerConfig c, {bool select = false}) {
    items.add(c);
    if (select || selectedId == null) selectedId = c.id;
    _save();
  }

  void update(ServerConfig c) => _save();

  void remove(ServerConfig c) {
    items.remove(c);
    if (selectedId == c.id) selectedId = items.isEmpty ? null : items.first.id;
    _save();
  }

  /// Imports share links / base64 subscriptions; returns how many were added.
  int importText(String text, {String? subscriptionId}) {
    final parsed = LinkParser.parseText(text);
    for (final c in parsed) {
      c.subscriptionId = subscriptionId;
      items.add(c);
    }
    if (parsed.isNotEmpty && selectedId == null) selectedId = parsed.first.id;
    _save();
    return parsed.length;
  }

  Future<int> addSubscription(String name, String url) async {
    final body = await NetTools.fetch(url);
    final sub = Subscription(id: DateTime.now().microsecondsSinceEpoch.toRadixString(36), name: name, url: url, updated: DateTime.now());
    subscriptions.add(sub);
    return importText(body, subscriptionId: sub.id);
  }

  Future<int> refreshSubscription(Subscription sub) async {
    final body = await NetTools.fetch(sub.url);
    final parsed = LinkParser.parseText(body);
    if (parsed.isEmpty) return 0;
    final keep = selectedId;
    final oldSelected = items.where((c) => c.id == keep).firstOrNull;
    items.removeWhere((c) => c.subscriptionId == sub.id);
    for (final c in parsed) {
      c.subscriptionId = sub.id;
      items.add(c);
    }
    if (oldSelected != null && oldSelected.subscriptionId == sub.id) {
      selectedId = parsed.where((c) => c.name == oldSelected.name).firstOrNull?.id ?? parsed.first.id;
    }
    sub.updated = DateTime.now();
    _save();
    return parsed.length;
  }

  void removeSubscription(Subscription sub) {
    items.removeWhere((c) => c.subscriptionId == sub.id);
    subscriptions.remove(sub);
    if (items.every((c) => c.id != selectedId)) selectedId = items.isEmpty ? null : items.first.id;
    _save();
  }

  void notify() => notifyListeners();
}
