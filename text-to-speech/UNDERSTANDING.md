# TextToSpeech → Android App: What I Understand You Want

**Source reviewed:** `TextToSpeech.html` ("Professional TTS for uTest Academy" / "uTest Academy TTS Pro", 390 lines, single HTML file) — preserved unchanged at `text-to-speech/original/TextToSpeech.html`.
**Status:** ✅ BUILT (2026-10-26, on the user's "Okay build it"). Native Android app at `text-to-speech/android-app/` (see its README), compiled to an installable APK by GitHub Actions (`.github/workflows/android-tts.yml`, artifact "TTSQueuePlayer-APK"). The open assumptions in §5 were taken as the bracketed defaults: `.txt` + paste (+ Share-sheet bonus included), −5s/+5s seek, standard Previous behaviour, no queue looping, queue reorder/remove/rename + remembered position, English US/UK, personal sideloaded APK. §3.6 anticipated Media3; the shipped app uses the framework `MediaSession` + foreground service instead — same user-visible behaviour, zero third-party dependencies, much smaller APK.

---

## 1. What your current file does (review)

Your file is a **single web page**, not an app. You open it in a browser, paste text into a box, press **Speak**, and the browser reads it aloud using the Web Speech API (`speechSynthesis`).

What it already has:

| Feature | In your file |
|---|---|
| Paste text + "Paste" (clipboard) button, "Load Sample" (uTest Academy sample), "Format Text" (cleans extra spaces/blank lines) | ✅ |
| Choose voice, language (English US / English UK only) | ✅ |
| Speed slider (0.5×–3×, default 1.2×), Pitch slider | ✅ |
| Speak / Pause / Resume / Stop buttons | ✅ |
| Reading pane underneath that highlights the part currently being read (yellow) and auto-scrolls to keep it centred | ✅ |
| Progress bar while speaking | ✅ |
| Smart chunking: text is cut into small pieces (paragraphs → sentences → clauses over ~60 characters at commas/"and"/"or"/"but"/colons), spoken piece-by-piece, with small natural pauses between pieces (bigger pause at sentence ends), pauses shortened at faster speeds | ✅ |

What it does **not** have / why it fails your needs today:

1. **Stops when you leave it.** It is a browser tab. On Android, when you switch to another app or lock the screen, the browser pauses/kills the page and `speechSynthesis` stops (or behaves unreliably). There is no background playback.
2. **No notification / lock-screen player.** It produces speech, not a media stream. There is no Media Session, so Android shows nothing in the notification bar — no pause, no rewind without reopening the browser and finding the tab.
3. **No rewind.** Only Pause / Resume / Stop (Stop = back to zero). Speech synthesis has no "seek bar" like an MP3 — you cannot drag backwards 5 seconds. The code *does* track which chunk it is on (`currentChunk`), but never exposes going back.
4. **Only one document at a time, pasted by hand.** No file opening, no queue, no list. Pasting new text replaces the old. Nothing is saved — refresh/close the tab and text + position are gone.
5. **Needs internet for its looks** (loads W3.CSS + Font Awesome from CDNs) * Small bugs worth noting: the reading pane inserts your text into the page as raw HTML (`innerHTML`), so text containing `<`, `>` or `&symbols;` can break the display; the voice list can be empty/mis-indexed if the browser loads voices late; Pause on Android Chrome is notoriously flaky.

None of this means the file is bad — the chunking + highlighting + pause-timing logic is genuinely good and is worth carrying over almost exactly into the Android version. The problem is the *platform*: a web page fundamentally cannot keep playing in the background with notification controls on Android. That is why you are asking for a real app, and you are right.

---

## 2. What you are asking for — explained like you are 2 years old

> You have a robot that reads your book out loud.
>
> Right now the robot only reads while you sit and look at it. If you walk to another room, the robot stops. Naughty robot.
>
> You want the robot to live inside your phone properly, like the music app.
>
> - The robot keeps reading even when you go play in other apps. It does not stop.
> - At the top of your phone, when you pull down, there are little buttons: **Stop** (pause), **Go**, **Go back a tiny bit** — for when you did not hear that last bit — and **Next book** / **Book before**. You do not have to go find the robot again.
> - You can give the robot a little pile of books — **1, 2, 3 … up to 10 books**. It reads the first book, then the next book, then the next, all by itself. And if you say "not this book — next book!" it jumps straight there. Or "go back to the book before!" and it jumps back.
> - The books do not have to be whole books from a shelf. You can copy some words from somewhere else — a website, a message, anywhere — and **stick (paste) them in**, and they become a new little book in the pile, same as the others.
>
> That is all. A reading robot that does not stop, with buttons at the top, and a pile of 10 books.

---

## 3. What you are asking for — explained like an adult

You want the web page converted into a **native, installable Android application** (an APK with its own icon on your home screen — no browser, no tab). The core job stays the same — turn text into spoken audio — but the playback must behave like a music/podcast player, not like a web page demo. Specifically:

### 3.1 Background playback (the main point)
Playback must continue uninterrupted while you use other apps, and with the screen locked/off. In Android terms this means playback cannot live in the UI; it must run in a **foreground service** with an ongoing notification (Android requires that visible notification for anything allowed to keep running — which conveniently is exactly the control bar you asked for), proper **audio-focus** handling (pause/duck when a call or another media app takes over), and headset/Bluetooth button support.

### 3.2 Notification-bar / lock-screen media controls
While reading, a media-player notification (and lock-screen controls, same thing under the hood — a `MediaSession`) must show:
- the title of the document currently being read (and ideally "Document 3 of 7" + progress),
- **⏮ Previous document** — jump back to the document before this one in the queue,
- **⏪ Rewind ≈5 seconds** — re-hear the bit you just missed (within the current document),
- **⏯ Play / Pause**,
- **⏭ Next document** — skip the rest of this document and start the next one in the queue,
- stop/close (swipe away / stop button in-app).

That is five controls, which fits the standard Android media notification layout exactly: `⏮ Prev doc | ⏪ −5s | ⏯ Play/Pause | ⏭ Next doc` (with room for ⏩ +5s if wanted). The same Next/Previous buttons also live on the app's player screen, tapping any item in the queue list jumps straight to it, and headset/Bluetooth next/previous-track buttons do the same thing.

Two standard edge-case behaviours (assumed — correct me if you want otherwise):
- **⏮ Previous** behaves like music/podcast apps: if you are more than a few seconds into the current document, the first press restarts *this* document from the top; pressing it again (at/near the start) goes to the previous document. On the first document, it just restarts it.
- **⏭ Next** on the last document stops playback at the end of the queue (no loop back to document 1, unless you ask for a repeat/loop option). Finishing a document normally still auto-advances to the next one without you touching anything.

**The one technical subtlety you should understand:** text-to-speech is *not* an audio file. There is no recording with a timeline to scrub through — the voice is being generated live, sentence by sentence. So "rewind 5 seconds" has to be *translated*: stop the current sentence, step back however many words/sentences equal roughly 5 seconds at your current speed (≈12–18 words at normal speed, adjusted by the speed slider), and start speaking again from there. To you it will feel exactly like a 5-second rewind; under the hood it is "go back N chunks using the same `currentChunk` tracking your HTML already has." A 100%-exact alternative exists (pre-record each document to a WAV file, then play it as real audio with a true seek bar), but it delays the start, eats storage, and makes speed changes messy — not recommended for the first version.

### 3.3 Load and queue up to 10 text documents
- Load documents from phone storage (file picker).
- **Or paste text directly into the queue (confirmed 2026-10-26):** copy (cut/copy) text from anywhere — a web page such as uTest Academy, WhatsApp, email, another app — then inside the app tap **Paste / Add from clipboard** (or long-press in a text box, edit/trim it if you want, the old "Format Text" clean-up included) and **Add to Queue**. This is exactly how your current HTML works today ("Paste uTest Academy content here…"), except:
  - each paste becomes **its own queue item** — one entry in the list of up to 10, equal in every way to a loaded file (plays in order, reachable by ⏭/⏮ Next/Previous, tappable, removable, remembered after closing the app);
  - you can paste several times to build the queue up piece by piece, and mix freely: e.g. 3 loaded `.txt` files + 4 pasted items = 7 of 10 slots used;
  - pasting is allowed **while something else is already playing** — the new item simply joins the end of the queue without interrupting playback;
  - since a paste has no filename, the item is auto-titled from its first line (falling back to "Pasted text 1", "Pasted text 2", …) and the title can be renamed (assumed — correct me if unwanted);
  - the 10-item limit applies to pastes exactly as to files: with a full queue, "Add to Queue" is refused with a message until you remove something.
  - (Android note: apps may only read the clipboard while they are on screen, which is fine here — you are inside the app when you press Paste.) A natural bonus on the same mechanism, if you want it: Android's **Share → this app** from other apps to send selected text straight into the queue without copy/paste — say the word and it's in scope.
- Up to **10 documents in a queue/playlist**: they play in order, one automatically following the next, like songs in a playlist.
- **Next / Previous controls move through this queue** (next document / previous document) — from the notification bar, the lock screen, the in-app player, and headset/Bluetooth buttons — exactly like next/previous track in a music app. See §3.2 for the assumed edge-case behaviour.
- You can see the queue list, see which one is playing, and tap any document in the list to jump straight to it; remove/reorder items (implied, please confirm).
- The 10 limit is enforced by the app (adding an 11th is refused with a message).
- Sensible extras this implies: the queue and your exact stopping point (which document, which sentence) should be **remembered if you close the app**, so tomorrow you resume where you left off. (Please confirm you want this.)

### 3.4 Everything good from the HTML, carried over
Voice choice, language, speed/pitch controls, the on-screen reading pane with the current sentence highlighted and auto-scrolling, the progress bar, and the smart sentence/clause chunking with natural pauses. On Android the voice engine becomes the phone's built-in text-to-speech engine (on-device, free, works offline; the exact voice names will differ from the browser's — e.g. your HTML prefers desktop voices like "Microsoft David" which don't exist on a phone).

### 3.5 What this is *not*
Not a web page wrapped in an app shell (WebView) — that would inherit the same background-playback failure. Not cloud/AI voices (they'd need internet + API keys + cost money per character) unless you later ask for them. The reading happens entirely on the phone.

### 3.6 How it would be built (so you know what's coming)
Kotlin + Android `TextToSpeech` engine for the voice, a `MediaSession`-based foreground service (Media3) for background/notification/lock-screen playback, your chunking logic ported sentence-by-sentence, a queue screen (max 10) backed by on-phone storage, and Android 13+ notification permission + media-playback foreground-service permission. The end product is an installable APK you can put straight on your phone (Play Store publishing is a separate, optional step).

---

## 4. My one-paragraph restatement (check me)

> Take my single-document, paste-and-play, browser-only TTS page and rebuild it as a real Android app that keeps reading in the background while I use other apps or lock the phone; gives me a Spotify-style player in my notification bar / lock screen with previous-document, ~5-second rewind, pause/resume, and next-document controls; and lets me load files and paste copied text (freely mixed) into a queue of up to 10 items that it reads through in order (auto-advancing, and skippable forwards/backwards) — keeping the voice/speed/pitch controls and follow-along sentence highlighting I already have.

## 5. Questions I need answered before I build (my assumptions in brackets)

1. **Document types:** ✅ Paste-in text as queue items confirmed (added 2026-10-26) — see §3.3. Still open: plain `.txt` files only, or also PDF / Word (`.docx`)? And do you want the **Share → app** shortcut for sending text into the queue from other apps? [Assumed: `.txt` + paste for now. PDF/Word are doable but are extra work.]
2. **Rewind / Next / Previous:** ✅ Next-document and Previous-document controls confirmed (added 2026-10-26). Remaining sub-questions: is rewind fixed at ~5 seconds, do you also want **forward +5/+10s**, and is the standard "Previous first restarts this document, press again for the one before" behaviour (§3.2) what you expect? [Assumed: −5s rewind, +5s forward if space allows, standard Previous behaviour, no queue looping.]
3. **Queue controls:** reorder, remove, tap-to-jump to a document? Remember queue + position after closing the app? [Assumed: yes to all.]
4. **Languages/voices:** English (US/UK) only, as in the file, or more? [Assumed: same as file, using whichever English voices your phone has.]
5. **Your phone:** roughly which Android version? (Affects notification permissions.) And is sideloading an APK I build for you acceptable, or must this go through the Play Store? [Assumed: personal APK, modern Android.]

Say "correct, build it" (and answer any of the 5 you care about) and the next step is the Android project itself.
