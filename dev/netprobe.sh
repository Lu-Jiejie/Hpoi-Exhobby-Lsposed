#!/usr/bin/env bash
# Ad-hoc: compile and run NetProbe against the live site.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JSON="$ROOT/dev/probe/json.jar"
OUT="$ROOT/build/netprobe"
to_win() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
if command -v cygpath >/dev/null 2>&1; then SEP=';'; else SEP=':'; fi
rm -rf "$OUT"; mkdir -p "$OUT"
{
  to_win "$ROOT/app/src/main/java/io/github/lu_jiejie/exhobby/data/ExhobbyApi.java"; echo
  to_win "$ROOT/dev/probe/src/io/github/lu_jiejie/exhobby/data/NetProbe.java"; echo
} > "$OUT/sources.txt"
JAR="$(to_win "$JSON")"
javac -encoding UTF-8 -source 11 -target 11 -parameters -classpath "$JAR" \
  -d "$(to_win "$OUT/classes")" @"$(to_win "$OUT/sources.txt")" 2>&1 | grep -vE "^(ע|注|warning)" || true
java -cp "$(to_win "$OUT/classes")$SEP$JAR" io.github.lu_jiejie.exhobby.data.NetProbe
