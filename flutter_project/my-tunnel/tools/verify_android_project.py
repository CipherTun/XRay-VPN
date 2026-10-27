#!/usr/bin/env python3
"""Fail-fast source/manifest checks after Flutter generates the Android project."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
android = root / "android"
manifest_path = android / "app/src/main/AndroidManifest.xml"
manifest = manifest_path.read_text(encoding="utf-8") if manifest_path.exists() else ""
source_dir = android / "app/src/main/kotlin/xyz/xray/vpn"
required_sources = ("MainActivity.kt", "XrayVpnService.kt", "TunnelDaemonService.kt")
errors = []

if not manifest:
    errors.append("generated AndroidManifest.xml is missing")
for filename in required_sources:
    generated = source_dir / filename
    source = root / "tools/native" / filename
    if not generated.is_file():
        errors.append(f"required generated Kotlin file is missing: {generated.relative_to(root)}")
    elif source.is_file() and generated.read_bytes() != source.read_bytes():
        errors.append(f"generated Kotlin file is stale: {generated.relative_to(root)}")

if 'android:name="xyz.xray.vpn.XrayVpnService"' not in manifest:
    errors.append("XrayVpnService is not registered in the manifest")
if 'android:permission="android.permission.BIND_VPN_SERVICE"' not in manifest:
    errors.append("VPN service BIND_VPN_SERVICE protection is missing")
if 'android:name="android.permission.FOREGROUND_SERVICE"' not in manifest:
    errors.append("FOREGROUND_SERVICE permission is missing")
if 'android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"' not in manifest:
    errors.append("FOREGROUND_SERVICE_SYSTEM_EXEMPTED permission is missing")
if 'android:foregroundServiceType="systemExempted"' not in manifest:
    errors.append("XrayVpnService lacks its Android 14+ VPN foreground service type")
if 'android:name="android.net.VpnService"' not in manifest:
    errors.append("VpnService intent filter is missing")
if not (android / "app/build.gradle.kts").exists() and not (android / "app/build.gradle").exists():
    errors.append("generated Android app Gradle file is missing")

# A stale or incorrect namespace can compile but break service lookup at runtime.
for filename in required_sources:
    path = source_dir / filename
    if path.is_file() and not re.search(r"(?m)^package xyz\.xray\.vpn\s*$", path.read_text(encoding="utf-8")):
        errors.append(f"wrong Kotlin package declaration in {filename}")

if errors:
    print("ANDROID PROJECT VERIFICATION FAILED:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)
print("Android project verification passed: native VPN service, manifest and Kotlin sources agree.")
