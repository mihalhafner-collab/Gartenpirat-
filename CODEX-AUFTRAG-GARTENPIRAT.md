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

## 3. Tatsächlich abgeglichener Ausgang und neuer Quellstand

Ausgang am 19.10.2026: aktuelles Remote-`main` war `b2c37a2f909cc8ac6defabe68dbdd3ce6a42db14` (Build 70). Der vorhandene operative Cloud-Branch enthielt bereits Build 77 bis `5fbe0a3a8c1f9073dc83f83b37b741a59e935ee4`. Beide Historien wurden normal in `main` zusammengeführt (`d67a4ffe549039df4bff78dc41ee0f27076c680b`), ohne Reset, Desktopimport, neue operative App oder History-Rewrite.

Tatsächlich über den bestehenden Connector gelesener Pixel: `at.hausgarten.service`, **1.50.1 / 77**, Datenbank 4, Snapshot 2, internes MCP-Protokoll 8, 111 Werkzeuge, Katalog-Hash `ae7cb69f7994ae8141d2d8c925594982f40236921d65abb3739eee270b5b312f`, Schreiben aktiv. Dieser installierte Stand wurde nicht durch Codex Cloud aktualisiert.

Neuer Quellstand: **1.51.0 / 78**, gleiches Paket und bestehende Originalsignierung, internes MCP-Protokoll **9**, **117 Werkzeuge**. `build.gradle` bleibt Versionsquelle; Release-Builds müssen die bestätigte installierte Baseline 77 überschreiten. Git-SHA, Quellhash, Dirty-Status, Buildtyp und Protokoll sind über den Build-Fingerprint lesbar. Ein physischer Update-/Connector-Test bleibt bis zu einem tatsächlich autorisierten Geräteupdate offen.

## 4. Erneute Fehlerprüfung und konsolidierter Umsetzungsstand

Die verbindliche Abnahme steht im abschließenden Statusblock dieser Datei und in `CODEX-STATUS.md`. Historische Baselines sind keine aktuellen Fehlerbehauptungen.

- **`price_source`: im Quellstand behoben.** `user_confirmed` ist in gemeinsamem Vertrag, MCP-Schema und Domain-Prüfung identisch. Unbekannte Werte werden vor Speicherung abgewiesen; Katalogpreise erfordern vorhandene ID, aktuellen Hash und freigegebenen Preis. Zusätzlich wurden echte literale Backslash-n-Sequenzen der vorherigen beiden Main-Commits korrigiert, die Java-Kompilierung verhinderten. Der installierte Build 77 enthält diesen Fix weiterhin nicht.
- **50er-Abschneidung: nicht reproduzierbar.** Live lieferte der bestehende Build 77 bereits 100+56 von 156 Aktionen über Offset/Limit. Im neuen Code zusätzlich eindeutiger `next_cursor`, Katalog-Hashbindung und automatischer Vollständigkeits-/Quellreview-Contract; keine Behauptung, zuvor seien nur 50 erreichbar gewesen.
- **UI/MCP:** 156 semantische Aktionen, 145 fachliche MCP-Zuordnungen, elf ausdrücklich begründete Plattform-/Darstellungsgruppen. Zuordnung ist ein Contract-Nachweis und kein Beweis, dass jede einzelne Aktion auf dem physischen Pixel ausgeführt wurde.
- **Testdaten-Isolation:** Testangebote/-rechnungen/-aufträge verlangen markierte Testkunden und Testempfänger. Keine Umwidmung realer Kunden in Testkunden. Testentwürfe samt Validierungs-, Finalisierungs-, Share- und Archivfolgeaktionen bleiben lokal; kein Drive-Export, externer Versand oder produktiver Auftrag. Nur ausdrücklich markierte Test-PDFs dürfen lokal für Abnahmezwecke erzeugt werden.
- **Angebote/Kostenvoranschläge:** eine bestehende Entität und ein gemeinsamer Journal-/UI-/PDF-Pfad für `fixed_price`, `time_material`, `estimate_binding`, `estimate_nonbinding`. Ausdrückliche Bindungsbestätigung, versionsgebundener Hinweistext, zentrale Unternehmens-/Textkonfiguration und Preisgrundlagen. Änderungen am Bindungstext übernehmen keine alte Bestätigung. `validated` ist weiterhin editierbar; finale Dokumente bleiben unverändert.
- **Versand:** `share_prepared` ist getrennt von `finalized`/`sent`. `prepare_offer_delivery` bereitet den bestätigten Empfänger und das Original-PDF vor; ein separat ausdrücklich beauftragter Mailkanal sendet. `confirm_offer_delivery` setzt `sent` erst nach echtem Gmail-SENT-Readback mit Abgleich von Aussteller, Empfänger und PDF-SHA. Das Pixel benötigt dafür eine gesonderte Gmail-Lesefreigabe; eine ChatGPT-Freigabe ersetzt sie nicht. Kein realer Versand im Entwicklungstest.
- **Readonly Self-Test:** Version/Protokoll, Katalog, Write-Gate, Einstellungs-Vollständigkeit, Drive-/Sync-Zustand, Capabilities, privater Dateispeicher, UI-Zuordnung und Plattformgrenzen; PASS/FAIL/NOT TESTABLE ohne Geschäftsdatenänderung.

