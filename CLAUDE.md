# IronLog — project context for Claude Code

IronLog is Rahul's personal offline workout tracker. It's a PWA hosted on GitHub Pages
(repo: `rahultewatia-hue/ironlog`, live at https://rahultewatia-hue.github.io/ironlog/)
and installed on his iPhone via Safari → Add to Home Screen.

The app's display name is **Aesthetic Body** (title, header, home-screen label, manifest). "IronLog" remains the
internal/project name: keep the repo name, the URL, the `ironlog-v1` storage key and the `ironlog-vN` cache names unchanged.

## Files
- `index.html` — the entire app (HTML + CSS + JS). No build step, no external libraries, no CDNs. It must keep working offline.
- `sw.js` — service worker. `index.html` is fetched network-first, so the phone picks up updates automatically. Other assets are cache-first.
- `manifest.json` — PWA manifest.
- `icon-180.png`, `icon-192.png`, `icon-512.png` — app icons: glossy lime dumbbell at 35° with glow inside a lime progress ring on a dark charcoal-green background (generated with Pillow; Android adaptive foreground is a smaller-scaled copy so it fits the safe zone).

## Android app
- `android/` is a minimal native WebView wrapper (Java, no AndroidX) that loads the live Pages URL, so web changes reach it automatically; the service worker handles offline.
- `index.html` `exportData()` calls `window.AndroidApp.saveBackup(name, json)` when running inside the wrapper (Web Share and downloads don't work in a WebView). Keep that branch.
- `.github/workflows/android.yml` builds a signed APK on GitHub Actions on every app change (`index.html`, `sw.js`, `manifest.json`, icons, `android/**`) and publishes it to the `android-latest` release:
  https://github.com/rahultewatia-hue/ironlog/releases/latest/download/aesthetic-body.apk
- `versionCode` = number of commits touching `android/` + 1 (`NATIVE_VERSION`), written into the release notes as `versionCode=N`. The app checks that release (every 6 h, on launch/resume) and offers to download and install a newer APK, so only native changes trigger an update prompt. Web changes appear live; the WebView reloads after an hour in the background.
- The signing key is in GitHub secrets (`ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`) and backed up locally outside the repo. Never regenerate it or change `applicationId`, or updates won't install over the existing app.

## Hard rules
1. **Never break saved data.** Data lives in localStorage under the key `ironlog-v1` with the shape
   `{sessions:[], weights:[], active:null|{...}, prs:[{id,ex,w,r,date}], settings:{restC, restI, bar, ez}}` (`prs` was added later; `load()`, restore and import default it to `[]`).
   If the data shape must change, add a migration in `load()` so old data keeps working.
2. **Stay offline-capable.** No network requests, no external fonts, scripts or images (fonts and icons are bundled). Exceptions: the user-tapped "Full tutorial" link (opens YouTube). Exercise videos are saved on the phone by the service worker (cache `MEDIA` = `ironlog-media-N`, all 19 clips + posters + thumbs, ~10 MB, saved on activate and again via a `save-media` message each time the app is opened online) and served with Range support, so they play offline. When you add or replace a clip: update `SLUGS` in `sw.js`, bump `MEDIA` there and `MEDIA_CACHE` + `VID_V` in `index.html`, and bump `VERSION`.
3. **Bump `APP_BUILD` near the top of the `<script>` in `index.html` on every change to it** (open apps compare it with the live file and reload). **If you change `sw.js`, `manifest.json` or any icon, also bump `VERSION` in `sw.js`** (`ironlog-v1` → `ironlog-v2`, etc.). Changes to `index.html` alone need only the `APP_BUILD` bump, not a `VERSION` bump.
4. After every change, extract the `<script>` from `index.html` and check it for syntax errors (e.g. `node --check`), then commit with a clear message and push to `main`. Tell Rahul in a sentence or two what changed.

## What the app does
- **Navigation:** fixed glass top bar (Back, Forward, title, Home, Settings) and a bottom tab bar (Home, Workout, Weight, Progress, History).
  Screens are routed through the browser History API (`go(tab, ex)`, `popstate`, URL hash like `#workout/EZ-bar%20curl`), so Back/Forward and Android's back button work. Settings opens from the gear.
- **Home tab (default):** greeting, today's plan card over the first exercise's photo (Start / Continue / Done today / Rest day), weekly goal ring (workouts of 4, kcal, body weight change), Mon–Sun strip, quick-workout photo cards, last workout.
- **Data safety:** `save()` writes localStorage (`ironlog-v1`) and mirrors the same JSON to IndexedDB (`aesthetic-body` db, `kv` store) and, in the Android app, to a private file via `AndroidApp.saveData/loadData`. On start `restoreIfNeeded()` restores from a backup copy if the main data is empty.
- **Active workout controls:** while a workout is running, the timer pill has **Finish** and **Discard** buttons beside it on both the workout screen (`.workbar`) and the exercise screen (over the video). They use `workBtns()` / `data-act` and call `finish()` / `discardWorkout()` (the same logic as the buttons at the bottom of the workout screen).
- **Personal records (Big 3 only):** user-entered PRs for `PR_LIFTS` = Barbell bench press, Conventional deadlift, Barbell back squat, stored in `db.prs` (separate from the auto-detected "Personal bests"). A "Personal record" card with a lime **Log PR** button sits under the muscle chips on those three exercise screens only (`prCard`), and Progress has a "Big 3 records" card with Log PR buttons and a kg total once all three exist (`big3Card`). The sheet (`#prModal`: kg, reps, date) is `openPR`/`savePR`; best = highest est. 1RM; entries can be deleted.
- **Reports tab** (`#report`, opened from Home or Progress): Daily (any day: workouts, minutes, kcal, sets, kg lifted, PBs, weight vs previous entry, per-exercise sets) and Weekly (summary sentence, Mon–Sun strip, deltas vs the same point last week / the week before, muscles by volume, est. 1RM changes, PBs). "Share report" uses Web Share or copies text.
- **iPhone:** Home shows an "Install on your iPhone" guide when opened in Safari (not standalone); home-screen apps keep storage, Safari can clear it.
- **Workout tab:** opens on today's workout. Each exercise has set rows (kg + reps + ✓). Ticking a set starts the rest timer (compound `restC` = 150 s, isolation `restI` = 90 s) with a beep. Shows last session's sets as placeholders and "add 2–4 kg" when every set hit 8 reps. Includes a plate guide, a finisher checkbox, and an effort selector. The session is saved on Finish.
- **Weight tab:** body weight log for any calendar date up to today (date picker `#wDay`, `wDate` state; tapping an entry loads its date for editing; one entry per date via `upsertWeight`), 7-day average, and a trend chart.
- **Progress tab:** weekly calories bar chart, strength chart (estimated 1RM per session, Epley formula), and personal bests.
- **History tab:** past sessions, expandable, deletable.
- **Settings tab:** barbell and EZ bar weights, rest times, calorie explanation, export/import JSON backup, erase all.
- Charts are hand-drawn SVG (`smoothChart`) and CSS 3D bars; no libraries.
- Never use native `confirm()`/`prompt()`/`alert()` (blocked in some web views, e.g. the Android wrapper); use the in-app sheet `ask(msg, {ok, danger, input})`, which returns a Promise.

## Training program (the `PROGRAM` object)
Goal: fat loss. 6–8 reps, low volume, high intensity, sets taken 1–2 reps short of failure.
- **Mon — Chest + Triceps:** barbell bench press 3, incline barbell press 3, decline dumbbell press 2, close-grip bench press 3, EZ-bar skull crusher 2. Finisher: 6 min alternating 30 s push-ups / 30 s burpees.
- **Tue — Legs:** barbell back squat 3, Romanian deadlift 3, Bulgarian split squat 2, barbell hip thrust 2. Finisher: 6 × (20 s jump squats, 40 s rest).
- **Wed — rest** (walk).
- **Thu — Back + Biceps:** conventional deadlift 3, barbell bent-over row 3, single-arm dumbbell row 2, EZ-bar curl 3, dumbbell hammer curl 2. Finisher: 6 min alternating 30 s renegade rows / 30 s mountain climbers.
- **Fri — Shoulders:** standing overhead press 3, seated dumbbell press 3, dumbbell lateral raise 3, rear-delt fly 2, barbell shrug 2. Finisher: 6 × (20 s light dumbbell thrusters, 40 s rest).
- Sat/Sun — rest.

## Equipment (home gym)
Barbell, EZ (zig-zag) bar, 4 dumbbell handles, adjustable bench (flat/incline/decline), squat rack.
Plates: 2×15, 2×10, 4×5, 4×3, 2×2.5, 4×2, 2×1 kg.
This is `PLATES_PER_SIDE` = 15×1, 10×1, 5×2, 3×2, 2.5×1, 2×2, 1×1 per side. Bar weights are user settings (defaults: bar 20, EZ 10).

## Calorie estimate
`kcal = (MET − 1) × bodyweight_kg × hours`. Effort levels are Moderate 3.5, Hard 5 (default) and All-out 6. The finisher is 8 MET for up to 6 min.
These are active calories (net of resting burn) and are presented as an estimate. Body weight is the latest logged entry. Sessions open more than 120 min prompt for the real duration.

## Design
Dark theme with a restrained lime accent (redesigned 2026-10-05 from a reference, then toned down and made more professional on 2026-10-06).
bg `#0b0e10`, mostly solid dark surfaces `rgba(22,27,30,.84)` with light blur, 8% white hairline borders, 18px radius (16 tiles, 20 hero/plan/sheets), neutral shadows only.
No coloured glows, glossy highlights, 3D tilt, bouncy easing or decorative 3D plates. Keep motion to short ease-out `cubic-bezier(.2,.8,.2,1)`.
Text `#f3f6f4`, muted `#8a949b`. Accent is a livelier lime `#c2ea3f` (`--lime`; text on lime `#0b0f05`); `--sage` `#a9be72` for secondary icons/labels; red `#ff6b5b` for bad/danger. Use the accent only for primary actions, active state, progress and ticks.
Primary buttons are lime pills (Start workout has a dark circle with a play icon); segmented controls use a sliding lime lens.
Fonts are bundled in `fonts/` (SIL OFL, licences included): **Outfit** for headings and big numbers (`--display`), **Plus Jakarta Sans** for UI text (`--font`).
Icons are **Lucide** (ISC), inlined as an SVG `<symbol>` sprite at the top of `<body>`; use `ico('name')`. To add an icon, copy its inner SVG from lucide-static into a new `<symbol id="i-name">`.
Shaded SVG muscle maps (`figure()` / `pair()`, gradients `gP` lime = main, `gS` olive = helper, `gN` = untrained) and CSS 3D bars.
`MUSCLES` maps every exercise to [primary, secondary] muscles and `HOWTO` holds 3 steps + a tip; keep both in sync with `PROGRAM`.
Exercise screen: top half is a sticky stage playing a muted, looping **stock video** of the exercise showing correct form (`VIDEOS` maps exercise → `videos/<slug>.mp4` + `.jpg` poster;
Pexels and Mixkit clips, 800×700 H.264 with the full frame letterboxed over a blurred copy of itself so the whole body stays visible, sources in `videos/CREDITS.md`).
MP4s, posters and thumbs are saved offline by the service worker (see hard rule 2); `sw.js` answers Range requests from the cache because Safari/WebViews need 206 responses. Settings shows how many videos are saved (`mediaStatus`).
If a clip can't load (not yet saved and no internet) the poster stays with a "Retry" message.
Form coach: the clips are generic stock footage, so `formCoach()` overlays the `HOWTO` steps and tip one at a time on the stage (auto-advances every 5 s while playing, tap for next) to turn the footage into a guided tutorial. The user does NOT want animated/motion-graphic demos, only real footage.
Pause/play pill and a "Full tutorial" link that opens a YouTube search. List rows show a photo from `videos/thumbs/<slug>.jpg` (168×210 portrait crop cut from the source clip) via `photo()`, with the muscle figure as fallback.
New exercise → add a clip to `videos/`, a thumb to `videos/thumbs/`, and entries in `VIDEOS`, `MUSCLES`, `HOWTO`.
Bottom half: muscle chips, How to do it, Today's sets logging, plate guide, muscle map, chart, previous sessions. Tab bar hides there (`body.focus`).
Plate chip colours: 15 gold, 10 green, 5 white, 3 blue, 2.5 red, 2 light blue, 1 grey. Sentence-case labels. Safe-area aware. Mobile-first (iPhone ~390 px wide).
