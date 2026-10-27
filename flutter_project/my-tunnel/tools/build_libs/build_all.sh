#!/usr/bin/env bash
set -euo pipefail
echo "ERROR: refusing to generate placeholder native VPN engines." >&2
echo "DNSTT, SlowDNS, OpenConnect and Psiphon require verified upstream integrations." >&2
exit 1
