import 'dart:async';

import 'package:flutter/foundation.dart';

enum LogLevel { debug, info, warning, error }

class LogEntry {
  LogEntry(this.level, this.source, this.message) : time = DateTime.now();
  final DateTime time;
  final LogLevel level;
  final String source;
  final String message;

  String format() {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${two(time.hour)}:${two(time.minute)}:${two(time.second)} [${level.name.toUpperCase()}] $source: $message';
  }
}

class LogService extends ChangeNotifier {
  final List<LogEntry> _items = [];
  Timer? _debounce;

  List<LogEntry> get items => _items;

  void add(LogLevel level, String source, String message) {
    _items.add(LogEntry(level, source, message));
    if (_items.length > 3000) _items.removeRange(0, 500);
    _debounce ??= Timer(const Duration(milliseconds: 200), () {
      _debounce = null;
      notifyListeners();
    });
  }

  void info(String src, String msg) => add(LogLevel.info, src, msg);
  void warn(String src, String msg) => add(LogLevel.warning, src, msg);
  void error(String src, String msg) => add(LogLevel.error, src, msg);

  void clear() {
    _items.clear();
    notifyListeners();
  }

  String export([Iterable<LogEntry>? entries]) => (entries ?? _items).map((e) => e.format()).join('\n');
}
