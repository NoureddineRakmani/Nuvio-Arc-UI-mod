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
