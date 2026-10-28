# Etappen 1/2: Prüfung auf Build 113

Prüfdatum: 28. Oktober 2026. Ausschließlich geprüfte Repositoryquellen und
synthetische Daten; keine Produktivdaten, kein Gerät, keine Installation,
Migration oder Rechnungsfreigabe. Die Appquellen wurden bei dieser Prüfung
nicht geändert. Dies ist eine neue Ausgangsmessung, kein neuer Optimierungsnachweis.

## Gebundene Arbeitsbasis

- GitHub `main`, Commit `bd25fa9b9cc1ea183974c5d2434ef11f56b4d12a`.
- Aktuelle Quellen: `struktur-neu/android/app/src/main`, Version 1.55.8 / Build 113.
- Neu berechneter Quellfingerprint:
  `8c5bb6151d7517fb8b82ce60704c02749badf582d8390772e0729bdad5815f8e`.
  Er entspricht exakt dem dokumentierten Build113-Fingerprint.
- Originalquelltag: `source/v1.55.8-build113`, Quellcommit
  `2758d2e68dc00ff6b70078879b98a8fb3797c18c`.
- Cloud-Prüfkopie aus GitHub-Dateien; wiederverwendete lokale Quellbytes gegen die
  GitHub-Blob-SHAs geprüft. Vollständiger App-Quell-/Assetpfad vorhanden.
  Nicht benötigte alte Root-Bestände und redaktionelle Pflanzenunterlagen nicht
  materialisiert; Quellprivacy-PASS gilt für die geprüften vorhandenen Dateien,
  nicht für fremde oder nicht geladene Workspaces.

## Befund zu Etappe 1

`BusinessDataBridge.java` misst `getState`, `ownerCall`, `getWorkSession` mit
`System.nanoTime()` und UTF-8-Antwortgröße. Je Bezeichnung höchstens 128 Stichproben
im Prozessspeicher; keine Argumente, Antworten, IDs, Zeitstempel oder Übertragung.
`MainActivity.java` stellt sie über das vorhandene `getAppInfo()` bereit.
`startup-ui.js` erfasst `render`, Seitenwechsel und Start, ebenfalls begrenzt und
ohne Persistenz. Der aktuelle Starttest prüft die Puffergrenze und Datenfreiheit.

