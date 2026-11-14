# Nuvio Mod

My own version of Nuvio Arc, installed next to Nuvio and Nuvio Arc (it never replaces them).

- App name on the TV: **Nuvio Mod** (package `com.nuvio.tv.test.mod`)
- Built automatically by `.github/workflows/build-my-nuvio.yml`: every 6 hours it checks for a new Nuvio Arc release, applies my changes and publishes a new release here. The app's built-in updater picks it up.
- My UI changes live in `mod/patches/` as small `.patch` files. Nothing else in this repository needs to be edited.
- To build right now: Actions tab → "Build my Nuvio" → Run workflow.

Secrets needed (Settings → Secrets and variables → Actions):
`MOD_KEYSTORE_BASE64`, `MOD_KEYSTORE_PASSWORD`, `MOD_LOCAL_PROPERTIES`.

Keep a copy of the signing key file (`nuvio-mod.p12`) and its password somewhere safe:
if it's lost, Nuvio Mod has to be uninstalled and reinstalled once with a new key.

## Working with Claude Code on the PC
`mod/CLAUDE.md` holds the project context and rules for Claude Code; `mod/notes/` holds the change notes
(`addendum.md` is the newest). `~/nuvio-try` creates `~/nuvio-mod-dev/CLAUDE.md`, which points to it, so start
Claude Code in `~/nuvio-mod-dev`. Keep the notes up to date when something changes.

## Editing it yourself (on the PC)

All commands work in the normal terminal (fish is fine).

| Command | What it does |
|---|---|
| `bash ~/nuvio-try edit` | Fresh copy of Arc + all changes in `~/nuvio-mod-dev/arc`, no build. |
| *(edit any file under `~/nuvio-mod-dev/arc/app/src`)* | Code is in `app/src/main/java/com/nuvio/tv/`, texts and images in `app/src/main/res/`. |
| `bash ~/nuvio-try keep` | Build and install the folder **as it is now**, with your edits (`keep smooth`, `keep fast` also work). |
| `bash ~/nuvio-try save "what I changed"` | Turns your edits into the patch files, checks they rebuild your folder exactly, sends them to GitHub (`dev`). |
| `bash ~/nuvio-try discard` | Throws your unsaved edits away and gives a fresh copy. |
| `bash ~/nuvio-try` / `smooth` / `fast` | Fresh copy from GitHub, build, install. **Refuses to run if you have unsaved edits**, so nothing is lost by accident. |

Good to know
- Only files under `app/src` are saved. Changes elsewhere (build files, Android Studio's `.idea`) are ignored.
- If Claude changed the patches on GitHub while you were editing, `save` puts your edits on top of the new version. If both changed the same lines, it stops and keeps your edits in `~/nuvio-mod-dev/my-edits.diff`.
- `save` sends to `dev`, never to `main`: the published Nuvio Mod only changes when `main` is updated.
- One-time setup for `save`: your name and email for commits (`git config --global user.name "…"` and `git config --global user.email "…"`), and signing in to GitHub (`sudo pacman -S --needed github-cli && gh auth login && gh auth setup-git`). `save` tells you when one is missing.
- The patch files are grouped by area (home, posters, top menu, glass and highlight, search, settings, details page, other screens, age rating). `save` sorts files into them automatically.
