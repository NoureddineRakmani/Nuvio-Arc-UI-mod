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
# Commands (see mod/README.md, "Editing it yourself"):
#   bash ~/nuvio-try [smooth|fast]     fresh copy of Arc + my changes from GitHub, build, install
#   bash ~/nuvio-try edit              fresh copy, no build: then edit files in ~/nuvio-mod-dev/arc/app/src
#   bash ~/nuvio-try keep [smooth|fast] build and install the folder as it is now, with your edits
#   bash ~/nuvio-try save "note"       turn your edits into the patch files and send them to GitHub
#   bash ~/nuvio-try discard           throw your unsaved edits away and start from a fresh copy
#   bash ~/nuvio-try trace             record what happens when opening details pages (no build)
CMD="${1:-}"
case "$CMD" in
  trace) exec bash "$(dirname "$0")/trace.sh" ;;
  edit|keep|save|discard) shift ;;
  ""|smooth|fast) CMD="build" ;;
  *) printf 'Unknown command "%s". Use: smooth, fast, edit, keep, save, discard, trace\n' "$CMD"; exit 1 ;;
esac
shopt -s nullglob
MARK="$ROOT/prepared"        # line 1: state of app/src right after applying the patches; line 2: patch commit used
UNPUSHED="$ROOT/unpushed"    # exists when "save" made a commit that is not on GitHub yet
step() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31mSTOPPED: %s\033[0m\n' "$*"; exit 1; }
# Snapshot of app/src (tracked + new files) as a git tree id, without touching the files.
src_tree() { git -C "$ARC" add -A -- app/src >/dev/null 2>&1; local t; t=$(git -C "$ARC" write-tree); git -C "$ARC" reset -q; echo "$t"; }
has_unsaved_edits() { [ -f "$MARK" ] && [ -d "$ARC/.git" ] && [ "$(src_tree)" != "$(sed -n 1p "$MARK")" ]; }
guard_edits() {
  if has_unsaved_edits; then
    fail "You have edits in ~/nuvio-mod-dev/arc that are not saved.
  Keep them and build:   bash ~/nuvio-try keep
  Save them to GitHub:   bash ~/nuvio-try save \"what I changed\"
  Throw them away:       bash ~/nuvio-try discard"
  fi
}

prepare() {

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
printf '%s\n%s\n' "$(src_tree)" "$(git -C "$MINE" rev-parse HEAD)" > "$MARK"
}

build_install() {
local MODE="${1:-}"

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
#   bash ~/nuvio-try          quick build (fast to make, the app is a bit laggy)
#   bash ~/nuvio-try smooth   quick build + pre-compiled on the TV
#   bash ~/nuvio-try fast     fully optimized build, like the real releases (takes longer to make)
if [ "$MODE" = "fast" ]; then
  step "Building the fully optimized version (several minutes)"
  ( cd "$ARC" && CI_USE_DEBUG_SIGNING=true ./gradlew :app:assembleFullRelease "-PnuvioAppIdSuffix=$SUFFIX" --console=plain -q ) || fail "The build failed. Copy the red lines above to Claude."
  APK_DIR="$ARC/app/build/outputs/apk/full/release"; APK_KIND="release"
else
  step "Building (first time: 15-30 min, after that much faster)"
  ( cd "$ARC" && ./gradlew :app:assembleFullDebug "-PnuvioAppIdSuffix=$SUFFIX" --console=plain -q ) || fail "The build failed. Copy the red lines above to Claude."
  APK_DIR="$ARC/app/build/outputs/apk/full/debug"; APK_KIND="debug"
fi

# 8. Install on the TV box.
step "Installing on the TV box"
adb connect "$BOX" >/dev/null || true
abi=$(adb -s "$BOX" shell getprop ro.product.cpu.abi | tr -d '\r')
apk="$APK_DIR/app-full-$abi-$APK_KIND.apk"
[ -f "$apk" ] || fail "No APK for $abi at $apk."
aapt2=$(ls "$SDK"/build-tools/*/aapt2 | sort -V | tail -1)
pkg=$("$aapt2" dump packagename "$apk")
[ "$pkg" = "$EXPECTED" ] || fail "Built package is $pkg, expected $EXPECTED. Not installing."
adb -s "$BOX" install -r "$apk"

# 9. Quick builds skip the slow "optimize" step of real releases, so let the TV box
#    pre-compile the app instead. Without this it runs noticeably slower than Nuvio Mod.
if [ "$MODE" = "smooth" ] || [ "$MODE" = "fast" ]; then
  step "Optimizing the app on the TV box (1-3 min)"
  adb -s "$BOX" shell cmd package compile -m speed -f "$EXPECTED" || echo "Optimizing failed - the app still works, just slower."
  adb -s "$BOX" shell am force-stop "$EXPECTED" || true
else
  echo "(Quick build: the app may feel laggy. Smoother: bash ~/nuvio-try smooth  -  fully optimized: bash ~/nuvio-try fast)"
fi
adb -s "$BOX" shell monkey -p "$EXPECTED" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 || true
END=$(date +%s)
printf '\n\033[1;32mDone in %dm %02ds. "%s" is open on the TV.\033[0m\n' $(( (END-START)/60 )) $(( (END-START)%60 )) "$LABEL"
}

# Turns the edits in ~/nuvio-mod-dev/arc/app/src into the 9 patch files, checks them, sends them to GitHub.
save_edits() {
  local msg="${1:-My edits}"
  [ -f "$MARK" ] || fail "Nothing to save yet. Start with: bash ~/nuvio-try edit"
  local cur ptree pcommit
  cur=$(src_tree); ptree=$(sed -n 1p "$MARK"); pcommit=$(sed -n 2p "$MARK")
  if [ "$cur" = "$ptree" ] && [ ! -f "$UNPUSHED" ]; then echo "No edits to save."; return 0; fi
  [ -n "$(git -C "$MINE" config user.name || true)" ] && [ -n "$(git -C "$MINE" config user.email || true)" ] \
    || fail "Git needs your name and email once (they appear on your GitHub commits). Run:
  git config --global user.name \"Your name\"
  git config --global user.email \"the email of your GitHub account\"
then: bash ~/nuvio-try save \"$msg\""

  if [ "$cur" != "$ptree" ]; then
    step "Checking GitHub for newer changes"
    git -C "$MINE" fetch -q --depth 1 origin dev
    if [ "$(git -C "$MINE" rev-parse FETCH_HEAD)" != "$pcommit" ]; then
      [ -f "$UNPUSHED" ] && fail "GitHub changed and you also have a save that is not on GitHub yet. Tell Claude."
      echo "The patches changed on GitHub since you started editing: putting your edits on top of the new version."
      git -C "$ARC" diff --binary "$ptree" "$cur" -- app/src > "$ROOT/my-edits.diff"
      git -C "$MINE" reset -q --hard FETCH_HEAD
      prepare
      if ! git -C "$ARC" apply --whitespace=nowarn "$ROOT/my-edits.diff" 2>/dev/null; then
        git -C "$ARC" add -A -- app/src
        git -C "$ARC" apply --3way --whitespace=nowarn "$ROOT/my-edits.diff" \
          || { git -C "$ARC" reset -q; fail "Your edits clash with the new version. They are kept in ~/nuvio-mod-dev/my-edits.diff. Tell Claude."; }
      fi
      git -C "$ARC" reset -q
      cur=$(src_tree)
    fi

    step "Turning your edits into patch files"
    local tmp="$ROOT/.save-new" chk="$ROOT/.save-check" out="$ROOT/.save-patches"
    git -C "$ARC" worktree remove -f "$tmp" 2>/dev/null || true; git -C "$ARC" worktree remove -f "$chk" 2>/dev/null || true
    rm -rf "$tmp" "$chk" "$out"; git -C "$ARC" worktree prune
    git -C "$ARC" worktree add -q --detach "$tmp" HEAD
    git -C "$ARC" worktree add -q --detach "$chk" HEAD
    rm -rf "$tmp/app/src" && cp -a "$ARC/app/src" "$tmp/app/src"
    # Undo the test app's name change; it is added by every build, not part of your edits.
    ( cd "$tmp" && python3 - "$LABEL" <<'PY'
import pathlib, re, subprocess, sys
label = sys.argv[1]
pat = re.compile(r'(<string name="app_name"[^>]*>)([^<]*)(</string>)')
for f in pathlib.Path("app/src").glob("*/res/values*/strings.xml"):
    t = f.read_text(encoding="utf-8")
    if label not in t: continue
    try: orig = subprocess.run(["git", "show", f"HEAD:{f.as_posix()}"], capture_output=True, text=True, check=True).stdout
    except subprocess.CalledProcessError: continue
    m = pat.search(orig)
    if m: f.write_text(pat.sub(lambda x: x.group(1) + m.group(2) + x.group(3) if x.group(2) == label else x.group(0), t), encoding="utf-8")
PY
    )
    git -C "$tmp" add -A -- app/src
    mkdir -p "$out"
    local P=app/src/main/java/com/nuvio/tv f r t
    declare -A T=()
    while IFS= read -r f; do
      r=${f#$P/}
      case "$r" in
        data/*) t=09-data-age-rating ;;
        MainActivity.kt|ui/v2/navigation/*) t=03-top-menu ;;
        ui/v2/*) t=04-glass-and-highlight ;;
        ui/screens/home/ModernHomePresentation.kt|ui/navigation/*|ui/screens/search/*) t=05-search-and-collections ;;
        ui/screens/home/ModernHomeRows.kt|ui/components/NuvioCard.kt|ui/components/ContinueWatchingSection.kt|ui/components/ContentCard.kt|ui/components/GridContentCard.kt) t=02-poster-cards ;;
        ui/screens/home/*|ui/components/TrailerPlayer.kt) t=01-home-layout-and-hero ;;
        ui/screens/settings/*) t=06-settings ;;
        ui/screens/detail/*) t=07-details-page ;;
        *) t=08-other-screens ;;
      esac
      T[$t]="${T[$t]:-} $f"
    done < <(git -C "$tmp" diff --cached --name-only HEAD)
    for t in "${!T[@]}"; do
      # shellcheck disable=SC2086
      git -C "$tmp" diff --cached --binary HEAD -- ${T[$t]} > "$out/00$t.patch"
    done
    for f in "$out"/*.patch; do git -C "$chk" apply --whitespace=nowarn "$f" || fail "$(basename "$f") does not apply on a clean copy. Nothing was saved. Tell Claude."; done
    git -C "$chk" add -A -- app/src
    [ "$(git -C "$chk" write-tree)" = "$(git -C "$tmp" write-tree)" ] || fail "The new patch files do not rebuild your folder exactly. Nothing was saved. Tell Claude."
    echo "Checked: the patch files rebuild your folder exactly."
    rm -f "$MINE"/mod/patches/*.patch && cp "$out"/*.patch "$MINE/mod/patches/"
    git -C "$ARC" worktree remove -f "$tmp"; git -C "$ARC" worktree remove -f "$chk"; rm -rf "$out"
    git -C "$MINE" add -A mod/patches
    if git -C "$MINE" diff --cached --quiet; then echo "No change to the patch files."; else
      git -C "$MINE" diff --cached --stat | tail -1
      git -C "$MINE" commit -q -m "$msg"
    fi
  fi

  step "Sending to GitHub"
  if git -C "$MINE" push -q origin HEAD:dev; then
    rm -f "$UNPUSHED"
    printf '%s\n%s\n' "$(src_tree)" "$(git -C "$MINE" rev-parse HEAD)" > "$MARK"
    printf '\n\033[1;32mSaved to GitHub (dev).\033[0m Your next "bash ~/nuvio-try" builds include your edits.\n'
  else
    : > "$UNPUSHED"
    fail "Saved on this PC, but GitHub refused it (not signed in). One time:
  sudo pacman -S --needed github-cli && gh auth login && gh auth setup-git
then: bash ~/nuvio-try save"
  fi
}

case "$CMD" in
  build)   guard_edits; prepare; build_install "${1:-}" ;;
  edit)    guard_edits; prepare
           printf '\n\033[1;32mReady to edit:\033[0m ~/nuvio-mod-dev/arc/app/src\n'
           echo "Then: bash ~/nuvio-try keep   (build your edits)   ->   bash ~/nuvio-try save \"note\"   (send to GitHub)" ;;
  keep)    [ -f "$MARK" ] || fail "Start with: bash ~/nuvio-try edit"; build_install "${1:-}" ;;
  save)    save_edits "${1:-}" ;;
  discard) prepare; echo "Your unsaved edits are gone; the folder is a fresh copy." ;;
esac
