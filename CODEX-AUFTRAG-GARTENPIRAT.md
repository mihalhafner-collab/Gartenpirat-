# CODEX-AUFTRAG-GARTENPIRAT

Stand: 19. Oktober 2026  
Repository: `mihalhafner-collab/Gartenpirat-`  
Priorität: P0  
Arbeitsumgebung: **ausschließlich Codex Cloud**

> Diese Datei ist der zentrale ausführbare Arbeitsauftrag. `AGENTS.md` bleibt die übergeordnete Regeldatei. Bei Widerspruch gilt `AGENTS.md`.

## 1. Ziel

Gartenpirat technisch zu einem stabilen produktionsreifen Stand führen.

Verbindliche Zielarchitektur:

- Entwicklung / Code / Tests / Build / Originalsignierung / Releasevorbereitung: **Codex Cloud ↔ GitHub**
- Operativer Betrieb: **Pixel-App ↔ Google Drive direkt**
- Laptop/Desktop: **keine Voraussetzung, kein produktiver Bestandteil, kein Fallback**

Codex Cloud ist Entwicklungs- und Buildumgebung, aber kein dauerhafter Runtime-Server. Die fertige App muss im laufenden Betrieb ohne Codex Cloud und ohne Laptop funktionieren.

## 2. Verbindliche Codebasis

Nur dieses Repository verwenden:

`mihalhafner-collab/Gartenpirat-`

Keine zweite App.  
Kein zweites Repository.  
Keine parallele Desktop-/Laptop-Codebasis.  
Keine unnötigen neuen Branches.  
Kein Force-Push.  
Kein History-Rewrite.

Vor jeder Änderung:

1. `AGENTS.md` vollständig lesen.
2. Diese Datei vollständig lesen.
3. `CODEX-STATUS.md` lesen.
4. `REPAIR-v1.49.1-2026-10-18.md` lesen.
5. `ORIGINAL-SIGNIERUNG-CLOUD.md` lesen.
6. `docs/MCP-EIGENTUEMERZUGRIFF-2026-10-18.md` lesen.
7. `docs/SYNC-DRIVE-KUNDENAKTE-1.48.md` lesen.
8. GitHub Issue #1 lesen.
9. `main`-Head und vorhandene Cloud-Branches vergleichen.

Neuere Änderungen niemals überschreiben.

## 3. Aktueller Versionsstand\n\nAktuell verifizierter Live-Pixel:

- Paket: `at.hausgarten.service`
- Version: `1.50.1`
- versionCode / Build: `77`
- Datenbankversion: 4
- MCP-Protokoll: 8
- MCP-Modus: `pixel_business_write`
- MCP-Tools: exakt `111`
- MCP-Tool-Katalog-Hash: `ae7cb69f7994ae8141d2d8c925594982f40236921d65abb3739eee270b5b312f`
- Schreibzugriff: `write_enabled=true`
- `offers_sync_ready=true`
- `service_catalog_sync_ready=true`
- Snapshot-Schema: 2
- Drive konfiguriert
- Provider-Rücklesen aktiv; `cloud_confirmed=false` bleibt davon getrennt.

Aktueller `main`-HEAD nach der am 19.10.2026 bereits eingespielten Angebotskorrektur:

`8430f43542606391f960990dd485db5c5b2df69f`

Achtung: `android/app/build.gradle` steht im Repository noch auf `1.49.1 / versionCode 70`. Das ist gegenüber dem installierten Pixel `1.50.1 / 77` veraltet. Vor dem nächsten produktiven Build Versionsmetadaten sauber auf einen noch unbenutzten Wert **größer als Build 77** anheben. Kein Downgrade und keinen versionCode wiederverwenden.\n
## 4. Aktueller Live-Fehlerstand\n\nLetzte Live-Prüfung am 19.10.2026:

- MCP-Katalog vollständig erreichbar: 111 Tools.
- Davon 50 Read-Tools und 61 Write-Tools.
- 17 Tools sind als destruktiv/administrativ annotiert.
- Angebote und Leistungskatalog sind im direkten Pixel-/Drive-Pfad sync-bereit.
- `get_business_settings` funktioniert wieder.
- Business Settings wurden aus einer realen, bereits ausgestellten Rechnung belastbar ergänzt:
  - `business_phone=06802376503`
  - `business_email=hafnerm@gmx.at`
  - `tax_mode=small_business`
  - Steuerbefreiungshinweis gemäß § 6 Abs. 1 Z 27 UStG 1994
  - Zahlungsziel 14 Tage
  - Ausstellerbestätigung aktiv
