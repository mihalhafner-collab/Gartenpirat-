# CODEX-GESAMTAUFTRAG (eine Datei): Sicherheit, Grafik, Kalender-Übersicht – Basis 1.54.2 / Build 91

**Diese eine Datei enthält alle Vorgaben.** Die früher getrennten Aufträge stehen hier als Teile 1 bis 3. Wo ein Teil auf `CODEX-AUFTRAG-SICHERHEIT-1.54.md`, `CODEX-AUFTRAG-GRAFIK-1.54.md` oder `CODEX-AUFTRAG-KALENDER-UI-1.54.2.md` verweist, ist der entsprechende Teil **in dieser Datei** gemeint. Es gibt keine weiteren Anhänge.

## Reihenfolge und Zuständigkeit

0. **Ausgangslauf** der vorhandenen Tests auf dem unveränderten Stand (läuft unabhängig weiter, Ergebnis notieren).
1. **Teil 1 – Sicherheit:** Paket A (Klartext-HTTP) und Paket C (WebView/CSP). *Paket B entfällt (Webapp verworfen).*
2. **Teil 2 – Grafik:** G1 (Tokens), danach G3, G4, G5, G6. **G2 wird durch K8 in Teil 3 ersetzt.**
3. **Teil 3 – Kalender-Übersicht:** K1–K9.
4. Abschlusslauf und **ein** Abschlussbericht in `CODEX-STATUS.md`.

Basisbranch: `codex/calendar-permanent-20261021` (Quellfingerprint `747762839020b4ffb0491ac360c3cc054c455245eb4126b0c1e6d4e8882b0c3c`), Arbeitsbranch `codex/calendar-ui-overview-20261021`. Jedes Paket ein eigener Commit, kein Push auf `main`, keine APK/Signierung/Versionserhöhung. Pixel-Abnahme und echte Google-Freigabe bleiben NOT EXECUTED.

---

# TEIL 1 – SICHERHEIT

# CODEX-AUFTRAG: Sicherheits-Härtung der Android-App auf Basis 1.54.0 / Build 89

Dieser Auftrag ist für Codex Cloud geschrieben. Er enthält zwei kleine Arbeitspakete (A und C; B entfällt, weil das Webapp-/Laptop-Server-Projekt verworfen wurde) mit fertigen Diffs, Abnahmekriterien und einer Verbotsliste.
Reihenfolge: A → C. Nach jedem Paket die genannten Tests ausführen. Pro Paket ein eigener Commit auf einem eigenen Branch, kein Push auf `main`.

---

## 0. Rahmen (zuerst lesen)

- Basis: Repository `Gartenpirat-`, Stand 1.54.0 / versionCode 89. Regeln aus `AGENTS.md` und `CODEX-AUFTRAG-GARTENPIRAT.md` gelten weiter.
- **Keine APK, keine Signierung, keine Versionserhöhung, keine Installation.** Nur Quellcode und Tests. Wenn später eine APK gewünscht ist, kommt das als eigener Auftrag.
- **Nicht ändern:** Paketname `at.hausgarten.service`, IDs, Relationen, Speichermodelle, Drive-Dateiformate, MCP-Werkzeugnamen (117 Tools), UI-Aktionen (156), Berechtigungen im Manifest (außer unten ausdrücklich genannt: keine).
- **Keine Daten- oder Kontozugriffe.** Nur synthetische Testdaten.
- Die Diffs unten sind aus der Quelle abgeleitet, aber ohne Git-Zugriff geschrieben. Zeilennummern können abweichen. Maßgeblich ist der Kontext. Wenn ein Kontext nicht passt: Stelle suchen, Absicht umsetzen, im Statusbericht vermerken.
- Ergebnis nach `CODEX-STATUS.md` schreiben (neuer Abschnitt). Nur tatsächlich ausgeführte Tests als PASS melden. Geräteabnahme auf dem Pixel bleibt **NOT EXECUTED**.

---

## Paket A: Klartext-HTTP in der Android-App einschränken

**Problem:** `usesCleartextTraffic="true"` und `cleartextTrafficPermitted="true"` erlauben unverschlüsselten Verkehr app-weit. Im App-Code gibt es kein produktives `http://`-Ziel (nur `https://api.openai.com`, Google-APIs und Tests auf `127.0.0.1`).

**Ziel:** Klartext nur noch für Loopback und den Android-Emulator-Host (für Tests), sonst überall HTTPS.

### A1. `android/app/src/main/res/xml/network_security_config.xml`

```diff
 <?xml version="1.0" encoding="utf-8"?>
 <network-security-config>
-    <base-config cleartextTrafficPermitted="true" />
+    <base-config cleartextTrafficPermitted="false" />
+    <!-- Nur lokale Testziele (Emulator/Loopback). Keine echten Server. -->
+    <domain-config cleartextTrafficPermitted="true">
+        <domain includeSubdomains="false">127.0.0.1</domain>
+        <domain includeSubdomains="false">localhost</domain>
+        <domain includeSubdomains="false">10.0.2.2</domain>
+    </domain-config>
 </network-security-config>
```

### A2. `android/app/src/main/AndroidManifest.xml`

```diff
         android:networkSecurityConfig="@xml/network_security_config"
         android:supportsRtl="true"
-        android:theme="@style/AppTheme"
-        android:usesCleartextTraffic="true">
+        android:theme="@style/AppTheme">
```

(Das Attribut `usesCleartextTraffic` entfällt komplett. Die Konfigurationsdatei ist maßgeblich.)

### A3. Prüfen, ob etwas Klartext braucht

1. `grep -rn "http://" android/app/src/main/java android/app/src/main/assets --include=*.java --include=*.js --include=*.html` ausführen. Erlaubt sind nur Treffer in `plant-catalog.js` (Datenlinks, kein Netzverkehr), Namespaces (`w3.org`) und die Testzweige für `127.0.0.1`.
2. Das Preview-Manifest wird aus `tools/build_ui_preview_assets.py` bzw. einem eigenen Manifest erzeugt. Prüfen, ob dort `usesCleartextTraffic` erwähnt wird. Falls ja, ebenso entfernen. Preview hat kein `INTERNET`, darf nichts Neues erhalten.
3. `tools/verify_ui_preview_apk.py`, `tests/apk_privacy_guard.py` und `tools/check_release_baseline.py` auf feste Erwartung `usesCleartextTraffic` durchsuchen. Falls vorhanden, Erwartung anpassen (nicht das Gate abschwächen, nur die neue Sollkonfiguration eintragen).

### A4. Abnahme Paket A

- `./gradlew :app:compileReleaseJavaWithJavac` und `:app:lintRelease` laufen ohne neue Fehler (Lint darf keine neue `CleartextTraffic`-Warnung zeigen).
- Instrumentierungs-Quellen (`PixelTunnelLiveProbe`, `PixelMcpInstrumentation`) kompilieren weiter. Sie sprechen `127.0.0.1` an und bleiben erlaubt.
- Keine Berechtigungsänderung (Manifest-Vergleich vorher/nachher).

---

## Paket C: WebView absichern (ohne Architekturumbau)

**Problem:** Die WebView lädt `file:///android_asset/mobile.html`, erlaubt Dateizugriff und hat vier JS-Brücken. Es gibt keine Content-Security-Policy. Wenn irgendwo ein Text ohne Maskierung per `innerHTML` landet, könnte Fremdcode Brücken aufrufen oder Daten senden.

**Wichtig, bewusst begrenzt:** `WebViewAssetLoader` (Umstellung auf `https://appassets.androidplatform.net`) wird **nicht** in diesem Auftrag gemacht. Es ändert den Origin, damit gehen `localStorage`-Entwürfe, Foto-Allowlists und mehrere Tests verloren. Das wäre ein eigener Migrationsauftrag. Hier nur die risikoarmen Schritte.

Befund, auf dem das Paket aufbaut:
- Es gibt in den App-Assets **kein** `fetch`, `XMLHttpRequest`, `WebSocket` oder `EventSource`. Die UI braucht also keine Netzverbindungen. Alle Netzzugriffe laufen nativ.
- `mobile.html` hat 6 Inline-`<script>`-Blöcke und keine Inline-Eventhandler (`onclick=` o. ä.). Deshalb muss `'unsafe-inline'` für Skripte vorerst bleiben. Der Hauptnutzen der CSP ist daher: **kein Datenabfluss und keine fremden Ressourcen**.
- Bilder kommen über `data:`-URIs, `blob:` (`URL.createObjectURL` in `gartenpirat.js`) und lokale Dateien.

### C1. WebView-Einstellungen: `MainActivity.java`, Methode `configureWebView()`

```diff
         settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setDatabaseEnabled(true);
-        settings.setAllowFileAccess(true); settings.setAllowContentAccess(true); settings.setCacheMode(WebSettings.LOAD_DEFAULT);
+        // Assets unter file:///android_asset/ bleiben auch ohne Dateizugriff erreichbar.
+        settings.setAllowFileAccess(false);
+        settings.setAllowFileAccessFromFileURLs(false);
+        settings.setAllowUniversalAccessFromFileURLs(false);
+        settings.setAllowContentAccess(true); // Foto-/Thumbnail-Provider (content://) bleiben nötig
+        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
```

