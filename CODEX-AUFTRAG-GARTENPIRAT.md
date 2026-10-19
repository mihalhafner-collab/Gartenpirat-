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


## P0 – Repository dauerhaft frei von produktiven Betriebsdaten machen

Dieser Block hat Vorrang vor allen nicht sicherheitskritischen Erweiterungen. Ziel ist eine harte technische Trennung zwischen Programmcode und Produktivdaten, damit Codex Cloud den vollständigen Quellstand bearbeiten, bauen und testen kann, ohne reale Kunden-, Arbeits-, Rechnungs-, Angebots-, Foto-, Drive- oder Backupdaten sehen oder benötigen zu müssen.

### P0.1 Zielarchitektur

Verbindliche Trennung:

- GitHub / Codex Cloud: ausschließlich Quellcode, öffentliche Katalog-/Markendaten, leere Bootstrap-Daten und synthetische QA-Fixtures.
- Pixel: operative Datenbank, Kunden, Aufträge, Arbeitsaufzeichnungen, Angebote, Rechnungen, Fotos und lokale Dokumente.
- Google Drive: produktiver Sync-, Austausch-, Dokument- und Sicherungspfad.
- Externer privater Sicherungsspeicher: optionale Rohbackups außerhalb des Git-Checkouts.
- Keine produktiven Rohdaten zurück in den Repository-Workspace.

.gitignore allein gilt ausdrücklich nicht als ausreichende Schutzmaßnahme: ignorierte Dateien innerhalb des Checkouts sind weiterhin für lokale Werkzeuge/Codex lesbar.

### P0.2 Zentrale Datenpfad-Regel

Eine zentrale Pfadabstraktion einführen, z. B. runtime_paths.py bzw. eine äquivalente gemeinsame Konfiguration.

Produktive Betriebsdaten dürfen nur über einen externen privaten Root erreichbar sein, vorzugsweise HG_PRIVATE_DATA_ROOT.

Verbindliche Regeln:

1. Produktive Datenpfade dürfen nicht implizit auf ROOT/data, ROOT/outputs, android/.../assets oder andere Unterverzeichnisse des Repositorys zurückfallen.
2. Ist HG_PRIVATE_DATA_ROOT nicht gesetzt, müssen produktive Backup-/Export-/Importfunktionen fail-closed abbrechen.
3. Löst sich HG_PRIVATE_DATA_ROOT innerhalb des Repositorys auf, muss der Vorgang mit klarer Fehlermeldung abbrechen.
4. Testpfade verwenden ausschließlich tempfile/isolierte synthetische Stores und niemals HG_PRIVATE_DATA_ROOT.
5. Secrets bleiben weiterhin getrennt von Betriebsdaten und dürfen ebenfalls nicht in den Workspace.

### P0.3 initial-data.json unveränderlich als leerer Bootstrap

android/app/src/main/assets/initial-data.json bleibt dauerhaft ein leerer, schemafähiger Bootstrap-Datensatz.

Verbindlich:

- alle betrieblichen Collections leer;
- keine Kunden-, Arbeits-, Rechnungs-, Foto-, Angebots- oder Auftragsdaten;
- kein produktives Tool darf diese Datei mit Live-Daten überschreiben;
- Quelltest muss Schreibzugriffe auf initial-data.json erkennen und blockieren, außer ausdrücklich erlaubter Erzeugung des leeren Bootstrap-Inhalts.

export_mobile_snapshot.py in seiner bisherigen Form darf nicht weiter reale Daten in initial-data.json schreiben. Entweder entfernen oder in einen klar benannten externen Betriebsdaten-Exporter umbauen, dessen Ziel ausschließlich außerhalb des Repositorys liegt.

### P0.4 Produktive Backups aus dem Repository-Workspace entfernen

Alle Skripte prüfen, die Rohdaten unter data/, outputs/, output/, tmp/ oder anderen Repository-Unterordnern ablegen.

Mindestens inventarisieren und korrigieren:

- tests/pixel_update_release.py
- tests/pixel-usb-backup.cjs
- historische Backup-/Export-/Restore-Helfer
- Python-DB-Pfade in db.py
- MCP-/Server-Testpfade, soweit sie produktiven Fallback verwenden
- alle weiteren Treffer auf data/backups, haus-garten.db, HausGarten-Laptop.json oder produktive Fotoordner.

Rohbackups dürfen nur außerhalb des Checkouts gespeichert werden.

Im Repository dürfen für Update-/Datenerhalt-Nachweise nur technische Metadaten verbleiben, z. B.:

- Collection-Counts
- Schema-Version
- kanonische SHA-256-Hashes
- Relations-/ID-Hash
- Queue-/Journal-Hash
- PASS/FAIL/NOT TESTABLE

Keine Namen, Adressen, E-Mails, Telefonnummern, Rechnungstexte, Fotoinhalte oder sonstigen Geschäftsdaten in Testreports.

### P0.5 Synthetische Testdaten als einzige Entwicklungsdaten

Eine zentrale synthetische Testdatenquelle einführen, z. B. SyntheticBusinessDataFactory / synthetic_fixtures.

Mindestens drei reproduzierbare Profile:

- minimal
- edge_cases
- scale

Verbindliche Testkennzeichen:

- IDs nur im qa_/test_-Namespace;
- Namen wie QA Musterkunde;
- Adressen ausschließlich künstlich;
- E-Mail nur unter example.invalid;
- test_record=true, soweit das Datenmodell dies unterstützt;
- keine echten sevDesk-IDs, Telefonnummern, IBANs, Kunden-IDs oder historischen Rechnungsnummern.

Alle Unit-, JVM-, Python-, Browser-, MCP- und Android-Instrumentationstests auf diese synthetische Quelle migrieren. Reale Stammdaten in Tests ebenfalls ersetzen, einschließlich realer Ausstelleradresse/-name, soweit sie nur Testzwecken dienen.