- Keine aktuellen `sync_errors`.
- Queue-Zustand: `review_required`, aber keine ausstehenden Uploads.
- `cloud_confirmed=false`; `confirmation_scope=provider_readback`. Provider-Rücklesen nicht als unabhängige Google-Cloud-Bestätigung ausgeben.

### P0/P1 – neu entdeckter Angebotsfehler `price_source`

Reproduzierter Fehler:

Das MCP-Schema akzeptierte für Angebotspositionen bei `price_source` beliebigen Text. Die tatsächliche Validierung akzeptiert bei manuell bestätigten Preisen jedoch ausschließlich exakt:

`user_confirmed`

Folge: Ein korrekt befüllter Angebotsentwurf mit z. B. `price_source="Vom Eigentümer bestätigter Testpreis"` wurde später mit

`Position N: Preisquelle fehlt – keinen Preis erfinden`

abgewiesen.

Der Fehler ist bereits in `main` korrigiert:

- Commit `6d2a032c20a0bbe8ac0a14959ba837b57d194a21`: MCP-Schema auf `price_source=user_confirmed` einschränken und dokumentieren.
- Commit `8430f43542606391f960990dd485db5c5b2df69f`: Regressionstest ergänzt.

Geänderte Dateien:

- `android/app/src/main/java/at/hausgarten/service/OfferTools.java`
- `tests/java/at/hausgarten/service/OffersAndPhotosTests.java`

Dieser Fix ist im aktuell installierten Build 77 noch **nicht** aktiv. Er muss mit einer neuen originalsignierten APK ausgerollt und anschließend live verifiziert werden.

### P1 – UI-Aktionskatalog / MCP-Parität vollständig prüfen

`get_ui_action_catalog` meldet:

- `total=156` semantische UI-/Operatoraktionen
- UI-Katalog-Hash: `39280e7fe8a2779ae31dcd2e513bb4cd293da98b093c18bb4ce7bc860a72b519`

Die aktuell zurückgelieferte Antwort enthält jedoch nur die ersten 50 Aktionen. Bei diesen 50 gilt:

- alle 50 besitzen mindestens einen MCP-Pfad
- keine referenziert ein fehlendes MCP-Tool
- Bereiche: Kunden, Aufträge, Arbeiten, Zeiterfassung und Beginn Rechnungen

Zu klären und zu beheben:

1. Ist die 50er-Grenze im App-Code, im MCP-Transport oder nur in der Antwortserialisierung?
2. `get_ui_action_catalog` muss alle 156 Aktionen vollständig und deterministisch prüfbar machen, entweder vollständig oder sauber paginiert.
3. Für alle 156 Aktionen muss automatisiert gelten: jede fachliche UI-Aktion hat mindestens einen existierenden MCP-Pfad.
4. Plattformaktionen, die zwingend Android-/Google-Consent oder Geräteinteraktion benötigen, ausdrücklich als Plattformgrenze markieren statt fälschlich als MCP-Parität auszugeben.
5. Besonders `prepare_offer_share` / Datei-/E-Mail-Weitergabe auf echte sprachbedienbare End-to-End-Nutzung prüfen. `sharing_requires_device_interaction` ist aktuell als Plattformlimit gemeldet und darf nicht als vollautomatischer Versand ausgegeben werden.

### P1 – Testdatenhygiene

Beim Live-Test wurde versehentlich ein klar markierter 1-EUR-Testentwurf am echten Kunden Michael Hilpert angelegt:

`off_0edfb5133e3c1e4269dd76e2b47b3746`

Titel:

`[TEST] MCP Angebotsworkflow`

Der Datensatz ist nicht finalisiert und nicht versendet. Nach erfolgreichem neuen Build-Test kontrolliert aus den aktiven Daten entfernen/archivieren, ohne den Kunden oder andere Geschäftsdaten zu verändern.

Für alle weiteren Schreibtests ausschließlich verwenden:

`cus_mobile_mcp_cff36e205f50a96f92d9d8872a75022a`

