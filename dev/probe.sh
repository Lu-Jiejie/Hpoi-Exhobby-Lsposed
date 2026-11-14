#!/usr/bin/env bash
# Exercises ExhobbyApi against the live site on the desktop JVM (no device needed).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JSON="$ROOT/dev/probe/json.jar"
OUT="$ROOT/build/probe"

# javac/java may be native binaries, so every path handed to them must be native too.
to_win() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s' "$1"
  fi
}

if [ ! -f "$JSON" ]; then
  echo "!! missing $JSON" >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT"

if command -v cygpath >/dev/null 2>&1; then
  SEP=';'
else
  SEP=':'
fi

# Only the network client is Android-free; the loader and UI need a device.
{
  to_win "$ROOT/app/src/main/java/io/github/lu_jiejie/exhobby/data/ExhobbyApi.java"
  echo
  to_win "$ROOT/dev/probe/src/io/github/lu_jiejie/exhobby/data/ApiProbe.java"
  echo
} > "$OUT/sources.txt"

JAR="$(to_win "$JSON")"

javac -encoding UTF-8 -source 11 -target 11 -parameters \
  -classpath "$JAR" -d "$(to_win "$OUT/classes")" @"$(to_win "$OUT/sources.txt")" \
  2>&1 | grep -vE "^(ע|注|warning)" || true

java -cp "$(to_win "$OUT/classes")$SEP$JAR" io.github.lu_jiejie.exhobby.data.ApiProbe "$@"
