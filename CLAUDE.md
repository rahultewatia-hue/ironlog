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
- `icon-180.png`, `icon-192.png`, `icon-512.png` — app icons (dumbbell with yellow/green/white plates on #1c2129).

## Android app
- `android/` is a minimal native WebView wrapper (Java, no AndroidX) that loads the live Pages URL, so web changes reach it automatically; the service worker handles offline.
- `index.html` `exportData()` calls `window.AndroidApp.saveBackup(name, json)` when running inside the wrapper (Web Share and downloads don't work in a WebView). Keep that branch.
- `.github/workflows/android.yml` builds a signed APK on GitHub Actions on every app change (`index.html`, `sw.js`, `manifest.json`, icons, `android/**`) and publishes it to the `android-latest` release:
  https://github.com/rahultewatia-hue/ironlog/releases/latest/download/aesthetic-body.apk
- `versionCode` = number of commits touching `android/` + 1 (`NATIVE_VERSION`), written into the release notes as `versionCode=N`. The app checks that release (every 6 h, on launch/resume) and offers to download and install a newer APK, so only native changes trigger an update prompt. Web changes appear live; the WebView reloads after an hour in the background.
- The signing key is in GitHub secrets (`ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`) and backed up locally outside the repo. Never regenerate it or change `applicationId`, or updates won't install over the existing app.

## Hard rules
1. **Never break saved data.** Data lives in localStorage under the key `ironlog-v1` with the shape
   `{sessions:[], weights:[], active:null|{...}, settings:{restC, restI, bar, ez}}`.
   If the data shape must change, add a migration in `load()` so old data keeps working.
2. **Stay offline-capable.** No network requests, no external fonts, scripts or images.
3. **If you change `sw.js`, `manifest.json` or any icon, bump `VERSION` in `sw.js`** (`ironlog-v1` → `ironlog-v2`, etc.). Changes to `index.html` alone don't need a bump.
4. After every change, extract the `<script>` from `index.html` and check it for syntax errors (e.g. `node --check`), then commit with a clear message and push to `main`. Tell Rahul in a sentence or two what changed.

## What the app does
- **Workout tab:** opens on today's workout. Each exercise has set rows (kg + reps + ✓). Ticking a set starts the rest timer (compound `restC` = 150 s, isolation `restI` = 90 s) with a beep. Shows last session's sets as placeholders and "add 2–4 kg" when every set hit 8 reps. Includes a plate guide, a finisher checkbox, and an effort selector. The session is saved on Finish.
- **Weight tab:** daily body weight log, 7-day average, and a trend chart.
- **Progress tab:** weekly calories bar chart, strength chart (estimated 1RM per session, Epley formula), and personal bests.
- **History tab:** past sessions, expandable, deletable.
- **Settings tab:** barbell and EZ bar weights, rest times, calorie explanation, export/import JSON backup, erase all.
- Charts are hand-drawn SVG (`smoothChart`) and CSS 3D bars; no libraries.

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
Light "liquid glass" theme (redesigned 2026-10-05). bg `#eef0f5` with soft blurred colour blobs (peach, lavender, mint) behind
frosted `.glass` surfaces (`backdrop-filter: blur(22px) saturate(180%)`, white hairline border, inner highlight, sheen).
Text `#1d2230`, muted `#7a8296`. Accent is soft gold `#d9a441` (text `#b07d1c`); green `#3f9a72` / sage gradient marks done/good.
3D touches: shaded SVG muscle maps (`figure()` / `pair()`, gradients `gP` primary, `gS` secondary, `gN` untrained), tilt-on-drag
cards (`.tilt`), glossy 3D plates, 3D calorie bars, floating glass tab bar, sliding glass "lens" on segmented controls.
`MUSCLES` maps every exercise to [primary, secondary] muscles; keep it in sync with `PROGRAM`.
Workout flow: Start workout → exercise list → tap an exercise to open its detail screen (Today's sets logging, plate guide,
muscle map, Weight/Reps/Est. 1RM chart, previous sessions) → finisher + effort → Finish and save.
Plate chip colours: 15 gold, 10 green, 5 white, 3 blue, 2.5 red, 2 light blue, 1 grey.
Font is `ui-rounded` (SF Pro Rounded on iPhone). Sentence-case labels, no all-caps. Safe-area aware. Mobile-first (iPhone ~390 px wide).