### P0.6 Test-/Produktiv-Datastore klar trennen

Fachlogik soll nicht direkt an produktive Dateipfade gekoppelt sein.

Zielstruktur sinngemäß:

- gemeinsamer Business-/Repository-Vertrag;
- produktiver Adapter: Pixel/Drive;
- Testadapter: temporärer/synthetischer Store.

Keine zweite operative App und keine konkurrierende produktive Datenbank erzeugen. Der Teststore ist ausschließlich kurzlebige QA-Infrastruktur.

### P0.7 Drei verpflichtende Privacy-Gates

Die vorhandenen Datenschutzprüfungen zu einem dreistufigen Release-Gate ausbauen:

A. Source Gate
- committed Quellcode enthält keine produktiven Betriebsdaten oder Secrets;
- initial-data.json leer;
- keine bekannten Produktivmarker in Source/Fixtures.

B. Workspace Gate
- gesamten Checkout prüfen, nicht nur Git-tracked Dateien;
- auch ignorierte/untracked Dateien berücksichtigen;
- keine .db, .sqlite*, Rohbackups, Drive-Snapshots, Kundendokumente, produktiven Fotos oder Exporte im Workspace;
- Ausnahmen nur für eindeutig synthetische Testartefakte und Buildartefakte ohne Betriebsdaten.

C. Artifact Gate
- APK/AAB/Source-ZIP/Review-Paket auf eingebettete Betriebsdaten und Secrets prüfen;
- operative Seed-Zeilen exakt 0;
- bei Befund Build/Release abbrechen.

Alle drei Gates sind verpflichtend für Release-Abnahme. Ein fehlgeschlagenes Gate ist P0 und verhindert Abschluss.

### P0.8 Datenschutzklassifikation

Dateien/Artefakte logisch in vier Klassen einteilen:

- PUBLIC_SOURCE
- SYNTHETIC_TEST
- PRIVATE_RUNTIME
- SECRET

Nur PUBLIC_SOURCE und SYNTHETIC_TEST dürfen innerhalb des Repository-Workspaces existieren.

PRIVATE_RUNTIME und SECRET müssen außerhalb liegen.

Codex soll diese Klassifikation in Regeln/Dokumentation nachvollziehbar machen und nicht für jede Datei manuell duplizieren, wenn eine zentrale Regel genügt.

### P0.9 Self-Test ohne Produktivdaten

Den read-only MCP-Self-Test so erweitern/erhalten, dass er keine produktiven Rohdaten benötigt und keine Geschäftsdaten ausgibt.

Technische Checks dürfen z. B. umfassen:

- Build-/MCP-Version und Fingerprint
- Tool-Katalog/Hash
- Schema-/Validator-Contracts
- UI→MCP-Parität
- Write-Gate
- Drive-Konfiguration
- Sync-Grundzustand
- Dokument-/Dateispeicher technisch erreichbar
- Capabilities
- Plattformgrenzen

Für schreibende End-to-End-QA einen temporären Sandbox-/synthetischen Store verwenden, nicht den produktiven Kundenbestand.

### P0.10 AGENTS.md dauerhaft absichern

Nach Implementierung folgende Regel sinngemäß dauerhaft in AGENTS.md verankern:

Produktive Kunden-, Auftrags-, Arbeitszeit-, Angebots-, Rechnungs-, Foto-, Drive- und Backupdaten dürfen nicht Bestandteil des Repository-Workspaces, von Testfixtures oder Entwicklungsartefakten sein. Tests verwenden ausschließlich synthetische Daten. Produktive Datenintegrität wird über Counts, Hashes, Schema- und Relationsinformationen geprüft.

Diese Regel darf nicht durch bequemere Testpfade umgangen werden.

### P0.11 Historische Git-Historie separat behandeln

In diesem P0-Schritt kein History-Rewrite.

Zuerst den aktuellen main-Checkout und alle zukünftigen Builds dauerhaft betriebsdatenfrei machen.

Danach ausschließlich lesend prüfen, ob frühere Commits tatsächlich produktive sensible Daten enthalten. Ergebnis klassifizieren:

- keine produktiven Daten gefunden;
- nur bereits synthetische/historische technische Werte;
- produktive sensible Daten in Historie gefunden.

Falls echte sensible Daten in der Git-Historie vorhanden sind, nicht automatisch filter-repo, Force-Push oder History-Rewrite ausführen. Dafür separaten Sicherheitsauftrag mit Backup, Auswirkungsanalyse und ausdrücklicher Freigabe verlangen.

### P0.12 Verbindliche Umsetzungsreihenfolge

1. aktuellen Workspace/Source read-only inventarisieren;
2. alle produktiven Datenpfade und echten Testwerte auflisten;
3. zentrale externe Runtime-Pfadregel implementieren;
4. initial-data.json gegen produktive Befüllung absichern;
5. Backup-/Export-/Update-Skripte auf externen privaten Root umstellen;
6. synthetische Factory/Fixtures zentralisieren;
7. bestehende Tests auf synthetische Daten migrieren;
8. reale Betriebsstammdaten aus Tests/Beispielen entfernen;
9. Source-/Workspace-/Artifact-Privacy-Gates implementieren;
10. Gates in normale Quality-Suite und Releaseprozess integrieren;
11. Self-Test/Sandbox gegen Produktivdatenzugriff absichern;
12. AGENTS.md, CODEX-STATUS.md und diese Datei aktualisieren;
13. erst danach weitere nicht sicherheitskritische Funktionsarbeit fortsetzen.

### P0.13 Abnahmekriterien

P0 gilt nur als bestanden, wenn:

- initial-data.json garantiert leer bleibt;
- kein produktives Skript Rohdaten innerhalb des Repositorys ablegt;
- produktive Datenroot innerhalb des Repos technisch abgewiesen wird;
- alle automatisierten Tests ausschließlich synthetische/temporäre Daten verwenden;
- reale Betriebsstammdaten aus Testfixtures entfernt sind;
- Source Gate PASS;
- Workspace Gate PASS;
- Artifact Gate PASS;
- APK enthält 0 operative Seed-Zeilen;
- keine produktive .db/.sqlite/Backup-/Drive-Datei im Checkout;
- Update-/Datenerhalt-Nachweise funktionieren mit Hashes/Counts ohne Rohdaten im Report;
- bestehende App-ID, IDs, Relationen, Sync-Formate und Originalsignierung unverändert kompatibel bleiben;
- keine produktiven Kunden-/Rechnungs-/Arbeits-/Foto-Daten während der Migration verändert werden;
- Abschlussbericht PASS/FAIL/NOT TESTABLE je Unterpunkt ausweist.

Wenn ein P0-Kriterium FAIL oder NOT TESTABLE ist, darf dieser Block nicht als vollständig abgeschlossen markiert werden.


## 3. Tatsächlich abgeglichener Ausgang und neuer Quellstand

Ausgang am 19.10.2026: aktuelles Remote-`main` war `b2c37a2f909cc8ac6defabe68dbdd3ce6a42db14` (Build 70). Der vorhandene operative Cloud-Branch enthielt bereits Build 77 bis `5fbe0a3a8c1f9073dc83f83b37b741a59e935ee4`. Beide Historien wurden normal in `main` zusammengeführt (`d67a4ffe549039df4bff78dc41ee0f27076c680b`), ohne Reset, Desktopimport, neue operative App oder History-Rewrite.

Tatsächlich über den bestehenden Connector gelesener Pixel: `at.hausgarten.service`, **1.50.1 / 77**, Datenbank 4, Snapshot 2, internes MCP-Protokoll 8, 111 Werkzeuge, Katalog-Hash `ae7cb69f7994ae8141d2d8c925594982f40236921d65abb3739eee270b5b312f`, Schreiben aktiv. Dieser installierte Stand wurde nicht durch Codex Cloud aktualisiert.

Neuer Quellstand: **1.51.0 / 79**, gleiches Paket und bestehende Originalsignierung, internes MCP-Protokoll **9**, **117 Werkzeuge**. `build.gradle` bleibt Versionsquelle; Release-Builds müssen die bestätigte installierte Baseline 77 überschreiten. Git-SHA, Quellhash, Dirty-Status, Buildtyp und Protokoll sind über den Build-Fingerprint lesbar. Ein physischer Update-/Connector-Test bleibt bis zu einem tatsächlich autorisierten Geräteupdate offen.

## 4. Erneute Fehlerprüfung und konsolidierter Umsetzungsstand

Die verbindliche Abnahme steht im abschließenden Statusblock dieser Datei und in `CODEX-STATUS.md`. Historische Baselines sind keine aktuellen Fehlerbehauptungen.

- **`price_source`: im Quellstand behoben.** `user_confirmed` ist in gemeinsamem Vertrag, MCP-Schema und Domain-Prüfung identisch. Unbekannte Werte werden vor Speicherung abgewiesen; Katalogpreise erfordern vorhandene ID, aktuellen Hash und freigegebenen Preis. Zusätzlich wurden echte literale Backslash-n-Sequenzen der vorherigen beiden Main-Commits korrigiert, die Java-Kompilierung verhinderten. Der installierte Build 77 enthält diesen Fix weiterhin nicht.
- **50er-Abschneidung: nicht reproduzierbar.** Live lieferte der bestehende Build 77 bereits 100+56 von 156 Aktionen über Offset/Limit. Im neuen Code zusätzlich eindeutiger `next_cursor`, Katalog-Hashbindung und automatischer Vollständigkeits-/Quellreview-Contract; keine Behauptung, zuvor seien nur 50 erreichbar gewesen.
- **UI/MCP:** 156 semantische Aktionen, 145 fachliche MCP-Zuordnungen, elf ausdrücklich begründete Plattform-/Darstellungsgruppen. Zuordnung ist ein Contract-Nachweis und kein Beweis, dass jede einzelne Aktion auf dem physischen Pixel ausgeführt wurde.
- **Testdaten-Isolation:** Testangebote/-rechnungen/-aufträge verlangen markierte Testkunden und Testempfänger. Keine Umwidmung realer Kunden in Testkunden. Testentwürfe samt Validierungs-, Finalisierungs-, Share- und Archivfolgeaktionen bleiben lokal; kein Drive-Export, externer Versand oder produktiver Auftrag. Nur ausdrücklich markierte Test-PDFs dürfen lokal für Abnahmezwecke erzeugt werden.
- **Angebote/Kostenvoranschläge:** eine bestehende Entität und ein gemeinsamer Journal-/UI-/PDF-Pfad für `fixed_price`, `time_material`, `estimate_binding`, `estimate_nonbinding`. Ausdrückliche Bindungsbestätigung, versionsgebundener Hinweistext, zentrale Unternehmens-/Textkonfiguration und Preisgrundlagen. Änderungen am Bindungstext übernehmen keine alte Bestätigung. `validated` ist weiterhin editierbar; finale Dokumente bleiben unverändert.
- **Versand:** `share_prepared` ist getrennt von `finalized`/`sent`. `prepare_offer_delivery` bereitet den bestätigten Empfänger und das Original-PDF vor; ein separat ausdrücklich beauftragter Mailkanal sendet. `confirm_offer_delivery` setzt `sent` erst nach echtem Gmail-SENT-Readback mit Abgleich von Aussteller, Empfänger und PDF-SHA. Das Pixel benötigt dafür eine gesonderte Gmail-Lesefreigabe; eine ChatGPT-Freigabe ersetzt sie nicht. Kein realer Versand im Entwicklungstest.
- **Readonly Self-Test:** Version/Protokoll, Katalog, Write-Gate, Einstellungs-Vollständigkeit, Drive-/Sync-Zustand, Capabilities, privater Dateispeicher, UI-Zuordnung und Plattformgrenzen; PASS/FAIL/NOT TESTABLE ohne Geschäftsdatenänderung.