Der ausdrücklich zur Bereinigung freigegebene falsche 1-EUR-Testentwurf bleibt eindeutig identifiziert als `off_0edfb5133e3c1e4269dd76e2b47b3746` (`[TEST] MCP Angebotsworkflow`, Entwurf). Seine kontrollierte Archivierung darf erst nach erfolgreichem neuen Build und frischer Versionsprüfung erfolgen. Das echte Angebot für Michael Hilpert darf weder neu erstellt, finalisiert noch erneut versendet werden. Für technische Tests ausschließlich private synthetische Stores; kein Testangebot an einem realen Kunden, kein produktiver Test-Sync und keine echte Testmail.

## 5. Offene Sync-Warteschlange

Aktueller realer Sicherungsversuch im installierten Build 77: `READ_ERROR: Ungültige Foto-Dateireferenz.` Mindestens eine ältere Foto-Referenz verhindert die vollständige Sicherung; zwölf aktive Snapshot-Fotozeilen besitzen noch keine heutigen SHA-/Storage-Metadaten. Keine Altentscheidung ohne vollständigen Beleg. Der ergänzte Quellweg sichert exakt referenzierte Legacy-Originale mit zwei identischen Provider-Reads und beobachtetem Hash im selben Backup, ohne Foto-/Geschäftsdaten umzuschreiben. Realer Nachtest nach Update bleibt offen.


Aktuell existieren exakt 7 Review-Aktionen. `pending_upload=0`; der normale Drive-Abgleich ist nicht technisch fehlgeschlagen.

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

Die früher dokumentierten zwei technischen Testfehler `SYNC_COMPONENT_UPDATE_REQUIRED` und `ACTION_ID_CONFLICT` sind in der aktuellen Queue nicht mehr vorhanden. Sie nicht künstlich neu erzeugen und nicht weiter als offene Live-Queue-Fälle behandeln.

## 6. Bereits implementierte Reparaturen nicht zurückbauen

In 1.49.1 ist bereits korrigiert:

- `SYNC_COMPONENT_UPDATE_REQUIRED` ist kein manuell auflösbarer Datenkonflikt mehr.
- Manuelle Konfliktauflösung ist auf echte fachliche Konflikte begrenzt.
- Infrastruktur-/Kontrollfehler werden fail-closed behandelt.
- Eingebettete unveränderliche Originalbelege für Konfliktentscheidungen werden unterstützt.
- Überholte Konfliktentscheidungen können ohne Geschäftsdatenwirkung superseded werden.
- zusätzliche Sync-/Owner-Vertragstests existieren.

Diese Schutzlogik erhalten.

## 7. Architektur und historische Referenzen

Der aktuelle operative Pixel-/Drive-Pfad verarbeitet die nachfolgenden Kernentitäten selbst. Frühere Laptop-Abhängigkeiten sind keine aktuellen Rolloutvoraussetzungen. Historische Komponenten bleiben ausschließlich als Referenz, Migration und Regression erhalten.

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

