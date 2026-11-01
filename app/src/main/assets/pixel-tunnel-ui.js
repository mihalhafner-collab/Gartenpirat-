(function () {
  'use strict';
  function install() {
    const page = document.getElementById('sync-page');
    if (!page || document.getElementById('pixel-tunnel-card')) return;
    const card = document.createElement('div');
    card.id = 'pixel-tunnel-card';
    card.className = 'card';
    card.innerHTML = '<h3>ChatGPT-Steuerung</h3>' +
      '<p id="pixel-tunnel-state" class="sub" role="status" aria-live="polite">Verbindung noch nicht eingerichtet.</p>' +
      '<p class="sub">Mit deiner Freigabe kannst du die App auch über ChatGPT bedienen.</p>' +
      '<button type="button" id="pixel-tunnel-setup" class="secondary" style="min-height:52px;width:100%">ChatGPT EIN / AUS</button>';
    page.appendChild(card);
    const button = card.querySelector('button');
    button.addEventListener('click', function () {
      if (typeof AndroidData !== 'undefined' && AndroidData.openPixelTunnelSettings) AndroidData.openPixelTunnelSettings();
      else document.getElementById('pixel-tunnel-state').textContent = 'Die Einrichtung ist in der neuen aktuellen Android-App verfügbar.';
    });
    function update() {
      if (document.hidden || !page.classList.contains('active')) return;
      try {
        const info = JSON.parse(AndroidData.getPixelTunnelStatus());
        document.getElementById('pixel-tunnel-state').textContent = window.HGBusinessFeedback?.connection(info)||'ChatGPT ist ausgeschaltet.';
      } catch (_) {}
    }
    update();
    setInterval(update, 3000);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install);
  else install();
}());
