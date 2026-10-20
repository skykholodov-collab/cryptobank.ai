#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

: "${CAPTURE_URL:?CAPTURE_URL is required}"
: "${CAPTURE_DIR:?CAPTURE_DIR is required}"
RUNTIME_DIR="${RUNTIME_DIR:-/home/runner/work/_temp/omgithub-runtime}"

time_cmd() { /usr/bin/time -p "$@"; }

time_cmd test -f "$RUNTIME_DIR/scripts/default-capture.mjs"
time_cmd mkdir -p "$CAPTURE_DIR"

set +e
time_cmd node "$RUNTIME_DIR/scripts/default-capture.mjs"
capture_status=$?
set -e

if [[ "$capture_status" -ne 0 ]]; then
  exit "$capture_status"
fi

time_cmd node -e '
const fs = require("fs");
const dir = process.argv[1];
for (const name of ["final-desktop.png", "final-mobile.png"]) {
  const file = `${dir}/${name}`;
  const data = fs.readFileSync(file);
  if (data.length < 24 || data.subarray(0, 8).toString("hex") !== "89504e470d0a1a0a") {
    console.error(`Invalid PNG: ${file}`);
    process.exit(1);
  }
}
' "$CAPTURE_DIR"

exit 0
