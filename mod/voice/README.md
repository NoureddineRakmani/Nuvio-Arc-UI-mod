# PG Voice

The TV box's voice assistant: the remote's mic button opens PG Voice instead of Google's assistant, and PG Voice
sends what you say to the right place, including apps Google's assistant ignores.

- **Hearing**: Google's speech recognition (Katniss's RecognitionService), the same engine PG Prime's mic uses. Her
  choice on 2026-11-22: the offline Whisper engine (kept in `offline/`, with `patches/` for whisper.cpp) was too
  inaccurate for her voice on this box and took 2-5 s. **So Katniss must stay installed and enabled.**
- **Languages**: the bar starts in French; **OK** on the remote while it listens = English (blue dot; OK again does nothing). Her remote drops a quick second mic press (box log: one "Assistant has been requested" per double press). Google's TV
  recognizer takes one language per request online, and detects languages only offline: no offline packs on this box.
- **Mic button** (opens as the box's assistant): a small bar at the bottom of the screen, over any app.
  - "The Matrix", "cherche Dune" → PG Prime search
  - "a movie about magic", "un film de magie", "ask Scout …" → Scout (PG Prime, with your AI key)
  - "cat videos on YouTube", "une vidéo YouTube sur …" → SmartTube search, words as heard (no AI: her call)
  - "open Arte", "ouvre Eclipse", "Arte" → opens the app
  - "pause", "play", "suivant", "monte le son", "coupe le son", "accueil" → player / volume / home
- **AI for PG Prime** (`route/VoiceAi.kt`): with a Gemini key typed on PG Voice's page, the words of PG Prime requests
  go to Gemini (gemini-flash-lite-latest), which fixes misheard words and picks Search or Scout. Rules are the fallback.
- PG Prime takes searches only if its manifest has `pgvoice.handoff` (Nuvio Mod: `ui/voice/ModVoiceInbox.kt`).
- Works without PG Prime: PG Prime requests then show "PG Prime isn't installed".

## Where it lives
In the fork: `mod/voice/` (this folder). Working copy on Flora's PC: `~/nuvio-mod-dev/pg-voice` (has the build
outputs, models and whisper.cpp, which are not saved). Edit there, then copy the sources here and commit
(`~/nuvio-mod-dev/mine` is reset to GitHub `dev` by `nuvio-try`, so never edit here only). Not built by CI.

## Build

    cd ~/nuvio-mod-dev/pg-voice && ./gradlew :app:assembleRelease
    adb -s 192.168.1.10:5555 install -r app/build/outputs/apk/release/app-release.apk

## Make it the box's assistant (adb)

    adb shell settings put secure assistant com.pgprime.voice/.ui.VoiceActivity
    adb shell settings put secure voice_interaction_service ""

Leave `voice_recognition_service` on Katniss and Katniss enabled: PG Voice (and PG Prime's mic) hear through it.

## Undo: back to Google's assistant

    adb shell settings put secure assistant com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService
    adb shell settings put secure voice_interaction_service com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService

## Logs
`adb logcat -s PgVoice:V ModVoice:V` — what Google heard, Gemini's choice and timings.
