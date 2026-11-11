#!/usr/bin/env bash
# Builds Arc + my patches on this PC as a separate test app and installs it on the TV box.
#   App name: "Nuvio Mod Test"   package: com.nuvio.tv.test.dev
# It never touches Nuvio, Nuvio Arc or Nuvio Mod.
set -euo pipefail
ROOT="$HOME/nuvio-mod-dev"
MINE="$ROOT/mine"
ARC="$ROOT/arc"
FORK="NoureddineRakmani/Nuvio-Arc-UI-mod"
UPSTREAM_URL="https://github.com/ysosrs123/NuvioTV-Fork.git"
BOX="${NUVIO_BOX:-192.168.1.10:5555}"
SUFFIX=".dev"
EXPECTED="com.nuvio.tv.test.dev"
LABEL="Nuvio Mod Test"
START=$(date +%s)
step() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31mSTOPPED: %s\033[0m\n' "$*"; exit 1; }

# 1. Which Arc version: the one my published Nuvio Mod is built from.
step "Checking which Arc version to use"
tag=$(curl -fsSL "https://api.github.com/repos/$FORK/releases?per_page=1" 2>/dev/null \
  | python3 -c 'import json,sys,re; print(re.sub(r"[.-]mod\.\d+$","",json.load(sys.stdin)[0]["tag_name"]))' 2>/dev/null || true)
if [ -z "$tag" ] && [ -f "$ROOT/arc-tag" ]; then tag=$(cat "$ROOT/arc-tag"); fi
[ -n "$tag" ] || fail "Could not find the Arc version (no internet?)."
echo "Arc $tag"

# 2. Arc's source, reset to a clean copy (build caches are kept, so rebuilds stay fast).
if [ ! -d "$ARC/.git" ]; then git init -q "$ARC"; git -C "$ARC" remote add origin "$UPSTREAM_URL"; fi
if [ "$(cat "$ROOT/arc-tag" 2>/dev/null || true)" != "$tag" ]; then
  step "Downloading Arc $tag source"
  git -C "$ARC" fetch -q --depth 1 origin "refs/tags/$tag"
  git -C "$ARC" checkout -q -f --detach FETCH_HEAD
  echo "$tag" > "$ROOT/arc-tag"
fi
git -C "$ARC" reset -q --hard
git -C "$ARC" clean -fdq

# 3. My changes.
step "Applying my changes"
shopt -s nullglob
for p in "$MINE"/mod/patches/*.patch; do
  echo "  $(basename "$p")"
  git -C "$ARC" apply --3way --whitespace=nowarn "$p" || fail "$(basename "$p") does not fit Arc $tag. Tell Claude."
done
git -C "$ARC" reset -q

# 4. Make it the separate test app.
( cd "$ARC" && python3 - "$LABEL" <<'PY'
import pathlib, re, sys
label = sys.argv[1]
g = pathlib.Path("app/build.gradle.kts"); s = g.read_text()
s, n = re.subn(r'buildConfigField\("boolean", "UPDATE_CHECK_ENABLED", "true"\)',
               'buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "false")', s)
if "nuvioAppIdSuffix" not in s: sys.exit("Arc's build file changed: package suffix option is gone.")
g.write_text(s)
count = 0
for f in list(pathlib.Path("app/src/main/res").glob("values*/strings.xml")) + list(pathlib.Path("app/src/debug/res").glob("values*/strings.xml")):
    t = f.read_text(encoding="utf-8")
    t2, k = re.subn(r'(<string name="app_name"[^>]*>)[^<]*(</string>)', rf'\g<1>{label}\g<2>', t)
    if k: f.write_text(t2, encoding="utf-8"); count += k
if not count: sys.exit("Could not find the app name to rename.")
PY
) || fail "Could not prepare the test app."

# 5. App settings (the same 13 lines as the MOD_LOCAL_PROPERTIES secret).
SETTINGS="$ROOT/app-settings.txt"
if [ ! -f "$SETTINGS" ]; then
  found=$(find "$HOME/Downloads" "$HOME/Téléchargements" "$HOME" -maxdepth 1 -name 'MOD_LOCAL_PROPERTIES*.txt' 2>/dev/null | head -1 || true)
  if [ -n "$found" ]; then cp "$found" "$SETTINGS"; echo "Using app settings from $found"; fi
fi
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
[ -d "$SDK/platforms" ] || fail "Android SDK not found at $SDK."
{ echo "sdk.dir=$SDK"; [ -f "$SETTINGS" ] && cat "$SETTINGS"; } > "$ROOT/local.properties.new"
cmp -s "$ROOT/local.properties.new" "$ARC/local.properties" || cp "$ROOT/local.properties.new" "$ARC/local.properties"
[ -f "$ARC/local.dev.properties" ] || : > "$ARC/local.dev.properties"
[ -f "$SETTINGS" ] || echo "NOTE: $SETTINGS not found - login, Trakt and ratings will be off in the test app."

[ "${NUVIO_PREPARE_ONLY:-}" = 1 ] && { echo "Prepared only."; exit 0; }

# 6. Java 17.
JAVA_HOME=""
for d in /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/*17*; do [ -x "$d/bin/javac" ] && JAVA_HOME="$d" && break; done
if [ -z "$JAVA_HOME" ]; then
  step "Installing Java 17 (asks for your password, one time)"
  sudo pacman -S --needed --noconfirm jdk17-openjdk
  JAVA_HOME=/usr/lib/jvm/java-17-openjdk
fi
export JAVA_HOME ANDROID_HOME="$SDK"

# 7. Build.
step "Building (first time: 15-30 min, after that much faster)"
( cd "$ARC" && ./gradlew :app:assembleFullDebug "-PnuvioAppIdSuffix=$SUFFIX" --console=plain -q ) || fail "The build failed. Copy the red lines above to Claude."

# 8. Install on the TV box.
step "Installing on the TV box"
adb connect "$BOX" >/dev/null || true
abi=$(adb -s "$BOX" shell getprop ro.product.cpu.abi | tr -d '\r')
apk="$ARC/app/build/outputs/apk/full/debug/app-full-$abi-debug.apk"
[ -f "$apk" ] || fail "No APK for $abi at $apk."
aapt2=$(ls "$SDK"/build-tools/*/aapt2 | sort -V | tail -1)
pkg=$("$aapt2" dump packagename "$apk")
[ "$pkg" = "$EXPECTED" ] || fail "Built package is $pkg, expected $EXPECTED. Not installing."
adb -s "$BOX" install -r "$apk"

# 9. Quick builds skip the slow "optimize" step of real releases, so let the TV box
#    pre-compile the app instead. Without this it runs noticeably slower than Nuvio Mod.
if [ "${NUVIO_NO_OPTIMIZE:-}" != 1 ]; then
  step "Optimizing the app on the TV box (1-3 min)"
  adb -s "$BOX" shell cmd package compile -m speed -f "$EXPECTED" || echo "Optimizing failed - the app still works, just slower."
  adb -s "$BOX" shell am force-stop "$EXPECTED" || true
fi
adb -s "$BOX" shell monkey -p "$EXPECTED" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 || true
END=$(date +%s)
printf '\n\033[1;32mDone in %dm %02ds. "%s" is open on the TV.\033[0m\n' $(( (END-START)/60 )) $(( (END-START)%60 )) "$LABEL"
