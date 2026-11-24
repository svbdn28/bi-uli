// Offline copy of the app. Bump VERSION whenever any file below changes, so phones pick up the new copy.
const VERSION = 'uli-1';
const FILES = [
  './', 'index.html', 'app.css', 'app.js', 'engine.js', 'questions.json', 'manifest.webmanifest',
  'fonts/sans.woff2', 'fonts/mono.woff2', 'icons/icon-180.png', 'icons/icon-192.png', 'icons/icon-512.png',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

// Cache first: the app never needs the network once installed.
self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  e.respondWith(caches.match(req, { ignoreSearch: true }).then((hit) => hit || fetch(req)));
});
