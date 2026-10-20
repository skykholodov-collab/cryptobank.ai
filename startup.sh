#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

RUNTIME_DIR="${RUNTIME_DIR:-/home/runner/work/_temp/omgithub-runtime}"
PLAYWRIGHT_HOME="${PLAYWRIGHT_HOME:-$HOME/.local/share/omgithub-playwright}"

time_cmd() { /usr/bin/time -p "$@"; }

if [[ ! -x "$PLAYWRIGHT_HOME/playwright-cli" || ! -f "$PLAYWRIGHT_HOME/linux.json" ]]; then
  time_cmd bash "$RUNTIME_DIR/scripts/setup-linux-playwright.sh"
else
  time_cmd test -x "$PLAYWRIGHT_HOME/playwright-cli"
  time_cmd test -f "$PLAYWRIGHT_HOME/linux.json"
fi

time_cmd mkdir -p "$PLAYWRIGHT_HOME/output"