Hinweise für Codex:
- `file:///android_asset/…` und `file:///android_res/…` sind von `setAllowFileAccess(false)` ausgenommen. Das ist die dokumentierte Android-Ausnahme. Wenn `openMobile()` (`webView.loadUrl("file:///android_asset/mobile.html")`) trotzdem fehlschlägt, **nicht** wieder auf `true` stellen, sondern Fehler im Bericht beschreiben.
- Vor der Änderung prüfen: `grep -rn "file:///" android/app/src/main/java android/app/src/main/assets`. Es darf außer `android_asset` kein weiterer Dateipfad geladen werden (nach bisheriger Durchsicht: nur `android_asset`, ergänzend `MainActivity` Zeile ca. 174, 324, 547).
- `setAllowContentAccess(true)` bleibt, solange `PhotoThumbnailProvider`/`PhotoCaptureProvider`-URIs in der WebView geladen werden.

### C2. Content-Security-Policy in `android/app/src/main/assets/mobile.html`

Direkt nach `<meta charset="utf-8">` einfügen (muss vor dem ersten `<script>` stehen):

```diff
-<html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
+<html lang="de"><head><meta charset="utf-8">
+<meta http-equiv="Content-Security-Policy" content="default-src file: data: blob: 'unsafe-inline'; connect-src 'none'; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'">
+<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
```

Erklärung der Wirkung:
- `connect-src 'none'`: kein `fetch`/XHR/WebSocket aus der UI. Verhindert Datenabfluss aus der Seite.
- `default-src file: data: blob: 'unsafe-inline'`: Skripte, Styles und Bilder nur aus der App, aus `data:`/`blob:` und Inline. Keine Ressourcen von `https://…`.
- `object-src`, `frame-src`, `base-uri`, `form-action` gesperrt.
- Links (`<a href="https://…">`) bleiben möglich, weil sie über `shouldOverrideUrlLoading` an externe Apps gehen und keine Ressourcenladung sind.

Falls `file:` als Quelle von Chromium in der WebView nicht greift (Skripte würden blockiert): erst im Browser-Testlauf prüfen. Dann Variante mit `'self'` ergänzen (`default-src 'self' file: data: blob: 'unsafe-inline'`), **nicht** `connect-src` lockern. Ergebnis im Bericht festhalten.

### C3. Preview-Variante und Generator

`tools/build_ui_preview_assets.py` erzeugt eine Preview-`mobile.html`. Prüfen, dass dort die CSP-Zeile mit übernommen wird (Preview soll ebenfalls netzlos sein). Das Preview-Gate `tools/verify_ui_preview_apk.py` anpassen, falls es `mobile.html` byte-genau vergleicht.

### C4. Fingerprint-/Review-Gates

`CODEX-STATUS.md` nennt einen UI-Quellreview-Fingerprint, der bei Änderungen an `mobile.html` neu nachzuführen ist (Ablauf wie im Status beschrieben: erst echte Review, dann Fingerprint aktualisieren). Das Gate nicht abschwächen, nur korrekt nachführen.

### C5. Tests für Paket C

Neue Datei `tests/webview-hardening.test.cjs` (Muster wie `tests/access-integrity.test.cjs`, Chromium, `pathToFileURL`):

```js
/* WebView-Härtung: CSP vorhanden, keine Netzzugriffe, App startet. Synthetische Brücke, keine Geräte-Aussage. */
'use strict';
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const { pathToFileURL } = require('node:url'), { chromium } = require('./browser.cjs');
const assets = path.resolve('android/app/src/main/assets');
const results = [];
(async () => {
  const html = fs.readFileSync(path.join(assets, 'mobile.html'), 'utf8');
  const check = async (name, fn) => { try { await fn(); results.push({ name, result: 'PASS' }); } catch (e) { results.push({ name, result: 'FAIL', error: e.message }); } console.log(results.at(-1)); };
  await check('CSP-Meta steht vor dem ersten Script', () => {
    const csp = html.indexOf('Content-Security-Policy'), script = html.indexOf('<script');
    assert(csp > -1 && csp < script);
    assert(/connect-src 'none'/.test(html) && /object-src 'none'/.test(html) && /frame-src 'none'/.test(html));
  });
  await check('Keine Netz-APIs in den App-Assets', () => {
    for (const f of fs.readdirSync(assets).filter(n => n.endsWith('.js') && n !== 'plant-catalog.js')) {
      const t = fs.readFileSync(path.join(assets, f), 'utf8');
      assert(!/\bfetch\s*\(|XMLHttpRequest|new\s+WebSocket|EventSource/.test(t), f + ' nutzt eine Netz-API');
    }
  });
  await check('Java: Dateizugriff aus, content-Zugriff an', () => {
    const j = fs.readFileSync('android/app/src/main/java/at/hausgarten/service/MainActivity.java', 'utf8');
    assert(/setAllowFileAccess\(false\)/.test(j) && /setAllowFileAccessFromFileURLs\(false\)/.test(j) && /setAllowUniversalAccessFromFileURLs\(false\)/.test(j));
    assert(/setAllowContentAccess\(true\)/.test(j));
  });
  await check('Seite startet mit CSP ohne Skriptfehler und ohne CSP-Verstöße', async () => {
    const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
    try {
      const page = await browser.newPage({ viewport: { width: 426, height: 852 } }), errors = [];
      page.on('pageerror', e => errors.push(e.message));
      page.on('console', m => { if (/Content Security Policy|Refused to/.test(m.text())) errors.push(m.text()); });
      await page.addInitScript(() => { /* gleiche synthetische AndroidData-Fixture wie access-integrity.test.cjs übernehmen */ });
      await page.goto(pathToFileURL(path.join(assets, 'mobile.html')).href);
      await page.waitForTimeout(800);
      assert.deepEqual(errors, []);
    } finally { await browser.close(); }
  });
  fs.mkdirSync('outputs', { recursive: true });
  fs.writeFileSync('outputs/webview-hardening-tests.json', JSON.stringify({ actualBrowser: true, nativeBridge: 'isolated fixture', results }, null, 2));
  if (results.some(r => r.result === 'FAIL')) process.exitCode = 1;
})();
```

Codex: die `addInitScript`-Fixture aus `tests/access-integrity.test.cjs` übernehmen (AndroidData-Stub), damit die Seite wie in den bestehenden Suiten startet. Neue Suite im gemeinsamen Browser-Runner registrieren.

### C6. Abnahme Paket C

- Alle 44 Browser-Suiten plus neue Suite PASS. Besonders `daily-usability`, `design-v139`, `gartenpirat-*`, `customer-photos`, `field-ui`, `calendar-authorization-ui` (hängen am Seitenstart und an Fotos/Blob-Bildern).
- 33 JVM-Suiten und `:app:compileReleaseJavaWithJavac` PASS.
- Cross-Language-Vertragshash (`899906c2…`) und 117 Tools / 156 UI-Aktionen unverändert.
- Pixel-WebView-Verhalten (Kamera, Foto-Auswahl, Seitenstart mit `setAllowFileAccess(false)`) bleibt **NOT EXECUTED** und wird im Bericht ausdrücklich als offen genannt. Diese Änderung muss vor Auslieferung auf dem Pixel getestet werden.

---

## Hinweis: verworfene Webapp

Das Webapp-/Laptop-Projekt ist verworfen, nur die Android-App zählt. In diesem Auftrag wird **nichts am Webapp-Code geändert oder gelöscht** (`server.py`, `static/`, `db.py`, `mcp_integration/`). Einiges davon hängt noch an Tests, am Export-/Bootstrap-Vertrag und am Drive-Format. Eine Aufräumaktion (toter Webcode) ist ein eigener, späterer Auftrag nach einer Abhängigkeitsanalyse.

## Pflicht-Abschlussbericht (in `CODEX-STATUS.md`)

Abschnitt „Sicherheits-Härtung A/C (Basis 1.54.0/89)" mit:
1. Geänderte Dateien je Paket (Liste).
2. Tatsächlich ausgeführte Tests mit Anzahl und Ergebnis, getrennt von nicht ausgeführten.
3. Abweichungen von den Diffs oben (z. B. andere Zeilen, CSP-Variante mit `'self'`).
4. Offene Punkte: Pixel-Abnahme, optional späterer Auftrag „WebViewAssetLoader mit Origin-Migration der Entwürfe".
5. Bestätigung: keine APK, keine Version, keine Berechtigungsänderung, keine Datenzugriffe, kein Push auf `main`.

## Verboten

- Kein Abschwächen von Privacy-, Signier-, Fingerprint- oder Vertrags-Gates.
- Keine Entfernung bestehender Tests, keine Löschung von Daten, kein Zugriff auf Konten, Drive oder Schlüssel.
- Kein `setAllowFileAccess(true)` als „Notlösung" bei Testproblemen.
- Keine Erweiterung der Berechtigungen oder neue Abhängigkeiten.


---

# TEIL 2 – GRAFIK

# CODEX-AUFTRAG: Grafische Vereinheitlichung der Android-App (Basis 1.54.0 / Build 89)

Für Codex Cloud. Reiner Oberflächen-Auftrag: Farben, Abstände, Buttons, Leisten, Statusdarstellung. **Keine Geschäftslogik, keine Daten, keine IDs.**
Der Auftrag ist mit den vorhandenen Testsuiten verknüpft (Abschnitt 3) und mit dem Sicherheitsauftrag `CODEX-AUFTRAG-SICHERHEIT-1.54.md` (Abschnitt 4).

---

## 0. Rahmen

