// Aesthetic Body (IronLog) offline cache. Bump VERSION when you upload new files.
const VERSION = 'ironlog-v6';
const ASSETS = ['./', './index.html', './manifest.json', './icon-180.png', './icon-192.png', './icon-512.png', './fonts/plus-jakarta-sans.woff2', './fonts/outfit.woff2'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(ASSETS)));
  self.skipWaiting();
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== VERSION).map(k => caches.delete(k)))));
  self.clients.claim();
});
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  // App page: try network first (gets updates), fall back to cache offline
  if (e.request.mode === 'navigate') {
    // Only the app page itself; other pages (e.g. preview.html) go straight to the network
    const path = new URL(e.request.url).pathname;
    if (!/\/(index\.html)?$/.test(path)) return;
    e.respondWith(fetch(e.request).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(VERSION).then(c => c.put('./index.html', copy)); }
      return res;
    }).catch(() => caches.match('./index.html')));
    return;
  }
  e.respondWith(caches.match(e.request, { ignoreSearch: true }).then(r => r || fetch(e.request)));
});
