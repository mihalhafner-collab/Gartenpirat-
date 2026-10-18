# CODEX-AUFTRAG-GARTENPIRAT

Stand: 18. Oktober 2026  
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

## 3. Aktueller Versionsstand

Repository-Zielstand bei Auftragserstellung:

- Paket: `at.hausgarten.service`
- Repository-Version: `1.49.1`
- Repository-versionCode: `70`
- minSdk: 26
- targetSdk: 37
- compileSdk: 37

Live installierter Pixel bei letzter Prüfung:

- Version: `1.49.0`
- versionCode: `69`
- Paket: `at.hausgarten.service`
- Datenbankversion: 4
- MCP-Protokoll: 3
- MCP-Modus: `pixel_business_write`
- MCP-Tools: 83
- Schreibzugriff: aktiv

Der Pixel läuft also noch nicht auf Build 70.

## 4. Aktueller Live-Fehlerstand

Letzte Live-Prüfung:

- `offers_sync_ready=false`
- `service_catalog_sync_ready=false`
- `get_business_settings` scheitert mit `SETTINGS_UNAVAILABLE`
- Teile von Foto-/Rechnungsänderungen sind noch durch fehlende Remote-Capabilities blockiert
- `cloud_confirmed=false`
- `confirmation_scope=provider_readback`
- `pending_actions=9`

Provider-Rücklesen ist keine unabhängige Cloud-Bestätigung. Diese Trennung beibehalten.

## 5. Offene Sync-Warteschlange

Es existieren aktuell 9 Review-Aktionen.

### Sieben historische fachliche Fälle

1. `wrk_mobile_913976ba4bd1453ba75a5468b89c3c32`  
   Code: `DELETE_REVIEW_REQUIRED`

2. `wrk_mobile_b8445cff3af746718d480fce9be3889a`  
   Code: `DELETE_REVIEW_REQUIRED`

3. `wrk_mobile_325735fd783c4f5fb3d012ab7969ccfd`  
   Code: `DELETE_REVIEW_REQUIRED`

4. `delete_change_f82a727e-90ce-4021-a741-2238101b2505`  
   Code: `DELETE_REVIEW_REQUIRED`

5. `delete_change_b1fc39af-3f04-4baa-81bf-a34a20e76479`  
   Code: `DELETE_REVIEW_REQUIRED`

6. `customer_change_3b8f4a4bdaca4d5e9b67a4b9b3ac3020`  
   Code: `WRITE_CONFLICT`  
   Aktuellen Zustand erhalten.

7. `delete_change_9304498d-e90b-47de-be7c-bfd2e627fdf3`  
   Code: `DELETE_REVIEW_REQUIRED`

Für diese sieben existiert der dedizierte Legacy-Workflow:

- `preview_legacy_conflict_resolution`
- `prepare_legacy_conflict_backup`
- `close_legacy_conflicts`

Früherer geprüfter Preview:

- Anzahl: exakt 7
- Resolution: `keep_current`
- Reason: `historisch_nicht_mehr_pruefbar`
- Effect: `no_business_data_changed`

Diese sieben erst nach erfolgreicher technischer Reparatur und neuem Backup schließen.

### Zwei technische Testfehler

#### Technische Angebotsaktion

Client-ID:

`mcp_owner_b6f1c56ba6534a250f6f5057175f365623155aa656d55fd0342f2517b4a539da`

Offer-ID:

`off_88a3e818c170ae9ea1dc27c51bf48b64`

Fehler:

`SYNC_COMPONENT_UPDATE_REQUIRED`

Das ist ein technisches Testangebot. Keine echte Rechnung erzeugen, nicht versenden, nicht doppelt anlegen.

#### Technische Konfliktauflösungsaktion

Client-ID:

`mcp_owner_43e89c1a01b88694f43a944e5c4109cb1a7e1a8c15378ee0c1116779bb1ce54c`

Fehler:

`ACTION_ID_CONFLICT`

Sie entstand durch einen früheren falschen Versuch, den Infrastrukturfehler manuell als Datenkonflikt aufzulösen. Sie darf keinerlei Geschäftsdaten ändern. Nach Behebung der technischen Ursache sicher als erledigt/superseded quittieren.

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

## 10. Business Settings

`get_business_settings` muss ohne lokalen Web-/Laptop-Nummernservice funktionieren.

Vorhandene Einstellungen niemals überschreiben, nur weil sie in einem alten Snapshot fehlen.

Erforderlich:

- lesen
- versionsgeschützt ändern
- bei Stale Hash fail-closed
- keine stillen Defaults über bestehende produktive Werte schreiben

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

## 13. Angebote

Angebotsfunktion vollständig produktionsfähig machen.

Erforderlich:

- create
- read
- update
- validate
- duplicate
- finalize
- convert to order
- share handoff

müssen Sync und Neustart überleben.

Finalisieren darf keine Rechnung erzeugen.

Kein erneutes „Angebot verschwindet nach Sync“.

Rechtsregelsatz aktuell: `AT-2026-10-18`.

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

## 26. Build und APK

Nach erfolgreicher funktionaler Reparatur neue Version vergeben.

Build 70 nicht überschreiben.

Nächster produktiver Kandidat mindestens:

- Version `1.49.2`
- versionCode `71`

oder höher, falls bereits vergeben.

Keinen versionCode wiederverwenden.

Erforderliche Schritte:

1. Release-Build
2. Privacy-Prüfung
3. Asset-Abgleich
4. Regressionstests
5. Originalsignierung in persönlicher Codex-Cloud
6. `apksigner verify`
7. Zertifikat vergleichen
8. zipalign prüfen
9. SHA-256 ermitteln

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

## 28. Abschlussbericht

Am Ende in `CODEX-STATUS.md` und im Abschlussbericht dokumentieren:

### Code
- finaler Commit
- Version
- versionCode
- geänderte Dateien

### Architektur
- entfernte produktive Laptop-Abhängigkeiten
- verbleibender Legacy-Code
- finaler Pixel/Drive-Pfad

### Datenintegrität
- Counts vorher/nachher
- relevante Hashes
- Nachweis, dass Konfliktcleanup keine Geschäftsdaten verändert hat

### Queue
- Zustand vorher
- Zustand danach
- Behandlung aller 9 bekannten Einträge

### Tests
Jeder Test mit:
- PASS
- FAIL
- NOT TESTABLE

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

Wenn noch ein P0/P1-Fehler besteht, Auftrag nicht als vollständig abgeschlossen markieren.

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