Der ausdrücklich zur Bereinigung freigegebene falsche 1-EUR-Testentwurf `off_0edfb5133e3c1e4269dd76e2b47b3746` (`[TEST] MCP Angebotsworkflow`) wurde nach erfolgreicher Build-/Android-Abnahme, frischer Versionsprüfung und erlaubter Vorschau am 19.10.2026 reversibel archiviert. Archiv-Readback: genau ein passender Eintrag; aktive Angebote 2→1. Nur diese Angebotsgruppe änderte sich; alle anderen Geschäftsgruppenhashes blieben im unmittelbaren Vergleich identisch. Keine physische Löschung. Das echte Angebot für Michael Hilpert darf weder neu erstellt, finalisiert noch erneut versendet werden. Für technische Tests ausschließlich private synthetische Stores; kein Testangebot an einem realen Kunden, kein produktiver Test-Sync und keine echte Testmail.

## 5. Offene Sync-Warteschlange

Aktueller realer Sicherungsversuch im installierten Build 77: `READ_ERROR: Ungültige Foto-Dateireferenz.` Mindestens eine ältere Foto-Referenz verhindert die vollständige Sicherung; zwölf aktive Snapshot-Fotozeilen besitzen noch keine heutigen SHA-/Storage-Metadaten. Keine Altentscheidung ohne vollständigen Beleg. Der ergänzte Quellweg sichert exakt referenzierte Legacy-Originale mit zwei identischen Provider-Reads und beobachtetem Hash im selben Backup, ohne Foto-/Geschäftsdaten umzuschreiben. Realer Nachtest nach Update bleibt offen.


Ursprüngliche Baseline: sieben Review-Aktionen. **Letzter realer Readback 19.10.2026 11:03:55 UTC: sechs aktive Reviews, null Uploads, 15 Historieneinträge.** Normaler Drive-Abgleich ohne technischen Fehler; `provider_readback`, `cloud_confirmed=false`. Folgende Liste bleibt als historische Ausgangsmenge erhalten; Eintrag 1 ist inzwischen nicht mehr aktiv.

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

Ziel für eine künftig frisch bestätigte Auswahl: `keep_current`, keine alte Löschung nachziehen, keine Geschäftsdaten verändern. **In diesem Auftrag kein Legacy-Abschluss ausgeführt.** Der Dienst meldete inzwischen außerhalb der hier ausgeführten Aufrufe für Eintrag 1 `apply_proposed` (19.10.2026 10:33:04 UTC). Keine Zuschreibung an eine Person und keine Änderung dieser Historie; der betroffene Datensatz war bereits in der ursprünglichen Vorschau nicht vorhanden. Die anderen Geschäftsgruppen außerhalb der ausdrücklich archivierten Fehl-Testofferte sind im Vorher-/Nachher-Vergleich hashgleich.

Ursprünglicher Siebener-Preview `68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7` ist ungültig. Neuer lesender Sechser-Preview `e6752c0dc5a04bc3f729caccdbc3187d87327f6adc0792ccc7f7f722b0a51c8b`, Effekt unverändert `no_business_data_changed`. Keine Wiederverwendung der alten Sammelfreigabe. Der Quellfix erlaubt 1–100 vollständig historische Einträge bei exakt passendem Queueumfang; abweichende Anzahl erfordert explizit den frischen Previewhash. Vollständiges Backup, CAS und `confirmed=true` bleiben Pflicht. Reale Bereinigung bis zur vollständigen Sicherung und neuer Prüfung offen.

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

Rechtsregelsatz im neuen gemeinsamen Vertrag: `AT-2026-10-19` (offizielle RIS-Quellen und Grenzen siehe Abschnitt 31).

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

Nur vollständig isolierte synthetische Datensätze für technische Schreibtests verwenden; der historische Testkunde ist kein Anlass für neue produktive Testaktionen.

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

### Phase E – historische Alt-Konflikte, aktuelle Auswahl erneut bestätigen

Die ursprüngliche Siebener-Auswahl ist überholt (Abschnitt 5). Kein Abschluss mit altem Hash. Neues Backup.

`preview_legacy_conflict_resolution`

Prüfen:

- frisch gelesene Anzahl und exakte Hashes; ursprüngliche sieben sind jetzt sechs
- `keep_current`
- `no_business_data_changed`

Backup erzeugen.

Hash erneut verifizieren.

Erst dann:

`close_legacy_conflicts`

Keine Geschäftsdatenänderung.

### Phase F – End-to-End-Test

Ausschließlich private synthetische Stores mit klar markiertem Testkunden verwenden, keine produktiven Schreibtests.

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
- Legacy-Preview für sieben sowie 7→6 mit veraltetem/fehlendem/frischem Hash
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
- Zustand vorher: ursprünglich sieben, letzter Readback sechs Review-Fälle (Abschnitt 5)
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

Erledigt: Der falsche 1-EUR-Testentwurf am echten Kunden wurde am 19.10.2026 kontrolliert reversibel archiviert (siehe Abschnitt 4 und CODEX-STATUS Eintrag 44). Nicht neu anlegen oder erneut versenden.

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

Nach erfolgreichem Build und vollständigem neuem Backup ausschließlich eine frisch ausdrücklich bestätigte aktuelle Auswahl über `keep_current` abschließen. Die ursprünglichen sieben sind inzwischen sechs (Abschnitt 5); ihre alte Vorschau/Freigabe nicht wiederverwenden. Voraussetzungen:

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