- **Keine APK, keine Signierung, keine Versionserhöhung, keine Installation.** Nur Quellcode und Tests. Pixel-Abnahme bleibt **NOT EXECUTED**.
- **Nicht ändern:** Paket `at.hausgarten.service`, IDs, Relationen, Speichermodelle, Drive-Formate, die 117 MCP-Werkzeuge, die 156 UI-Aktionen, den Vertragshash `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`, Berechtigungen.
- **DOM-IDs, `data-*`-Attribute und CSS-Klassen, auf die Tests oder JS zugreifen, bleiben bestehen** (z. B. `#field-today-actions`, `#field-quick-entry`, `.nav`, `[data-open]`, `#status-page`, `#global-search-dialog`, `.page-head`, `.back`). Layout ändern ja, Selektoren umbenennen nein.
- Jedes Paket (G1–G6) = eigener Commit auf eigenem Branch, kein Push auf `main`. Nach jedem Paket die Tests aus Abschnitt 3 laufen lassen.
- Nur synthetische Testdaten, keine Konten-/Drive-/Schlüsselzugriffe.
- Die Gates (Privacy, UI-Quellreview-Fingerprint, Preview-Verifikation, Asset-Anzahl) **nicht abschwächen**, nur korrekt nachführen.
- Die fünf Screenshots des Nutzers (Heute, Mehr & Einstellungen, Status, Kalender & Wetter oben und unten; Pixel, 06:57/06:58) gehören als Anhang zur Aufgabe. Vorher/Nachher-Screenshots aus Chromium (320/360/426 px, 150 % Schrift) in `outputs/grafik-*.png` ablegen.

---

## 1. Ausgangsbefund (aus Quellcode und Screenshots)

| # | Befund | Ursache im Code |
|---|---|---|
| 1 | Farben/Radien/Schatten uneinheitlich | `--green` ist in 6 `:root`-Blöcken verschieden definiert: `mobile.html` (2×), `design-concept.css` (2×), `workday-ui.css`, weitere. Werte: `#0c4a2f`, `#154d38`, `#164d38`, `#164d39`, `#174c39`. Dazu ~25 fast gleiche Grün-/Grautöne, 5 Schattenvarianten, ~15 Schriftgrößen, Gewichte 650/700/750/800. |
| 2 | Schrift: `Inter` im Font-Stack, aber nicht gebündelt | `mobile.html` `:root{font-family:Inter,Roboto,…}`. Auf dem Pixel greift Roboto/Noto. |
| 3 | Seite „Kalender & Wetter" sieht aus wie eine andere App: graue eckige Standard-Buttons mit Schatten, Standard-Checkboxen | **Das ist kein WebView-Screen.** `PlanningConnectionActivity.java` baut die Oberfläche nativ (`new Button`, `new CheckBox`, `new TextView`, Zeilen ca. 22–61). Auch `DriveConnectionActivity` und `PixelTunnelActivity` sind native Activities. CSS erreicht sie nicht. |
| 4 | Heute: Leiste „Planung / Arbeit erfassen" schwebt über der Tab-Leiste und überdeckt die Karte „Heute" | `field-ui.js` Zeile ca. 30–32 erzeugt `#field-today-actions` (Dock), `field-ui.css` Zeile ca. 29 positioniert es fest. |
| 5 | Status: breite „Zurück"-Leiste über der Tab-Leiste deckt einen Button ab, zusätzlich Zurück-Pfeil oben links | `navigation-status.js` Zeile ca. 25 plus `.daily-toolbar-back` in `workday-ui.css`. |
| 6 | Mehr: Zurück-Pfeil links *und* Hamburger rechts auf einem Hauptreiter | `daily-usability`/`navigation-status`-Kopfzeile. |
| 7 | Dieselbe Google-Fehlermeldung steht 3× (Kopfzeile, rote Zeile in der Karte, Status) | `PlanningConnectionActivity` (Statuszeile `status` und Fehlertext) und `navigation-status.js`. |
| 8 | Statuskarte: gelber Seitenstreifen am Eck abgeschnitten; Fehlertext mischt „Freigabe nicht abgeschlossen" mit „Wetterwarnungen EIN · Pixel-Meldungen bereit" | `border-left` an Karte mit `border-radius`. |
| 9 | Heute: dicker weißer Strich zwischen „0 h" und „8 h"; „Arbeit starten / Arbeitszeit starten" doppelt; Zeile „Planung" in Mehr ist blau hinterlegt und wirkt wie „ausgewählt" | `.design-metrics`-Grid-Lücke; Texte in `workday-ui.js`; Blau-Hintergrund in `workday-ui.css`/`field-ui.css`. |

---

## 2. Arbeitspakete

### G1 – Design-Tokens an einer Stelle (kein sichtbarer Bruch)

**Ziel:** Eine Datei `android/app/src/main/assets/tokens.css`, als **erstes** Stylesheet in `mobile.html` geladen. Alle bisherigen `:root{--green…}`-Definitionen entfallen oder verweisen auf Tokens.

Neue Datei (Werte sind die am häufigsten genutzten bestehenden; Blau/Gelb/Rot aus der vorhandenen CSS übernehmen, nicht neu erfinden – `grep -n "Planung" android/app/src/main/assets/*.css`):

```css
/* tokens.css – einzige Quelle für Farben, Radien, Schatten, Schrift */
:root{
  --hg-bg:#f5f7f5; --hg-surface:#ffffff; --hg-text:#17241d; --hg-muted:#53655a; --hg-line:#dce4de;
  --hg-green:#164d38;      /* Primär Text/Icon, Kopfzeilen */
  --hg-green-btn:#23744e;  /* Primärbutton-Fläche (weißer Text, ≥ 4,5:1 prüfen) */
  --hg-sage:#edf3ee;       /* Sekundärfläche */
  --hg-info-bg:#e8f1fb;  --hg-info-text:#1f5c8f;   /* Planung (Wert aus Bestand prüfen) */
  --hg-warn-bg:#fff0cd;  --hg-warn-text:#765010;   /* Prüfbedarf */
  --hg-danger:#8a2e25;   --hg-danger-bg:#fbeceb;   /* Fehler/Löschen */
  --hg-radius:14px; --hg-radius-lg:18px; --hg-radius-pill:999px;
  --hg-shadow-1:0 1px 3px #173d2714;               /* flach (Karten) */
  --hg-shadow-2:0 -4px 14px #163d2414;             /* erhöht (feste Leisten) */
  --hg-font:Roboto,system-ui,"Noto Sans",sans-serif;
  --hg-fs-caption:.82rem; --hg-fs-body:1rem; --hg-fs-title:1.25rem; --hg-fs-head:1.6rem;
  --hg-fw-regular:400; --hg-fw-strong:700;
}
```

Umsetzung:
1. `tokens.css` in `mobile.html` direkt nach der CSP-Zeile (falls Sicherheitsauftrag C schon gemerged ist) bzw. als erstes `<link rel="stylesheet" href="tokens.css">` einfügen.
2. Die 6 `:root`-Blöcke auf Tokens umstellen (`--green:var(--hg-green)` etc. als **Alias**, damit bestehende Regeln nicht brechen). Danach in kleinen Schritten harte Hex-Werte durch `var(--hg-…)` ersetzen, **nur** dort, wo der Wert dem Token bis auf Nuancen entspricht. Keine Farbumdeutung, kein Dunkelmodus.
3. Font-Stack: `Inter` entfernen (nicht gebündelt, keine neuen Assets, damit die Asset-Gates stabil bleiben). Schriftgrößen auf die 4 Stufen und 2 Gewichte vereinheitlichen, wo ohne Layoutbruch möglich (Rest in Folgepaket).
4. Kein `@import`, keine externen URLs (CSP aus Sicherheitsauftrag C).
5. Neue Datei muss in `tools/build_ui_preview_assets.py` (Preview-Generator) und in den Asset-Listen/Gates (`tools/verify_ui_preview_apk.py`, `tests/apk_privacy_guard.py`, Release-Baseline) berücksichtigt werden. Erwartete Main-Asset-Anzahl **417 → 418**, Erwartung gezielt anpassen, nicht entfernen.

### G2 – Native Screens im gleichen Stil (größter sichtbarer Hebel)

**Ziel:** `PlanningConnectionActivity`, `DriveConnectionActivity`, `PixelTunnelActivity` sehen aus wie die WebView-Seiten.

Neue Hilfsklasse `android/app/src/main/java/at/hausgarten/service/NativeUi.java` (nur Darstellung, keine Logik):

```java
package at.hausgarten.service;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.Switch;

/** Gemeinsame Optik für native Screens. Farben spiegeln assets/tokens.css (Test prüft Gleichheit). */
final class NativeUi {
    static final int BG = 0xfff5f7f5, SURFACE = 0xffffffff, TEXT = 0xff17241d, MUTED = 0xff53655a,
        LINE = 0xffdce4de, GREEN = 0xff164d38, GREEN_BTN = 0xff23744e, SAGE = 0xffedf3ee,
        WARN_BG = 0xfffff0cd, WARN_TEXT = 0xff765010, DANGER = 0xff8a2e25;
    private NativeUi() {}

    static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable shape(Context c, int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(dp(c, 1), stroke);
        return d;
    }

    /** kind: 0 = Primär (grün), 1 = Sekundär (salbei), 2 = Gefahr (rot umrandet) */
    static void style(Button b, int kind) {
        Context c = b.getContext();
        b.setAllCaps(false); b.setStateListAnimator(null); b.setElevation(0);
        b.setMinHeight(dp(c, 52)); b.setTextSize(16);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        if (kind == 0) { b.setBackground(shape(c, GREEN_BTN, 0, 14)); b.setTextColor(Color.WHITE); }
        else if (kind == 2) { b.setBackground(shape(c, SURFACE, DANGER, 14)); b.setTextColor(DANGER); }
        else { b.setBackground(shape(c, SAGE, 0, 14)); b.setTextColor(GREEN); }
    }

    /** Zeile mit Schalter statt Checkbox; Zeile mind. 56 dp hoch, ganze Zeile tippbar. */
    static Switch toggle(Switch s) {
        Context c = s.getContext();
        s.setMinHeight(dp(c, 56)); s.setTextSize(16); s.setTextColor(TEXT);
        return s;
    }
}
```

