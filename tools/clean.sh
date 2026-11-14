#!/usr/bin/env bash
# Reclaims disk space from build artifacts. Everything removed here is regenerable:
# work/ comes from re-running jadx, sdk/ from tools/setup_sdk.py.
#
#   bash tools/clean.sh          # build output + logcat leftovers
#   bash tools/clean.sh work     # also the reverse-engineering dump
#   bash tools/clean.sh all      # also the local Android SDK
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-build}"
rm -rf build hpoi-exhobby.apk hpoi-exhobby.apk.idsig

if [ "$MODE" = "work" ] || [ "$MODE" = "all" ]; then
    rm -rf work
fi

if [ "$MODE" = "all" ]; then
    rm -rf sdk
fi

printf 'cleaned (%s)\n' "$MODE"
du -sh . 2>/dev/null | cut -f1
