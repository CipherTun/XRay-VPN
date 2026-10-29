String fmtBytes(num b) {
  const u = ['B', 'KB', 'MB', 'GB', 'TB'];
  var v = b.toDouble();
  var i = 0;
  while (v >= 1024 && i < u.length - 1) {
    v /= 1024;
    i++;
  }
  return '${v.toStringAsFixed(i == 0 ? 0 : (v < 10 ? 2 : 1))} ${u[i]}';
}

String fmtSpeed(num bytesPerSec) => '${fmtBytes(bytesPerSec)}/s';

String fmtDuration(Duration d) {
  String two(int n) => n.toString().padLeft(2, '0');
  final h = d.inHours;
  return '${two(h)}:${two(d.inMinutes % 60)}:${two(d.inSeconds % 60)}';
}
