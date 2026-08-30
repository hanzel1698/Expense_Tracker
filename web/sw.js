// ── Service worker ────────────────────────────────────────────────────────────
// Network-first so a redeploy is picked up immediately, with a cache fallback
// that keeps the app usable offline (all data lives in localStorage anyway).

const CACHE = 'expense-tracker-v1';
const ASSETS = [
  './',
  './index.html',
  './config.js',
  './manifest.webmanifest',
  './css/aurora.css',
  './icons/icon.svg',
  './js/app.js',
  './js/dom.js',
  './js/util.js',
  './js/model.js',
  './js/store.js',
  './js/nav.js',
  './js/components.js',
  './js/calendar.js',
  './js/charts.js',
  './js/csv.js',
  './js/recurring.js',
  './js/sample.js',
  './js/sync.js',
  './js/screens/signin.js',
  './js/screens/dashboard.js',
  './js/screens/expenses.js',
  './js/screens/budget.js',
  './js/screens/entry.js',
  './js/screens/settings.js',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.addAll(ASSETS))
      .then(() => self.skipWaiting())
      .catch(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  // Never cache Google's auth/Drive endpoints.
  if (url.origin !== self.location.origin) return;

  event.respondWith(
    fetch(request)
      .then((response) => {
        const copy = response.clone();
        caches.open(CACHE).then((cache) => cache.put(request, copy)).catch(() => {});
        return response;
      })
      .catch(() => caches.match(request).then((cached) => cached || caches.match('./index.html'))),
  );
});
