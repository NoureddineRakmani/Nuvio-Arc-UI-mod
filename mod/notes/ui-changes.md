# Nuvio Mod — UI changes log (2026-11-12; newer state in addendum.md)

Flora uses the **Modern** home layout, full-screen backdrop ("media hero"), French UI, portrait posters for catalog rows; "Continuer à regarder" uses wide cards. **Interface = Nuvio v2**, top navigation, a **smaller UI size** setting (fixed-dp layouts leave gaps). Answer in English.

How she uses the app: **Bibliothèque = her "save for later" list (keep it in the menu)**; she doesn't use Discover (switched off in settings — don't remove it in code); collections are something she checks when searching (moved from Home to Search); Homatics box, no Google mic. Compares Nuvio Mod against **official NuvioTV** for speed/smoothness. Doesn't use Trakt.

## Working rules (her explicit requests)
- Before coding anything, check what Arc's settings can already do. Only patch what settings can't do.
- When comparing with official NuvioTV, read its source instead of guessing.
- No speculative solutions: verify in source before stating or proposing; say plainly what was read vs run.
- Keep answers simple; one consistent picture.
- Do exactly what she asked; but when she asks for a look on "the details page" / "other pages", apply it to every element of that scope. If a request is ambiguous, ask one short question.
- When asked to "check", audit and report only.

## Design direction (her words)
"Get closer to an Apple TV UI, not identical though." Clean, picture-first; no slow animations; cheap-looking effects are a deal-breaker; no borders/edge lines on highlights.

## The look — everywhere outside the player
- Glass: flat dark frost (tint 0xFF161618 @65% over blur, @94% without), no rims/reflections/tint, no grain.
- Focus highlight: white fill + radial sheen from the upper left, sized from height, no edge line, drawn once. Values in `ui/v2/components/ModLook.kt` (current: see addendum).
- Selected (not focused): flat white pane, no underline/outline/accent.
- Moving items (posters, cast, logos, comments, rating tiles, episode cards, avatars, "see all" cards): grow 6%, no ring. Buttons/rows/pills don't grow.
- Accent kept only for brand bits and accent-filled main action buttons.
- Shapes: menu items, search bar, settings chips = pill; mic = circle; buttons/rows/tabs/filter pills/option cards/rating chips = 12dp.
- Player: unchanged.

## Settings audit vs patches
| Area | Arc setting (FR label) | Decision |
|---|---|---|
| Top navigation | Interface → Style de navigation → "Navigation supérieure" | code on top (top-menu patch) |
| Trailers in background | "Lecture en arrière-plan" = details page only | Home part still code |
| Next-row peek | "Afficher la rangée d'aperçu" | she turns it off; one-row layout still code |
| Card depth effect | "Card depth effect" | our sheen dropped |
| Discover | Discover location | setting (off) |
| Icon-only detail buttons | none | code |

## TMDB integration — settings given to her (verified in Arc source)
Enrichment on; modern home off; Continue Watching off; language English; visuals off; basic info on; details/credits on; **episodes off** (TMDB numbering vs TVDB); trailers on; similar/collections/productions/networks on.

## Her addons (never repeat credentials)
- AIOMetadata "PG Dex" (en-US; movies TMDB, series+anime TVDB; Top Posters). Serves all detail pages.
- AIOMetadata "PG Dex Anime": catalog-only.
- AIOStreams "PG Prime".

## ModLook.kt = the single definition of the look
`FILL`, `SHEEN`, `SELECTED`, `GROW` 1.06, `Corner` 12dp, `Pill`; `drawModSoftHighlight`, `Modifier.modPillHighlight(focused, shape, fill)`, `Modifier.modFocusHighlight(shape, fill)` (tracks focus itself, no-op outside v2), `modNoRingInV2(stroke)`, `ModPlainButton` (= `NuvioActionPill` in v2).

## Left as Arc made them (on purpose)
Profile picker and editor; 4 accent-filled buttons (Scout rate controls, Scout action, collection TMDB picker primary, collection-management selected tab); source-status chips on the stream screen; rings that mark "selected" (theme card, app icon, layout card), the search-jump landing marker; a few plain buttons (red sign-out, 2 MDBList, 4 comments, 1 library dialog, 1 profile screen). Arc's dead-code switches stay (smaller diff against Arc).

## Gotchas (androidx.tv source)
`Button`/`Card` content has `defaultMinSize(58dp × 40dp)`: use `Modifier.height/width` to go smaller. A Surface/Card/Button applies the caller's `modifier` outside its focus scale: an item that gets the modifier highlight must not grow.