Die bereits bestätigten produktiven Aussteller-, Kontakt-, Bank-, Steuer- und Zahlungsbedingungen erhalten. Private Werte nicht erneut in Arbeitsnachweise kopieren und nicht neu erfinden oder überschreiben. Vorhandene `business_settings` sind gemeinsame Quelle für Angebot, Kostenvoranschlag, Rechnung und PDF. Finale Dokumente frieren den tatsächlich bestätigten Aussteller/Empfänger ein.

Editierbare zentrale Standardtexte: `offer_binding_text`, `offer_nonbinding_text`, `offer_time_material_text`; zusätzlich individueller bestätigter `binding_notice` im Dokument. Leere Standardtext-Einstellungen verwenden die bisherigen zentralisierten Standardtexte, ändern aber kein bestehendes Dokument. Nach jedem Dokumentart-/Hinweiswechsel Bindung erneut ausdrücklich bestätigen.

Weiterhin prüfen: Persistenz nach Neustart/Sync, versionsgeschütztes Update, stale Hash fail-closed, keine stillen Steuerdefaults. Der technisch unterstützte Umfang ersetzt keine automatische Klausel-/Gewerbe-/FAGG-Einzelfallprüfung.

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

Angebotsfunktion vollständig produktionsfähig machen und den am 19.10.2026 reproduzierten Schema-/Validatorfehler verifizieren.

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

Das reale 540-EUR-Angebot an Michael Hilpert wurde wegen des damaligen Validierungsfehlers nicht als App-PDF finalisiert; es wurde nur als bestätigter E-Mail-Text versendet. Keine falsche Behauptung erzeugen, es existiere bereits ein finalisiertes Angebots-PDF.

## 13a. Installierte Live-Baseline und UI-Parität – 19.10.2026

Installierte Baseline vor dem offenen Update (neue Quelle: 117 / Protokoll 9):

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

### Historisches Pixel-Geschäftsbackup vom 18.10.2026

Kein Nachweis des aktuellen Bestands und kein Ersatz für eine neue Sicherung vor Legacy-Cleanup.

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

### Historische Drive-Ausgangssicherung

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

### Phase D – historische technische Testfehler erneut prüfen

Am 19.10.2026 sind diese zwei Fehler nicht mehr in der Live-Queue vorhanden; ältere offene Aussagen sind veraltet. Nicht künstlich neu erzeugen. Die folgenden Bedingungen gelten ausschließlich bei erneuter Reproduktion.

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

Für die neue Korrektur ist eine **neue originalsignierte APK** erforderlich, weil der `price_source`-Schemafix im Android-Code liegt.

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

Dabei ausschließlich den technischen Testkunden verwenden.

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

## 29a. Zusätzliche Architektur- und Bedienverbesserungen – verbindlich prüfen

Diese Punkte sind kein kosmetischer Zusatz, sondern dienen dazu, wiederkehrende Fehlerklassen strukturell zu beseitigen.

### A. Single Source of Truth für MCP-Verträge

Schema, Validator, UI und Tests dürfen zentrale Domänenwerte nicht unabhängig voneinander definieren.

Besonders zentralisieren:

- `price_source`
- Dokumentarten
- Statuswerte
- Steuerarten
- Pflichtfelder
- Rollen / Rechte
- destruktive Bestätigungsparameter
- zulässige Vertragswege
- Archiv-/Restore-/Purge-Zustände

Ziel: Ein Wert, den das MCP-Schema akzeptiert, darf nicht erst später vom Validator wegen einer abweichenden versteckten Konstante abgelehnt werden.

Contract-Test: alle Enum-/Pflichtfelddefinitionen zwischen Tool-Schema und Validator automatisch vergleichen.

### B. Source-/APK-Provenienz

Es muss jederzeit eindeutig nachweisbar sein, welcher Git-Commit auf dem Pixel installiert ist.

Erforderlich:

- versionName/versionCode aus derselben Releasequelle
- Commit-SHA oder Build-Provenienz im Diagnosebereich lesbar
- kein Repository-Stand hinter installiertem Produktiv-Build
- Release darf nicht gebaut werden, wenn `build.gradle` hinter der installierten Baseline liegt
- Buildbericht enthält Commit, Version, versionCode, APK-SHA256 und Zertifikat-SHA256

### C. UI→MCP-Parität als automatischer Contract-Test

Nicht nur manuell zählen.

Build-/Testschritt muss für alle 156 semantischen UI-Aktionen prüfen:

1. MCP-Pfad vorhanden, oder
2. ausdrücklich `platform_only` mit Begründung.

Fehlende oder unbekannte Zuordnungen => Test FAIL.

`get_ui_action_catalog` darf nicht still bei 50 Aktionen abschneiden. Entweder vollständige Rückgabe oder explizite Pagination mit `offset/limit/next_cursor`.

### D. Höherwertige Voice-Tools

111 Einzeltools bleiben als technische Grundlage bestehen. Zusätzlich prüfen, ob häufige Sprachabläufe durch fachlich atomare Tools robuster werden.

Kandidaten:

- Arbeit bei Kunde starten
- Arbeit beenden und Arbeitsnachweis speichern
- offene Arbeitszeiten eines Kunden zu Rechnungsentwurf zusammenfassen
- Angebot aus bestätigten Leistungen/Preisen erstellen und validieren
- Angebot finalisieren und Versand vorbereiten
- Fotos aufnehmen/hochladen und eindeutig Kunde/Auftrag/Worklog zuordnen
- Sync prüfen und nur bei echtem Handlungsbedarf melden

Diese Tools dürfen bestehende Sicherheitsprüfungen nicht umgehen. Intern weiterhin Hashes, request_id, Preview und bestätigte Ziel-IDs verwenden.

### E. Sprachsichere Aktionsklassen

Bedienmodell prüfen:

- Read-only: direkt ausführbar
- reversible Low-Risk Writes: direkt nach klarer Sprachabsicht
- geschäftlich bindende Aktionen: kurze explizite Bestätigung
- endgültige/destruktive Aktionen: Preview + Hash + `confirmed=true`

Keine zusätzliche Sicherheitsabfrage nur aus Gewohnheit einbauen, wenn die Plattformfreigabe und der fachliche Vertrag bereits eine eindeutige Freigabe darstellen.

### F. Angebotsversand Ende-zu-Ende

Ziel für vollständig sprachbedienbaren Ablauf:

1. Angebot validieren
2. finalisieren
3. Original-PDF erzeugen
4. PDF per MCP-Dateipfad sicher lesbar machen
5. Versand an bestätigte Kundenadresse über verbundenen Mailkanal vorbereiten
6. tatsächlichen Versand nur auf ausdrückliche Nutzeranweisung
7. Versand-ID / Zeitstempel als separate Versandquittung erfassen
8. nie `sent` behaupten, bevor der Mailanbieter den Versand bestätigt

Wenn Android `prepare_offer_share` Geräteinteraktion benötigt, diese Plattformgrenze sauber von einem möglichen direkten Gmail-MCP-Versand trennen.

### G. Testdaten-Isolation

Technische Schreibtests ausschließlich mit:

`cus_mobile_mcp_cff36e205f50a96f92d9d8872a75022a`

`MCP FUNKTIONSTEST – KEIN ECHTER KUNDE`

Zusätzlich Schutzmechanismus prüfen:

- Testangebote/-rechnungen/-aufträge nur an Testdatensätzen
- `test_record=true` als erkennbare Schranke
- `[TEST]`-Dokumente dürfen nicht versehentlich an reale externe Empfänger versendet oder produktiv finalisiert werden
- Regressionstest dafür ergänzen

Der aktuell vorhandene 1-EUR-Testentwurf am echten Kunden Michael Hilpert muss nach erfolgreichem neuen Build kontrolliert bereinigt werden.

### H. MCP-Self-Test

Neues read-only Diagnose-/Self-Test-Tool prüfen, das ohne produktive Datenänderung mindestens testet:

- App-/MCP-Version
- Tool-Katalog und Hash
- Write-Gate
- Business Settings Vollständigkeit
- Drive-Konfiguration
- Sync-Status
- Service Catalog / Offers Capabilities
- Datei-/PDF-Speicher erreichbar
- benötigte Plattformgrenzen
- UI→MCP-Paritätsstatus

Ausgabe kompakt als PASS / FAIL / NOT TESTABLE je Teilprüfung.

Keine echten Kunden- oder Rechnungsdaten in Diagnoseausgabe aufnehmen.

### I. Legacy-Reviews aus Normalbetrieb entfernen

Nach erfolgreichem Build und neuem Backup die sieben historischen Review-Fälle mit dem bereits geprüften `keep_current`-Workflow abschließen, sofern Preview/Hashes unverändert bestätigen:

- keine alten Löschungen
- keine Geschäftsdatenänderung
- Konflikthistorie erhalten

Danach soll `get_sync_overview` nicht dauerhaft `review_required` nur wegen historischer Altlasten anzeigen.

### J. Business Settings als zentraler Ausstellerkontext

Angebote, Rechnungen, PDFs und Versandtexte müssen dieselben bestätigten Stammdaten verwenden.

Keine getrennten Defaults pro Dokumentpfad.

Persistenz- und Konsistenztests für:

- owner_name
- business_name
- Adresse
- Telefon
- E-Mail
- Steuerhinweis
- Zahlungsbedingungen
- Bankdaten
- UID bzw. Kleinunternehmermodus

## 29b. Kostenvoranschlag vollständig implementieren und bestehende Angebotslogik konsolidieren

Dieser Punkt ist verbindlich und gehört zum selben Release wie die oben beschriebenen MCP-/Angebotskorrekturen.

### Ausgangslage

Im Repository existieren bereits Teilbausteine für Kostenvoranschläge, insbesondere Dokumentarten wie:

- `estimate_nonbinding`
- `estimate_binding`

sowie PDF-/Darstellungslogik in der bestehenden Angebots-/Dokumentpipeline.

Diese Teilimplementierung darf **nicht** durch einen zweiten, unabhängigen Dokumentpfad ersetzt werden. Codex soll den vorhandenen Angebots-/Kostenvoranschlags-Pfad konsolidieren und als Single Source of Truth fertigstellen.

### Zielbild

Kostenvoranschläge müssen als eigener fachlicher Dokumenttyp innerhalb derselben konsistenten Business-Dokumentarchitektur funktionieren.

Unterstützt werden mindestens:

1. unverbindlicher Kostenvoranschlag
2. verbindlicher Kostenvoranschlag
3. normales Angebot / Fixpreisangebot
4. Zeit-/Material-Angebot, falls im bestehenden Modell vorgesehen

Die Dokumentarten müssen in UI, MCP, Validator, PDF, Persistenz, Sync und Tests identisch verstanden werden.

### Datenmodell

Prüfe und vereinheitliche mindestens:

- `document_type`
- `pricing_mode`
- Bindungsart
- Gültigkeitsdatum
- Annahmen
- Ausschlüsse
- Hinweise
- Zahlungsbedingungen
- Positionen
- Menge
- Einheit
- Einzelpreis
- Preisquelle
- Steuerart / Steuerbetrag
- Netto / Brutto
- Ausstellerdaten
- Empfängerdaten
- Status
- Finalisierungszeitpunkt
- PDF-Metadaten
- Versandstatus

Keine versteckten Defaultwerte, die UI und MCP unterschiedlich behandeln.

### Preislogik

Der bereits gefundene `price_source`-Fehler ist auch für Kostenvoranschläge verbindlich zu berücksichtigen.

`user_confirmed` muss schema-, validator- und persistenzseitig identisch behandelt werden.

Für Kostenvoranschläge muss außerdem klar abgebildet sein, ob ein Preis:

- vom Nutzer bestätigt
- aus Leistungskatalog übernommen
- berechnet
- geschätzt

ist.

Nur tatsächlich unterstützte Enum-Werte zulassen. Keine freien Fantasiewerte.

### Unverbindlich vs. verbindlich