`MCP FUNKTIONSTEST – KEIN ECHTER KUNDE`\n
## 5. Offene Sync-Warteschlange\n\nAktuell existieren exakt 7 Review-Aktionen. `pending_upload=0`; der normale Drive-Abgleich ist nicht technisch fehlgeschlagen.

1. `wrk_mobile_913976ba4bd1453ba75a5468b89c3c32`  
   `DELETE_REVIEW_REQUIRED`

2. `wrk_mobile_b8445cff3af746718d480fce9be3889a`  
   `DELETE_REVIEW_REQUIRED`

3. `wrk_mobile_325735fd783c4f5fb3d012ab7969ccfd`  
   `DELETE_REVIEW_REQUIRED`

4. `delete_change_f82a727e-90ce-4021-a741-2238101b2505`  
   `DELETE_REVIEW_REQUIRED`

5. `delete_change_b1fc39af-3f04-4baa-81bf-a34a20e76479`  
   `DELETE_REVIEW_REQUIRED`

6. `customer_change_3b8f4a4bdaca4d5e9b67a4b9b3ac3020`  
   `WRITE_CONFLICT`  
   Aktuellen Zustand erhalten.

7. `delete_change_9304498d-e90b-47de-be7c-bfd2e627fdf3`  
   `DELETE_REVIEW_REQUIRED`

Der dedizierte Legacy-Workflow bleibt:

- `preview_legacy_conflict_resolution`
- `prepare_legacy_conflict_backup`
- `close_legacy_conflicts`

Zielentscheidung für alle sieben: `keep_current`, keine alte Löschung nachziehen, keine Geschäftsdaten verändern.

Die früher dokumentierten zwei technischen Testfehler `SYNC_COMPONENT_UPDATE_REQUIRED` und `ACTION_ID_CONFLICT` sind in der aktuellen Queue nicht mehr vorhanden. Sie nicht künstlich neu erzeugen und nicht weiter als offene Live-Queue-Fälle behandeln.\n
## 6. Bereits implementierte Reparaturen nicht zurückbauen

In 1.49.1 ist bereits korrigiert:

- `SYNC_COMPONENT_UPDATE_REQUIRED` ist kein manuell auflösbarer Datenkonflikt mehr.
- Manuelle Konfliktauflösung ist auf echte fachliche Konflikte begrenzt.
- Infrastruktur-/Kontrollfehler werden fail-closed behandelt.
- Eingebettete unveränderliche Originalbelege für Konfliktentscheidungen werden unterstützt.
- Überholte Konfliktentscheidungen können ohne Geschäftsdatenwirkung superseded werden.
- zusätzliche Sync-/Owner-Vertragstests existieren.

Diese Schutzlogik erhalten.

## 7. Hauptarchitekturproblem

Historische Komponenten setzen teilweise noch einen lokalen Python-/Web-/Laptop-Prozessor voraus.

Das ist nicht mehr zulässig.

Historische Komponenten wie:

- `server.py`
- `db.py`
- `mcp_integration/`
- lokale Web-App
- lokale SQLite-Python-Anwendung

dürfen als Regressionstestbasis, historische Referenz und Migrationsquelle bestehen bleiben.

Sie dürfen aber keine notwendige produktive Runtime-Komponente sein.

Der produktive Pfad muss vollständig auf dem Pixel implementiert sein.

## 8. Zielarchitektur für Sync

Pixel ist primäre operative App.

Google Drive dient als:

- Synchronisationsspeicher
- Austauschpfad
- Backup-Pfad
- Journal-/Receipt-Speicher
- Schutz vor Datenverlust

Der Pixel muss selbst produktiv verarbeiten:

- Kunden
- Aufträge
- Arbeitsaufzeichnungen
- Fotos
- Angebote
- Leistungskatalog
- Rechnungsentwürfe
- Business Settings
- Archiv / Restore
- Konfliktentscheidungen
- Receipts
- Nummernstatus
- Owner-Journal-Aktionen

Keine dieser Kernfunktionen darf einen Laptop/Webserver voraussetzen.

Historische Dateinamen wie `HausGarten-Laptop.json` dürfen aus Kompatibilitätsgründen zunächst bestehen bleiben. Nicht allein wegen des Namens migrieren.

## 9. Erforderliche Capabilities

Im direkten Pixel/Drive-Pfad tatsächlich implementieren und testen:

