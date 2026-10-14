const CACHE = 'haus-garten-v11';
const ASSETS = ['/', '/index.html', '/styles.css?v=1.25', '/app.js?v=1.25', '/invoice-flow.js?v=1.25', '/invoice-editor.js?v=1.25', '/invoice-editor.css?v=1.25', '/hafner-logo.png', '/manifest.webmanifest', '/icon.svg'];
self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS))));
self.addEventListener('activate', event => event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('haus-garten-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim())));
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/photos/')) return;
  event.respondWith(fetch(event.request).then(response => {
    const clone = response.clone(); caches.open(CACHE).then(cache => cache.put(event.request, clone)); return response;
  }).catch(() => caches.match(event.request).then(hit => hit || caches.match('/index.html'))));
});
