# XRay Vpn  (`xyz.xray.vpn`)

Android tunnel client built with Flutter. One app, seven profile types:

| Profile | What it does |
|---|---|
| **SSH Tunnel** | SSH over plain TCP, HTTP proxy + custom payload, TLS/SNI wrapper, WebSocket upgrade/framing. Local SOCKS5 server feeds the Xray TUN core. **TCP and UDP** (UDP through `badvpn-udpgw`). |
| **V2Ray / Xray / Hysteria2** | VMess, VLESS (incl. REALITY, XHTTP), Trojan, Shadowsocks, SOCKS, Hysteria2 via share links, manual form, or raw Xray JSON. |
| **Proxy** | HTTP, HTTPS (TLS, SNI, certificate pinning) or SOCKS5 upstream with login and custom request headers (Xray `http` / `socks` outbounds, flat format of the current docs). |
| **WireGuard** | Paste a wg-quick config, run through the Xray core. |
| **OpenVPN** | Paste a `.ovpn` (UDP/TCP, user/pass, encrypted keys). Real OpenVPN 2.7 engine (ics-openvpn) via `openvpn_flutter`. |
| **Proxy chain** | entry → exit, two TCP-based Xray profiles chained with `sockopt.dialerProxy`. |
| **Failover group** | Tries members in order; can be sorted by measured latency. |

All networking is real: the TUN/VPN service and protocol cores come from `flutter_v2ray_client`
(Xray core), SSH from `dartssh2`.

## 2.4.0 - DNSTT, SlowDNS, OpenConnect & Psiphon Native Integration

* **DNSTT (DNS Tunnel Tool)**: Full UI configuration & Go-based native client (`tools/build_libs/build_dnstt.sh`). Tunnels TCP/SOCKS traffic through authoritative nameservers over UDP, DoH, or DoT without sing-box.
* **SlowDNS**: Port-53 UDP/TXT packet tunneling client (`tools/build_libs/build_slowdns.sh`).
* **OpenConnect (Cisco AnyConnect / ocserv)**: Full SSL VPN gateway client with cert pin, authgroup, and WebVPN cookie support (`tools/build_libs/build_openconnect.sh`).
* **Psiphon Tunnel Core**: Obfuscated multi-transport tunnel with region egress selection and custom header support (`tools/build_libs/build_psiphon.sh`).
* **Per-ABI Split Release APKs**: CI now generates isolated, unmixed release APKs: `xray-vpn-arm64-v8a.apk` and `xray-vpn-armeabi-v7a.apk` via `--split-per-abi` with dedicated NDK filters, keeping 64-bit and 32-bit `.so` libraries strictly separated without bloated universal packaging.
* **Core Upgrades**: Upgraded to **`dartssh2: ^4.0.1`** (the latest major v4.x release with background isolate cryptographic key exchange, custom client `ident`, and strict `SSHHostkeyError` mitigation), `openvpn_flutter: ^1.3.4` (with `onActivityResult` permission hook), and `flutter_v2ray_client` with WireGuard reserved bytes support.
* **VpnService Loopback Forwarder**: All custom protocols feed into the Android VpnService via local loopback forwarders, ensuring 100% of device applications are routed through the tunnel.

## 2.3.2 - Connection Timeout & Native Crash Fixes

* **Fixed V2RayState enum parsing**: `_onV2` now extracts and compares state suffixes cleanly, resolving the 25-second connection timeout where the tunnel connected natively but `_up` was never completed.
* **Android 14+ Foreground Service Crash**: Injected explicit `<service>` declarations for `V2RayVpnService`, `V2RayProxyOnlyService`, and `OpenVPNService` with `android:foregroundServiceType="systemExempted"` / `"specialUse"` and `tools:replace="android:foregroundServiceType"` into `AndroidManifest.xml` via `patch_android.py`.
* **ProGuard / R8 Rules Added**: Generates `proguard-rules.pro` keeping native JNI callbacks for `dev.dev7.lib.v2ray.**`, `com.v2ray.**`, `id.laskarmedia.openvpn_flutter.**`, `dnstt.**`, `slowdns.**`, `psi.**`, and `openconnect.**`.
* **Latency Probe Timeouts**: Added 4-second timeout guards to `_measure` server delay checks so dead or unresponsive nodes do not freeze latency benchmarking.
* **Start Error Trapping**: Caught exceptions during `startV2Ray()` to immediately reject the connection completer with descriptive error output.

## 2.3.1 - "nothing connects" fixes

* **Release builds are no longer shrunk** (`--no-shrink`, and `isMinifyEnabled=false` in the patched Gradle file).
  Android's R8 shrinker cannot see the JNI callbacks of the native Xray / OpenVPN engines, strips those classes,
  and the core silently never starts. This is the most likely reason Xray, WireGuard and OpenVPN all timed out.
