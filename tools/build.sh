#!/usr/bin/env bash
# Build, align and sign the module APK with the SDK build-tools only (no Gradle).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/app"
SDK="${ANDROID_SDK_ROOT:-$ROOT/sdk}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
XPOSED="$ROOT/libs/api-82.jar"
OUT="$ROOT/build"
APK="$ROOT/hpoi-exhobby.apk"

# build-tools ship .exe on Windows and extensionless shell wrappers elsewhere.
tool() {
  for candidate in "$BT/$1" "$BT/$1.exe" "$BT/$1.bat"; do
    if [ -f "$candidate" ]; then
      echo "$candidate"
      return 0
    fi
  done
  echo "$BT/$1"
}

AAPT2="$(tool aapt2)"
D8="$(tool d8)"
ZIPALIGN="$(tool zipalign)"
APKSIGNER="$(tool apksigner)"

if [ -n "${PYTHON:-}" ]; then
  PY="$PYTHON"
elif command -v python3 >/dev/null 2>&1; then
  PY=python3
else
  PY=python
fi

# javac wants ';' on Windows and ':' elsewhere; cygpath only exists under MSYS.
if command -v cygpath >/dev/null 2>&1; then
  SEP=';'
  to_native() { cygpath -w "$1"; }
else
  SEP=':'
  to_native() { printf '%s' "$1"; }
fi

for f in "$AAPT2" "$D8" "$PLATFORM" "$XPOSED"; do
  if [ ! -e "$f" ]; then
    echo "!! missing $f — run 'python tools/setup_sdk.py' first" >&2
    exit 1
  fi
done

rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== resources =="
"$AAPT2" compile --dir "$APP/res" -o "$OUT/res/resources.zip"
"$AAPT2" link \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$OUT/gen" \
  -A "$APP/assets" \
  -o "$OUT/base.apk" \
  "$OUT/res/resources.zip"

echo "== compile =="
# List sources with native paths: on Windows javac rejects MSYS-style /d/... paths.
find "$APP/src" "$OUT/gen" "$ROOT/stubs" -name '*.java' | sed 's|^/\([a-z]\)/|\1:/|' > "$OUT/sources.txt"
if command -v cygpath >/dev/null 2>&1; then
  CP="$(to_native "$PLATFORM")$SEP$(to_native "$XPOSED")"
  OUT_CLASSES="$(to_native "$OUT/classes")"
else
  CP="$PLATFORM$SEP$XPOSED"
  OUT_CLASSES="$OUT/classes"
fi
# -parameters is required: d8 8.2.2 NPEs on anonymous classes without MethodParameters.
javac -encoding UTF-8 \
  -source 11 -target 11 \
  -parameters \
  -classpath "$CP" \
  -d "$OUT_CLASSES" \
  @"$OUT/sources.txt"

if [ ! -f "$OUT/classes/com/kelvin/hpoi/exhobby/MainHook.class" ]; then
  echo "!! compilation produced no MainHook.class" >&2
  exit 1
fi

echo "== dex =="
# The ViewBinding stub only serves javac; the host app provides the real interface.
# d8 is a Windows binary too, so hand it Windows paths.
find "$OUT/classes" -name '*.class' | grep -v '/androidx/viewbinding/' \
  | sed 's|^/\([a-z]\)/|\1:/|' > "$OUT/classes.txt"
"$D8" --min-api 24 --lib "$PLATFORM" --output "$OUT/dex" @"$OUT/classes.txt"

echo "== package =="
"$PY" "$ROOT/tools/pack_apk.py" "$OUT/base.apk" "$OUT/dex/classes.dex" "$OUT/package.apk"

echo "== align =="
"$ZIPALIGN" -f -p 4 "$OUT/package.apk" "$OUT/aligned.apk"

echo "== sign =="
KEYSTORE="${EXHOBBY_KEYSTORE:-$HOME/.android/debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  echo "!! no keystore at $KEYSTORE (set EXHOBBY_KEYSTORE)" >&2
  exit 1
fi
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-pass "${EXHOBBY_KEYSTORE_PASS:-pass:android}" \
  --key-pass "${EXHOBBY_KEY_PASS:-pass:android}" \
  --ks-key-alias "${EXHOBBY_KEY_ALIAS:-androiddebugkey}" \
  --out "$APK" \
  "$OUT/aligned.apk"

"$APKSIGNER" verify "$APK"
echo "BUILD OK -> $APK ($(wc -c < "$APK") bytes)"
