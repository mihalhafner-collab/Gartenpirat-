(function () {
  'use strict';
  function install() {
    const page = document.getElementById('sync-page');
    if (!page || document.getElementById('pixel-tunnel-card')) return;
    const card = document.createElement('div');
    card.id = 'pixel-tunnel-card';
    card.className = 'card';
    card.innerHTML = '<h3>ChatGPT-Verbindung auf dem Pixel</h3>' +
      '<p id="pixel-tunnel-state" class="sub" role="status" aria-live="polite">Verbindung noch nicht eingerichtet.</p>' +
      '<p class="sub">Kunden, Aufträge und Arbeitsberichte lesen. Mit Freigabe auch Cloudname_189a14e78c anlegen und korrigieren. Verwendet deinen zuletzt gespeicherten Drive-Stand.</p>' +
      '<button type="button" id="pixel-tunnel-setup" class="secondary" style="min-height:52px;width:100%">Verbindung und Akku-Einstellungen</button>';
    page.appendChild(card);
    const button = card.querySelector('button');
    button.addEventListener('click', function () {
      if (typeof AndroidData !== 'undefined' && AndroidData.openPixelTunnelSettings) AndroidData.openPixelTunnelSettings();
      else document.getElementById('pixel-tunnel-state').textContent = 'Die Einrichtung ist in der neuen Android-Testversion verfügbar.';
    });
    function update() {
      if (document.hidden || !page.classList.contains('active')) return;
      try {
        const info = JSON.parse(AndroidData.getPixelTunnelStatus());
        document.getElementById('pixel-tunnel-state').textContent = info.detail || 'Verbindung noch nicht eingerichtet.';
      } catch (_) {}
    }
    update();
    setInterval(update, 3000);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install);
  else install();
}());