Erledigt: Der falsche 1-EUR-Testentwurf wurde nach erfolgreicher Abnahme reversibel archiviert, ohne andere Geschäftsgruppen zu ändern. Keine neue Testaktion an einem echten Kunden erzeugen.

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


## 31. Konsolidierter Abschluss – 1.51.0 / 79, 19.10.2026

**Implementierung und Cloud-Abnahme getrennt von Geräteabnahme behandeln. Gesamtauftrag noch nicht vollständig abgenommen:** physischer Pixel/Connector nach Update, reale Legacy-Originalsicherung und neu bestätigter Abschluss sowie echter Gmail-Versandbeleg bleiben offen. Keine Installation, keine echte Testmail, kein erneut erstelltes oder versendetes Hilpert-Angebot. Ein erfolgreicher Originalbuild beweist keinen Datenerhalt auf dem physischen Gerät.

### 31.1 Ausgang, Lieferung und Provenienz

- Tatsächlich aktuelles Remote-main beim Einstieg und abschließenden Fetch: `b2c37a2f909cc8ac6defabe68dbdd3ce6a42db14`, **1.49.1/70**. Neuere operative Cloud-Historie bis `5fbe0a3a8c1f9073dc83f83b37b741a59e935ee4` normal integriert, Merge `d67a4ffe549039df4bff78dc41ee0f27076c680b`. Kein Reset, Desktopimport, neuer Branch, neues Repository oder History-Rewrite.
- Reales Gerät vor/nach Entwicklung weiterhin **1.50.1/77**, Datenbank 4, Snapshot 2, Protokoll 8, 111 Tools. Kein Zugriff auf physisches Pixel über ADB.
- Neuer Release **1.51.0/79**, `at.hausgarten.service`, Protokoll **9**, 117 Tools. Finaler **APK-Quellcommit `5ae296394b5a9103f94421ea04ba01e55d06f8f0`**, Quellhash `b8b68448d1b6971c8cde6ebd7a00c7ccd88ecb6cf15c9a7b0065a9962c05f4da`, Buildtyp release, Quellbaum beim Build unverändert. Späterer Liefercommit enthält ausschließlich Dokumentation und fertiges Artefakt; dessen Hash ist `git log -1` zu entnehmen, nicht der eingebettete APK-Quellcommit.
- APK `downloads/Haus-Garten-1.51.0-79-original-update-kandidat.apk`, **34.504.813 Bytes**, SHA256 **`b06798341ac929d00145f566db26d15377c3472b676e4d96b275a677291cc891`**. Originalzertifikat **`a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`**, ein Signer, v2/v3 gültig, 16-KiB-Ausrichtung bestanden, 411 Assets bytegleich. Ausschließlich `tools/sign_original_apk.py`, vorhandene Environment-Secrets; keine neue Signatur/QA-App.
- Privater, kontoabhängiger Download und vollständige Rückleseprüfung stehen in [downloads/README.md](downloads/README.md). Die frühere interne 78 wird nicht ausgeliefert. Installation ausschließlich als bestehendes Originalupdate nach vollständiger Sicherung und ausdrücklicher Freigabe, ohne Deinstallation/App-Datenlöschung.

### 31.2 Erneute Einordnung sämtlicher dokumentierter Review-Fälle

Die Einordnung betrifft genau den genannten Umfang. „Behoben“ im neuen Code bedeutet keine bereits erfolgte Pixel-Installation.

