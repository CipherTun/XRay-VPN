#!/usr/bin/env bash
set -euo pipefail

cat >&2 <<'MSG'
ERROR: SlowDNS is not buildable from this project yet.
The previous script generated a fake/stub implementation or an empty placeholder AAR;
that artifact was not a working SlowDNS tunnel and must not be packaged.
Upstream reference: No verified upstream client/library is configured in this project.
Integrate and pin the real upstream Android client/library, then add a smoke test
that proves an actual tunnel connection before enabling this protocol.
MSG
exit 1
