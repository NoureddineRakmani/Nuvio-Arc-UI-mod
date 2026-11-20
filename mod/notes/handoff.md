# Nuvio Mod — repo, release and signing facts (2026-11-11)

Goal: Flora's own version of Nuvio Arc with her UI tweaks, installed NEXT TO her existing apps, auto-updating whenever Arc releases. Keep it simple for her; never touch her existing apps.

## Fixed facts
- Fork: `NoureddineRakmani/Nuvio-Arc-UI-mod` (public; `main` = released, `dev` = work in progress). Upstream: `ysosrs123/NuvioTV-Fork`.
- TV box (Homatics, 192.168.1.10:5555 over adb) has `com.nuvio.tv` (official Nuvio) and `com.nuvio.tv.test` (Nuvio Arc). DO NOT replace either.
- Her mod = `com.nuvio.tv.test.mod`, label "PG Prime" (was "Nuvio Mod"), via Arc's `-PnuvioAppIdSuffix=.mod`. Local test builds use `.dev` ("PG Prime Test").
- Her shell is fish: give her fish-compatible commands.
- Arc has a built-in updater: reads GitHub Releases of `GITHUB_OWNER`/`GITHUB_REPO` in `app/build.gradle.kts`; picks the highest `<!-- nuvio-fork-version-code: N -->` in release notes; assets named `app-full-<abi>-release.apk`.
- Arc's debug build has no package suffix (would clash with Arc): always use a suffix.
- Arc's build reads `versionCode`/`versionName` from `-ParcVersionCode`/`-ParcVersionName`.
- Arc's own CI: Java 17, `:app:assembleFullRelease`, local.properties from a secret; release build is R8-minified.

## CI (`.github/workflows/build-my-nuvio.yml`, builds `main` only)
Every 6 h / on push to `mod/**` / manual → newest Arc release → clone its tag → (if newer than `mod/arc-base`: fetch that older tag too, so `git apply --3way` can merge) → apply `mod/patches/*.patch` → updater pointed at her repo; versionCode = ArcReleaseCode×1000 + run%1000; versionName = `<arc tag>.mod.<run>`; name + icons by `mod/dev/brand.py --name "PG Prime"` (resource overlay `arc/mod-brand/res` on the `fullRelease`/`fullDebug` source sets; nothing renamed in Arc's files) → `assembleFullRelease -PnuvioAppIdSuffix=.mod` → package check → publish release. Skips scheduled runs if nothing changed.
Secrets (set by Flora): `MOD_KEYSTORE_BASE64`, `MOD_KEYSTORE_PASSWORD`, `MOD_LOCAL_PROPERTIES`. Never print their values.

## App settings (local.properties keys)
Set: NUVIO_SUPABASE_URL, NUVIO_SUPABASE_ANON_KEY, AVATAR_PUBLIC_BASE_URL, TRAKT_CLIENT_ID, TRAKT_CLIENT_SECRET, TMDB_API_KEY, TVDB_API_KEY, SIMKL_CLIENT_ID, MDBLIST_CLIENT_ID, WETRAKR_CLIENT_ID, INTRODB_API_URL, IMDB_RATINGS_API_BASE_URL, IMDB_TAPFRAME_API_BASE_URL.
Deliberately empty: PLAYBACK_REPORTS_BASE_URL + REPORTS_SIGNING_KEY (don't send signed reports from a modified build). SCOUT_ENABLED=true since 2026-11-20 (her request: Arc's assistant "Ask"), added by `try.sh` and the workflow unless the settings file/secret sets it; Ask uses the AI key each person enters in the app.
Values were extracted from Arc's APK; if Arc adds new keys, they must be extracted again.
