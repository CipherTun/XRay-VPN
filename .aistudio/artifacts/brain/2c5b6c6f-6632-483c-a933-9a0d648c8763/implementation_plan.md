# Core Upgrades & Split APK Build Pipeline Complete

## Accomplishments

1. **Core Protocol Upgrades (Latest Upstream Releases)**
   - **Xray & WireGuard Core**: Supports VLESS Vision, REALITY (`pbk`, `sid`, `spx`), XHTTP, gRPC, and WireGuard outbounds with Cloudflare Warp / custom router `reserved` bytes parsing and generation.
   - **SSH Core**: `dartssh2: ^2.11.0` (pure-Dart async socket engine with Ed25519, SHA-2, keepalive, and WebSocket framing) alongside badvpn UDPGW (`lib/udpgw.dart`).
   - **OpenVPN Core**: Upgraded to `openvpn_flutter: ^2.0.4` with official `ics-openvpn` 0.7.50+ core, configured with Android 14+ foreground service types (`systemExempted`) and JNI ProGuard rules.
   - **Dependencies Aligned**: `shared_preferences: ^2.3.2`, `flutter_secure_storage: ^9.2.2`, `permission_handler: ^11.3.1`.

2. **Strict Per-ABI Split APK Build System**
   - **`tools/patch_android.py`**:
     - Enforces `splits.abi { enable = true, include("arm64-v8a", "armeabi-v7a"), universalApk = false }`.
     - Injects `ndk.abiFilters "arm64-v8a", "armeabi-v7a"` into `defaultConfig`.
     - Ensures native `.so` files are never cross-pollinated: `app-arm64-v8a-release.apk` contains only 64-bit binaries; `app-armeabi-v7a-release.apk` contains only 32-bit binaries.
   - **`.github/workflows/build.yml`**:
     - Compiles native AARs targeting `android/arm,android/arm64`.
     - Executes `flutter build apk --release --no-shrink --split-per-abi --target-platform android-arm,android-arm64`.
     - Uploads separate release artifacts:
       - `xray-vpn-arm64-v8a-apk`
       - `xray-vpn-armeabi-v7a-apk`
       - `xray-vpn-debug-arm64-apk`

3. **Packaging & Verification**
   - Verified Dart regex compliance (`tools/fix_regex_flags.py`).
   - Verified shell scripts syntax (`bash -n`).
   - Re-packaged updated codebase into `public/xray-vpn-fixed.zip` and `public/assets/xray-vpn-fixed.zip`.
   - Native Android app verified and compiling cleanly with `compile_applet`.
