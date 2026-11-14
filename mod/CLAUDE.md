# Nuvio Mod — instructions for Claude Code on Flora's PC

Flora builds **Nuvio Mod**, her own version of the Android TV app Nuvio Arc. Until 2026-11-14 the work was done
by Claude in a cloud session; this file hands it over to you, running on her PC (CachyOS, folder `~/nuvio-mod-dev`).
**Answer in English** (she sometimes writes in French). Her shell is fish; your Bash tool is bash, which is fine,
but commands you give *her* to type must work in fish (no `VAR=$(...)`; use `set VAR (...)` or `bash -c '...'`).

Read before changing anything: `mine/mod/README.md`, then the notes in `mine/mod/notes/`:
`addendum.md` (newest state — wins over the others), `ui-changes.md` (the look and the rules), `handoff.md`
(repo, signing, CI).

## Her working rules (all still in force)
- **Check Arc's own settings before coding.** Only patch what settings can't do.
- **No guesses.** Verify in source (or a trace/log) before stating a cause or proposing a fix; say plainly what you
  read vs what you ran. Her words: "stop making guesses… search instead, be thorough".
- Do exactly what she asked — but when she names a scope ("the details page", "everywhere"), apply it to the
  whole scope without making her list every button.
- "Check" = audit and report only; change nothing until she says.
- Ambiguous request → ask one short question.
- Keep answers simple and consistent. Design direction: "closer to an Apple TV UI, not identical". No shades over
  pictures (two attempts rejected — don't propose again). No black screens anywhere (see addendum).

## Safety (her requests — never break these)
- Never replace or touch the apps `com.nuvio.tv` (official Nuvio) or `com.nuvio.tv.test` (Nuvio Arc).
  Her real mod is `com.nuvio.tv.test.mod`; test builds are `com.nuvio.tv.test.dev` ("Nuvio Mod Test").
- Never print, quote or copy API keys/tokens: `arc/local.properties`, her settings file, her addon configs.
  Mask them if you must read a file containing them.
- Don't send signed playback reports from a modified build (those keys stay empty).
- Her name, email and accounts: ask her, never fill them in (e.g. git name/email for commits).
- Branch `dev` = work in progress. **Don't publish to `main`** (CI builds and releases `main`) until she has
  tried the build and says so.
- Commit messages end with a `Co-Authored-By:` line for the Claude model you are.

## Layout on this PC
| Path | What it is |
|---|---|
| `~/nuvio-try` | starter: updates `mine`, then runs `mine/mod/dev/try.sh` |
| `~/nuvio-mod-dev/mine` | sparse, depth-1 clone of the fork, `mod/` only. **Reset to GitHub `dev` on every `nuvio-try` run** (unless `unpushed` exists) — never keep work here |
| `~/nuvio-mod-dev/arc` | Arc tag + all patches applied = the code. **Edit here** (`arc/app/src`) |
| `~/nuvio-mod-dev/prepared`, `unpushed` | state markers used by `try.sh` |
| `~/nuvio-mod-dev/CLAUDE.md` | one line importing this file; keep it that way |

Repos: fork `NoureddineRakmani/Nuvio-Arc-UI-mod` (branch `dev`); upstream Arc `ysosrs123/NuvioTV-Fork`,
tag `1.1.0-nt.stable`. TV box adb: `192.168.1.10:5555`.

## The loop (commands in `mod/README.md`, "Editing it yourself")
1. `bash ~/nuvio-try edit` — fresh copy from GitHub `dev` (refuses if there are unsaved edits).
2. Edit `~/nuvio-mod-dev/arc/app/src/...`.
3. Quick compile check (much faster than a full build):
   `cd ~/nuvio-mod-dev/arc && ./gradlew :app:compileFullDebugKotlin -PnuvioAppIdSuffix=.dev --console=plain -q`
4. `bash ~/nuvio-try keep fast` — build the edits (optimized, like real Arc) and install "Nuvio Mod Test".
   `keep` alone = quick debug build: laggy, never compare its speed with Arc.
5. She tries it on the TV.
6. `bash ~/nuvio-try save "note"` — turns `arc/app/src` into the 9 patch files, verifies them on a clean copy,
   commits and pushes to `dev`. Needs `gh auth login && gh auth setup-git` and a git name/email (ask her).
`bash ~/nuvio-try discard` throws unsaved edits away. Never hand-edit `mod/patches/*.patch`; `save` regenerates them.

Patches are file-disjoint by theme; `save` files each changed path into one of:
01 home layout and hero · 02 poster cards · 03 top menu (`MainActivity.kt`, `ui/v2/navigation`) · 04 glass and
highlight (other `ui/v2`, incl. `ui/v2/components/ModLook.kt` = the single definition of the look) · 05 search
and collections · 06 settings · 07 details page · 08 other screens · 09 data.
Mark every change with a `// Nuvio Mod:` comment, keep diffs against Arc small (survives Arc updates).

## Kotlin/Compose lessons from this project
- An extension function/property must be **imported**; it can't be called by its fully qualified name
  (two past build errors). Non-extensions (e.g. `androidx.compose.runtime.remember`) can be.
- `if (a) x else { y } ?: z` binds the elvis to the else branch only — parenthesise.
- androidx.tv `Button`/`Card` content has a 58×40dp minimum; their `modifier` sits outside the focus scale.
- Modern Home card key = `<rowStableKey>_<apiType>:<id>`: never parse IDs out of keys.

## Debugging on the TV
- `bash ~/nuvio-try trace` records Arc's details-page timings (log tag `NuvioDetails`) to `~/nuvio-details-trace.txt`.
- Custom capture: `adb shell setprop log.tag.NuvioDetails VERBOSE; adb logcat -c`, reproduce, then
  `adb logcat -d -v time -s NuvioDetails:V MetaDetailsViewModel:V StreamScreenViewModel:V`.
- Prefer a log over reasoning about what *might* happen.

## Where things stand (2026-11-14, `dev` at 1f2619d)
**Open bug — Home "Coming up" (Calendar) row:** a card showing S11 E62 (La Villa des cœurs brisés, airs daily)
opens the show page on **S1 E1**; she says "it does flash something briefly and then S1E1". Official NuvioTV has no
such row. Fixes so far (see addendum): 8a9e0df (aired card → look up episode id → streams), 1f2619d (every card →
`onContinueWatchingClick` → NavHost intercept → streams if out, else details with `returnFocusSeason/Episode`
through `modOpenDetail`; trace marks `mod_comingup_*`). Still S1 E1 on her build. Two candidate paths, neither
confirmed: (a) streams screen opens then something returns to details; (b) details opens on S11 then Arc's
next-to-watch/return-focus logic (`MetaDetailsViewModel.updateNextToWatch`, `resolveReturnFocusStep` in
`MetaDetailsScreen.kt`) moves back to S1 because she has no watch history for the show.
**Next step:** confirm her installed build contains 1f2619d, capture the log above while she presses the card,
and find the path from `mod_comingup_*` marks + the ViewModel logs before changing code.

Other open items: startup speed vs Arc (5b566f1 — blur only while the top bar shows, folder covers 10 s after
start; not measured; if still slower, measure `adb shell am start -W -S` on both apps). La Villa episode pictures
(app-side season-poster option offered; she hasn't chosen the fit). Unverified by her: see addendum's last line.
