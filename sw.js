// Aesthetic Body (IronLog) offline cache. Bump VERSION when you upload new files.
const VERSION = 'ironlog-v11';
const ASSETS = ['./', './index.html', './manifest.json', './icon-180.png', './icon-192.png', './icon-512.png', './fonts/plus-jakarta-sans.woff2', './fonts/outfit.woff2'];
// Exercise videos are saved on the phone so they play offline. Bump MEDIA (and VID_V in index.html) when the clips change.
// Keep SLUGS in sync with the VIDEOS map in index.html.
const MEDIA = 'ironlog-media-3';
const SLUGS = ['bench-press', 'incline-press', 'decline-db-press', 'close-grip-bench', 'skull-crusher', 'back-squat', 'romanian-deadlift', 'bulgarian-split-squat', 'hip-thrust', 'deadlift', 'barbell-row', 'db-row', 'ez-curl', 'hammer-curl', 'overhead-press', 'seated-db-press', 'lateral-raise', 'rear-delt-fly', 'shrug'];
const MEDIA_FILES = SLUGS.flatMap(s => [`./videos/${s}.mp4`, `./videos/${s}.jpg`, `./videos/thumbs/${s}.jpg`]);

async function saveMedia() {
  const c = await caches.open(MEDIA);
  // videos first so the tutorials work offline as soon as possible; skip what is already saved
  await Promise.allSettled(MEDIA_FILES.map(async f => {
    const url = new URL(f, self.location).href;
    if (await c.match(url)) return;
    const res = await fetch(url, { cache: 'reload' });
    if (res.status === 200) await c.put(url, res);
  }));
}

// Serve a saved video, answering Range requests (Safari and WebViews need 206 partial responses to play video)
async function serveVideo(req) {
  const c = await caches.open(MEDIA);
  const res = await c.match(req.url, { ignoreSearch: true });
  if (!res) return fetch(req);
  const range = req.headers.get('range');
  if (!range) return res;
  const blob = await res.blob();
  const m = /bytes=(\d*)-(\d*)/.exec(range);
  let start = 0, end = blob.size - 1;
  if (m && m[1]) { start = +m[1]; if (m[2]) end = Math.min(+m[2], end); }
  else if (m && m[2]) { start = Math.max(0, blob.size - +m[2]); }
  if (start >= blob.size) return new Response(null, { status: 416, headers: { 'Content-Range': `bytes */${blob.size}` } });
  return new Response(blob.slice(start, end + 1), { status: 206, statusText: 'Partial Content', headers: {
    'Content-Type': 'video/mp4', 'Accept-Ranges': 'bytes', 'Content-Length': String(end - start + 1), 'Content-Range': `bytes ${start}-${end}/${blob.size}` } });
}

self.addEventListener('install', e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(ASSETS)));
  self.skipWaiting();
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== VERSION && k !== MEDIA).map(k => caches.delete(k)))).then(saveMedia));
  self.clients.claim();
});
// The page asks for another attempt whenever it is opened online (covers an interrupted first download)
self.addEventListener('message', e => { if (e.data === 'save-media') e.waitUntil(saveMedia()); });
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const path = new URL(e.request.url).pathname;
  if (path.endsWith('.mp4')) { e.respondWith(serveVideo(e.request)); return; }
  // Video poster stills and list photos
  if (path.includes('/videos/')) {
    e.respondWith(caches.open(MEDIA).then(c => c.match(e.request, { ignoreSearch: true })).then(r => r || fetch(e.request)));
    return;
  }
  // App page: try network first (gets updates), fall back to cache offline
  if (e.request.mode === 'navigate') {
    // Only the app page itself; other pages (e.g. preview.html) go straight to the network
    if (!/\/(index\.html)?$/.test(path)) return;
    e.respondWith(fetch(e.request).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(VERSION).then(c => c.put('./index.html', copy)); }
      return res;
    }).catch(() => caches.match('./index.html')));
    return;
  }
  e.respondWith(caches.match(e.request, { ignoreSearch: true }).then(r => r || fetch(e.request)));
});