Die ursprüngliche Abnahmetabelle ist im
[Etappe1-Beleg](https://github.com/mihalhafner-collab/Gartenpirat-/blob/012b0433c5ffcf912a85ea95f898473bce10ae3a/docs/verification/performance-stage1-2026-10-28.md)
erhalten. Sie fehlte als Datei im neuen Dokumentationsordner, war jedoch auf dem
Importzweig vorhanden. Daher ist „nicht umgesetzt“ keine zutreffende Schlussfolgerung.

## Befund zu Etappe 2

Aktueller `mobile.html`-Code: `load()` übernimmt `state.sync_status`; nur bei einer
älteren/fehlerhaften Bridge ohne eingebetteten Status bleibt der bisherige Fallback.
`render()` leert den Cache nicht mehr pauschal. Zustands-/Pendingwechsel invalidieren
Daten; aktuelle Seite, Filter, Datum und Anzeigegrenzen bestimmen den Render-Schlüssel.
`today-ui.js`, `revier-solo-ui.js` und die betroffenen vorhandenen Module erhalten
gleiche Daten-/DOM-Referenzen bei unverändertem Zustand.

Frisch ausgeführter Kontext-/Cachetest auf diesen Appquellen bestätigt:

- Normaler expliziter `load()`-Pfad: ein `getState`-Aufruf, null zusätzlicher
  `getSyncStatus`-Aufruf; sämtliche geprüften Warn-/Hintergrundfelder unverändert.
- Unverändert Heute → Büro → Heute: null State-/Owner-Aufrufe, null
  `HGDataOrder`-Neusortierungen, gleiche Daten- und DOM-Referenzen.
- Anlage, Änderung, Löschung und native Änderungsmeldung werden sichtbar;
  auch später geöffnete Seiten werden aktualisiert.

Der Zähler misst die simulierte Browserbridge. Er beweist nicht, dass die gesamte
native App nur einen Snapshot erzeugt. Weitere Pfade, etwa `onDriveSync` und
`saveWorklogDetail`, enthalten nach wie vor getrennte Statusabfragen; das enger
formulierte `load()`-Abnahmekriterium ist dadurch nicht widerlegt. Native
Profilierung vor dem Datenbankumbau bleibt erforderlich.

Historische Vorher-/Nachhermessung und damalige Einschränkungen:
[Etappe2-Beleg](https://github.com/mihalhafner-collab/Gartenpirat-/blob/012b0433c5ffcf912a85ea95f898473bce10ae3a/docs/verification/performance-stage2-2026-10-28.md).
Diese historische Messung wird nicht als heutiger Testlauf ausgegeben.

## Neue Ausgangsmessung mit unveränderten Build113-Quellen

Selbst ausgeführt in Cloud Chromium 153.0.8010.0, Playwright 1.64.1, Acorn 8.19.0.
Browserpaket `@sparticuz/chromium` 153.0.0 ausschließlich außerhalb des Repositorys
als Testwerkzeug. Der direkte Playwright-Browserdownload war nicht verwendbar;
die App und ihre Abhängigkeiten wurden deswegen nicht verändert.

Der vorhandene `tests/startup-ui.test.cjs`-Leistungslauf wurde unverändert verwendet:
40 Wiederholungen, 360 × 840 CSS-Pixel, 300 synthetische Kunden, 5 000 Arbeiten,
3 000 Fotometadaten, 500 Rechnungen (zusätzlich 300 synthetische Aufträge).
Frische Browserseite pro Stichprobe, gemeinsamer warmer Assetcache, keine Warmups.
Seitenwechsel bis zum zweiten Animationsframe; Suche einschließlich bestehender
130-ms-Debouncewartezeit; Start bis `hg:startup-ready`. Speichern verwendet den
Kundenformular-Submit und prüft den simulierten zurückgelesenen Datensatz.
Langsamste 5 % = Mittelwert der zwei langsamsten von 40 Werten. Zeiten in ms.

| Bedienweg | Stichproben | Mittelwert | P95 | Langsamste 5 % |
| --- | ---: | ---: | ---: | ---: |
| App-Start | 40 | 866.3 | 1023.5 | 1120.3 |
| Heute → Büro | 40 | 103.7 | 123.2 | 124.0 |
| Büro → Heute | 40 | 118.7 | 157.4 | 179.7 |
| Kunden öffnen | 40 | 209.8 | 269.6 | 314.0 |
| Suche | 40 | 150.6 | 158.4 | 161.2 |
| Speichern | 40 | 476.6 | 552.2 | 566.0 |

Andere Browser-/Hostumgebung als die historische Etappe2-Messung. Die Werte dürfen
nicht als Geschwindigkeitsgewinn durch eine hier vorgenommene Appänderung gedeutet
werden. Vor der nächsten Codeänderung denselben Lauf in derselben Umgebung erneut
als unmittelbare Vorhermessung ausführen; danach identisch wiederholen.

Die folgenden Bridgewerte gehören ausschließlich zu JavaScript-Testdoubles:

| Bridge | Aufrufe | Mittel ms | Langsamste 5 % ms | Mittlere Antwortbytes |
| --- | ---: | ---: | ---: | ---: |
| getState | 120 | 4.5400 | 8.7000 | 1594868.0 |
| getWorkSession | 280 | 0.0129 | 0.1071 | 16.0 |
| ownerCall | 200 | 0.0150 | 0.1000 | 30.4 |

**Keine Android-, SQLite-, Emulator- oder Pixel-Laufzeitmessung.** Native
Messinstrumentierung ist im Code vorhanden; ihr Geräteleistungsnachweis fehlt.

## Selbst ausgeführte Prüfungen

- `python3 -B struktur-neu/tools/check_current.py`: PASS, 62 Pythonprüfungen
  (46 Signier-/Baselineverträge, 8 Backup-Buildprüfungen, 8 Kontext-Hookprüfungen),
  Vertrags-/Inventar-/Fixture-/Pflanzenspiegel-/Wortlautprüfungen PASS.
- `tests/startup-ui.test.cjs`: 13 Prüfgruppen PASS.
- Kontext-/Cachetest: 18 Prüfgruppen PASS. Unveränderter Testquelltext aus
  [tests/context-search-storage.test.cjs](https://github.com/mihalhafner-collab/Gartenpirat-/blob/012b0433c5ffcf912a85ea95f898473bce10ae3a/tests/context-search-storage.test.cjs)
  gegen die aktuellen Build113-Assets ausgeführt; die vorhandene Browser-Testgrenze
  aus `struktur-neu/tests/browser.cjs` benutzt. Keine neue Laufzeitdatei angelegt.
- Großbestandsmessung: 40 Stichproben für alle sechs Wege, alle Speicher-Readbacks
  erfolgreich, keine Browser-Seitenfehler.
- `git diff --check`: PASS für die beauftragten Dokumentänderungen.

Nicht neu ausgeführt: Android-Kompilierung/APK-Bau, JVM-Suiten, native Android-
Instrumentierung, Pixel-Leistung und physische Sicherung/Wiederherstellung.
Frühere Kompilierungs-/JVM-Belege sind kein neuer PASS dieser Prüfung.

Die 31 Browserprüfgruppen:

- PASS: Five cold bundled-page starts use the same synthetic fixture and record every visible intermediate frame
- PASS: Performance diagnostics retain only bounded numeric samples without persisting content
- PASS: Offline operation and missing backup configuration do not block presentation or write business rows
- PASS: A local read error remains visible in the finished UI without becoming a startup or Drive gate
- PASS: A retained work draft survives restart and conscious recovery from a stylesheet error with the same edit ID
- PASS: An initial modal remains operable and no partially initialized page is exposed while waiting for it
- PASS: A delayed module can finish after the slow-start notice without reload, fallback or business writes
- PASS: Missing simple-forms.js keeps the legacy view inaccessible and offers conscious recovery
- PASS: Missing field-ui.css keeps the legacy view inaccessible and offers conscious recovery
- PASS: Missing startup-ui.js keeps the legacy view inaccessible and offers conscious recovery
- PASS: Missing modern-ui.css keeps the legacy view inaccessible and offers conscious recovery
- PASS: Missing equipment-photo-ui.js keeps the legacy view inaccessible and offers conscious recovery
- PASS: A critical initializer exception fails visibly without showing the old controls
- PASS: One full state build per load preserves the complete embedded sync status
- PASS: Unchanged Today → Office → Today reuses sorted data, overview and existing DOM nodes
- PASS: Create, edit, delete and native notification invalidate cached visible and hidden pages
- PASS: Natural alphabetic ordering includes all customer selectors and new accounts
- PASS: Long hold opens exact customer context without a write
- PASS: Long hold opens exact order context without a write
- PASS: Long hold opens exact worklog context without a write
- PASS: Long hold opens exact invoice context without a write
- PASS: Long hold opens exact photo context without a write
- PASS: Long hold opens exact offer context without a write
- PASS: Long hold opens exact service_template context without a write
- PASS: Long hold opens exact appointment context without a write
- PASS: Scrolling and multiple fingers cancel long hold
- PASS: Visible actions and keyboard context use the same stable record ID
- PASS: Global search covers German aliases, multiple words, filenames, document numbers and catalog
- PASS: Status read failure cannot replace valid business data; failed data read retains last confirmed view
- PASS: Photo originals in the viewer are bounded and released when closing
- PASS: Context sheets remain reachable at narrow widths and enlarged text

## Folgeschritte und Änderungsumfang

Geändert werden ausschließlich `CODEX-AUFTRAG-GARTENPIRAT.md`, der vorhandene
Arbeitsstand und dieser Prüfbeleg. Der aktuelle Auftrag samt Tabellen-/Übernahme-/
Rückwegplan steht in der Auftragsdatei; der Status bleibt ausschließlich in
`AKTUELLER-STAND.json`. Die Appquellen, SQLite-Struktur und Schutzgates sind unverändert.

Etappe3 ist noch nicht implementiert. Die aktuelle Vorgabe verlangt verschlüsselte
Sicherung vor Übernahme, Build113 verwendet unverschlüsselte Pixel-ZIPs. Diese
konkrete Voraussetzung ist vor Festlegung des Aktivierungspfads zu klären.
Etappe4 folgt erst nach abgeschlossenem Etappe3-Nachweis. Für Etappe5 fehlt die
Nutzerbestätigung des tatsächlichen Pixel-Speichern-/Wiederherstellen-Tests.
Keine dieser Etappen wird hier als abgeschlossen oder produktiv freigegeben gemeldet.