| Review-Fall | Einordnung | Nachweis / Grenze |
| --- | --- | --- |
| `price_source` Schema/Validator/Persistenz widersprüchlich | **behoben** | Exakt `user_confirmed`, ungültig/fehlend/staler Katalog abgewiesen; gemeinsamer Vertrag und Java/Android/Browser-Regressionsfälle |
| Literale Backslash-n-Sequenzen der früheren Main-Fixes verhindern Java-Build | **behoben** | Echte Zeilenumbrüche, Release/Instrumentation/Lint erfolgreich |
| UI-Katalog grundsätzlich auf 50 abgeschnitten | **nicht reproduzierbar** | Installierter Build77 lieferte bereits 100+56/156 |
| Fehlender Cursor/Hashbindung/Vollständigkeitsvertrag des UI-Katalogs | **behoben** | 156 vollständig, 145 fachliche Pfade, elf explizite Plattformgruppen; 47 geprüfte Quell-Fingerprints, Contract-Gate |
| Falscher 1-EUR-Testentwurf an realem Kunden | **behoben** | Ausschließlich exakt freigegebenen Entwurf reversibel archiviert; Archiv-Readback, Angebote2→1, übrige Geschäftsgruppen hashgleich |
| Test-Provenienz geht bei Validierung/Finalisierung/Archiv/Purge verloren | **behoben** | UI/MCP/Journal bewahren local_only; gemischte Test-/Drive-Sammellöschung vor Mutation abgewiesen; native Export-/Neustartprobe |
| Kostenvoranschläge bisher nur teilweise / unterschiedlich implementiert | **behoben** | Vier Dokumentarten in bestehender Offer-Entität, persistierte Bindungsart/Hinweis/Bestätigungshash, UI/MCP/PDF/Sync-/Migrationstests |
| Stille Bindungsannahme bzw. alte Bestätigung nach Typ-/Textänderung | **behoben** | Bewusste Auswahl/Bestätigung; Änderung invalidiert vorherige Bestätigung, unbekannte Altarten nicht umgedeutet |
| Preisgrundlage berechnet/geschätzt/Katalog nicht nachvollziehbar | **behoben** | Gemeinsame Enums, Faktoren/Erläuterung, explizite Preisfreigabe; fehlende Preise bleiben fehlend |
| Finales PDF aus veränderlichen Ausstellerdefaults / Share gleich sent | **behoben** | Eingefrorener Kontext, identischer SHA-Readback, eigener delivery_status; sent nur nach echtem Providerbeleg |
| Reale Gmail-End-to-End-Abnahme und Kontofreigabe | **weiterer Fix notwendig** | Geräte-/Google-Konto-/API-Freigabe und expliziter echter Versand müssen separat erfolgen; synthetischer Belegtest bestanden, keine reale Mail versendet |
| 540-EUR-Angebot historisch nur als Mailtext, kein App-PDF belegt | **veraltet** | Historischer Zustand bleibt dokumentiert; nicht neu erstellt, finalisiert oder erneut versendet; kein PDF nachträglich behauptet |
| Produktive Laptop-/Web-Runtime für Kernaktionen erforderlich | **veraltet** | Bereits vorhandene direkte Pixel/Drive-Engine wiederverwendet; reale Capabilities/Readback und isolierter Provider-Rundlauf, kein Laptop-Fallback |
| `offers_v1`/Katalog/Business Settings fehlen operativ | **veraltet** | Bereits im tatsächlichen Build77 vorhanden, neue Folgeaktionen über offer_workflow_v2; Daten nicht neu erfunden |
| `SYNC_COMPONENT_UPDATE_REQUIRED` / `ACTION_ID_CONFLICT` als zwei aktive Testfehler | **veraltet** | Nicht mehr in tatsächlich gelesener Queue; fail-closed Schutzregressionen erhalten |
| Zyklus „erst Drive-Sync abschließen, dann Konfliktbackup“ | **behoben** | Dedizierter preview-/hashgebundener Backup-Pfad, synthetische sieben Keep-current-Abschlüsse ohne Geschäftsänderung, keine Sicherungsabkürzung |
| Reales Legacy-Backup „Ungültige Foto-Dateireferenz“ | **reproduzierbar** | Auf Build77 tatsächlich fehlgeschlagen; neuer sicherer exakter Legacy-Dateiweg positiv/negativ im Android-Provider getestet, echte Originalverfügbarkeit offen |
| Exakt sieben historische Reviews noch aktiv | **veraltet** | Aktuell sechs, Historie15; unabhängiger apply_proposed-Beleg für bereits zuvor fehlenden Datensatz. Keine Legacy-Auflösung durch diesen Auftrag |
| Reale historische Reviews vollständig sicher abgeschlossen | **weiterer Fix notwendig** | Sechs offen; neuer Preview/Backup/ausdrückliche neue Bestätigung nach Geräteupdate erforderlich, keine Wiederverwendung des alten Siebener-Hashes |
| Feste Siebener-Konstante blockiert frisch bestätigte Sechser-Auswahl | **behoben** | 1–100 historische Einträge, exakter Queueumfang; 7→6 stale/ohneHash FAIL, neuerHash PASS, zusätzliche Aktion FAIL |
| Owner-Beleg transferred, aber confirmation_scope=local_only | **behoben** | Echte aktuelle oder kompatible alte Uploadbelege zählen, applied_on_pixel allein nicht; nativer Positiv-/Negativtest |
| Katalog-UI: freie Pricing-Enum / Zurück-Klick als weitere Seite | **behoben** | Gemeinsamer Picker, leerer Preis null, expliziter Event-Adapter, Browser-Regressionsfall |
| Schmale Dokumentliste quetscht Titel | **behoben** | Gemeinsame gestapelte Aktionen, sichtbare Titelbreite/Zeilenhöhe bei320/360/426px und150% geprüft |
| Version/Build/Quellstand nicht eindeutig; main70 älter als Pixel77 | **behoben** | Neuere Cloud-Historie erhalten, Release79>77, tatsächlicher SHA/Quellhash/Buildtyp/Protokoll im Fingerprint |
| Physischer Connector zeigt neuen Quellumfang noch nicht | **weiterer Fix notwendig** | Noch111/Protokoll8; gesichertes freigegebenes Update und Refresh derselben Verbindung nötig, keine Quellcode-Freigabe ersetzt Geräteinstallation |
| Kamera, reale Kalender-/Wetter-/Google-Freigaben und Hintergrundbetrieb | **weiterer Fix notwendig** | Automatisierte Originalcode-/Browser-/Providerregressionen bestanden; physische Aufnahme, Sensorik, Google-OAuth, Kalender und Wetter nicht bestätigt |

### 31.3 Angebot / Kostenvoranschlag – gemeinsamer Ende-zu-Ende-Pfad

Keine zweite Entität, UI oder Estimate-Toolfamilie. `fixed_price`, `time_material`, `estimate_binding`, `estimate_nonbinding` verwenden dieselben Regeln, Status, Positionen, Preisquellen und privaten Dokumentdateien. `draft` → `validated` → `finalized`; Änderungen am geprüften Entwurf setzen Validierung zurück, finale Dokumente bleiben unveränderlich. `delivery_status=share_prepared|sent` ist separat. Angebot→Auftrag nur ausdrücklich, keine automatische Rechnung. Zentrale editierbare Aussteller-/Steuer-/Zahlungs-/Rechtstexte, keine erfundenen Preise oder Kunden.

Neue MCP-Operationen: `get_offer_contract`, `run_self_test`, `create_and_validate_offer`, `validate_offer_draft`, `prepare_offer_delivery`, `confirm_offer_delivery`. Alle bisherigen111 erhalten, sechs ergänzt; vollständige Katalogprüfung zeigt keine fehlenden bisherigen Werkzeuge. Gemeinsame Fehlerfelder, erlaubte Enums/Datentypen/Zahlengrenzen, Write-Gates, Request-Idempotenz, aktuelle Hashes und Bestätigungen geprüft. Self-Test ist read-only und meldet PASS/FAIL/NOT TESTABLE statt produktiver Testschreibvorgänge.

