# XRay Vpn (`xyz.xray.vpn`)

This repository's Android app source of truth is `flutter_project/my-tunnel/`.
The Android Xray engine is built from the official [XTLS/libXray](https://github.com/XTLS/libXray)
repository by its upstream `python3 build/main.py android` script. The generated AAR is not
checked in and no placeholder AAR is produced if the upstream build fails.

## Engine and VPN lifecycle

- Xray/V2Ray share links supported by libXray are converted through its documented
  `Invoke` API (`apiVersion: 3`, `convertShareLinksToXrayJson`). Legacy VMess QR URIs
  have an explicit compatibility parser because upstream libXray documents that format
  as unsupported.
- Android full-device mode requests the system VPN permission, creates a real
  `VpnService.Builder` TUN interface, passes its detached descriptor through the Xray
  configuration's `env.xray.tun.fd`, and starts the managed core using `runXray`.
- The native bridge registers libXray's socket-protection controller and DNS resolver
  before starting the core. The TUN is closed and libXray's DNS resolver reset on stop
  or revocation.
- Proxy-only mode starts the actual Xray core with a local proxy inbound and does not
  create a TUN interface or claim that device-wide routing is active.
- Xray live traffic totals are not yet wired to libXray's metrics endpoint; they must not
  be interpreted as verified byte counts. Android hotspot/tethered-device routing is not
  implemented by this migration.
- Before a connection is started, libXray `pingBatch` performs a real outbound HTTPS
  probe. A failed probe prevents the app from reporting a successful start.
- OpenVPN remains a separate upstream engine (`openvpn_flutter`). Only one Android VPN
  service can own the device TUN at a time, so switching protocols first stops the prior
  engine.
- DNSTT, SlowDNS, OpenConnect, and Psiphon remain disabled. Their former builders were
  stubs and must not be enabled until their actual upstream clients are integrated.

## Build

GitHub Actions builds the upstream libXray AAR and then runs Flutter analysis/tests and
builds separate `arm64-v8a` and `armeabi-v7a` APKs. The AAR is built from the upstream
release ref `v26.10.21` (override only after reviewing the matching API and testing it).

For local Android builds, install Flutter, Go, Python, Git, and the Android NDK, then run
from this directory:

```sh
flutter create --platforms=android --org xyz.xray --project-name vpn .
python3 tools/patch_android.py
python3 tools/build_libxray.py
python3 tools/fix_regex_flags.py
flutter pub get
flutter analyze --no-fatal-infos --no-fatal-warnings
flutter test
flutter build apk --release --split-per-abi --target-platform android-arm,android-arm64
```

## Honest verification status

The source contains the integration and build workflow, but this archive has **not** been
built or installed in an Android runtime in this environment. Do not call it production
verified until CI compiles the pinned AAR and both APK ABIs, Flutter tests pass, and device
checks confirm VPN permission/revocation, TCP/UDP traffic, DNS, IPv4/IPv6 leak behavior,
reconnection, proxy-only behavior, and real server connections. A successful core startup
or APK build alone is not proof of end-to-end VPN connectivity.


### Current Android build behavior

- The workflow resolves the newest published stable libXray release unless an exact tag is supplied through `workflow_dispatch`. It records the release tag, source commit, build number and ABI list inside the APK's `assets/build-info.json`.
- It builds separate ARM32 (`armeabi-v7a`) and ARM64 (`arm64-v8a`) release APKs and matching debug APKs, then uploads each as a GitHub Actions artifact.
- `openvpn_flutter` is pinned to the current compatible 2.x line (`^2.0.0`) and the workflow uses Android NDK r27c.
- The CI checks confirm that the native library and metadata are packaged. They do not prove that a given server profile connects; that still requires real-device testing.