- `offers_v1`
- `service_catalog_v1`
- `owner_journal_v2`
- `invoice_compare_and_save`
- `photo_compare_and_save`
- `durable_action_receipts`
- `worklog_compare_and_save`
- `entity_compare_and_save`
- `customer_custom_fields_compare_and_save`
- `billing_relations`
- `photo_attachments_v1`

Wichtig: Capability-Flags erst auf `true` setzen, wenn die jeweilige Verarbeitung wirklich vorhanden und getestet ist.

Keine reine Metadatenkosmetik.

## 10. Business Settings\n\n`get_business_settings` funktioniert aktuell auf dem Pixel. Die zuvor fehlenden produktiven Stammdaten wurden anhand einer bereits ausgestellten Originalrechnung ergänzt.

Aktuell verifiziert:

- owner: Hafner Michael
- Straße: Goethestraße 2
- PLZ/Ort: 9560 Feldkirchen
- Telefon: 06802376503
- E-Mail: hafnerm@gmx.at
- Bank/BIC/IBAN vorhanden
- Steuer-/Ausstellerinformation vorhanden
- `invoice_issuer_confirmed=1`
- `tax_mode=small_business`
- Steuerbefreiungshinweis gemäß § 6 Abs. 1 Z 27 UStG 1994
- Standardzahlungsziel: 14 Tage

Codex soll diese Werte **nicht neu erfinden oder überschreiben**. Prüfen:

- Persistenz nach Neustart
- Persistenz nach Drive-Sync
- versionsgeschütztes Update
- Stale-Hash fail-closed
- keine stillen Defaults über bestehende produktive Werte
- Angebot, Rechnung und PDF verwenden denselben konsistenten Ausstellerkontext.\n
## 11. Rechnungen

Ziel:

- Entwurf erstellen
- Entwurf lesen
- Entwurf ändern
- Positionen ändern
- Arbeitsaufzeichnungen zuordnen
- Business Settings lesen
- Zahlungsinformationen erhalten
- Korrektur/Storno vorbereiten
- finale Ausgabe nur nach expliziter Bestätigung

Keine automatische Rechnungsstellung.

Nummer erst bei finaler Ausgabe vergeben.

Nummernvergabe muss ohne Laptop:

- persistent
- replay-sicher
- idempotent
- kollisionsgeschützt

sein.

Bei unklarem oder parallel verändertem Nummernstand: fail-closed.

Historische sevDesk-Rechnungen nicht verändern.

`billed` nicht mit persönlichem `b.b.` verwechseln.

## 12. Persönliches b.b.

`b.b.` bedeutet ausschließlich persönliche Markierung „Arbeitszeit bezahlt“.

Keine Verbindung zu:

- Rechnung erstellt
- `billed`
- sevDesk
- Rechnungsnummer
- Rechnungsausstellung

Diese Trennung erhalten.

## 13. Angebote\n\nAngebotsfunktion vollständig produktionsfähig machen und den am 19.10.2026 reproduzierten Schema-/Validatorfehler verifizieren.

Erforderliche Funktionen:

- create
- read
- update
- validate
- duplicate
- finalize
- convert to order
- share/export handoff
- Sync und Neustart überleben

### Verbindlicher `price_source`-Vertrag

Für eine manuell ausdrücklich bestätigte Preisposition gilt exakt:

`price_source="user_confirmed"`

Für einen freigegebenen Katalogpreis:

- `catalog_entry_id`
- aktueller `catalog_hash`
- bestätigter Katalogpreis

verwenden; nicht zusätzlich einen freien `price_source`-Text erfinden.

Das MCP-Schema muss diesen Vertrag selbst ausdrücken, damit ChatGPT keinen Wert erzeugen kann, den der Validator später ablehnt.

Regression zwingend:

1. `user_confirmed` wird vom Schema akzeptiert und vom Validator akzeptiert.
2. Beliebiger Text wie `"Vom Nutzer bestätigt"` wird bereits am Schema abgewiesen.
3. Fehlende Preisquelle wird fail-closed abgewiesen.
4. Katalogpreis mit falschem/stalem Hash wird abgewiesen.
5. Finalisierung erzeugt genau ein unveränderliches Angebots-PDF und keine Rechnung.
6. Replay gleicher `request_id` bleibt idempotent.
7. `prepare_offer_share` funktioniert nur mit finalisiertem, unverändertem Angebot.