Die beiden Kostenvoranschlagsarten dürfen nicht nur unterschiedliche PDF-Texte sein.

Prüfe, welche Unterschiede fachlich im Datenmodell und Verhalten erforderlich sind.

Mindestens muss eindeutig gespeichert und abrufbar sein:

- Dokumentart
- Bindungsstatus
- zugehöriger Hinweistext
- Gültigkeit
- eventuelle Annahmen / Ausschlüsse

Keine Rechtswirkung erfinden oder aus Bezeichnungen ableiten, die im Code nicht fachlich definiert ist.

Rechtstexte nicht stillschweigend verändern. Vorhandene Texte gegen die tatsächlich gewünschte österreichische Betriebslogik prüfen und als zentral konfigurierte Dokumenttexte behandeln, nicht mehrfach hardcoden.

### MCP-Funktionsumfang

Der Kostenvoranschlags-Pfad muss vollständig über MCP bedienbar sein.

Mindestens prüfen bzw. implementieren:

- Entwurf erstellen
- Entwurf lesen
- Entwurf bearbeiten
- validieren
- Dokumentart setzen/ändern, solange fachlich erlaubt
- Positionen hinzufügen/ändern/löschen
- Preise/Preisquellen setzen
- Annahmen/Ausschlüsse/Hinweise setzen
- finalisieren
- PDF erzeugen
- PDF abrufen
- Share/Versand vorbereiten
- Versandstatus getrennt vom Finalisierungsstatus führen
- duplizieren
- in Auftrag überführen, sofern bestehender Workflow dies unterstützt
- Suche/Liste/Filter nach Dokumentart und Status

Keine neue Parallelfamilie wie `create_estimate_*`, wenn die vorhandenen `create_offer_*`-Tools mit sauberem `document_type` fachlich korrekt erweitert werden können.

Wenn getrennte Tools technisch doch erforderlich sind, muss Codex dies im Abschlussbericht begründen.

### UI

Die UI muss Kostenvoranschläge explizit und verständlich unterstützen.

Mindestens:

- Dokumentart auswählbar
- verbindlich/unverbindlich klar sichtbar
- Entwurf vs. finalisiert klar sichtbar
- Kostenpositionen einfach erfassbar
- Summen jederzeit nachvollziehbar
- Pflichtfeldfehler feldbezogen anzeigen
- PDF-Vorschau
- kein automatischer Versand
- bestehendes Design beibehalten
- keine doppelte Angebots-/Kostenvoranschlags-Oberfläche erzeugen, wenn eine gemeinsame Oberfläche mit Dokumentart genügt

### PDF

PDF-Erzeugung muss denselben bestätigten Datensatz verwenden wie UI und MCP.

Prüfen:

- korrekte Dokumentbezeichnung
- korrekte Aussteller-/Empfängerdaten
- Datum / Gültigkeit
- Positionen
- Netto/Steuer/Brutto
- Kleinunternehmerlogik
- Annahmen
- Ausschlüsse
- Hinweise
- Zahlungsbedingungen
- zentral definierter Text für verbindlich/unverbindlich
- Dateiname
- Dokument-ID
- PDF-SHA256
- erneuter Readback identisch zum finalisierten Datensatz

Keine PDF-Erzeugung aus separaten veralteten Defaults.

### Statusmodell

Statuslogik vereinheitlichen.

Mindestens unterscheiden:

- draft
- validated
- finalized
- share_prepared
- sent

`sent` darf ausschließlich gesetzt werden, wenn der externe Versand tatsächlich bestätigt wurde.

`prepare_offer_share` bzw. dessen Kostenvoranschlags-Nutzung darf niemals allein `sent` setzen.

### Testdaten

Alle technischen Kostenvoranschlags-Tests ausschließlich mit dem bestehenden Testkunden und Testflag.

Keine realen Kunden verwenden.

Der bestehende 1-EUR-Testentwurf am echten Kunden Michael Hilpert ist nach erfolgreichem Build kontrolliert zu bereinigen, ohne andere Kundendaten zu verändern.

### Automatisierte Tests

Mindestens ergänzen:

1. unverbindlicher Kostenvoranschlag vollständig gültig
2. verbindlicher Kostenvoranschlag vollständig gültig
3. ungültiger `document_type`
4. `price_source=user_confirmed` PASS
5. ungültige Preisquelle FAIL
6. fehlende Pflichtfelder FAIL
7. Summenberechnung
8. Kleinunternehmermodus
9. Standardsteuer-Modus
10. PDF für unverbindlich
11. PDF für verbindlich
12. PDF-Readback
13. Finalisierung
14. Finalisierung mit veraltetem Hash FAIL
15. Share-Vorbereitung
16. `sent` ohne Versandbestätigung darf nicht möglich sein
17. Sync + Neustart + Readback
18. Testdaten-Isolation
19. UI-/MCP-`document_type`-Parität
20. Migration bestehender Angebots-/Kostenvoranschlagsdaten ohne Datenverlust

### Migration und Rückwärtskompatibilität

Bestehende Angebote/Kostenvoranschläge dürfen durch die Konsolidierung nicht beschädigt werden.

Codex muss:

- vorhandene Dokumentarten inventarisieren
- bestehende Datensätze migrieren oder kompatibel lesen
- unbekannte/alte Typen sauber melden
- keine Daten stillschweigend umdeuten
- Regressionstest mit altem Snapshot ergänzen

### Zusammenhang mit bereits gefundenen Fehlern

Der Kostenvoranschlags-Block ist gemeinsam mit folgenden bereits bekannten Punkten zu lösen:

- MCP-Schema/Validator-Drift bei `price_source`
- UI-Aktionskatalog meldet mehr Aktionen als abrufbar
- Tool-/UI-Abdeckung als Contract-Test
- Testdaten an realem Kunden
- Build-/APK-Provenienz
- Business Settings als zentrale Ausstellerquelle
- Angebots-/Dokumentversand Ende-zu-Ende
- strukturierte Fehlerantworten
- Self-Test / Diagnose
- Versions- und Katalog-Hash-Verifikation nach neuem Build

Keine Einzellösung implementieren, die diese Architekturprobleme umgeht.

### Abnahmekriterien Kostenvoranschlag

Der Kostenvoranschlag gilt erst als fertig, wenn:

- UI PASS
- MCP create/read/update/validate/finalize PASS
- beide Dokumentarten PASS
- PDF PASS
- PDF-Readback PASS
- Sync/Restart PASS
- Testdaten-Isolation PASS
- Schema/Validator-Contract PASS
- keine Produktivdaten verändert
- kein Versand ohne explizite Nutzerfreigabe
- keine falsche `sent`-Markierung
- bestehende Daten weiterhin lesbar
- neuer originalsignierter APK-Build erfolgreich
- neuer Build-Fingerprint über MCP eindeutig nachweisbar

Wenn einer dieser Punkte FAIL oder NOT TESTABLE ist, im Abschlussbericht offen ausweisen und den Auftrag nicht als vollständig abgeschlossen markieren.

### Arbeitsweise für Codex

Codex soll vor Implementierung den bestehenden Pfad vollständig inventarisieren und danach die kleinste konsistente Änderung durchführen.

Keine zweite Dokumentarchitektur aufbauen.
Keine großflächige Nebenrefaktorierung.
Keine produktiven Kundendaten für Tests verwenden.
Keine Rechts-/Steuerdaten erfinden.
Keine erfolgreiche Geräteinstallation oder Live-Verifikation behaupten, wenn kein physischer Zugriff bestand.

`CODEX-STATUS.md` nach jeder abgeschlossenen Phase aktualisieren.


## 30. Abschlussregel

Nicht versuchen, die alte Laptop-Architektur lediglich wieder zum Laufen zu bringen.

Ziel ist:

**eine eigenständige, sichere Pixel-App mit direktem Google-Drive-Abgleich und vollständiger MCP-Eigentümerfunktion, deren gesamte Entwicklung, Prüfung, Build- und Signierkette ausschließlich über Codex Cloud erfolgt.**

Datenintegrität hat Vorrang vor Geschwindigkeit und Zusatzfunktionen.