Kostenvoranschläge erhalten explizit gespeicherten Bindungsmodus, bestätigten Hinweistext und dessen Hash. KV-/Angebotsnummern bleiben vom zentralen Rechnungsnummernstand getrennt. Beide KV-Arten mit Netto-/Steuer-/Bruttosummen, Kleinunternehmer-/Standardsteuer, Pflichtfeldern, PDF-Metadaten/Readback, Migration, Idempotenz, stale Hash, Bearbeitung, Duplikat, Status und direkter Journal-/Neustartverarbeitung getestet. Alte Snapshots ohne offers bleiben gültig; unbekannte Dokumentarten werden nicht still umgedeutet.

Offizielle RIS-Quellen tatsächlich am19.10.2026 gelesen: [ABGB§1170a](https://www.ris.bka.gv.at/NormDokument.wxe?Abfrage=Bundesnormen&Gesetzesnummer=10001622&Paragraf=1170a), [KSchG§5](https://www.ris.bka.gv.at/NormDokument.wxe?Abfrage=Bundesnormen&Gesetzesnummer=10002462&Paragraf=5). Regelversion `AT-2026-10-19`. Verbraucherstatus und Bindungsart bewusst bestätigen; technische Regeln sind keine automatische Rechtsprüfung aller Klauseln oder FAGG-Ausnahmen. Bestehende Texte zentral editierbar erhalten.

### 31.4 MCP, UI-Katalog und tatsächliche Berechtigungen

- Quell-/Emulatorkatalog: **117**, davon52 read/65 write, Protokoll9, Hash **`235f683d99d5170801d1e34c19ad386d9c4acf68b60a6992f72a3e5d2ea14314`**.
- Echter Pixel/ChatGPT-Connector: **111**, Protokoll8, Hash **`ae7cb69f7994ae8141d2d8c925594982f40236921d65abb3739eee270b5b312f`**, ready/pixel_business_write/write_enabled=true. Reale autorisierte Archivoperation belegt erreichbar; keine Behauptung, sämtliche destruktiven Tools am Produktivbestand ausgeführt zu haben.
- UI-Katalog: **156** semantische Aktionen, **145** fachliche MCP-Pfade, **11** begründete Plattform-/Darstellungsgruppen. Hash **`34c9cdd7494658bb1a3ec0a71db267e1ffa1044fd31e6a735c5be2ce0c8a0d91`**. Vollständig100+56 abrufbar, Cursor/Offset/Limit und Hashbindung getestet. Contract-Abdeckung ist keine physische Ausführung jeder Aktion.
- Elf Grenzen: erstmalige Android-/Google-/Drive-Zustimmung; Kalenderkonto-Ersteinrichtung; privater Wetterschlüssel; Tunnel-Vertrauensanker/Stop; Android-Benachrichtigungs-/Standort-/Kamera-/Akku-Dialoge; physische Kamera/Galerieauswahl; GPS-Messung; fremde Teilen-/Telefon-/Karten-App; vollständiges Gerätebackup/Installation; flüchtige Darstellung; ungespeicherte lokale Eingabe. Fachliche Alternativen und konkrete Gründe stehen je Aktion im bestehenden `ui-mcp-actions.json`.
- Nach geschütztem Originalupdate denselben Tunnel/Connector aktualisieren; tatsächliches tools/list, `get_capabilities`, `integration_health`, Fingerprint und Hash neu vergleichen. Kein neuer Connector/Desktop-Tunnel. Google-Konto-/API-/OAuth-/Dokumentbaumrechte bleiben unabhängig von ChatGPT „Allow all actions“.

### 31.5 Tests und Datenintegrität

- **24 JVM-Suiten PASS**, darunter121 neue gemeinsame Estimate-/Preis-/Status-/Versandbeleg-Verträge,1032 UI-/MCP-Paritätsprüfungen und173 Papierkorb-/Backup-Policy-Prüfungen. `HG_JSON_JAR=… python3 tools/run-followup-java.py`.
- **273 Python-Tests PASS**, `python3 tests/run_quality_suite.py --exclude-qa-signing`. QA-Key-Erzeugung bewusst nicht ausgeführt; deren Tests/Code erhalten. Keine Ersatzsignatur für die Abnahme.
- **28 Browser-Suiten PASS**, lokaler Chromium/Playwright mit lokalen Assets;130 Angebote paginiert/gefiltert,105 Foto-Metadaten/30er-Lazy-Seiten,21 Dateien nacheinander einschließlich fehlerhafter Datei, sichtbare Titel/Zeilenhöhe bei320/360/426px und150%. Keine Pixel-Leistungsmessung.
- **Endgültige79: 133 native Android-/SQLite-/MCP-/Providerproben PASS,0 Fehler**, einschließlich beider KV-Arten, aller vier Dokumenttypen, PDF/Chunk/SHA, Test-Provenienz/kein Drive-Export, Journal-/Retry-/Neustartpfade, echter LocalStore, synthetischer Gmail-Belegtransport sowie vollständiger Legacy-Sicherung und sieben synthetischer Keep-current-Abschlüsse ohne Geschäftsänderung. Native Provider-Upload/Readback, falscherReadback/Netzfehler/Retry/Hashgrenzen und alte/neue Übertragungsbelege geprüft.
- Anschließend auf derselben endgültigen79-APK **In-place-Update PASS** (bestehender synthetischer Snapshot/IDs/Beziehungen/Queue/Einstellungen/Tunnelkonfiguration/aktiver Timer erhalten), **echte native WebView-Navigation PASS** und **frischer isolierter Originalcode-Datenbereich PASS** (leer, keine Credentials/automatische Netzwerkverbindung). Ausschließlich expliziter Cloud-Emulator5582 mit AVD-/QEMU-Prüfung und exklusivem Lock; keine Pixel-ADB-Verbindung, kein bestehendes Profil gelöscht.
- Gradle offline Release/AndroidTest/**Lint PASS**,33 nichtfatale Lint-Hinweise bleiben ausgewiesen; kein fehlerfreier Warnungsstand behauptet. Shared-Contract-/Pflanzen-/UI-Fingerprint-Gates, APK-Privacy, Assetvergleich, DEX-Buildfingerprint, apksigner und zipalign PASS.
- Signiert ausschließlich über vorhandenen Originalweg. Signier-Tempverzeichnisse0, getrackte Schlüsseldateien0, keine Secrets ausgegeben/gespeichert;27 eigene veraltete interne78-APKs entfernt, frühere gelieferte74–77 und Emulatorprofile erhalten. Working Tree enthält nur beabsichtigte Lieferdateien, keine produktiven Testdaten.

Tests ausschließlich synthetisch/isoliert; keine technischen Angebote an realen Kunden, keine Testmails oder produktiver Test-Sync. Einzige reale Änderung dieses Auftrags: ausdrücklich freigegebenen falschen 1-EUR-Testentwurf reversibel archiviert. Frisch rückgelesen: Kunden53, Aufträge3, Arbeiten45, Rechnungen95, Fotos25, Bausteine33, Angebote1, Termine0; außer Angebotsgruppe alle Gruppenhashes zum Ausgang identisch. Existierende Beziehungen, Medien und ausstehende Aktionen nicht entfernt.

Fotoarchitektur wiederverwendet: private content-addressed Dateien, getrennte Metadaten/Thumbnails, automatische Qualitätsprofile, 30er-Lazy-Pagination, keine Base64-Originale im Snapshot. Mehr als10 und105 synthetische Metadatensätze/20+-Importpfade in erhaltenen Browser-/Android-Regressionen; keine Pixel-Kameramessung daraus ableiten. Getrennte Medienübertragung/Hashreadback, Fehler-/Retry-/Neustartpfade ohne Duplikate und ohne Blockieren unabhängiger Geschäftsaktionen geprüft.

### 31.6 Reale Sync-/Legacy-Abnahme und offene externe Grenzen

Letzter realer Readback19.10.2026 11:03:55 UTC: **pending_upload0, review6, Historie15**, `drive_sync_failed=false`, keine technischen Syncfehler, Zustand `review_required`, Provider-Readback, **cloud_confirmed=false**. Keine unabhängige zentrale Cloud-Bestätigung behauptet. Ursprünglicher Backupversuch im alten installierten Build scheiterte an Legacy-Fotoreferenz; deshalb keine Legacy-Auflösung durch diesen Auftrag. Die aktuelle Sechser-Vorschau ist kein Ersatz für vollständige Sicherung oder eine neue ausdrückliche Sammelbestätigung.

Offen: tatsächliche Originaldateien und vollständiges Backup am aktualisierten Pixel prüfen, frisch bestätigte Keep-current-Auswahl sicher abschließen und neue Queue/History/Providerbelege zurücklesen; unabhängige Gerätesicherung und ausdrückliche Installationsfreigabe; physischer Update-/Datenerhalt-/Kamera-/Connector-Endtest; Google-Gmail-Zustimmung und API-Leserecht für echte Versandbelege; reale Kalender-/Wetterkonfiguration und Android-Hintergrund-/Akkuverhalten. Cloud-Egress, Google-Kontorechte und Providerquoten können zusätzlich begrenzen; Quellcode ersetzt keine dieser Freigaben. 401 nicht umgehen, temporäre Fehler mit begrenztem Backoff/Retry-After. Keine Dauerverfügbarkeit zusichern.

### 31.7 Geänderte Bereiche und bestehende Nachweise

- Gemeinsamer Vertrag/Generator: `offer-contract.json`, generierte `OfferContract.java`/`offer-contract.js`, `tools/sync_offer_contract.py`; bestehende `OfferRules`, `OfferTools`, `OfferJournalRules`, historische `offers.py` kompatibel vereinheitlicht.
- MCP/UI/Isolation: `McpSchema`, `PixelMcpReadTools`, `PixelMcpOwnerTools`, `PixelMcpEntityWriteTools`, `PixelMcpBusinessTools`, `PixelOwnerAccess`, `MainActivity`, `TestDataRules`, `InvoiceDraftRules`, `InvoiceIssueRules`, `OwnerJournalRules`, `offers-ui.js`, `owner-ui.js`, `mobile.html`, `design-concept.css`, `ui-mcp-actions.json` und Quellreview-Gate.
- Dateien/Provider: `BusinessDocumentStore`, `OfferDeliveryClient`, `MailAccount`, `DriveConnectionActivity`, `DriveDirectFiles`, `DriveSyncCoordinator`, `DriveJournalRules`, `PixelDriveState`, `PhotoAttachmentStore`, `LegacyBackupPolicy`, `LegacyConflictBackup`.
- Version/Fingerprint: `android/app/build.gradle`, `BuildProvenance`, `tools/check_release_baseline.py`. Tests: neue `EstimateContractTests`/`EstimateNativeAcceptance`, erweiterte bestehende Offer-/Trash-/Parity-/Daily-/Provider-/Planning-/Lifecycle-/Instrumentation-/Browserfälle und Runner. Keine operative Test-App-Codebasis oder neue Dossiers.
- Zentrale Dokumentation hier, `CODEX-STATUS.md`, bestehender MCP-Nachweis und `downloads/README.md`/`SHA256SUMS`; fertige Original-APK ausdrücklich von `.gitignore` ausgenommen. Vollständige Pfadliste: `git diff --name-only d67a4ffe549039df4bff78dc41ee0f27076c680b..HEAD`.
