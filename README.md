# XRay Vpn

The authoritative Flutter application is in [`flutter_project/my-tunnel/`](flutter_project/my-tunnel/).
The Android Xray engine is built from the official XTLS/libXray upstream release; no
prebuilt placeholder library is committed.

Read [`REAL_IMPLEMENTATION_AUDIT.md`](REAL_IMPLEMENTATION_AUDIT.md) for migration details
and the exact verification limits. The source has not been compiled or live-tested on a
device in this environment, so it must not be described as production-verified until the
CI build and real connection test matrix pass.

GitHub Actions is configured at `.github/workflows/build.yml` to build the libXray AAR,
run Flutter analysis/tests, and produce separate ARM64 and ARM32 APK artifacts.
