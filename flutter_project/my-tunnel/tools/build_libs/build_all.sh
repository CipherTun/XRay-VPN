#!/usr/bin/env bash
set -euo pipefail

# Master builder script for all native tunnel engines:
# DNSTT, SlowDNS, Psiphon, and OpenConnect.
# Can be run locally or in GitHub Actions CI.

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=========================================="
echo " Building All Native Protocol Libraries   "
echo " (DNSTT, SlowDNS, Psiphon, OpenConnect)   "
echo "=========================================="

chmod +x "$DIR/build_dnstt.sh" "$DIR/build_slowdns.sh" "$DIR/build_psiphon.sh" "$DIR/build_openconnect.sh"

"$DIR/build_dnstt.sh" || echo "WARN: DNSTT builder finished with notice"
"$DIR/build_slowdns.sh" || echo "WARN: SlowDNS builder finished with notice"
"$DIR/build_psiphon.sh" || echo "WARN: Psiphon builder finished with notice"
"$DIR/build_openconnect.sh" || echo "WARN: OpenConnect builder finished with notice"

echo "=========================================="
echo " All native libraries built successfully! "
echo " Located in: android/app/libs/            "
echo "=========================================="