Rechtsregelsatz aktuell: `AT-2026-10-18`.

Das reale 540-EUR-Angebot an Michael Hilpert wurde wegen des damaligen Validierungsfehlers nicht als App-PDF finalisiert; es wurde nur als bestätigter E-Mail-Text versendet. Keine falsche Behauptung erzeugen, es existiere bereits ein finalisiertes Angebots-PDF.\n
## 13a. MCP-Funktionsumfang und UI-Parität – Live-Audit 19.10.2026

Aktueller MCP-Katalog:

- 111 Tools gesamt
- 50 Read-Tools
- 61 Write-Tools
- 17 als destruktiv/administrativ annotiert
- Protokoll 8
- write_enabled=true

Funktionsbereiche im Katalog:

- Settings/Admin: 8
- Kunden: 6
- Aufträge: 7
- Arbeiten/Zeiterfassung: 22
- Rechnungen: 8
- Fotos/Dateien: 8
- Sync/Backup/Konflikte: 18
- Angebote/Leistungskatalog: 17
- Archiv/Löschen/Wiederherstellen: 6
- Planung/Kalender/Wetter: 11

Diese Summen dienen als Live-Baseline. Codex muss nach Änderungen den vollständigen Katalog erneut zählen, Tool-Namen vergleichen und den neuen Katalog-Hash dokumentieren.

Besonders vollständig testen:

- Kunden create/read/update/archive/restore
- Aufträge create/read/update/review/aus Angebot übernehmen
- Worklogs create/update/reassign/billed/b.b./Archiv
- Timer start/break/resume/stop/finish/discard/restore
- Rechnungsentwurf create/update/read
- Rechnungsabschluss nur nach Bestätigung
- Zahlung/Korrektur
- Fotos Chunk-Upload/create/read/update/Zuordnung
- Datei-Metadaten/Chunk-Read
- Angebote/Katalog vollständig
- Backups create/list/inspect/restore
- Delete/Archive/Purge/Restore mit Hash-/Preview-Schutz
- Sync-Review/Legacy-Konflikte
- Termine/Serien/Planung/Wetter
- Business-/Geräteeinstellungen

Adminziel: Für die private Eigentümer-App muss ChatGPT bei `Allow all actions` alle fachlich vorgesehenen Owner-Write/Adminpfade erreichen können. Schutzparameter wie Hash, Preview, confirmed=true, request_id und Backup bleiben zwingend erhalten.

## 14. Leistungskatalog

Nicht mit Pflanzenkatalog verwechseln.

- Pflanzenkatalog: ca. 181 Profile
- Angebots-/Leistungskatalog: ca. 32 Service-Vorlagen

Service Catalog eigenständig synchronisieren und versionsschützen.

## 15. Fotos und Kundenakte

Produktziel: viele Fotos pro Kunde über lange Zeiträume.

Der alte Grenzwert 10 Bilder / 30 MB ist nicht ausreichend.

Ziel:

- automatische Bildoptimierung
- Originaldatei erhalten, sofern vorgesehen
- Thumbnail erzeugen
- UI flüssig halten
- skalierbare Kundengalerie
- sichere Kunden-/Auftrags-/Worklog-Zuordnung
- direkte Kamera
- Metadatenänderung
- keine künstliche 10-Bilder-Grenze

Bereits erfolgreich getestet wurden:

- MCP-Chunk-Upload
- SHA-256-Prüfung
- MIME-/Dimensionsprüfung
- Optimierung
- Thumbnailerstellung
- Originalspeicherung

`update_photo` muss ohne `REMOTE_GUARD_REQUIRED` funktionieren.

Archivieren ist nicht physisches Löschen.

## 16. Kamera

Reale Android-Pfade erneut prüfen:

- Kamera öffnen
- Foto aufnehmen
- Kunde zuordnen
- optional Auftrag
- optional Arbeitsaufzeichnung
- Vorschau
- Thumbnail
- Vollbild
- Neustart
- Sync
- Wiederlesen

Browser-Test ersetzt keinen echten Android-Kameratest.

## 17. Bestehende Core-Funktionen dürfen nicht regressieren

Bereits grundsätzlich funktionierend:

### Technischer Testkunde

`cus_mobile_mcp_cff36e205f50a96f92d9d8872a75022a`

Name:

`MCP FUNKTIONSTEST – KEIN ECHTER KUNDE`

