# XRay-VPN source migration and implementation audit

## What was changed in this revision

### Xray core migration

- Removed `flutter_v2ray_client` from the Flutter dependency manifest and all Dart imports/usages.
- Added a CI build step that resolves the latest published stable XTLS/libXray release by default (with exact-tag override) and invokes its own `python3 build/main.py android` script. The workflow refuses to continue if the real AAR is missing, its required Java API is absent, or ARM64/ARM32 native libraries are missing.
- Added Dart-to-native calls for libXray `Invoke` API version 3: `convertShareLinksToXrayJson`, `pingBatch`, `runXray`, `stopXray`, and `xrayVersion`.
- Kept the legacy VMess QR-code URI format as an explicit compatibility parser because the upstream API documents that it does not parse legacy VMess QR URIs. Other supported share-link formats use the upstream converter.
- Added an Android `VpnService` implementation that requests the real system VPN permission, establishes the TUN interface, sets `env.xray.tun.fd`, installs libXray socket-protection and DNS-controller callbacks, invokes the managed Xray lifecycle API, and closes the TUN descriptor on stop/revocation.
- Added proxy-only operation that runs the actual Xray engine with a local proxy inbound without creating a device TUN.
- Added an outbound preflight using `pingBatch` before starting the managed instance. A failed real probe prevents a success state.
- Updated both GitHub Actions workflow copies to build and verify the upstream libXray runtime for `arm64-v8a` and `armeabi-v7a` rather than building placeholder AARs.

### Other protocols / fake implementation cleanup

- Removed previously seeded fake public servers, demo credentials, and fabricated latency/traffic fallbacks from the audited Kotlin shell.
- DNSTT, SlowDNS, OpenConnect and Psiphon remain explicitly disabled. Their prior AAR builders were stubs, not real protocol clients; this revision does not claim to have implemented these engines.
- OpenVPN continues to use `openvpn_flutter` as a separate VPN engine. Protocol switching stops the prior Xray engine before starting another VPN implementation.
- Removed the duplicate top-level Compose shell and its Gradle project so the archive has one authoritative app implementation. The Flutter project under `flutter_project/my-tunnel/` is the sole build target.

## Upstream sources consulted

- libXray repository and API/build README: https://github.com/XTLS/libXray
- libXray Android wrapper/controller API: https://github.com/XTLS/libXray/blob/main/android_wrapper.go
- Xray Android TUN FD integration: https://github.com/XTLS/Xray-core/blob/main/proxy/tun/tun_android.go
- Android VPN service and TUN lifecycle: https://developer.android.com/develop/connectivity/vpn
- Android `VpnService.protect(int)` API: https://developer.android.com/reference/android/net/VpnService#protect(int)
- Official libXray release list: https://github.com/XTLS/libXray/releases

## Important verification limits

This source ZIP was edited and statically inspected here, but Flutter, Gradle, Android runtime, and real remote server tests were **not available in this environment**. No claim is made that CI has passed, that an APK has been installed, or that the service has been verified on a device. Before production release, the generated AAR and both APK ABIs must compile; Flutter analysis/tests must pass; and device tests must verify VPN consent/revocation, IPv4 and IPv6 routing/leaks, DNS, UDP, proxy-only operation, Wi-Fi/mobile handoff, reconnect, battery/background behavior, and successful traffic through real server profiles.

A successful `runXray` response indicates the managed core accepted startup; it is not alone proof that every destination is reachable. The preflight `pingBatch` is a real outbound test, but it cannot replace end-to-end tests of the TUN path.


## Follow-up build-pipeline fixes (2026-10-27)

- Fixed the generated Android source pipeline to copy `tools/native/XrayVpnService.kt` into `android/app/src/main/kotlin/xyz/xray/vpn/` and fail fast if it is absent.
- Added a pre-build verifier that compares generated Kotlin sources with the maintained source files and checks the VPN service manifest declaration and permissions.
- Updated `openvpn_flutter` from `^1.3.4` to `^2.0.0`, the current package release researched for this update; NDK changed to r27c to meet the plugin's current native build guidance.
- Changed libXray build selection from a hard-coded tag to the latest published stable GitHub release by default, with exact-tag override available in manual workflow dispatch. The resolved release and commit are embedded in `assets/build-info.json` and uploaded alongside APK artifacts.
- Added both ARM32 and ARM64 release/debug APK artifacts and payload checks. These checks establish artifact presence and ABI packaging, not successful VPN traffic forwarding.
- Protected the official `libXray.aar` from the patcher's generic AAR cleanup so gomobile runtime classes are not stripped.

Still not implemented: real DNSTT, SlowDNS, OpenConnect and Psiphon engines; live Xray traffic counters in the UI; hotspot/tethering routing. WireGuard profiles still require validation as true WireGuard tunnels rather than assuming an Xray outbound is equivalent. Flutter analysis, Gradle builds, and live-device tests must run in GitHub Actions; this local environment has no Flutter/Android SDK.
