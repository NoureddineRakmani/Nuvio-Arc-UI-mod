#!/usr/bin/env bash
# Writes the release notes of a PG Prime release (Markdown, shown by PG Prime's updater: "Release notes" on the
# update banner and after updating). Her request (2026-11-22): pass on Nuvio Arc's own changelog, so she never
# has to open Nuvio Arc to see what changed, plus her own changes.
#
#   release-notes.sh <arc tag> <mod version code> <mod key> <this repo> <upstream repo> <this commit>
#
# - Arc's notes of every Arc release since the one in her previous release (newest first); nothing when only
#   her changes moved. Arc's "nuvio-fork-version-code" marker is removed from them: PG Prime's updater takes the
#   first such marker as the version, so it must only find ours (kept at the very end).
# - Titles of her commits since the previous release.
set -euo pipefail
tag=$1 code=$2 key=$3 repo=$4 upstream=$5 sha=$6

# PREV_KEY (tag@commit) overrides the previous release's key: for trying this script by hand.
prev_key=${PREV_KEY:-$(gh api "repos/${repo}/releases?per_page=1" --jq '.[0].body // ""' | grep -oP 'mod-key: \K\S+' || true)}
prev_tag=${prev_key%@*}
prev_sha=${prev_key#*@}

# Arc's notes without its markers, and without its last sections ("# Download": Arc's own APKs, which must not be
# installed from here; "# Licence and attribution"), with the "---" line just before them.
clean() {
  sed -E 's/<!--[^>]*-->//g' | sed '/^Nuvio Arc is my experimental fork/d' | awk '/^# Download/ { exit } { print }' |
    awk '{ l[NR] = $0 } END { n = NR; while (n > 0 && (l[n] ~ /^[[:space:]]*$/ || l[n] == "---")) n--; for (i = 1; i <= n; i++) print l[i] }'
}

releases=$(gh api "repos/${upstream}/releases?per_page=30")
code_of() {
  jq -r --arg t "$1" '[ .[] | select(.tag_name == $t) | (.body // "") | capture("nuvio-fork-version-code: (?<c>[0-9]+)") | .c ][0] // "0"' \
    <<<"$releases"
}
now_code=$(code_of "$tag")
prev_code=0
[ -n "$prev_tag" ] && [ "$prev_tag" != "$prev_key" ] && prev_code=$(code_of "$prev_tag")

echo "**PG Prime** = Nuvio Arc ${tag} + my changes."
echo

if [ "$prev_tag" = "$tag" ]; then
  echo "Same Nuvio Arc version as the last update (${tag}): only my changes are new."
  echo
else
  # Every Arc release newer than the previous one, up to this one, newest first.
  jq -r --argjson lo "$prev_code" --argjson hi "$now_code" '
    [ .[] | select(.draft | not)
      | { tag: .tag_name, body: (.body // ""),
          code: ([ (.body // "") | capture("nuvio-fork-version-code: (?<c>[0-9]+)") | .c ][0] // "0" | tonumber) }
      | select(.code > $lo and .code <= $hi) ]
    | sort_by(.code) | reverse | .[] | .tag' <<<"$releases" |
  while IFS= read -r t; do
    echo "---"
    echo
    echo "# What's new in Nuvio Arc ${t}"
    echo
    jq -r --arg t "$t" '.[] | select(.tag_name == $t) | .body // ""' <<<"$releases" | clean
    echo
  done
fi

if [ -n "$prev_sha" ] && [ "$prev_sha" != "$prev_key" ] && [ "$prev_sha" != "$sha" ]; then
  mine=$(gh api "repos/${repo}/compare/${prev_sha}...${sha}" \
    --jq '.commits[].commit.message | split("\n")[0]' 2>/dev/null | grep -v '^Merge ' || true)
  if [ -n "$mine" ]; then
    echo "---"
    echo
    echo "# My changes"
    echo
    sed 's/^/- /' <<<"$mine"
    echo
  fi
fi

echo "<!-- mod-key: ${key} -->"
echo "<!-- nuvio-fork-version-code: ${code} -->"