Nur diesen oder vollständig synthetische Datensätze für Schreibtests verwenden.

Keine echten Kunden als Testdaten.

### Auftrag

Create/read/update/hash/sync/provider-readback/archive funktionierte.

### Worklog

0 Stunden korrekt abgewiesen.  
0,01 Stunden korrekt gespeichert und gelesen.

### Timer

IDLE → start → RUNNING → stop → STOPPED_PENDING_REVIEW → discard → CANCELLED → IDLE

Keine ungewollte Arbeitsaufzeichnung erzeugt.

## 18. Backups

### Pixel-Geschäftsbackup

Backup-ID:

`backup_4ef99a6925a1c66e0af5c6d10721fd5993cd706bfacef5aa7b1e33d2052cae69`

SHA-256:

`4683945a1d959e34aeea818768eb8fc618ef2a9cc424e76054c05072e278cb6b`

State Hash:

`02c7a22f709b59765e96453acc778f7fae84217bc2772ae4a49ee4e6ee84cd5f`

Counts zum Prüfzeitpunkt:

- 51 Kunden
- 1 Auftrag
- 173 Arbeitsaufzeichnungen
- 95 Rechnungen
- 12 Fotos
- 32 Service-Catalog-Einträge
- 0 Angebote

Dieses Backup ist kein vollständiges Gerätebackup.

### Drive-Ausgangssicherung

Datei:

`HausGarten-Laptop-before-v1.49-sync-2026-10-18.json`

SHA-256:

`8556108cafc9ad47a6ee82fa3ce49d01d7a057b42407c8098539dc3825f18cc3`

Vor Migration nicht verändern.

## 19. Datenschutz und Produktionsdaten

Unter keinen Umständen:

- echte Kunden als Testdaten verändern
- echte Rechnungen versenden
- echte Rechnungen finalisieren
- produktive Datensätze löschen
- Kunden zusammenführen
- IDs neu vergeben
- historische Zuordnungen erraten
- Kundentexte in Logs ausgeben
- private Drive-Daten in Git committen
- echte Betriebsdaten als Fixtures verwenden

Produktive Vorher-/Nachher-Prüfung nur über IDs, Counts, Hashes und technische Zustände.

## 20. Pflanzenbereich

Quelle:

`android/app/src/main/assets/plant-catalog.json`

Vor Build:

`python3 tools/sync_plant_assets.py --write`

Prüfung:

`python3 tools/sync_plant_assets.py`

Keine zweite Pflanzen-Datenquelle erzeugen.

## 21. UI-/Workflow-Regeln

Bestehendes einheitliches Design erhalten.

Prioritäten:

- Bedienbuttons ergonomisch
- Start/Stop zuverlässig
- Pull-to-refresh
- Seitenmenü
- Tastatur nicht unnötig automatisch öffnen
- Kundenfotos leicht erreichbar
- direkte Kamera
- persönliche bezahlt-Markierung leicht erreichbar
- keine unnötigen technischen Dialoge

Funktion vor Kosmetik.

## 22. Verbindliche Arbeitsreihenfolge

### Phase A – Bestand verifizieren

1. aktuellen `main`-Head prüfen
2. Regeln und Status lesen
3. Branch-/History-Abgleich
4. Testsituation erfassen
5. keine Produktivdaten ändern
6. `CODEX-STATUS.md` aktualisieren

### Phase B – produktive Laptop-Abhängigkeit entfernen

Alle produktiven Stellen identifizieren, die noch abhängig sind von:

- `server.py`
- lokaler SQLite-Web-App
- Web-Importer
- lokalem Nummernservice
- Laptop-Sync

Fachliche Logik in den Pixel/Drive-Pfad migrieren.

Legacy-Code nicht vorschnell löschen.

### Phase C – Pixel/Drive-Sync vervollständigen

Direkte Verarbeitung implementieren für:

- offers
- service_catalog
- business_settings
- invoice compare/save
- photo compare/save
- owner journal
- durable receipts
- conflict supersession

### Phase D – zwei technische Testfehler bereinigen

Vorhandene Test-Angebotsaktion idempotent verarbeiten.

Technischen `ACTION_ID_CONFLICT` ohne Geschäftsdatenwirkung bereinigen.

Danach dürfen diese zwei technischen Queue-Einträge nicht mehr offen sein.

