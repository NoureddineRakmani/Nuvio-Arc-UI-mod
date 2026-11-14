# Nuvio Mod — addendum to ui-changes.md (2026-11-14)

Read after `ui-changes.md`. Where the two differ, this file wins.

## SHE NOW EDITS TOO (88fb8f6) — read before changing anything
`mod/dev/try.sh` has `edit`, `keep [smooth|fast]`, `save "note"`, `discard` (documented in `mod/README.md`, "Editing it yourself"). Her `save` regenerates the 9 themed patches from `~/nuvio-mod-dev/arc/app/src` (same path → theme rules as the cloud regen script, test-app name undone), verifies on a clean worktree, commits and **pushes to `dev`**.
- Before editing, start from the repo's current `dev` patches (`bash ~/nuvio-try edit`). Never regenerate patches from an older tree: it would erase saved edits.
- If `dev` moved while there are unsaved edits, `save` re-applies the edits on top (3-way); clashes stop with `~/nuvio-mod-dev/my-edits.diff`.
- State files: `~/nuvio-mod-dev/prepared`, `~/nuvio-mod-dev/unpushed`.
- Only `app/src` is saved. Push needs `gh auth login && gh auth setup-git` and a git name/email (she provides; never fill in).
- `bash ~/nuvio-try` (no mode) is the quick, unoptimized build: compare speed with Arc only on `bash ~/nuvio-try fast`.

## Start slower than Arc (fast build vs fast build) — 5b566f1
Read in source, **not measured**; the two things the mod added to startup:
- Top bar (`V2TopNavigation`): `hazeSource` on the whole page + `hazeEffect` ran on every frame even with the bar hidden (alpha 0). Arc's side menu blurs only while open (`shouldApplySidebarHaze`). Now both only while `navFocused || navBarAlpha > 0` (`modBlurOn`, derivedStateOf).
- Folder covers (`ModSearchCollections.keep`): all were fetched and **fully decoded** at Home start (Arc loaded them only when a collection row was scrolled to). Now enqueued from 10 s after process start (`Process.getStartElapsedRealtime`), `.size(64, 64)`, memory cache off.
Other startup changes only remove waits (hero 350 ms / logo 250 ms waits, settle delay). If still slower: measure (`adb shell am start -W -S`) on both apps before changing more.

## Home "Coming up" row opens its own episode (8a9e0df, 1f2619d) — STILL BROKEN for her
Her report: the Calendar ("Coming up") card shows e.g. S11E62 but opens the show page on S1 E1; NuvioTV fine. After 1f2619d: "still not working, it does flash something briefly and then s1e1".
Read in source: NuvioTV has no Coming up row (upcoming episodes are Continue Watching cards → `createContinueWatchingRoute` → streams). Arc's `calendarComingUpCard` sets `videoId = ""`; `hasAired` is true only for `CalendarComingUpWhen.NEW` (episode out, within 7 days, unwatched); TODAY/TONIGHT/TOMORROW/WEEKDAY/DATE = not out. Arc's `ModernHomeRows.openComingUp` opened details with only the show id → season from watch history, else first season. Arc's Calendar screen: `CalendarEpisodeResolver` → `openCalendarWatchTarget`.
Now: every Coming up card → `onContinueWatchingClick` → NavHost intercept (`comingUpKey != null && videoId blank`):
- out (`hasAired`) → `HomeViewModel.modOpenComingUp` (HomeComingUp.kt): videoId from matching Upcoming-row card, else `CalendarComingUpSource.modWatchTarget` (Calendar title by key + entry by S/E → Arc's resolver; may fetch meta, ≤ 8 s) → streams (`openCalendarWatchTarget`); if no id → details on that episode.
- not out → details on that episode.
- Details on an episode goes through the local `modOpenDetail(itemId, type, addon, focusSeason, focusEpisode)` in NavHost's Home route (Home's normal route with hero hand-over, + `returnFocusSeason/Episode`, the mechanism Arc's Calendar uses).
- Trace: `mod_comingup_S<s>E<e>_out_<bool>`, then `mod_comingup_streams` / `_no_episode_id` / `_not_out`.
Unconfirmed candidates for "flash then S1E1": streams screen opening then returning to details (stream `onBackPress` → details without focus?), or details opening on S11 then `resolveReturnFocusStep` / `updateNextToWatch` moving to nextToWatch = S1 (no watch history). Get a log first.

## Top menu: Left at the far left (16f24ec)
Left at the far left of any row brings the menu bar in, like the side menu. Same logic as Arc's side-menu scaffolds (`MainActivity.kt` content-root `.onKeyEvent`: try `moveFocus(Left)`, else open the menu), added to `V2TopNavigation`'s content box on root routes → `entryFocus.requestFocus()`. Arc's `blockLeftOnFirstExpandedItem` still consumes Left first; text fields consume Left while the cursor can move.

## Search page
- d0e270e: no "Lancer une recherche" placeholder.
- 1583eb5: collections from the first frame + folder covers preloaded (timing changed in 5b566f1). `ui/screens/search/ModSearchCollections.kt`: Home's `observeCollectionsPipeline` calls `keep(saved, appContext)`; `SearchViewModel.modCollections` starts from `last`.
- b44fd25: recent searches from the first frame: `HomeViewModel` gets `modSearchHistoryDataStore` injected and `observeCollectionsPipeline` collects it into `ModSearchCollections.recentSearches`; `SearchViewModel`'s initial `SearchUiState` uses it.

## Ratings (f4778fd)
- Details page: ratings requested at page open (`loadMDBListRatingsEarly` → `MDBListRepository.getRatingsForItem`), not after TMDB enrichment; Arc's later call kept, no longer erases.
- Home prefetch unchanged (focused + next row, 250 ms debounce). Offered, not done: all rows at start, shorter debounce.

## Highlight (dd01a90)
`ModLook.kt`: `FILL` **0.05**, `SHEEN` **0.06**, `SELECTED` **0.03** (ui-changes.md's 0.07/0.08/0.04 are outdated).

## Black screen around the details page — FIXED both ways (her: "great the fix worked", on 3410bec)
Her requirement: **no black at all, opening or leaving**.
- Opening: Home records hero card → title IDs (`modRecordHeroCard`); NavHost hands the decoded hero picture (`ModDetailSeed`) + URL; details draws it until its own backdrop loads; no skeleton page in v2.
- Leaving: `ModernHomeHero.kt` draws the kept picture until the hero is prepared again. **Do not remove** (wrongly removed once in 46c7082).
- Not covered: opening from Search / Library / rows inside details.

## Shades over the picture (Home + details, v2) — decided: NO SHADE
Both alternatives rejected — do not propose again: bottom-left oval; full-height left shade to the end of the description block.

## La Villa des cœurs brisés episode pictures (open)
TVDB S11 has no episode images → her metadata addon (AIOMetadata "PG Dex") uses the show background. It keeps only the highest-scored background per (type, language), then takes the no-language one. Her textless S11 upload only wins if it's on the show page, tagged no language, and top-scored; TVDB scores aren't user-votable or public. App-side option offered (read `app_extras.seasonPosters` from the meta, use the season poster when an episode thumbnail falls back to the show background); fit not chosen (centred + blurred fill, or crop).

## Poster sources
Home/Search: Top Posters through PG Dex. Details rows: Arc's plain TMDB posters (Arc setting Paramètres → Disposition → Source d'affiches personnalisée, per screen, could change that — offered).

`main` not published. Unverified by her: b27dd06 round, f4a05e0, b56b0d6, f4778fd, d0e270e, 1583eb5, 16f24ec, b44fd25, 5b566f1, 1f2619d.