Anwendung in `PlanningConnectionActivity` (Zeilen ca. 22–61), ohne Verhalten zu ändern:
- `button(...)`: nach `new Button(this)` → `NativeUi.style(b, kind)`. Fußleiste: „Zurück" = Sekundär (1), „Google-Freigabe erneuern"/„Google Kalender verbinden" = Primär (0). Das `setBackgroundTintList(...)` an `calendarAction` durch `NativeUi.style(calendarAction, 0)` ersetzen.
- `check(...)`: `CheckBox` → `android.widget.Switch` (Framework, **keine neue Abhängigkeit**), gleiche Listener, gleiche gespeicherten Schlüssel (`weather_enabled`, Pixel-Benachrichtigungen, „Google Kalender verwenden"). Textgröße/Abstände über `NativeUi.toggle`.
- `card(...)`: Hintergrund `NativeUi.shape(this, SURFACE, LINE, 18)`, innerer Abstand 16 dp.
- Hintergrund `screen.setBackgroundColor(NativeUi.BG)`.
- Fehlermeldungen: Hinweisbox (Warn-Hintergrund, Warn-Text, ein Icon-Zeichen „!" vorangestellt) statt nur rotem Fließtext.
- **Meldung nur einmal anzeigen:** Der Kopfzeilen-Status (`status`) zeigt bei vorhandenem Fehler in der Karte nur die Kurzform („Verbindung nicht abgeschlossen"); der ausführliche Text steht einmal in der Hinweisbox. Fehlercodes (`CALENDAR_GOOGLE_AUTH_ERROR`, redigierte Detailtexte „Details") und alle bestehenden Strings, die Tests prüfen, bleiben unverändert verfügbar (Details-Bereich).
- Dieselben Helfer in `DriveConnectionActivity` und `PixelTunnelActivity` verwenden (nur Optik).
- Lange Erklärabsätze („Deine Einsätze erscheinen im eigenen Kalender …") in Hilfetextgröße (`.82rem` ≙ 13 sp) und `MUTED` setzen.

### G3 – Heute: keine überlappenden Leisten

- `#field-today-actions` (Dock mit „Planung" und „Arbeit erfassen") nicht mehr `position:fixed` über der Tab-Leiste. Stattdessen im Seitenfluss direkt unter dem Button „Arbeit starten" (`position:static`, Abstand 12 px). **IDs und DOM-Eltern des Originalformulars bleiben** (Dialog `#field-quick-entry`).
- Der Button „Arbeit starten" bekommt nur eine Textzeile, die nicht dasselbe wiederholt (Untertitel „Zeit läuft ab jetzt" statt „Arbeitszeit starten"; Text nur in `workday-ui.js` ändern, wo er erzeugt wird; Tests auf den geänderten Text anpassen).
- Unterer Platzhalter (`padding-bottom` der Seite) = Höhe der Tab-Leiste, damit nichts verdeckt wird (`--app-nav-height` aus `workday-ui.css` nutzen).
- `.design-metrics`: dicke weiße Lücke durch 1-px-Trennlinie (`border-left:1px solid var(--hg-line)`) ersetzen.
- „Planung" in Mehr: blaue Hintergrundfläche entfernen, **blauer Text/Icon bleibt** (Farbcode Blau = Planung bleibt erhalten, nicht mehr „ausgewählt"-Optik).
- Hinweisbalken „Arbeit erschwert" (gelb): zeigt, **falls die Ursache schon in den Daten steht** (Wetterwarnungstext), diese in einer zweiten Zeile; sonst unverändert. Keine neuen Datenabfragen.

### G4 – Einheitliche Kopfzeilen und Navigation

- Regel: **Unterseiten** = „‹ Titel" in `.page-head`. **Hauptreiter** (Heute, Kunden & Gärten, Mehr) = nur Titel, kein Zurück-Pfeil.
- Mehr & Einstellungen: Zurück-Pfeil ausblenden (CSS/Klasse), Hamburger rechts bleibt (öffnet das Seitenmenü).
- Status und andere Unterseiten: die zusätzliche untere „Zurück"-Leiste (`.daily-toolbar-back`) visuell entfernen, sofern der Pfeil oben vorhanden ist. **`handleAndroidBack` und Systemgeste bleiben unverändert.** Reihenfolge des Rückwegs (ein Schritt zurück, Entwurf bleibt) unverändert.
- Leerraum unter der Statusleiste verringern, soweit `env(safe-area-inset-top)` es zulässt (kein fester 180-px-Block).
- Feste untere Aktionsleisten (`.actions`, `.worklog-window-actions`) immer **oberhalb** der Tab-Leiste, nie darüber schwebend und nie Inhalte verdeckend (`bottom:var(--app-nav-height)`, Seiten-`padding-bottom` entsprechend).

### G5 – Statusdarstellung

- Status-Karten (Seite Status, Heute-Status, Kalender-Karte): **Punkt + Text-Chip** statt farbigem Seitenstreifen. Farben aus Tokens: grün „Verbunden/Aktuell", blau „Planung/Info", gelb „Aktion nötig", rot „Fehler". Immer Text dabei, nie nur Farbe.
- Abgeschnittenen `border-left` an gerundeten Karten ersetzen (Chip oder `box-shadow: inset 4px 0 0 <Farbe>` ohne Radiusbruch).
- Fehlerkarte **trennt Themen**: erst „Google Kalender: Freigabe nicht abgeschlossen" (gelb/rot), danach getrennte Statuszeilen „Wetterwarnungen: EIN" und „Pixel-Meldungen: bereit" (je eigene Zeile mit Punkt).
- Keine Änderung an den Zuständen selbst (nichts wird automatisch grün).

### G6 – Mehr-Menü (optional, zuletzt)

- „Überall suchen" als Suchfeld-Optik (Lupe, Platzhalter „Kunden, Arbeiten, Fotos, Dokumente"), öffnet **dasselbe** `#global-search-dialog` wie bisher. Keine neue Suchlogik.
- Pro Zeile ein Linien-Icon links (24 px, 2 px Strich, `currentColor`) als kleines Inline-SVG in `navigation-status.js` bzw. dem Menüaufbau; Icon-only-Elemente mit `aria-label`.
- Nur Darstellung. Reihenfolge und Ziele der Einträge bleiben.

---

## 3. Verknüpfung mit den vorhandenen Tests

Vor Beginn **Ausgangslauf** der unten genannten Suiten auf dem unveränderten Stand ausführen und Ergebnis notieren (Grünwert vorher). Nur danach ändern.

### 3.1 Bestehende Suiten, die nach jedem Paket laufen müssen

| Test | Schützt | Erwartete Anpassung |
|---|---|---|
| `tests/field-ui.test.cjs` | Heute-Dock, `#field-today-actions`, Erfassungsdialog `#field-quick-entry`, Zurück/Entwurf, 320/360/426 px, 150 % Schrift, 48-px-Ziele | **G3:** Annahmen zu `position:fixed` des Docks auf „im Fluss, überlappt Tab-Leiste nicht" ändern. Schutzassertions (Entwurf, Timerabschluss, Foto-Recovery) **nicht** entfernen. |
| `tests/daily-usability.test.cjs`, `tests/simple-design.test.cjs`, `tests/design-v139.test.cjs`, `tests/design-concept.test.cjs` | Navigation, Profile, Filter, Überlauf bei 360/412 | Nur wenn Texte geändert wurden (Untertitel „Arbeit starten"). |
| `tests/status-collisions-gestures.test.cjs` | Status, Kollisionen, Wischmenü, Zurück-Reihenfolge | **G4/G5:** Zurück-Leiste unten ist visuell weg; prüfen, dass `handleAndroidBack()` weiterhin genau einen Schritt zurückgeht. |
| `tests/navigation-calendar.test.cjs`, `tests/planning-ui.test.cjs`, `tests/p3-search-planning.test.cjs` | Planung, Kalenderansicht, Suche | Unverändert PASS erwartet. Suche muss weiterhin `#global-search-dialog` öffnen (G6). |
| `tests/calendar-authorization-ui.test.cjs` | Autorisierungsstatus in der UI (7 Prüfungen) | **G5:** Statuschips statt Streifen; Inhaltstexte und Details bleiben. |
| `tests/release-browser.test.js`, `tests/p3-uat-walkthrough.test.cjs`, `tests/p3-ux-comparison.test.cjs`, `tests/ui-preview.test.cjs` | Gesamtablauf, Preview-Isolation | Preview muss tokens.css mitnehmen, **keine** neuen Berechtigungen. |
| `tests/timer-completion.test.cjs`, `tests/work-location.test.cjs`, `tests/worklog-window.test.cjs`, `tests/personal-payment.test.cjs`, `tests/customer-photos.test.cjs`, `tests/work-entry-photo-failure.test.cjs` | Fachlich-visuelle Abläufe | Unverändert PASS erwartet. Bei Fehlern: Layout korrigieren, nicht den Test. |
| `tests/webview-syntax.test.cjs`, `tests/runtime-compat.test.cjs` | JS-Syntax/Kompatibilität | PASS. |
| JVM: `CalendarSetupTests`, `CalendarAuthorizationTests`, `PlanningTests`, `UiMcpParityTests`, `RefactoringCharacterizationTests` (33 JVM-Suiten insgesamt) | Kalender-Setup, Scopes, Fehlerklassifizierung, **117 Tools / 156 UI-Aktionen / Hash** | Müssen **unverändert** bestehen. Wenn eine Suite wegen Textänderung in nativer Activity bricht, nur die erwartete Textstelle anpassen, nie Fehlerklassifizierung. |
| Python: `tests/run_quality_suite.py` (326 Tests, 37 historische QA-Schlüsseltests bleiben ausgeschlossen), `tests/test_release_quality.py`, `tests/test_source_privacy_rules.py` | Release-Qualität, Privacy | PASS. |
| Tools: `tools/verify_ui_mcp_inventory.py`, `tools/check_refactoring_contract.py`, `tools/sync_plant_assets.py`, `tools/verify_ui_preview_apk.py`, `tests/apk_privacy_guard.py` | Inventar, Vertrag, Asset-Gates | Asset-Anzahl (tokens.css) und UI-Quellreview-Fingerprint **korrekt nachführen**. Danach `:app:compileReleaseJavaWithJavac` und Lint (0 Fehler). |

### 3.2 Neue Tests (zu ergänzen)

1. **`tests/visual-consistency.test.cjs`** (Chromium, Muster `tests/access-integrity.test.cjs`, synthetische `AndroidData`-Brücke):
   - `--hg-green` ist in **genau einer** Datei definiert (`tokens.css`); `mobile.html` und die übrigen CSS-Dateien definieren `--green` nur als Alias `var(--hg-…)`.
   - Font-Stack enthält kein `Inter`.
   - Auf Heute, Mehr, Status, Kunden und Arbeiten bei **320/360/426 px und 150 % Schrift**: `#field-today-actions`/untere Aktionsleisten überlappen die Tab-Leiste nicht (Bounding-Box-Vergleich) und verdecken keinen Button.
   - Alle sichtbaren Buttons ≥ 48 px Höhe/Breite-Ziel.
   - Kein horizontales Überlaufen (`scrollWidth ≤ innerWidth + 1`).
   - Hauptreiter haben keinen Zurück-Pfeil, Unterseiten haben genau einen.
   - Screenshots `outputs/grafik-heute-*.png`, `grafik-mehr-*.png`, `grafik-status-*.png` speichern, **jede Seite im geöffneten Zustand ansehen** und den Befund im Bericht nennen.
   - Kontrast: für Primärbutton, Sekundärbutton, Warnbox, Fehlertext, Chip-Texte mindestens 4,5:1 (rechnerisch aus Tokens).
2. **`tests/test_native_ui_consistency.py`** (statisch, JVM-frei):
   - In `PlanningConnectionActivity.java`, `DriveConnectionActivity.java`, `PixelTunnelActivity.java` gibt es **keine** nackten `new Button(`/`new CheckBox(` außerhalb der Hilfsmethoden, die `NativeUi.style(...)` aufrufen.
   - Hex-Werte in `NativeUi.java` (BG, SURFACE, TEXT, MUTED, LINE, GREEN, GREEN_BTN, SAGE, WARN_*, DANGER) stimmen mit `tokens.css` überein (ARGB↔RGB-Vergleich).
   - Bestehende Texte, die JVM-/Browser-Tests erwarten (z. B. „Google-Freigabe erneuern", „Kalender-App öffnen", „Konto und Freigaben", „Wetterwarnungen", „Pixel-Benachrichtigungen"), kommen unverändert im Quelltext vor.
3. Beide neuen Suiten in `tests/run_quality_suite.py` bzw. den gemeinsamen Browser-Runner und in `.github/workflows/qa-apk.yml` (manueller Workflow) eintragen.

### 3.3 Abnahmekriterien (alles messbar)

- Jede der fünf Beispielseiten (Heute, Mehr, Status, Kalender oben/unten) zeigt nur **eine** Primäraktion pro Bildschirmbereich, keine überlappenden festen Leisten, **eine** Fehlermeldung statt drei.
- Native Screens und WebView-Seiten haben dieselben Radien, Grüntöne und Schriftgrößen.
- Alle in 3.1 genannten Suiten PASS (oder dokumentiert: Test auf bewusst geänderten Text/Layout angepasst, Schutzassertions erhalten).
- 117 Tools, 156 UI-Aktionen, Vertragshash `899906c2…` unverändert.
- Manifest-Berechtigungen unverändert.

---

## 4. Verbindung zum Sicherheitsauftrag (`CODEX-AUFTRAG-SICHERHEIT-1.54.md`)

- Reihenfolge: **Sicherheit A → C zuerst, danach dieser Auftrag.** Beide ändern den `<head>` von `mobile.html`. Falls noch nicht gemerged: `<link rel="stylesheet" href="tokens.css">` **nach** der CSP-Meta-Zeile einfügen und nach dem Merge Konflikte manuell lösen.
- `tests/webview-hardening.test.cjs` (CSP vorhanden, keine Netz-APIs, Seite startet ohne CSP-Verstoß) muss weiter PASS sein. `tokens.css` lädt per `file:`-Quelle und ist von `default-src file: …` abgedeckt, es darf keine externe Schrift/URL enthalten.
- Netzwerk-Sicherheitskonfiguration (Paket A) bleibt unberührt.
- Die Preview-Variante (`at.hausgarten.service.preview`, ohne `INTERNET`) bleibt isoliert; `tools/verify_ui_preview_apk.py` bestätigt dies nach dem Hinzufügen von `tokens.css`.

---

## 5. Pflicht-Abschlussbericht in `CODEX-STATUS.md`

Abschnitt „Grafische Vereinheitlichung G1–G6 (Basis 1.54.0/89)" mit:
1. Geänderte/neue Dateien je Paket.
2. Tabelle der **tatsächlich ausgeführten** Tests (Anzahl, PASS/FAIL), getrennt von **nicht ausgeführten** (Pixel-WebView, Kamera, native Activity auf Gerät, Google-Freigabe → NOT EXECUTED).
3. Welche bestehenden Test-Erwartungen **wegen bewusster Layout-/Textänderung** angepasst wurden (mit Begründung); bestätigen, dass keine Schutzassertion entfernt wurde.
4. Ausgangslauf vs. Endlauf, Asset-Anzahl (417 → 418), neuer UI-Review-Fingerprint.
5. Vorher/Nachher-Screenshots (Pfade) und kurze Selbstkritik: was bleibt uneinheitlich.
6. Bestätigung: keine APK, keine Version, keine Berechtigungs-/Datenänderung, kein Push auf `main`.

## Verboten

- Keine Funktions-, Daten-, ID-, Schema- oder MCP-Änderungen.
- Keine Abschwächung von Privacy-, Signier-, Fingerprint- oder Vertragsgates; keine entfernten Schutzassertions.
- Kein Dunkelmodus, keine neuen Schriften/Bilder/Abhängigkeiten in diesem Auftrag.
- Keine nativen Berechtigungen, keine Netz-APIs in der UI.


---

# TEIL 3 – KALENDER-ÜBERSICHT

# CODEX-AUFTRAG: Kalender-Übersicht und einfache Bedienung (Basis 1.54.2 / Build 91)

Für Codex Cloud. **Prämisse: Übersicht und einfache Bedienung.** Reine Darstellungs- und Bedienungsänderung an der Kalenderseite („Planung"), an der nativen Seite „Kalender & Wetter" und an wenigen Stellen der Einstellungen. **Keine Änderung an Kalender-Logik, Google-Autorisierung, Datenmodell, Sync oder MCP.**

Dieser Auftrag baut auf drei vorhandenen Aufträgen auf:
- `CODEX-AUFTRAG-SICHERHEIT-1.54.md` (Paket A: Klartext-HTTP, Paket C: WebView/CSP)
- `CODEX-AUFTRAG-GRAFIK-1.54.md` (G1 Tokens, G3 Heute-Dock, G4 Kopfzeilen, G5 Status-Chips; **G2 wird hier durch K8 konkretisiert**)
- die Kalender-Wiederherstellung aus `CODEX-STATUS.md` (21.10.2026, Branch `codex/calendar-permanent-20261021`)

---

## 0. Rahmen (zuerst lesen)

1. **Basis:** Branch `codex/calendar-permanent-20261021` (enthält die Kalender-Wiederherstellung, Quellfingerprint `747762839020b4ffb0491ac360c3cc054c455245eb4126b0c1e6d4e8882b0c3c`). **Neuer Arbeitsbranch** `codex/calendar-ui-overview-20261021` davon abzweigen. Kein Push auf `main`, kein zweites Repository.
2. `AGENTS.md`, `CODEX-STATUS.md`, `AKTUELLER-STAND.json`, `source-lifecycle.json` vor Beginn lesen und deren Regeln einhalten. Statusdateien nur so nachführen, wie dort beschrieben.
3. **Nicht ändern:** Paket `at.hausgarten.service`, IDs/Relationen/Speichermodelle, Drive-Formate, Kalender-Scopes, Konto-/Kalenderbindung, ETags/CAS, Dublettenschutz, Wiederanlauf-/Retry-Logik, die **117 MCP-Tools / 156 UI-Aktionen**, den Vertragshash `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`, Manifest-Berechtigungen.
4. **DOM-IDs, `data-*`-Attribute und Klassen, die Tests/JS nutzen, bleiben** (u. a. `#planning-body`, `#planning-day`, `#planning-week`, `#planning-prev`, `#planning-next`, `#planning-new`, `#planning-suggest`, `#planning-more`, `#planning-all`, `#planning-settings`, `#planning-refresh`, `[data-appointment-open]`, `[data-open="weeks"]`, `[data-calendar-choice]`, `.planning-row`, `.planning-weather[data-severity]`, `.planning-collision-badge[data-uncertain]`, `#appointment-form`, `.planning-save-actions`).
5. **Keine APK, keine Signierung, keine Versionserhöhung, keine Installation.** Pixel-Abnahme und echte Google-Freigabe bleiben **NOT EXECUTED** und werden so im Bericht geführt. Aus fehlender Abnahme keinen Erfolg ableiten.
6. Nur synthetische Daten. Keine Konten, keine Tokens, keine echten Termine. Kein Google-Konto/keine E-Mail-Adresse in Tests, Logs, Screenshots oder Berichten.
7. Reihenfolge: **Baseline-Lauf → Sicherheit A, C → Grafik G1 → K1–K9 → Grafik G3–G6 (soweit nicht durch K ersetzt) → Abschlusslauf.** Jedes Paket ein eigener Commit.

---

## 1. Ausgangsbefund (Build 91, aus Quellcode und Screenshot)

| # | Befund | Fundstelle |
|---|---|---|
| 1 | Kalender ist nur eine Kartenliste (Tag/Woche). Kein „Heute", keine Wochenleiste, keine Tagesköpfe. Datum + Wochentag steht in **jeder** Karte. | `planning-ui.js` `list()`, `rowHTML()`, Variable `view='week'` |
| 2 | Karte bis zu sechs Textzeilen (Zeit, Kunde, Titel, Status·Ort, Ortsvorhersage, Wetter, Kollision). | `rowHTML(entry)` |
| 3 | Segment „Tag \| Woche \| Ist-Stunden": Ist-Stunden ist eine andere Seite, keine Ansicht. | `.planning-tabs` (3 Spalten) |
| 4 | Feste Leiste (+ Termin / Zeitfenster finden) hängt über der Tab-Leiste und verdeckt Inhalt. | `planning-ui.css` `.planning-list-actions{position:fixed;bottom:var(--app-nav-height)…}` |
| 5 | Verbindungsproblem nur als nicht tippbarer Satz („… unter Einrichten prüfen"). | `list()` Variable `attention` |
| 6 | Terminformular: Kunde, Auftrag, Titel, Zweck, Ort, Status, Wetterprofil, Anfahrt, Pause, Wiederholung, „Ende offen" in einem langen Formular. | `planning-ui.js` Zeilen ca. 112–113 |
| 7 | Kalenderabgleich-Dialog nennt technisch: „App-Termin verwenden / Google-Zeit/Ort übernehmen / App-Termin ohne Kalender behalten". | `planning-ui.js` `data-calendar-choice` |
| 8 | Native Seite „Kalender & Wetter": graue Standard-Buttons/Checkboxen, Fehlertext mehrfach, Rohcode „Prüfschritt … Hinweiscode …" sichtbar, „Kalenderverbindung trennen" optisch gleich wie harmlose Buttons, Fußbutton „Google-Freigabe erneuern" auch ohne frühere Verbindung. | `PlanningConnectionActivity.java` Zeilen ca. 24–25, 40–44, 61, 76–77 |
| 9 | Einstellungen/Sicherung: **zwei** „Erweitert / Diagnose" untereinander, neutraler Sync-Chip gelb und in „App-Information", „Wiederherstellen" optisch gleich wie „Sicherung prüfen", doppelte Zurück-Leiste. | `daily-usability.js` Zeile ca. 28, `business-actions-ui.js` Zeile ca. 44, `business-feedback.js` Zeile ca. 17 |

---

## 2. Arbeitspakete

### K1 – Orientierung: Heute, Wochenleiste, Tagesköpfe

In `list()` (`planning-ui.js`):
- Zeile `‹ Datumsbereich ›` bleibt, dazu ein Button **„Heute"** (setzt `anchor=new Date()`, `offset=0`, ruft `list()`). IDs: `planning-prev`, `planning-next` bleiben; neu `planning-today`.
- Darunter eine **Wochenleiste** mit 7 Buttons `Mo … So` (`data-planning-day="YYYY-MM-DD"`, `aria-label="Mittwoch 21. Oktober, 2 Termine"`), heutiger Tag hervorgehoben, ausgewählter Tag markiert, Punkt unter Tagen mit Terminen. Tippen = `view='day'`, `anchor=<Tag>`, `list()`.
- Wochenansicht: Termine **nach Tag gruppiert** mit Tageskopf `<h3 class="planning-day-head">Mi 21.10. · 2 Termine</h3>` (Anzahl; Summe der geplanten Stunden nur, wenn alle Termine ein Ende haben, sonst weglassen). Tagesansicht: genau ein Tageskopf.
- Die Karten zeigen **kein Datum mehr** (nur Uhrzeit). Leere Tage in der Wochenansicht **nicht** einzeln auflisten; Leere Woche: bestehender Text „Noch keine Termine in diesem Zeitraum." bleibt.
- **Datenmenge:** `list_appointments` wird weiter mit dem vorhandenen Schema aufgerufen (kein neues Argument, kein Tool-Umbau). Für Punkte/Zähler die bereits geladenen `items` verwenden. Reicht `limit:30` für eine Woche nicht (`offset<total`), die Seiten bis `offset>=total` nachladen (maximal 4 Aufrufe), oder, wenn das Schema es zulässt, größeres `limit` – **im Bericht begründen**, welche Variante gewählt wurde. „Weitere30 anzeigen" bleibt für Tagesansicht erhalten.

### K2 – Karte ruhiger, Inhalte nach Wichtigkeit

`rowHTML(entry)` neu gliedern, Container bleibt `<button class="card planning-row" data-appointment-open="ID">`:

```html
<span class="planning-time">14:00–20:40</span>            <!-- Zeile 1, fett -->
<strong>Kundenname</strong>                                <!-- Zeile 1/2 -->
<span class="planning-what">Gartenpflege · Siedlerweg 33</span>  <!-- Zeile 2, gedämpft -->
<span class="planning-chips">                             <!-- Chips, umbrechend -->
  <span class="planning-chip" data-kind="status">Bestätigt</span>      <!-- nur wenn nicht Standard -->
  <span class="planning-weather" data-severity="difficult">☂ Arbeit erschwert</span>
  <span class="planning-collision-badge" data-uncertain="false">⚠ Terminkollision</span>
  <span class="planning-chip" data-kind="calendar">Kalender prüfen</span>
</span>
```

Regeln:
- **Maximal 2 Textzeilen + 1 Chipzeile**; Ort/Titel mit Auslassung (`text-overflow`, 1 Zeile), kein 6-Zeilen-Block.
- Texte, auf die Tests prüfen, bleiben wörtlich erhalten: „Ende offen", „Terminkollision", „Mögliche Terminkollision", „Kalenderänderung prüfen" (als Chip-Text), „Einsatzort offen". Ortsvorhersage („Ortsvorhersage · Einsatzgebiet") in den Wetter-Chip statt eigener Zeile.
- Statuschip nur bei Abweichung vom Normalzustand; Farben ausschließlich aus Tokens (Grafikauftrag G1), immer Icon/Text, nie nur Farbe.
- Mindesthöhe 56 px, ganze Karte tippbar, kein horizontales Scrollen bei 320 px und 150 % Schrift.

### K3 – Steuerzeile vereinfachen

- Segment nur **„Tag | Woche"** (zwei Spalten; `#planning-day`, `#planning-week`, `aria-pressed` bleiben).
- **„Ist-Stunden ›"** wird ein einzelner Textlink in der Kopfzeile bzw. direkt unter dem Segment (`data-open="weeks"` bleibt, damit Navigation/Tests unverändert greifen).
- „Alle Kunden" (`#planning-all`) bleibt als Chip „Nur: <Kunde> ✕" sichtbar, wenn ein Kundenfilter aktiv ist.

### K4 – Aktionsleiste ohne Überlappung

- `.planning-list-actions` nicht mehr `position:fixed` über der Tab-Leiste. **„+ Termin"** (`#planning-new`) wird eine runde Schaltfläche (FAB, 56 px) rechts unten **oberhalb** der Tab-Leiste: `position:fixed; right:16px; bottom:calc(var(--app-nav-height,80px) + 16px)`, mit Beschriftung `aria-label="Neuer Termin"`. Seite erhält `padding-bottom` ≥ FAB-Höhe + 24 px, damit nichts verdeckt wird.
- **„Zeitfenster finden"** (`#planning-suggest`) wandert als kleiner Textbutton **„Freie Zeit finden"** unter die Wochenleiste (nur Listenansicht). Funktion unverändert.
- `.planning-save-actions` (Speichern-Leiste im Formular) bleibt am unteren Rand, überlappt die Tab-Leiste nicht (in der Formularansicht ist die Tab-Leiste bereits ausgeblendet – prüfen, nicht annehmen).

### K5 – Verbindungsstatus als tippbarer Chip

In `list()` die Variable `attention` (bisher `<p role="alert">`) ersetzen durch **einen** Chip, der den gleichen Handler wie `#planning-settings` auslöst (öffnet „Kalender & Wetter"):
- Priorität: `authorization_required` → „Kalender: Freigabe nötig ›"; `retry_limit_reached` → „Kalender: Verbindung prüfen ›"; `review_required` → „Kalender: Änderung prüfen ›"; Wetterstatus analog „Wetter: …".
- Bei blockierenden Zuständen `role="alert"`, sonst `role="status"`. Es erscheint **nur ein** Chip (höchste Priorität).
- `<details class="planning-diagnostics">` („Erweitert / Diagnose") bleibt, standardmäßig **geschlossen**; Inhalt unverändert.

### K6 – Terminformular: Kurzform + „Mehr Optionen"

In `drawEditor()` (Zeilen ca. 112–113):
- **Immer sichtbar (oben):** Kunde, Datum, Startzeit, **Dauer-Chips** „1 Std | 2 Std | Halber Tag | Ende offen" (setzen die vorhandenen Felder `planning-duration` / `end_open`; Eingabe „1,5" bleibt als freies Feld), Titel.
- **Unter `<details class="planning-more-options">` (Standard geschlossen):** Auftrag (optional), Zweck, Status, Wetterprofil, Ort/Anfahrt/Pause/Standortübernahme, Wiederholung, Notizen.
- Das Panel öffnet **automatisch**, wenn (a) ein bestehender Termin Werte in diesen Feldern hat, (b) die Validierung einen Fehler in einem dieser Felder meldet, (c) der Entwurf (`localStorage`) solche Werte enthält.
- **Feldnamen, IDs, `capture()`, `values`, `create_appointment`, `create_appointment_series`, Wochenwiederholung und Entwurfs-/Archivlogik bleiben unverändert.** Nur die Anordnung ändert sich.
- Aus der Kundenakte („Einsatz planen") bleibt der Kunde vorbelegt (`customerFilter`), der Cursor springt auf das erste leere Pflichtfeld.

### K7 – Kalenderabgleich verständlich

Der Dialog „Kalenderänderung vergleichen" (`[data-calendar-choice]`, Zeilen ca. 126–134):
- **Zwei Spalten** „Deine App | Google Kalender" (bei ≤ 360 px untereinander), unterschiedliche Werte (Zeit, Ort, Titel) farbig/fett markiert, Zeitangaben in `stamp()`-Format.
- Buttons umbenennen, **Werte der `data-calendar-choice`-Attribute und die aufgerufenen Funktionen bleiben**:
  - „App-Termin verwenden" → **„Meine App-Version behalten"**
  - „Google-Zeit/Ort übernehmen" → **„Google-Version übernehmen"**
  - „App-Termin ohne Kalender behalten" → **„Nur in der App behalten"** (mit einer Zeile Erklärung „Der Termin wird nicht mehr mit Google abgeglichen.")
- Keine neue Auswahl, kein Standard, der Daten überschreibt. Primärstil nur für **„Meine App-Version behalten"** (reversibel); „Google-Version übernehmen" Sekundär mit Bestätigung, falls das bisher schon so ist (nicht abschwächen).

### K8 – Native Seite „Kalender & Wetter" (`PlanningConnectionActivity.java`, ersetzt Grafikpaket G2 für diese Seite)

Voraussetzung: `NativeUi.java` aus dem Grafikauftrag (Buttons, Schalter, Karten, Farben aus `tokens.css`). Wenn dort noch nicht umgesetzt: zuerst anlegen.
- **Kopfbereich:** ein Status-Chip (Nicht verbunden / Verbunden / Prüfung nötig) plus **eine** Handlungszeile. Die Fehlermeldung (z. B. „Die Google-Freigabe wurde abgebrochen …") erscheint **einmal** in einer Hinweisbox, nicht in Kopfzeile + Karte + Status.
- **Dreischritt-Anzeige** „1 Konto · 2 Freigabe · 3 Kalender" als reine Darstellung aus den **vorhandenen** Zuständen (Konto gewählt? Freigabe erteilt? Kalender angelegt?). Keine neue Zustandslogik, keine neuen Preferences.
- **Fußleiste:** „Zurück" (Sekundär) und Hauptbutton. Text: „**Verbinden**", solange noch nie verbunden wurde (kein `calendar_setup_code`, kein Konto), danach „**Erneut verbinden**" bzw. „Freigabe erneuern" nur bei vorhandener Freigabe. Methode `calendarPrimary()` und alle Intents **unverändert**.
- **„Kalenderverbindung trennen"** im Gefahrstil (rot umrandet); der bestehende Bestätigungsdialog bleibt wörtlich erhalten.
- **Rohcode ausblenden:** „Prüfschritt …" und „Hinweiscode: CALENDAR_…" (ausschließlich aus `CalendarAccessRules.safeCode`) in einen eingeklappten Bereich **„Technische Details"** (bestehender `disclosure(...)`-Mechanismus). Sie dürfen nicht entfallen (Support/Diagnose).
- Checkboxen → Framework-`Switch` ganze Zeile, Texte unverändert; „Auch Belegtzeiten meines Hauptkalenders prüfen" mit einer Erklärzeile statt zweizeiligem Titel.
- **Sichtbare Konto-Adresse** weiterhin nur dort, wo sie jetzt schon steht („Konto und Freigaben"); nicht in Chip/Kopfzeile aufnehmen, nicht in Logs/Tests.
- Auf `DriveConnectionActivity` und `PixelTunnelActivity` die gleichen `NativeUi`-Stile anwenden.
- **Nicht anfassen:** `CalendarAuthorization*`, `CalendarAccessRules`, `PlanningAccount`, Scopes, Wiederanlauf, Retry-Zähler.

### K9 – Einstellungen/Sicherung aufräumen (Screenshot Mehr → Sicherung)

- **Ein** Aufklapper „Erweitert / Diagnose": den in `business-actions-ui.js` (Sicherungskarte) und den in `daily-usability.js` (Zeile ca. 28, „· Fehlermeldungen") zusammenführen; `pre.settings-error-details` und die vorhandenen Inhalte bleiben, die Karte verweist nicht doppelt.
- **Neutraler Sync-Chip:** `tone:'busy'` (`business-feedback.js`) in Salbei/neutral statt Gelb, mit kleinem Fortschrittsindikator, **in der Sync-Karte** statt in „App-Information". Gelb bleibt Aktion/Prüfung nötig.
- **„App-Information"** als kleine Fußzeile (Version/Build) statt eigener Karte.
- **Sicherungsbereich:** „Jetzt sichern" Primär, „Sicherung prüfen" Sekundär, **„Wiederherstellen" im Gefahrstil**; vorhandene Bestätigungen **nicht** abschwächen. Der Absatz darf kürzer werden, **der Satz „Die Synchronisierung allein ersetzt keine Sicherung." bleibt sichtbar** (nicht hinter einen Aufklapper). Restlicher Erklärtext optional unter „Mehr erfahren".
- Doppelte untere „Zurück"-Leiste auf Unterseiten entfernen (Grafikpaket G4); `handleAndroidBack` bleibt.

---

## 3. Verknüpfung mit den vorhandenen Tests

**Ausgangslauf vor der ersten Änderung** (Ergebnis und Zahlen notieren): Python-Suite (383 Tests/0 Skips, 37 historische QA-Schlüsseltests ausgeschlossen), 40 JVM-Suiten, Browser-Suiten, Release-/Preview-Gates. Wenn der Ausgangslauf nicht grün ist: **zuerst melden, nicht anpassen.**

### 3.1 Bestehende Tests, die geschützt werden

| Suite | Schützt | Erwartete Anpassung |
|---|---|---|
| `tests/planning-ui.test.cjs` | Liste, Tag/Woche, Formular, Speichern, Serien, Entwurf, Kalenderabgleich | **K1–K7:** nur Selektoren/Texte, die bewusst wandern (Datum aus Karte, Ist-Stunden-Position, Button-Beschriftungen). Schutzassertions (gespeicherte Werte, Serie, Entwurf, Abgleich) **bleiben**. |
| `tests/planning-open-end.test.cjs` | „Ende offen" | **K6:** Dauer-Chip „Ende offen" setzt dasselbe Feld wie bisher; Assertions unverändert. |
| `tests/navigation-calendar.test.cjs` | Navigation Planung/Kalender, Zurück | **K3/K4:** Ist-Stunden-Link, FAB statt Leiste. |
| `tests/p3-search-planning.test.cjs` | Suche → Planung | unverändert PASS. |
| `tests/calendar-authorization-ui.test.cjs` | Status/Autorisierung in der UI | **K5:** Chip statt Absatz; Texte „Freigabe nötig" etc. prüfen. |
| `tests/status-collisions-gestures.test.cjs` | Kollisionsbadge, Wischgesten, Zurück-Reihenfolge | **K2/K9:** Badge-Texte und `data-uncertain` bleiben; Zurück-Verhalten (`handleAndroidBack`) unverändert. |
| `tests/field-ui.test.cjs`, `tests/daily-usability.test.cjs`, `tests/design-concept.test.cjs`, `tests/design-v139.test.cjs`, `tests/simple-design.test.cjs` | Heute-Dock, Einstellungen, Navigation, Überlauf 320/360/426 px | **K4/K9:** Dock/FAB-Platzierung; nur eine „Erweitert / Diagnose". |
| `tests/p3-business-actions.test.cjs`, `tests/p3-customer-hub.test.cjs`, `tests/p3-ux-comparison.test.cjs`, `tests/p3-uat-walkthrough.test.cjs`, `tests/release-browser.test.js`, `tests/ui-preview.test.cjs` | Sicherung/Wiederherstellung, Kundenakte → „Einsatz planen", Gesamtablauf, Preview | Sicherung: „Wiederherstellen" bleibt bestätigungspflichtig. Preview muss `tokens.css` mitnehmen (Grafikauftrag). |
| `tests/webview-syntax.test.cjs`, `tests/webview-hardening.test.cjs` (aus Sicherheitsauftrag), `tests/visual-consistency.test.cjs` (aus Grafikauftrag) | Syntax, CSP (keine Netz-APIs, keine externen Ressourcen), Tokens | Weiter PASS; keine externen Schriften/URLs. |
| JVM: `CalendarRecoveryTests`, `CalendarSetupTests`, `CalendarAuthorizationTests`, `CalendarAccessTests`, `PlanningTests`, `AppointmentAvailabilityTests`, `OpenEndPlanningTests`, `WeatherWorkflowTests`, `FreeWeatherSourceTests`, `UiMcpParityTests` | Wiederanlauf/Abbruch/Zeit, Setup, Scopes, Verfügbarkeit, 117 Tools/156 Aktionen | **Müssen unverändert bestehen.** Nur Textassertions auf **bewusst geänderte** native Beschriftungen („Verbinden") dürfen mit Begründung angepasst werden, nie Fehlerklassifizierung oder Zustandslogik. |
| Python: `tests/run_quality_suite.py`, `tests/test_release_quality.py`, `tests/test_source_privacy_rules.py`, `tests/test_native_ui_consistency.py` (Grafikauftrag) | Release-/Privacy-Gates, native Farben = Tokens | PASS; Gates nicht abschwächen. |
| Tools: `tools/verify_ui_mcp_inventory.py`, `tools/check_refactoring_contract.py`, `tools/verify_ui_preview_apk.py`, `tests/apk_privacy_guard.py` | Inventar, Vertrag, Preview, Privacy | UI-Quellreview-Fingerprint und Asset-Anzahl (tokens.css, ggf. neue CSS) korrekt nachführen. Danach Android-Release-/Instrumentierungs-Java kompilieren und Lint ohne neue Fehler. |
| Native Instrumentierung (158 Prüfungen, `native_ui`) | echte Geräteabläufe | **Nur kompilieren** (kein Gerät): als **NOT EXECUTED** führen. |

### 3.2 Neue Tests

1. **`tests/planning-overview.test.cjs`** (Chromium, synthetische `AndroidData`-Brücke, Muster `planning-ui.test.cjs`; je 320/360/426 px, 150 % Schrift):
   - Wochenleiste hat 7 Buttons; Tage mit Terminen tragen Punkt und `aria-label` mit Anzahl; Tippen wechselt in die Tagesansicht des Tages; „Heute" setzt zurück und markiert den heutigen Tag.
   - Wochenansicht gruppiert: Tagesköpfe in Reihenfolge, Anzahl stimmt; **kein Datum in der Karte**.
   - Karte: höchstens 2 Textzeilen (`getClientRects`/Zeilenhöhe), kein Überlauf (`scrollWidth ≤ clientWidth+1`), Chips umbrechen; Wetter-/Kollisions-/Kalenderchips erscheinen nur bei passenden Fixtures.
   - Segment hat genau zwei Buttons (`#planning-day`, `#planning-week`); `[data-open="weeks"]` ist weiterhin auffindbar.
   - **FAB überlappt die Tab-Leiste nicht** und verdeckt keinen Button/Karte (Bounding-Box-Vergleich, letzte Karte bis ans Ende scrollbar).
   - Verbindungschip: bei `authorization_required` genau ein Chip, Klick löst denselben Handler wie `#planning-settings` aus (Stub zählt Aufrufe).
   - Formular-Kurzform: nur Kunde/Datum/Zeit/Dauer-Chips/Titel sichtbar, `.planning-more-options` geschlossen; öffnet bei bestehendem Termin mit Auftrag/Ort/Wetterprofil und bei Validierungsfehler im Panel.
   - **Regression Speichern:** Das an `create_appointment`/`create_appointment_series` übergebene Argumentobjekt ist bei gleicher Eingabe **identisch** mit dem Stand vor der Änderung (Golden-Vergleich aus dem Ausgangslauf).
   - Konfliktansicht: zwei Spalten, Unterschiede markiert, `[data-calendar-choice]`-Werte unverändert und rufen dieselben Funktionen auf.
   - Screenshots `outputs/kalender-*.png` (Liste Tag/Woche, leere Woche, Formular, Abgleich, Verbindungschip) speichern und im Bericht beschreiben.
2. **`tests/settings-cleanup.test.cjs`:** Einstellungsseite enthält genau **eine** Zusammenfassung „Erweitert / Diagnose"; `pre.settings-error-details` vorhanden; Sync-Chip im Zustand `busy` ohne Warnfarbe; Satz „Die Synchronisierung allein ersetzt keine Sicherung." sichtbar (nicht im geschlossenen `<details>`); „Wiederherstellen" mit Gefahrklasse und vorhandener Bestätigung.
3. **`tests/test_native_calendar_ui.py`** (statisch, JVM-/geräte-frei):
   - `PlanningConnectionActivity.java` enthält keine nackten `new Button(`/`new CheckBox(` außerhalb der gestylten Helfer.
   - Hauptbutton-Beschriftung „Verbinden" und „Erneut verbinden" kommen vor, `calendarPrimary()` ist unverändert vorhanden.
   - „Technische Details" enthält die Ausgabe von `CalendarAccessRules.safeCode(...)` und „Prüfschritt"; beides erscheint **nicht** in Kopfzeile/Fehlerbox.
   - Keine E-Mail-Adresse, kein Token, kein `Authorization`-Header in den neuen Strings.
4. Neue Suiten in `tests/run_quality_suite.py` bzw. im gemeinsamen Browser-Runner und im manuellen Workflow `.github/workflows/qa-apk.yml` eintragen.

### 3.3 Abnahmekriterien (messbar)

- Planungsliste: Tag/Woche mit Heute-Sprung, Wochenleiste, Tagesköpfen; **eine** Primäraktion (FAB) ohne Überdeckung; Karten ≤ 2 Textzeilen + Chips.
- Terminformular: Kurzform ohne Scrollen bei 360×800 px für Kunde, Datum, Zeit, Dauer, Titel und Speichern; Felder unverändert gespeichert.
- „Kalender & Wetter": Fehlermeldung einmal; Rohcode nur unter „Technische Details"; richtiger Hauptbutton-Text; Trennen im Gefahrstil mit Bestätigung.
- Einstellungen: ein Diagnose-Aufklapper, neutraler Sync-Chip, Wiederherstellen als Gefahraktion.
- Alle Suiten aus 3.1 PASS (oder dokumentiert: bewusst geändert, Schutzassertions erhalten). **117 Tools / 156 Aktionen / Vertragshash unverändert.**

---

## 4. Pflicht-Abschlussbericht (`CODEX-STATUS.md`, Abschnitt „Kalender-Übersicht K1–K9 (Basis 1.54.2/91)")

1. Geänderte/neue Dateien je Paket (Liste) und Branchname.
2. **Tatsächlich ausgeführte** Tests (Anzahl, PASS/FAIL), getrennt von **NOT EXECUTED**: Pixel, echte Google-Freigabe, native Instrumentierung auf Gerät, Update 90→91.
3. Welche bestehenden Test-Erwartungen wegen bewusster Layout-/Textänderung angepasst wurden (mit Begründung); Bestätigung, dass keine Schutzassertion und kein Gate entfernt oder abgeschwächt wurde.
4. Wahl bei K1 (Datenmenge/`limit`) mit Begründung; Ausgangslauf vs. Endlauf; Asset-Anzahl, UI-Review-Fingerprint, Quell-SHA-256.
5. Vorher/Nachher-Screenshots (Pfade), kurze Selbstkritik, was weiterhin uneinheitlich ist.
6. Bestätigung: keine APK, keine Version, keine Berechtigungs-/Daten-/Scope-/Sync-Änderung, keine Kontodaten in Artefakten, kein Push auf `main`.

## 5. Verboten

- Keine Änderung an Google-Autorisierung, Scopes, Konto-/Kalenderbindung, Retry-/Wiederanlauflogik, Datenmodell, Sync, MCP.
- Keine Abschwächung von Privacy-/Signier-/Fingerprint-/Vertrags-Gates, keine entfernten Schutzassertions.
- Kein Dunkelmodus, keine neuen Schriften, Bilder oder Abhängigkeiten, keine Netz-APIs in der UI.
- Keine Rohcodes, E-Mail-Adressen oder Tokens im sichtbaren Hauptbereich oder in Testartefakten.
- Kein `setAllowFileAccess(true)` als „Notlösung" (siehe Sicherheitsauftrag).

