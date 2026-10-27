#!/usr/bin/env bash
set -euo pipefail

cat >&2 <<'MSG'
ERROR: DNSTT is not buildable from this project yet.
The previous script generated a fake/stub implementation or an empty placeholder AAR;
that artifact was not a working DNSTT tunnel and must not be packaged.
Upstream reference: https://www.bamsoftware.com/software/dnstt/
Integrate and pin the real upstream Android client/library, then add a smoke test
that proves an actual tunnel connection before enabling this protocol.
MSG
exit 1
