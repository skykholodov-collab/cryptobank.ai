#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

PROJECT_DIR="$(pwd -P)"
WEB_SRC="$PROJECT_DIR/web"
OUTPUT_DIR="$PROJECT_DIR/dist"
WEB_META_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"
PORT="${PORT:-3000}"

time_cmd() { /usr/bin/time -p "$@"; }

time_cmd test -x /usr/bin/python3
time_cmd test -f "$WEB_SRC/index.html"
time_cmd test -f "$WEB_SRC/styles.css"
time_cmd test -f "$WEB_SRC/app.js"

time_cmd rm -rf "$OUTPUT_DIR"
time_cmd mkdir -p "$OUTPUT_DIR"
time_cmd cp -R "$WEB_SRC"/. "$OUTPUT_DIR"/
time_cmd mkdir -p "$WEB_META_DIR"
time_cmd /usr/bin/printf '{"project":"%s","directory":"%s"}\n' "$PROJECT_DIR" "$OUTPUT_DIR" > "$WEB_META_DIR/deployment-output.json"
time_cmd test -f "$OUTPUT_DIR/index.html"
time_cmd test -s "$WEB_META_DIR/deployment-output.json"

echo "Serving $OUTPUT_DIR on http://0.0.0.0:$PORT"
exec /usr/bin/time -p /usr/bin/python3 -m http.server "$PORT" --bind 0.0.0.0 --directory "$OUTPUT_DIR"