### Phase E – sieben Alt-Konflikte

Neues Backup.

`preview_legacy_conflict_resolution`

Prüfen:

- exakt 7
- `keep_current`
- `no_business_data_changed`

Backup erzeugen.

Hash erneut verifizieren.

Erst dann:

`close_legacy_conflicts`

Keine Geschäftsdatenänderung.

### Phase F – End-to-End-Test

Nur technischen Testkunden verwenden.

Testen:

1. Kunde lesen
2. Testauftrag create/update/read
3. Worklog create/update/read
4. Timer start/pause/resume/stop/discard
5. Foto upload
6. Foto update
7. Foto read
8. Angebot create
9. Angebot update
10. Angebot validate
11. Sync
12. Neustart-/Snapshot-Simulation
13. Angebot erneut read
14. Service Catalog read
15. Rechnungsentwurf create
16. Rechnungsentwurf update
17. Rechnungsentwurf read
18. Business Settings read
19. Replay identischer request_id
20. absichtlicher Hash-Konflikt
21. Archive/Restore
22. Queue prüfen

Keine echte Rechnung finalisieren oder versenden.

## 23. Regressionstests

Mindestens neue/angepasste Tests für:

- Pixel/Drive ohne Laptop
- offers roundtrip
- service catalog roundtrip
- business settings retention
- photo update guard
- invoice update guard
- durable receipts
- Request-ID replay
- ACTION_ID_CONFLICT prevention
- superseded technical action
- SYNC_COMPONENT_UPDATE_REQUIRED nicht manuell auflösbar
- Legacy-Preview exakt 7
- keep_current ohne Business-Data-Diff
- Capability-Flags nur bei echter Implementierung
- Crash/Restart zwischen Write und Ack
- duplicate journal
- reordered journal
- stale snapshot
- hash mismatch
- offline → online retry

## 24. Erwarteter Endzustand

`integration_health` mindestens:

- `status=ready`
- `write_enabled=true`
- `offers_sync_ready=true`
- `service_catalog_sync_ready=true`
- Drive konfiguriert
- keine Laptop-Abhängigkeit

Außerdem:

- `get_business_settings` funktioniert
- `update_photo` funktioniert
- `update_invoice_draft` funktioniert
- Angebote überleben Sync und Neustart
- keine neuen `SYNC_COMPONENT_UPDATE_REQUIRED`
- keine neuen `ACTION_ID_CONFLICT`
- technische Queue bereinigt
- nach kontrolliertem Legacy-Cleanup auch die sieben Alt-Reviews geschlossen

Normale echte Geschäftsaktionen dürfen offen bleiben, sofern sie fachlich tatsächlich offen sind.

## 25. Cloud-Bestätigung

Nicht `cloud_confirmed=true` behaupten, wenn nur Provider-Rücklesen vorliegt.

Wenn unabhängige Cloud-Bestätigung technisch nicht sicher möglich ist, weiterhin korrekt `provider_readback` ausweisen.

## 26. Build und APK\n\nFür die neue Korrektur ist eine **neue originalsignierte APK** erforderlich, weil der `price_source`-Schemafix im Android-Code liegt.

Installierte Live-Baseline:

- `1.50.1`
- versionCode `77`

Repository-Metadaten sind noch älter (`1.49.1 / 70`) und müssen vor dem Release korrigiert werden.

Nächster produktiver Kandidat mindestens:

- Version `1.50.2`
- versionCode `78`

oder höher, falls 78 bereits anderweitig vergeben wurde.

Verbindliche Reihenfolge:

1. aktuellen `main`-HEAD und Versionshistorie prüfen
2. versionName/versionCode auf unbenutzten Wert > 1.50.1 / 77 setzen
3. vollständige Tests einschließlich neuem Offer-Schema-Regressionsfall
4. Release-Build
5. Privacy-Prüfung
6. Asset-Abgleich
7. Originalsignierung ausschließlich in persönlicher Codex-Cloud
8. `apksigner verify --verbose --print-certs`
9. Zertifikat exakt gegen `HG_ORIGINAL_CERT_SHA256` prüfen
10. `zipalign` prüfen
11. APK SHA-256 ermitteln
12. APK **als Update** installieren; bestehende App nicht deinstallieren und keine App-Daten löschen
13. nach Installation Version/Build/Datenbestand/MCP-Katalog verifizieren
14. vorhandenen ChatGPT-Connector aktualisieren/refreshen; keinen dritten Connector anlegen
15. Pixel-Toolanzahl und ChatGPT-Toolanzahl sowie Katalog-Hash müssen wieder übereinstimmen
16. erst dann vollständigen Probeangebot-End-to-End-Test durchführen.