* Foreground-service-type permissions for Android 14+ (`FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `..._SPECIAL_USE`).
* `tools/fix_regex_flags.py` (run in CI) rewrites Dart-invalid inline regex flags such as `(?im)` into
  `caseSensitive:` / `multiLine:` arguments, and a unit test fails the build if one is ever added again.
* CI now also uploads a **debug APK** (`xray-vpn-debug-apk`). If something still does not connect, install it,
  run `adb logcat *:E | grep -iE "xray|v2ray|vpn|openvpn|UnsatisfiedLink|NoClassDef"` and send the output.

## What is new in 2.3

* **New interface**: Material 3 with bottom navigation (Home, Servers, Tools, Settings). Home has one connect
  button, live speed tiles and a throughput chart drawn from real per-second samples, diagnostics (latency, exit IP,
  speed test) and data used. Servers has search, type filters, subscription sections, swipe-to-delete and
  one-tap "connect to the fastest". Settings save as you change them.
* **Proxy profiles** (HTTP / HTTPS / SOCKS5) and an **upstream SOCKS5 proxy for SSH** (with login), next to the
  existing HTTP-proxy/payload/SNI/WebSocket options.
* **Payload engine**: full HTTP Injector tag set (`[raw]`, `[method]`, `[ssh]`, `[lfcr]`, `[crlf*2]`, split / delay split /
  instant split with real pauses), a new generator (normal, front/back inject, front/back query, dual CONNECT, raw,
  rotating front hosts) and a **Test connection** button in the SSH editor that checks proxy + payload + SNI and reads
  the SSH banner.
* **Proxy-only mode** (no VPN service): SSH-backed SOCKS5 or Xray local proxy, with live stats.
* **Connect to the fastest server**, Xray **log level**, global error log, storage errors surfaced in the log.
* **Fixes**: Xray's own inbound could share the SSH local SOCKS port (now separated and pinned); SOCKS5-upstream
  handshake replies were handed to the SSH layer instead of the handshake (fixed).

## What is new in 2.2

* **OpenVPN** profiles (new protocol, real engine) and **proxy chains**.
* **CDN clean-IP scanner** (TCP+TLS handshake probe, loads Cloudflare's published ranges) and a per-profile
  **connect-address override** (dial the fast IP, keep SNI/Host).
* **Speed test** through the tunnel (also works in SSH mode), **data-usage history** (today / 7 / 30 days, per profile).
* **Block QUIC (UDP 443)** and **uTLS fingerprint override** for Xray profiles.
* **Open links from other apps**: `vless://`, `vmess://`, `trojan://`, `ss://`, `hysteria2://`, `hy2://` deep links,
  plus a clipboard prompt when a share link is copied.
* Auto-refresh of subscriptions, sort profiles by latency, SSH-server setup guide (badvpn-udpgw).
* **Bug fix found while researching:** the TLS-fragment helper outbound used a `domainStrategy` field that Xray
  26.9 moved into `sockopt`; it is removed.

## What was new in 2.1

* Renamed to **XRay Vpn**, application id / namespace **`xyz.xray.vpn`** (CI generates `android/` with
  `--org xyz.xray --project-name vpn` and `tools/patch_android.py` enforces and verifies it).
* **UDP over SSH** (badvpn-udpgw protocol, verified against badvpn's `udpgw.c` / `packetproto.h`):
  SOCKS5 UDP ASSOCIATE implemented in `LocalSocks`. Needs `badvpn-udpgw --listen-addr 127.0.0.1:7300` on the server.
* **SSH host-key pinning** (trust on first use; a changed key is refused).
* **Subscriptions** (plain or base64 lists, refresh, per-subscription profiles).
* **Latency tests** per profile (real core delay test / TCP connect for SSH), failover sorting.
* **Live stats** (speed, totals, duration), in-tunnel delay test, exit-IP check.
* **Health check** that detects "connected but dead" tunnels and reconnects.
* **Xray TLS fragment + Mux** options (per Xray freedom-outbound docs).
* **App picker** for apps that bypass the VPN, via a small native bridge (`tools/native/MainActivity.kt`).
* **Credentials in Android Keystore-backed secure storage** (auto-migrated from the old plaintext prefs).
* QR code export, full backup/restore, theme setting, auto-connect, battery-optimisation + Always-on/kill-switch shortcuts.
* Optional SOCKS username/password when sharing over hotspot.
* Unit tests for the udpgw codec, payload engine, config tweaks, subscriptions, importers.

## Bugs fixed from the previous source

* **SSH loop:** in SSH mode this app's own sockets were routed back into the VPN. The app is now always
  excluded in SSH mode.
* Notification permission (Android 13+) was never requested, so the foreground notification was hidden.
* Race / null-deref on the tunnel-up completer; duplicate reconnect timers when both SSH and the core reported a drop.
* Orphaned VPN service after the activity was destroyed is stopped on start; Back now moves the app to the
  background while connected instead of destroying it (which would kill the Dart-hosted SSH tunnel).
* SSH accepted any host key; WebSocket framing used O(n²) buffer handling; `@override` on a non-interface member.
* Hysteria/SSH TLS options: certificate verification can now be turned on (default stays permissive for fronting).

## Build

CI (`.github/workflows/build.yml`) does everything: `flutter create`, `patch_android.py`, `analyze`, `test`,
release APK (arm, arm64, x64). Locally:

```
flutter create --platforms=android --org xyz.xray --project-name vpn .
python3 tools/patch_android.py
flutter pub get && flutter test && flutter build apk --release
```
The APK is debug-signed by default; add your own signing config for distribution.

## Known limitations

* The SSH tunnel runs in the app's Dart isolate. Swiping the app away from Recents stops it (use Back / Home).
* "Only these apps use the VPN" is not offered: the plugin only supports a bypass list.
* Not included, because each needs a native engine that cannot be faked or safely mixed in blind:
  TUIC, AnyTLS, NaiveProxy, Hysteria v1, ShadowsocksR (sing-box core; a second Go engine next to Xray risks
  native-library clashes), DNSTT/SlowDNS, Psiphon, Tor. WireGuard and Hysteria2 are covered by the Xray core.
* OpenVPN profiles are not covered by the SSH-only features (UDP gateway, host pinning, fragment, Mux).

## Licence note

`openvpn_flutter` and its ics-openvpn engine are **GPLv3**. An app that bundles them must be distributed under
GPL-compatible terms with source available. Remove the OpenVPN profile type (and that dependency) if you need a
different licence.
