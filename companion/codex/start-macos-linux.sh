#!/bin/sh
set -eu
cd "$(dirname "$0")"
command -v node >/dev/null 2>&1 || { echo "Node.js 20 or newer is required." >&2; exit 1; }
[ -d node_modules ] || npm ci --omit=dev
exec npm start