Nach dem Build muss mindestens geprüft werden:

`create_offer_draft → validate_offer → finalize_offer → PDF-Nachweis → prepare_offer_share → Sync-Status → Neustart/Readback`

Dabei ausschließlich den technischen Testkunden verwenden.\n
## 27. Originalsignierung

Nur über die persönliche Gartenpirat-Codex-Cloud-Umgebung.

Variablen:

- `HG_ORIGINAL_KEYSTORE_BASE64`
- `HG_ORIGINAL_STORE_PASSWORD`
- `HG_ORIGINAL_KEY_ALIAS`
- `HG_ORIGINAL_KEY_PASSWORD`
- `HG_ORIGINAL_CERT_SHA256`

Keine Werte loggen oder speichern.

Verifizierter Zertifikat-SHA256:

`a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`

Die fertige APK muss genau dieses Zertifikat besitzen.

Keine GitHub-Actions-Signierung.
Kein Debug-/QA-/Ersatzschlüssel.
Keine automatische Installation.

## 28. Abschlussbericht\n\nAm Ende in `CODEX-STATUS.md` und im Abschlussbericht dokumentieren:

### Code
- finaler Commit
- Version
- versionCode
- geänderte Dateien

### MCP
- Toolanzahl vorher/nachher
- Read-/Write-/Admin-Zählung
- Tool-Katalog-Hash
- UI-Aktionskatalog: 156 Aktionen vollständig/paginiert prüfbar
- fehlende UI→MCP-Zuordnungen: exakt auflisten
- Plattformgrenzen separat auflisten

### Architektur
- entfernte produktive Laptop-Abhängigkeiten
- verbleibender Legacy-Code
- finaler Pixel/Drive-Pfad

### Datenintegrität
- Counts vorher/nachher
- relevante Hashes
- Nachweis, dass Konfliktcleanup keine Geschäftsdaten verändert hat
- Testangebot am echten Kunden bereinigt, ohne Kundenänderung

### Queue
- Zustand vorher: aktuell 7 Review-Fälle
- Zustand danach
- Behandlung aller tatsächlich noch vorhandenen Fälle

### Tests
Jeder Test mit:
- PASS
- FAIL
- NOT TESTABLE

### Angebotstest
- `price_source=user_confirmed` Schema PASS
- beliebiger freier `price_source` Schema FAIL wie erwartet
- validate PASS
- finalize PASS
- PDF-Hash/Original PASS
- prepare/share PASS oder sauber dokumentierte Plattformgrenze
- Sync/Readback PASS
- kein Invoice-Seiteneffekt

### APK
- Dateiname
- Paket
- Version
- versionCode
- APK SHA-256
- Zertifikat SHA-256
- Signaturprüfung
- Privacy-Prüfung
- Asset-Prüfung

### Restprobleme
Alle verbleibenden P0/P1-Probleme ausdrücklich nennen.

Wenn noch ein P0/P1-Fehler besteht, Auftrag nicht als vollständig abgeschlossen markieren.\n
## 29. Statusführung

`CODEX-STATUS.md` ist während der Bearbeitung fortlaufend zu aktualisieren.

Nach jeder abgeschlossenen Phase dokumentieren:

- Zeit/Datum
- aktueller Commit
- abgeschlossene Phase
- geänderte Dateien
- Tests
- PASS / FAIL / NOT TESTABLE
- offene Probleme
- nächster Schritt

Keine langen Freitext-Tagebücher; nur belastbare technische Zustände.

## 30. Abschlussregel

Nicht versuchen, die alte Laptop-Architektur lediglich wieder zum Laufen zu bringen.

Ziel ist:

**eine eigenständige, sichere Pixel-App mit direktem Google-Drive-Abgleich und vollständiger MCP-Eigentümerfunktion, deren gesamte Entwicklung, Prüfung, Build- und Signierkette ausschließlich über Codex Cloud erfolgt.**

Datenintegrität hat Vorrang vor Geschwindigkeit und Zusatzfunktionen.
