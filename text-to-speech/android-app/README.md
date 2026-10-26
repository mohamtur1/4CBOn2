# TTS Queue Player (Android)

Native Android rebuild of `TextToSpeech.html` ("uTest Academy TTS Pro") — see
`../UNDERSTANDING.md` for the agreed requirements this implements.

## What it does

- **Reads text aloud in the background** while you use other apps or the screen
  is locked/off (foreground service + the phone's on-device TTS voices; works
  offline, no account, no ads).
- **Notification-bar / lock-screen player** (appears while reading or paused):
  `⏮ Previous document · ⏪ −5 seconds · ⏯ Play/Pause · ⏩ +5 seconds · ⏭ Next document`.
  Headset/Bluetooth media buttons do the same.
- **Queue of up to 10 items, files and pastes mixed:**
  - paste text copied from anywhere → **+ Add text to queue**,
  - open one or more `.txt` files → **Open .txt file(s)**,
  - or use **Share → TTS Queue Player** from another app.
  Items play in order with automatic advance; tap an item to jump to it;
  `↑ ↓` reorder, `✎` rename, `✕` remove. Adding items while listening does
  not interrupt playback. The 11th item is refused.
- **−5s / +5s seek.** TTS is not a recording, so seeking jumps backwards/
  forwards over whole sentence-chunks by estimated spoken duration at your
  current speed (`core/SpeechEstimate.kt`) — it feels like a 5-second rewind.
- **Everything from the web page, ported:** voice + language (English US/UK),
  speed (0.5×–3×, default 1.2×), pitch, Load Sample, Format Text, progress
  bar, and the follow-along yellow highlight with auto-scroll. The original
  sentence/clause chunking and natural-pause timings are preserved in
  `core/TextChunker.kt`.
- **Remembers everything:** the queue, settings, and your exact stopping
  point (document + sentence) survive closing the app.
- Polite audio behaviour: pauses for calls/other media and resumes after
  temporary interruptions; pauses if headphones are unplugged.

Known, deliberate limits of v1: Pause restarts the current sentence (Android
TTS cannot freeze mid-sentence — chunks are small, so you lose a few words at
most); English US/UK only; `.txt` + paste/share only (no PDF/Word yet);
voice/speed changes apply from the next sentence.

## Get the APK

Every push builds a debug APK in GitHub Actions and publishes it to the
rolling **`tts-latest` Release** as `TTSQueuePlayer.apk`:

👉 **https://github.com/mohamtur1/4CBOn2/releases/tag/tts-latest**

1. Open that link **on your phone**, tap `TTSQueuePlayer.apk` to download.
2. Tap the downloaded file to install, and allow "install unknown apps" for
   your browser when Android asks. (Debug APK = installable directly; Play
   Store publishing would need a signed release build.)
   The same APK is also kept as the build artifact **TTSQueuePlayer-APK** on
   each Actions run, as a backup.
4. On first launch, allow notifications (Android 13+) — that's what puts the
   player in your notification bar.
5. If your phone brand aggressively kills background apps (some Xiaomi/
   Samsung/Huawei settings), set this app's battery usage to **Unrestricted**
   (Settings → Apps → TTS Queue Player → Battery) for uninterrupted long
   reading sessions.

Requires Android 8.0 (API 26) or newer. The voices are whichever English
voices Settings → Accessibility/Text-to-speech has installed (Google
Speech Services voices can be downloaded there for better quality).

## Build it yourself

```bash
# Needs JDK 17, Gradle 8.9, Android SDK platform 34.
gradle -p text-to-speech/android-app testDebugUnitTest assembleDebug
# APK: text-to-speech/android-app/app/build/outputs/apk/debug/app-debug.apk
```

No AndroidX/Compose dependencies — only the Android framework
(`Activity`, `TextToSpeech`, `MediaSession`, foreground `Service`) plus JUnit
for the unit tests of the chunking/seek/queue logic
(`app/src/test/.../CoreLogicTest.kt`).

## Layout

```
app/src/main/java/io/github/mohamtur1/ttsqueueplayer/
  MainActivity.kt          the one screen (add/queue/player/voice/reading pane)
  player/PlaybackService.kt  background engine: TTS, MediaSession, notification
  player/PlayerBus.kt        playback snapshots -> UI
  store/QueueStore.kt        queue + position + settings persistence (JSON prefs)
  core/TextChunker.kt        HTML chunking/pauses port, with highlight offsets
  core/SpeechEstimate.kt     ±5s seek maths
  core/QueueRules.kt         the max-10 rule + auto-titles
  core/SampleText.kt         the original uTest sample text
```
