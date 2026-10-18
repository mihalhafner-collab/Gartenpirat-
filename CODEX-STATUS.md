# CODEX-STATUS

Stand: 18. Oktober 2026  
Repository: `mihalhafner-collab/Gartenpirat-`

> Diese Datei ist der fortlaufende technische Arbeitsnachweis für Codex Cloud.  
> Regeln: `AGENTS.md`  
> Vollständiger Auftrag: `CODEX-AUFTRAG-GARTENPIRAT.md`

## Aktueller Ausgangsstand

- Auftrag: **P0 Pixel/Drive Cloud-only Sync abschließen**
- Operativer Zielpfad: Pixel ↔ Google Drive direkt
- Entwicklungsweg: ausschließlich Codex Cloud
- Repository-Version: 1.49.4 / 73 (originalsignierter Papierkorb-/Bedienungs-Kandidat, Geräte-/Drive-Abnahme offen)
- Live-Pixel bei letzter Prüfung: 1.49.3 / 72 (erneut über MCP bestätigt)
- Live `offers_sync_ready`: true
- Live `service_catalog_sync_ready`: true
- Live `cloud_confirmed`: false
- Live Queue vor der aktuellen MCP-Testphase: 8 Review-Aktionen, 0 Uploads; 1 archivierte Prüfung. Sieben historische Fälle unverändert, zusätzlich ein älterer fehlgeschlagener Archivierungsauftrag. Aktuelle synthetische Testaktionen werden gesondert bilanziert.
- Historischer Zwischenstand nach früheren MCP-Tests: **38 = 8 bestehende Prüfungen + 30 synthetische Test-/Archivaktionen**. Kein neuer Drive-Beleg; lokaler Cleanup ist noch nicht synchronisiert.
- Aktuelle Live-Queue: **51 = 43 Übertragungen + 8 Prüfungen**, 1 historische Entscheidung; Geschäftshash bei lesender Vorher-/Nachher-Prüfung unverändert. Keine neue Schreibaktion dieses Auftrags.
- `get_business_settings`: lesbar; nur bekannte historische Ausstellerfelder, Steuer-/Nummernfreigaben nicht bestätigt.
- Quelle: Papierkorb mit endgültiger Einzelentfernung, gelösten Fotozuordnungen und vereinfachter Bedienung in 73 implementiert; live weiterhin 72. Queue aktuell 51 (43 Übertragungen, 8 Prüfungen); konkrete Barriere LEGACY_SOURCE_CHANGED, beide Drive-Stände erhalten. Keine Installation und keine produktive Schreibaktion dieses Auftrags. **Gesamtauftrag NICHT abgeschlossen; neue Geräte-/Drive-Abnahme offen.**

## Arbeitsstatus

| Phase | Status | Ergebnis |
|---|---|---|
| A – Bestand verifizieren | PASS | Regeln, Issue #1, Historien und Live-MCP geprüft; keine Betriebsdatenänderung |
| B – produktive Laptop-Abhängigkeit entfernen | PASS (Analyse/Zielpfad) | Abhängigkeiten ermittelt; native Ablösung in C in Arbeit, noch keine operative Abnahme |
| C – Pixel/Drive-Sync vervollständigen | PASS Host / NOT TESTABLE Gerät | Native Migration, Gesamtstände, Handler, Belege, Finanzabschluss; Geräte-/Google-Abnahme offen |
| D – 2 technische Testfehler bereinigen | Neue Live-Beobachtung / Belegabnahme offen | Beide früheren technischen IDs jetzt nicht mehr aktiv, 1 Konfliktprüfung archiviert; Übergang vor aktueller Testphase, kein Eingriff durch diese Tests |
| E – 7 Legacy-Konflikte kontrolliert schließen | Vorschau PASS / Ausführung gesperrt | Genau 7 bestätigt; direkte Drive-Abnahme und neue vollständige Sicherung fehlen |
| F – End-to-End-Abnahme | Host PASS / Live-MCP TEILWEISE, P0/P1 FAIL | 83 Werkzeuge angesprochen; 50/51 lokale Vergleiche PASS, Fotorolle FAIL, Drive-Rundlauf FAIL; Details Eintrag 8 |
| G – neuer Release-Build + Originalsignierung | PASS Kandidat / Gerätefreigabe offen | Originalsignierter 1.49.3/72 geprüft; keine Geräte-/Update-/Datenerhaltabnahme |
| H – Sync vereinfachen / Papierkorb | PASS Cloud / NOT TESTABLE live | Gemeinsame Zustände, Diagnose/Retry, begrenzte Medienabschnitte, sieben Löschbereiche, Restore und kontrollierte Rücknahme; Einträge 9–12 |

## Bekannte technische Queue-Einträge

### Historische 7
- `wrk_mobile_913976ba4bd1453ba75a5468b89c3c32`
- `wrk_mobile_b8445cff3af746718d480fce9be3889a`
- `wrk_mobile_325735fd783c4f5fb3d012ab7969ccfd`
- `delete_change_f82a727e-90ce-4021-a741-2238101b2505`
- `delete_change_b1fc39af-3f04-4baa-81bf-a34a20e76479`
- `customer_change_3b8f4a4bdaca4d5e9b67a4b9b3ac3020`
- `delete_change_9304498d-e90b-47de-be7c-bfd2e627fdf3`

### Technische 2 (historischer Stand vor der aktuellen MCP-Testphase)
- `mcp_owner_b6f1c56ba6534a250f6f5057175f365623155aa656d55fd0342f2517b4a539da` → `SYNC_COMPONENT_UPDATE_REQUIRED`
- `mcp_owner_43e89c1a01b88694f43a944e5c4109cb1a7e1a8c15378ee0c1116779bb1ce54c` → `ACTION_ID_CONFLICT`

Diese beiden IDs sind bei der aktuellen Live-Prüfung nicht mehr aktiv. Der achte bestehende Prüfauftrag ist jetzt `mcp_owner_739bc9fcba337de095f4dbb8beccf5e44f336e735468bd7c2c07399c98252a0e` (`archive`, `INVALID_ACTION`, verknüpfte Daten). In der aktuellen Testphase unverändert erhalten.

## Letzter geprüfter Sicherungsstand

Pixel-Geschäftsbackup:
- Backup-ID: `backup_4ef99a6925a1c66e0af5c6d10721fd5993cd706bfacef5aa7b1e33d2052cae69`
- SHA-256: `4683945a1d959e34aeea818768eb8fc618ef2a9cc424e76054c05072e278cb6b`
- State Hash: `02c7a22f709b59765e96453acc778f7fae84217bc2772ae4a49ee4e6ee84cd5f`

Drive-Ausgangssicherung:
- `HausGarten-Laptop-before-v1.49-sync-2026-10-18.json`
- SHA-256: `8556108cafc9ad47a6ee82fa3ce49d01d7a057b42407c8098539dc3825f18cc3`

## Fortlaufender Arbeitsnachweis

### Eintrag 9 – Sync vereinfachen / Phase A Bestandsprüfung

- Auftrag: Sync überarbeiten und vereinfachen, einfache Löschfunktion mit selbstständiger Verarbeitung nach definierten Regeln. Ausgang 426159c, sauberer Arbeitsbaum, vorgesehene Cloud-Branch, Pflichtvorfahr vorhanden; Remote bestätigt, main unverändert. Live ausschließlich gelesen: Build 71 / Protokoll 4, Geschäftsbestand hashgleich, Queue weiterhin 38 (30 Übertragungen, 8 Prüfungen).
- Status: PASS Analyse; keine produktive Mutation. Aktuelle Regeln, Auftrag, Reparaturnachweis, zentrale Sync-/Owner-Dokumentation und Issue #1 gelesen. Keine zweite Runtime oder Datenbank.
- Abnahme: einheitliche Sync-Zustände für UI/MCP/Jobs; expliziter Retry bleibt auch nach pausierten automatischen Versuchen möglich; Transportfehler mit konkretem nächsten Schritt; fachliche Prüfungen blockieren unabhängige Änderungen nicht; Fotojournal/Hash/Vorschau erhalten dieselbe Rolle; vollständiges Capabilities-Schema. Neue Löschanforderungen transaktional, idempotent, versionsgeschützt in bestehenden wiederherstellbaren Archivstrukturen verarbeiten; keine Kaskade und keine physische Medienlöschung. Geschützte Belege und verknüpfte Datensätze bleiben erhalten. Rückgängig erzeugt eine geprüfte Wiederherstellung, löscht keine schon übertragbare Aktion. Auch Angebotsentwürfe und inaktive Leistungsbausteine im gemeinsamen Papierkorb. Alte Löschaktionen bleiben gesondert geschützt.
- Tests ausschließlich synthetisch: Offline → Retry → Provider-Readback, Queue/Receipts nach Wiederholung und Neustart, parallele Änderung, Beziehungs-/Finanzschutz, Foto-Rollen- und Schemaregression, Papierkorb/Restore, kleine Bildschirmbreiten. Native Instrumentierung nur ausführen, wenn eine zulässige isolierte Geräteumgebung verfügbar ist; kein operativer Pixelzugriff außer MCP-Lesen, keine Installation.
- Nächster Schritt: gemeinsame Sync-Steuerung/Diagnose und unveränderte Fotojournale, danach Papierkorb und UI.

### Eintrag 10 – Sync vereinfachen / Phase B gemeinsame Steuerung

- Status: PASS synthetische JVM und Build, Gerät/echter Drive noch NOT TESTABLE. SyncPolicy führt dieselben Zustände für UI, MCP und Jobsteuerung. Versionierte Diagnose enthält Fehlercode, Phase, Versuchszähler und nächsten Schritt. Expliziter Retry setzt einen pausierten Versuchshaushalt einmal pro Request zurück; Replay erfindet keinen Erfolg. Automatische Auslöser beachten Backoff/Rechte und maximal acht Fehlversuche.
- Fachliche Prüfungen und erfolgreicher Transport werden getrennt ausgewiesen. Fotoübertragung pro Lauf auf vier eindeutige Inhalte/etwa 8 MiB begrenzt (ein einzelnes größeres Original darf fortschreiten); erfolgreiche Dateien werden wiederverwendet, weitere Abschnitte nach Fortschritt geplant. Foto-Metadatenjournale behalten Rolle und Hash; Capabilities behalten das vollständige chunk_base64-Schema.
- Tests: 16 JVM-Suiten einschließlich 69 neuer Sync-/Löschprüfungen bestanden; Release-Build, Android-Testquellen-Kompilierung und Lint bestanden. Native Foto-/Undo-Abnahme ergänzt, nur kompiliert. Vorläufiger Build 1.49.3/72 unsigned, noch keine Signatur/Installation. UI-/Gesamtregression und Detailprüfung der neuen Löschpfade folgen.

### Eintrag 11 – Sync vereinfachen / Phase C Papierkorb und automatische Verarbeitung

- Status: PASS synthetische Regeln/erste Browserprüfung; keine produktive Löschung. Gemeinsame DeletionRules für UI/MCP/Journal: sieben Bereiche, Vorschau und CAS vor Queue-Eintrag, Prüfung nochmals beim Abgleich. Neue MCP-Werkzeuge preview_delete/delete_entity, vorhandene Namen erhalten (additiv 85, Protokoll weiterhin kompatibel 4). list_archive nach Entität/ID filterbar, stabil paginiert. Gemeinsamer Papierkorb in Mehr/Sync, Wiederherstellen, Löschknöpfe auch bei Angebotsentwürfen und Leistungsbausteinen; Kunden/Fotos nutzen denselben Weg.
- Regeln: zulässige Löschung im bestehenden owner_archive, keine Kaskade, Originaldateien erhalten; Kunden/Bausteine deaktiviert. Festgeschriebene/nummerierte Rechnungen und finale/angenommene Angebote geschützt, abhängige Aufträge/Arbeiten blockiert. Rückgängig hängt eine neue Restore-Aktion an; Originalaktionen und Hashes bleiben erhalten. Eine gescheiterte Archivierung kann nach erneutem Hashvergleich ausdrücklich mit keep_current zurückgenommen werden; nur diese sichere Variante erlaubt, Verlauf und Daten bleiben erhalten. Frühere Löschaufträge werden nicht automatisch ausgeführt oder quittiert.
- Tests bisher: 88 neue JVM-Vergleiche bestanden; 11 neue UI-Prüfungen mit 65 synthetischen Papierkorbeinträgen bestanden (weitere Schutzprüfung ergänzt). 266 Python-Regressionen bestanden. Ein bestehender UI-Test fand den vorübergehend ausgeblendeten bisherigen Sync-Knopf; Bedienung auf den einen bestehenden Knopf zurückgeführt, gezielte Wiederholung bestanden. Kein App-/Datenbankneubau, keine Betriebsdatenmutation. Endgültige Regression und Kandidatenprüfung folgen.

### Eintrag 12 – Sync vereinfachen / Phase D Regression und Original-Kandidat 72

- Ausgang 426159c auf der vorhandenen Cloud-Branch, main weiterhin a38765d. Implementierung und Kandidat werden zusammen mit diesem Eintrag in derselben Branch gesichert. Keine Desktopübernahme oder zweite Runtime. Geändert: gemeinsame native Sync-/Löschregeln, Coordinator/Jobs/Store/Owner-Adapter, Sync-/Papierkorb-/Angebots-/Fotooberfläche, Regressionen/Runner sowie bestehende zentrale Dokumentation und Downloadliste.
- Ergebnis **PASS Cloud, NOT TESTABLE neue Live-Abnahme**. 266 Python-Tests (37 ältere Schlüssel erzeugende QA-Signierungstests ausdrücklich ausgeschlossen); 16 JVM-Suiten/731 Vergleiche, davon 88 neue Sync-/Löschprüfungen. 25 Browser-Suiten final bestanden; die korrigierte Erwartung „ein vorhandener Sync-Knopf“ im neuen Papierkorbtest separat wiederholt: 12/12 PASS. Runner nimmt alle vier neueren UI-Suiten künftig mit. Echte native Foto-/Undo-/Provider-Instrumentierung ergänzt und kompiliert, nicht ausgeführt. Release-Build/Lint PASS: 0 Fehler, 15 Warnungen, darunter synchrone technische SharedPreferences-Statusschreibvorgänge und bestehende Plattformhinweise.
- Verhalten: ein zentraler Abgleichknopf, Übertragungen und Prüfungen getrennt; gleicher Zustandsautomat für UI/MCP/Jobs; wiederholte Requests erhalten Belege. Automatische Versuche halten Backoff ein, höchstens acht Fehlversuche je explizit gestartetem Budget, Retry-After begrenzt; ein neuer ausdrücklicher Versuch startet erneut, ein identischer Replay setzt das Budget nicht fortlaufend zurück. Fehlende dauerhafte Android-Ordnerrechte führen zur erneuten Auswahl am Gerät; keine Freigabe durch Code erteilt. Technische Details nennen Fehlercode, Phase und nächsten Schritt; erfolgreiche Provider-Rücklesung bleibt von Cloud-Abschluss getrennt.
- Medien: je Durchgang höchstens vier eindeutige Foto-Inhalte/etwa 8 MiB, ein einzelnes größeres Original darf übertragen werden. Gleicher Hash innerhalb eines Durchgangs nur einmal versucht, bestätigte Inhalte wiederverwendet, separate Vorschaubilder erhalten. Weitere Abschnitte nach Fortschritt; einzelne Medienfehler erhalten die betroffenen Aktionen und blockieren unabhängige Geschäftsdaten nicht. Keine Original-Base64-Daten in Geschäftssnapshots. Canonical Foto-Metadatenrolle wird nicht aus dem älteren Dateicache überschrieben. Schemafeld chunk_base64 bleibt vollständig registriert.
- Löschregeln: Kunden, Aufträge, Arbeiten, Fotos, lokale Rechnungsentwürfe, Angebotsentwürfe und Leistungsbausteine. Löschanforderung wird als geprüfte Archivaktion automatisch im bestehenden Journal verarbeitet; UI-Vorschau plus atomarer Hash-/Beziehungscheck, erneute Prüfung beim Sync. Kunden und Bausteine werden deaktiviert. Verknüpfte Aufträge/Arbeiten sowie gestellte/nummerierte Rechnungen und finale Angebote bleiben geschützt. Keine Kaskade, keine automatische physische Original-/Dateilöschung. Papierkorb/Restore benutzen owner_archive; Rollen, IDs und Originale bleiben erhalten. Gescheiterte Löschanforderung nur ausdrücklich hashgebunden mit keep_current zurücknehmen, Verlauf in owner_conflict_history; niemals Infrastruktur- oder ACTION_ID_CONFLICT dadurch übergehen.
- Reale Schlusslesung über MCP: weiterhin **1.49.2/71, 83 Werkzeuge**, 38 Aktionen = 30 frühere synthetische Test-/Archivaktionen + 8 bisherige Prüfungen. Gespeicherte Geschäftssammlungen unverändert, Hash `0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b`; Counts 51/2/173/95/12/32/1. In dieser Entwicklungsphase ausschließlich gelesen, keine zusätzlichen Testaktionen und kein Cleanup an produktiven Queues. Neue Quelle registriert additiv 85 Werkzeuge (preview_delete/delete_entity), Protokoll 4 bleibt kompatibel; neue Werkzeuge noch **nicht** im installierten Connector bestätigt.
- Öffentlich geprüfter Kandidat: `downloads/Haus-Garten-1.49.3-72-original-update-kandidat.apk`, **at.hausgarten.service, 1.49.3/72, 34.410.051 Bytes**, minSdk 26, target/compile 37. Höher als der tatsächlich gelesene installierte Code 71. Unsigned SHA256 `df27ef215ddc210e3a923e184dde14bb4cbc405af5e3f42fbc658688139ac9df`; signiert SHA256 **`158fc4cf676ae0e42ef45d661d5575fec2ababd1911177db5a4dc259457d18a8`**.
- Signierung: vorher ausschließlich Vorhandensein der fünf HG_ORIGINAL_*-Variablen geprüft. Genau ein Originalsignierlauf über tools/sign_original_apk.py; keine Werte ausgegeben/gespeichert und kein Ersatzschlüssel. Zertifikat SHA256 **`a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`** stimmt mit Originalvorgabe überein; apksigner verifiziert v2/v3, genau einen Signer; zipalign 16 KiB PASS. Temp-Verzeichnisse danach 0, private Schlüsseldateien im Repository 0. 403 APK-Assets bytegleich mit der Quelle, Privacy PASS, operative Seedzeilen 0. Keine Installation und kein Vergleich mit dem Zertifikat der installierten APK.
- Bereitstellung im privaten Repository und [privater Drive-Download](https://drive.google.com/file/d/1zS-mRZZV5V20mq0404CbqMvfcovXbO6B/view), keine öffentliche Freigabe. Connector liefert nach erneutem vollständigem Dateiabruf eine herunterladbare Dateireferenz und 34.410.051 Bytes. Kein unabhängiger Vergleich der erneut heruntergeladenen Drive-Dateibytes behauptet.
- Offen: tatsächlicher Drive-Transportfehler des installierten 71 bleibt ohne neuen Diagnoselauf unbestimmt; nach vollständiger Sicherung und ausdrücklicher Installationsfreigabe 72 abnehmen. Danach die bestehenden 30 Testaktionen samt möglichem alten Fotorollen-/Archivvergleich prüfen, nicht umschreiben oder löschen; acht Altprüfungen bleiben geschützt. Alte sieben ausschließlich im bestehenden Backup-/Preview-/keep_current-Workflow mit gesonderter Bestätigung. Nach freigegebenem Update private MCP-Verbindung neu starten und tools/list/get_capabilities auf 85 Namen/aktuelle Schemata prüfen; nötigenfalls ChatGPT-Verbindung neu verbinden. Kein administrierbares Connector-Manifest im Repository. Kamera, Hintergrundwechsel, echte Medien-Recovery, native Transaktionen sowie Update/Datenerhalt bleiben separat nachzuweisen. Originalsignierung allein ist kein Update- oder Datenerhalt-Test.

### Eintrag 8 – echte MCP-Abnahme auf dem installierten Pixel (18.10.2026)

- Auftrag: „Teste App vollständig per mcp Schnittstelle“. Cloud-Ausgangspunkt `9509df6919c65ed913e1864965e087d8dc814dee`, unverändert auf `codex/original-signing-cloud-setup-20261018`; Pflichtvorfahr vorhanden. main unverändert (`a38765d586c3ea02b999e770e5ecb22b34642962`). Nur diese zentrale Statusdatei geändert; kein neuer Build, keine Installation, kein ADB, keine Schlüsselverwendung.
- **Gesamtergebnis: TEILWEISE BESTANDEN, KEINE vollständige Abnahme.** 83/83 Werkzeugnamen real angesprochen; bei 67 mindestens eine erfolgreiche Antwort, 16 nur Schutz-/Fehlerfälle. Eine erfolgreiche Antwort allein gilt nicht als erfolgreicher Fachprozess. 51 explizite fachliche Vergleiche: 50 PASS, 1 FAIL (Fotorolle). Zusätzlich Drive-Rundlauf FAIL und Capabilities-Schema inkonsistent.
- Tatsächlich installiert laut erneutem `get_capabilities` und `integration_health`: at.hausgarten.service, 1.49.2/71, DB 4, Snapshot 2, Protokoll 4, `pixel_drive_v1`, ready/pixel_business_write/write_enabled=true. Owner-Werkzeuge sowie Rechnungs-/Angebotswerkzeuge im Connector sichtbar. Kataloghash `ad4c1af1cd1f5b58db4ba18563d1165748c132b91099a929bac6a0980a5db8c6`; Hashabweichung allein nicht als Ursache eines Refresh-Problems interpretiert.
- Live-Start: 8 aktive Prüfungen, 0 Uploads, 1 archivierte Konfliktprüfung. Sieben historische Fälle und ein früherer `INVALID_ACTION`-Archivierungsauftrag; keine Auflösung ausgeführt. Der Übergang von früher 9 auf 8 fand vor dieser Testphase statt.
- Neue lokale Geschäftssicherung vor Teständerungen: `backup_9778e41a06e30d409bb8281417410ca5344f07aeae60367e1709bea5275b75f6`, 372514 Bytes, SHA-256 `3a4d578b87ec40d3a4ce83e5e277664888aecbe75b4a3b6f92ff8925609eb56b`. `inspect_backup`, Dateimetadaten, Liste und idempotente Wiederholung geprüft. Scope Geschäftssnapshot, offene Aktionen und lokal vorhandene Medien/Dokumente; **keine vollständige Gerätesicherung und keine neue vollständige Drive-Sicherung**.
- Testphase: 18:45–19:05 UTC; rein synthetische Datensätze, Namespace `mcpE2E_20261018_mve6e3g8`, sowie vorgesehener technischer Testkunde. Keine echten Kunden, Arbeitsleistungen, Preise oder Geschäftsrechnungen verändert. Zwei anfänglich unvollständige Test-Aufrufe auf Service-Wrapper wurden mit korrektem `entry.id` wiederholt; diese Parameterfehler sind kein App-Befund. Zu große Listenlimits wurden abgewiesen und anschließend mit ≤100 gelesen. Keine Produktionsinhalte als Fixtures/Logs gespeichert.

#### Abgeschlossene lokale Testphase

- Kunde neu/lesen/gezielt ändern, Zusatzfelder zusammenführen, Replay, stale Hash und Archiv/Wiederherstellung mit Original-ID bestanden.
- Auftrag neu/lesen/ändern, Replay, Request-ID mit abweichendem Inhalt (`REPLAY_MISMATCH`), stale Hash und lokales Archiv bestanden.
- Arbeitsaufzeichnung: 0 Stunden in beiden Create-Wegen abgewiesen, 0,01 h gespeichert, 0,02 h gezielt geändert, stale Hash blockiert. Manuelle Abrechnungsmarkierung an/aus getrennt von Rechnung und b.b.; identische Einsatz-/Empfängerzuordnung geprüft. Kein Wechsel zu einem fremden Kunden getestet.
- Timer: IDLE → RUNNING → PAUSED → RUNNING → STOPPED_PENDING_REVIEW → RUNNING → STOPPED_PENDING_REVIEW → CANCELLED → IDLE; Wiederherstellung derselben Testsitzung als PAUSED, erneut verworfen, abschließend IDLE. Keine zusätzliche Timer-Arbeitsaufzeichnung. Übernahme einer unbekannten Sitzung mit SESSION_CONFLICT blockiert; erfolgreicher `complete_work_session` nicht ausgeführt.
- Rechnungsentwurf neu/lesen/ändern (Zahlungsziel 7 Tage), Replay gleiche ID, stale Hash abgewiesen. Rein synthetische Null-Euro-Testposition; keine reale Preisbehauptung. Status bleibt Entwurf; Steuerstatus bleibt unconfigured. `confirmed=false` für Abschluss am Connector blockiert. Entwurf ohne ausgestelltes Original-PDF nicht zum Teilen freigegeben; Entwurf weder als bezahlt markierbar noch stornierbar. Keine Nummer reserviert, Rechnung ausgestellt oder versendet.
- Angebotsentwurf neu/lesen/ändern/Replay/stale Hash und alle vier Dokumentarten gespeichert. Validator meldet fehlenden Preis/Preisquelle, Aussteller-, Steuer- und Empfängerpflichtangaben; Entwurf bleibt unvollständig. Unbestätigter Abschluss/Umwandlung am Connector blockiert; Teilen eines Entwurfs abgewiesen. Kein gültiges finales Angebot, PDF, erfolgreicher Duplikat- oder Auftrag-Erfolgsweg getestet.
- Pflanzen-/Leistungskatalog und Vorlagen tatsächlich gelesen. Ein eigener Baustein mit bewusst fehlendem Preis erstellt/gezielt geändert, stale Hash blockiert, dauerhaft inaktiv. Keine bestehenden Preise geändert.
- Foto-MCP: zwei synthetische JPEGs hochgeladen, Datei/SHA wieder gelesen, Änderung der Beschriftung und stale Hash geprüft. Datei-Replay und Foto-Replay erzeugen dieselben IDs. Kleines Bild 96×64/524 Bytes bytegleich. Großes Bild 4200×2800, EXIF 6: nach Speicherung 2048×3072, 69.360 → 37.625 Bytes. Zurückgelieferte Datei in Cloud unabhängig dekodiert; SHA-256 `e99b1c7bf705b301dc6c6b64b2c4f534046b23188a8df3794b643bc087251f44`, Orientierungstag entfernt. **Rollenänderung fehlgeschlagen**, siehe Befunde.
- Geräte-/Unternehmenseinstellungen ausschließlich gelesen; absichtlich falsche Versionshashes bei Update und Restore abgewiesen. Reale Wiederherstellung, globale Settingsänderung und Entzug echter Berechtigungen nicht ausgeführt.

#### Abgeschlossene Sync-/Integritätsprüfung und Befunde

1. **P0 – Drive-Rundlauf live fehlgeschlagen.** Drei `request_sync`-Anforderungen wurden angenommen; kein neuer Lauf/Provider-Readback nachweisbar. Letzter gemeldeter Versuch 18:41:58.596338Z, letzter Sync 18:41:43.930867Z. Nutzer meldet nach manuellem Versuch „Lokal gespeichert · Abgleich fehlgeschlagen“. `android_provider`, `provider_readback`, `cloud_confirmed=false`. Kein direkter Drive-Serverbeleg, kein Medien-Retry-/Neustart-/Server-Roundtrip-Erfolgsnachweis. Die genaue Ursache ist über die vorhandene MCP-Antwort nicht feststellbar; fehlende Konto-/Ordnerrechte, Netzfehler oder ein Versionskonflikt werden nicht ungeprüft behauptet.
2. **P1 – Fotorolle wird lokal überdeckt.** `update_photo` mit neuer Beschriftung und `role=before` quittiert Erfolg; `get_photo` liefert die neue Beschriftung, aber weiterhin `role=documentation`. Gleicher Wert im lokalen Archiv. Quellursache: `MainActivity.LocalStore.pendingActions(false)` überschreibt in jedem Fotojournal die Nutzlastfelder mit älteren `photo_attachments`-Metadaten einschließlich `role` (um Zeile 1084); `pendingActions(true)` macht dies nicht. Daraus entsteht zusätzlich ein Risiko unterschiedlicher Aktions-/Vergleichshashes zwischen Vorschau und Drive. Der spätere Drive-Fehler dieses Testpfades ist **nicht tatsächlich ausgeführt**; eventuelle neue Konflikte müssen nach funktionierendem Sync hashgebunden geprüft werden. Keine unveränderliche Aktion nachträglich geändert.
3. **P2 – Capabilities-Schema unvollständig.** `write_file_chunk.inputSchema.required` nennt `chunk_base64`, in `properties` fehlt das Feld. `PixelMcpReadTools.withoutBinary` entfernt rekursiv alle gleichnamigen Schlüssel und trifft auch Schemadefinitionen. Connector und tatsächlicher Upload können das Feld korrekt nutzen; Werkzeugnamen stimmen 83/83 überein. Kein HTTP-401-Umgehen und keine erfundene Connector-Freigabe.
4. **Diagnosegrenze:** UI `syncStatus()` enthält `last_error`, `user_action_required`, `retry_count`, `next_retry`; `get_sync_overview` exponiert diese Transportdiagnose nicht. Deshalb kann MCP die genaue Ursache und notwendige erneute Geräte-/Kontofreigabe aktuell nicht zuverlässig bestimmen. Quellcode zeigt, dass sowohl REST als auch ein gespeicherter Android-Dokumentbaum grundsätzlich automatisch vorgesehen sind; ein Provider-Verbot wird daraus nicht abgeleitet.
5. **Funktionsgrenzen:** `list_photos` hat kein offset/cursor und höchstens 100 Ergebnisse; eine vollständige MCP-Blätterfunktion für größere Kundenbibliotheken fehlt. `archive_entity` unterstützt keine offers; synthetische Angebotsentwürfe können über MCP nicht ins Archiv verschoben werden. Diese Grenzen wurden am Schema statisch festgestellt, kein Last-/Pagination-Erfolgsnachweis daraus abgeleitet.

#### Nachbereitung und verbleibende Warteschlange

- Geschäftssammlungen im gespeicherten Ausgangsstand bleiben hashgleich: `0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b`. Rohbestand vorher/nachher: Kunden 51, Aufträge 2, Arbeiten 173, Rechnungen 95, Fotos 12, Leistungsbausteine 32, Angebote 1. Diese Werte schließen die ausstehenden Testprojektionen nicht ein.
- Bestehender technischer Testkunde, seine ursprüngliche Foto-Liste sowie Unternehmens-/Geräteeinstellungen unverändert. Alle 8 ursprünglichen Prüfungen mit identischen client_ids, action_hashes und current_hashes; Legacy-Vorschau weiterhin 7 und Hash `68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7`. Keine Löschung/Altentscheidung, kein Beleg erfunden, keine Queue gelöscht.
- Sechs eigene Einträge lokal im Archiv: ein neuer synthetischer Kunde, Auftrag, Arbeitsaufzeichnung, Rechnungsentwurf und zwei Fotos. Originalbytes nicht physisch gelöscht. Testbaustein inaktiv. Angebot bleibt klar markierter Entwurf ohne Auftragsverknüpfung, da kein Angebotsarchiv-MCP existiert. Timer IDLE; b.b.-Testmarkierung entfernt.
- **Aktive Queue danach: 38 = 8 vorherige Prüfungen + 30 neue synthetische Testaktionen (einschließlich Archivierungen).** Neue Aktionen vollständig den acht eigenen Testentitäten zugeordnet; keine zusätzliche persistierte Review-Aktion. Lokales Archivieren ist noch kein synchronisierter Cleanup. Die 30 dürfen weder gelöscht noch als bestätigt ausgegeben werden; nach Reparatur des Syncs normale hashgebundene Verarbeitung/Belegprüfung, beim Fotorollenpfad gegebenenfalls kontrollierte Konfliktprüfung. Keine weiteren Massen-Schreibtests bis dahin.
- Synthetische IDs: Kunde `cus_mobile_mcp_e8fd82850e5fb414fa5f85376cc6154f`; Auftrag `ord_mobile_mcp_c90b203ce9485a27b0717c30678e4f7b`; Arbeit `wrk_mobile_mcp_f5ae9f29b900a0cf9f86467ca4ecae36`; Entwurf `inv_mobile_mcp_b5def23d28b8eff3761111fe9dcb87cd`; Fotos `pho_mcp_39612a180dead58cfb430f3f8edd3205`, `pho_mcp_20c14716be17ceb5efa7fcb41941f707`; Angebot `off_a584c1640db5973da7d85cae204a4abe`; Baustein `svc_639d0ee62bf9cf360a49d973f0760acb`.

<details>
<summary>30 neue synthetische Journalaktionen für die spätere Belegprüfung</summary>

| client_id | Art | Synthetische Entität |
|---|---|---|
| `mcp_pixel_3dbdcb71bcfeeacc77ef16acd4f56ea3409bf6ac` | order | `ord_mobile_mcp_c90b203ce9485a27b0717c30678e4f7b` |
| `mcp_pixel_865559d81eba43a3e55fbf1c46d7144c38291f05` | order | `ord_mobile_mcp_c90b203ce9485a27b0717c30678e4f7b` |
| `mcp_pixel_f5d406529401d97a4a3e69e3610d3615df0e3007` | worklog | `wrk_mobile_mcp_f5ae9f29b900a0cf9f86467ca4ecae36` |
| `mcp_pixel_be8230f383d7a760522af8eb5a9c2d4072376c6d` | worklog | `wrk_mobile_mcp_f5ae9f29b900a0cf9f86467ca4ecae36` |
| `photo_change_b173108b-6808-4768-9ac5-a88809254202` | photo | `pho_mcp_39612a180dead58cfb430f3f8edd3205` |
| `mcp_owner_e6009ed5da9f9b59ee0052564b7e412fd2c989710652cb8aa5b4e7fcfb0d47a0` | photo | `pho_mcp_39612a180dead58cfb430f3f8edd3205` |
| `mcp_pixel_5fad9aac1d3c265ba4e34dbdc549fad70deb3b54` | invoice | `inv_mobile_mcp_b5def23d28b8eff3761111fe9dcb87cd` |
| `mcp_pixel_2fc08b3849ed3422c465b7548755d452c0a14914` | invoice | `inv_mobile_mcp_b5def23d28b8eff3761111fe9dcb87cd` |
| `mcp_owner_109b45a637af58056e51bd1400494fcaeef8640554ac8d8ecf347205c47a1de9` | offer | `off_a584c1640db5973da7d85cae204a4abe` |
| `mcp_owner_98964f7240f95746b88b46f34461bbf40fb049acd9f108f275f554db76517f38` | offer | `off_a584c1640db5973da7d85cae204a4abe` |
| `photo_change_d82ee904-3a41-4512-b955-ae039e57dfab` | photo | `pho_mcp_20c14716be17ceb5efa7fcb41941f707` |
| `mcp_owner_3ba2b1aea9e8eb1833fac42036a37ad554fb18b3e1992ed5f0fca918240ed8a4` | offer | `off_a584c1640db5973da7d85cae204a4abe` |
| `mcp_owner_9ee3eea31c7922722870a45e5668d178be6a67df88b5c2e59c933cd1d522fa79` | offer | `off_a584c1640db5973da7d85cae204a4abe` |
| `mcp_owner_4c20f8c5fcf095a01628ed1eb9cc666fdcfeb26dc0bd24aa76a4471cbb839d33` | offer | `off_a584c1640db5973da7d85cae204a4abe` |
| `mcp_pixel_d5c69e42db79333ee3f84ca62124788c12b26c3c` | customer | `cus_mobile_mcp_e8fd82850e5fb414fa5f85376cc6154f` |
| `mcp_pixel_189665c3d9988867a93bbfc5d74ebeefd0c10056` | customer | `cus_mobile_mcp_e8fd82850e5fb414fa5f85376cc6154f` |
| `mcp_owner_e334a11ea03e913c49e81ff3f5108661f8918a6fb39fd97d17f523e99a23226a` | service_template | `svc_639d0ee62bf9cf360a49d973f0760acb` |
| `mcp_owner_d5cbd686add476c13461db3d6dd2c21bec87fe96d6f1d0fba414dd9069baf0cb` | archive | `cus_mobile_mcp_e8fd82850e5fb414fa5f85376cc6154f` |
| `mcp_owner_c058585c0525d592e4274c404d0028504213cba4805e35eb9866592d13e053c1` | service_template | `svc_639d0ee62bf9cf360a49d973f0760acb` |
| `mcp_owner_f2d9a9e105654f267f0dfe424d30686a8748a947d956bf466edd55fecf92ef5d` | restore | `cus_mobile_mcp_e8fd82850e5fb414fa5f85376cc6154f` |
| `mcp_owner_c79539cc896f180248ec536553c7e195b98dc04610af8203054c6f535a6c1b74` | archive | `cus_mobile_mcp_e8fd82850e5fb414fa5f85376cc6154f` |
| `mcp_owner_b3dc470efa9ee77d5a65df1d865f2ff265eb7accc12811df63b006509f4b78f7` | worklog | `wrk_mobile_mcp_f5ae9f29b900a0cf9f86467ca4ecae36` |
| `mcp_owner_74d17eed7fa434c230c4e3078b976ae42bda726509a737a79c13199072bacea7` | worklog | `wrk_mobile_mcp_f5ae9f29b900a0cf9f86467ca4ecae36` |
| `mcp_owner_545331ff3cc5b786d96f025d96ca7698585aaa1805570f6fcb4c93f1ff8c9a64` | worklog | `wrk_mobile_mcp_f5ae9f29b900a0cf9f86467ca4ecae36` |
| `mcp_owner_cfd7d3d752798b94e0a077ac414ba80325124dd06c2f6e1bc1db6fb9052d7e31` | archive | `pho_mcp_39612a180dead58cfb430f3f8edd3205` |
| `mcp_owner_a4d7bcf72f83992e29dd876e789bdf588e79599311b0df55e68f109027b962f2` | archive | `pho_mcp_20c14716be17ceb5efa7fcb41941f707` |
| `mcp_owner_f17a6bee01bd9064f92e786b59092327e91f18e82958fe49cf430ba9d99c5cfe` | archive | `inv_mobile_mcp_b5def23d28b8eff3761111fe9dcb87cd` |
| `mcp_owner_4707ed8c6bbeb5809f441731647f00a2d17b46630d4faa05ecc6f0d7b538cc36` | archive | `wrk_mobile_mcp_f5ae9f29b900a0cf9f86467ca4ecae36` |
| `mcp_owner_1011d4301e40f240268203f722c479d9dddae9fc832bdef89c3ee322efa3b667` | offer | `off_a584c1640db5973da7d85cae204a4abe` |
| `mcp_owner_55d3c61c4271a2a7d775274bb1449c7d0199a7364c711c71c1b10de011b153b5` | archive | `ord_mobile_mcp_c90b203ce9485a27b0717c30678e4f7b` |

</details>

#### Tatsächliche Funktions- und Berechtigungsmatrix

„Bestanden“ bedeutet in dieser Tabelle den angegebenen lokalen MCP-Teilablauf, keine vollständige Geräte- oder Sync-Abnahme. Für 16 Werkzeuge gab es ausschließlich Negativprüfungen; bei `confirmed=false` blockiert bereits das Connector-Schema, nicht erst der native Ablauf.

| App-Bereich | Tatsächlich angesprochene MCP-Werkzeuge | Fachlicher Umfang | Erforderliche Rechte/Prüfungen | Tatsächlicher Teststatus / Barriere |
|---|---|---|---|---|
| Verbindung/Version | `integration_health`, `get_capabilities`, `get_business_summary` | Lesen | Privater Tunnel | Live lesbar; 83 Namen, Schemaabweichung bei chunk_base64. |
| Kunden/Suche | `search_customers`, `get_customer`, `create_customer`, `update_customer`, `search_business_data` | Lesen, Anlegen, gezielte Änderungen | Owner/Write, Hash bei Updates | Synthetischer Kunde: CRUD, Zusatzfelder, Replay/CAS bestanden. |
| Aufträge | `search_orders`, `get_order`, `get_open_orders`, `create_order`, `update_order` | Lesen, Anlegen, Ändern | Owner/Write, Hash | Testauftrag bestanden; Archiv als lokale Projektion geprüft. |
| Arbeitsaufzeichnungen | `list_work_records`, `get_work_record`, `get_customer_work_records`, `get_work_record_summary`, `create_work_record`, `create_customer_work_record`, `update_work_record`, `set_work_record_billed`, `reassign_work_record` | Lesen, Anlegen, Ändern, manuelle Abrechnungsmarkierung, Zuordnung | Owner/Write, Hash/Begründung | 0-Stunden-Negativtest beider Create-Wege; 0,01→0,02 h über Kundenweg; Markierung an/aus; identische Zuordnung bestätigt. Wechsel zu anderem Kunden nicht getestet. |
| Timer/Pausen | `get_active_work_session`, `start_work`, `start_break`, `end_break`, `stop_work`, `continue_work`, `discard_work_session`, `restore_work_session`, `complete_work_session` | Lesen, Start/Pause/Fortsetzen/Stop/Verwerfen/Wiederherstellen/Übernahme | Owner/Write, eigene Session-ID | Start bis Verwerfen/Wiederherstellen bestanden, am Ende IDLE. complete nur falsche Session abgewiesen; kein Timer-Worklog erzeugt. |
| Persönliche Markierung | `set_personal_paid` | b.b. an/aus | Owner/Write; gerätelokal | Bestanden; billed und Rechnung unverändert. |
| Fotos/Dateitransfer | `list_photos`, `get_photo`, `begin_file_upload`, `write_file_chunk`, `create_photo`, `update_photo`, `get_file_metadata`, `read_file_chunk` | Lesen, begrenzter Upload, Anlegen, Metadatenänderung | Owner/Write; Datei-ID/SHA; Hash bei Update | Kleine/große synthetische Datei und Leseschutz bestanden. Beschriftung geändert; Rollenwechsel FAIL. Keine MCP-Fotopagination (limit≤100, kein offset). |
| Rechnungsentwürfe | `list_invoices`, `get_invoice`, `create_invoice_draft`, `update_invoice_draft` | Lesen, Entwurf erstellen/ändern | Owner/Write, CAS/Request-ID | Null-Euro-Testentwurf erstellt, gelesen, geändert; Replay und stale Hash bestanden. Nicht gestellt/bezahlt/versendet. |
| Rechnungsabschluss/-korrektur | `request_invoice_issue`, `prepare_invoice_share`, `create_invoice_correction`, `update_invoice_payment` | Bestätigter Abschluss, PDF-Übergabe, Korrektur, Zahlung | Explizite Nutzeranweisung; Aussteller/Nummernfreigabe; Online-CAS; Original-PDF | Nur Schutzprüfungen: confirmed=false am Connector, Entwurf ohne Original-PDF, Zahlung/Korrektur auf Entwurf blockiert. Erfolgswege nicht ausgeführt. |
| Angebote | `list_offers`, `get_offer`, `get_offer_context`, `create_offer_draft`, `update_offer_draft`, `validate_offer` | Lesen, Kontext, Entwurf erstellen/ändern/prüfen | Owner/Write, Hash/Request-ID | Vier Dokumentarten, fehlender Preis/Pflichtangaben, Replay/CAS geprüft. Kein gültiges finales Dokument ohne Aussteller-/Steuerfreigabe. |
| Angebotsabschluss/Weitergabe | `finalize_offer`, `duplicate_offer`, `prepare_offer_share`, `convert_offer_to_order` | Bestätigter Abschluss, Duplikat, PDF-Übergabe, Auftrag | Owner/Write; aktuelle Hashes; ausdrückliche Bestätigung | Nur Negativtests: unbestätigt, Entwurf, unbekannte ID. Kein PDF-/Konvertierungs-Erfolgsnachweis. Kein Angebotsarchiv-Werkzeug vorhanden. |
| Pflanzen-/Leistungskatalog | `search_catalog`, `get_catalog_entry`, `search_offer_templates`, `get_offer_template`, `create_catalog_entry`, `update_catalog_entry` | Lesen, Leistungsbaustein erstellen/ändern/deaktivieren | Owner/Write für Bausteine, Hash | Pflanze und bestehende Bausteine gelesen; eigener preisloser Baustein erstellt/geändert, stale Hash blockiert, bleibt inaktiv. Keine Pflege echter Preise. |
| Archiv | `list_archive`, `archive_entity`, `restore_entity` | Lesen, reversibel archivieren/wiederherstellen | Owner/Write, Entity-/Archivhash, Beziehungsschutz | Eigener Kunde archiviert/wiederhergestellt/erneut archiviert; sechs eigene Einträge lokal archiviert. Drive-Übernahme ausstehend. |
| Sync/Konflikte | `get_sync_overview`, `get_sync_status`, `get_sync_conflicts`, `request_sync`, `preview_legacy_conflict_resolution`, `resolve_sync_conflict`, `prepare_legacy_conflict_backup`, `close_legacy_conflicts` | Lesen/Anfordern; hashgebundene Entscheidung | Owner/Write; Backup+Bestätigung für Altabschluss; direkte Drive-Freigabe | Lesen bestanden. request_sync nur angenommen, kein neuer Lauf sichtbar; Nutzer meldet Fehler. 8 Altprüfungen unverändert. Nur unbekannter Konflikt/unbestätigter Abschluss/fehlender Drive-Beleg negativ getestet. |
| Sicherung | `list_backups`, `inspect_backup`, `create_backup`, `restore_backup` | Lesen, Geschäftssicherung, geschützte additive Wiederherstellung | Owner/Write; State-Hash, leere Queue/IDLE für Restore | Backup erstellt/geprüft/Replay; veralteter Restore-Hash abgewiesen. Keine tatsächliche Wiederherstellung des produktiven Stores. |
| Einstellungen/Aussteller | `get_settings`, `get_business_settings`, `update_settings`, `update_business_settings` | Lesen, versioniert ändern | Owner/Write, Hash; keine OAuth-/Tunnelrechte durch Code | Lesen und stale-Hash-Abweisung bestanden, bestehende Einstellungen unverändert. Keine positive Änderung produktiver Konfiguration. |

#### Explizite Live-Vergleiche

| Prüfung | Ergebnis |
|---|---|
| App/Protokoll/Owner-Freigabe | PASS |
| Werkzeugnamen: Connector/Pixel 83/83 | PASS |
| Kunde Zusatzfelder gezielt zusammengeführt | PASS |
| Kunde Request-Replay gleiche ID | PASS |
| Kunde veralteter Hash abgewiesen | PASS |
| Kunde Archiv/Wiederherstellung Original-ID | PASS |
| Auftrag Update gelesen | PASS |
| Auftrag Replay gleiche ID | PASS |
| Request-ID mit anderem Inhalt abgewiesen | PASS |
| Auftrag veralteter Hash abgewiesen | PASS |
| Nullstunden abgewiesen | PASS |
| Arbeitsaufzeichnung erstellt/gezielt geändert | PASS |
| Arbeitsaufzeichnung veralteter Hash abgewiesen | PASS |
| b.b. ändert Arbeits- und Rechnungsstand nicht | PASS |
| Manuelle Abrechnungsmarkierung getrennt | PASS |
| Arbeitszuordnung identischer Ziel-IDs | PASS |
| Timer Pause/Fortsetzen/Stop | PASS |
| Timer Verwerfen/Wiederherstellen/IDLE | PASS |
| Timer falsche Sitzung abgewiesen | PASS |
| Foto Upload/Erstellen/Ändern/Lesen | FAIL – Rolle bleibt documentation statt before; Beschriftung geändert |
| Foto veralteter Hash abgewiesen | PASS |
| Datei Versionsschutz | PASS |
| Kleines Bild bytegleich | PASS |
| Großes Bild verkleinert/EXIF gedreht | PASS |
| Gespeicherte Bildbytes unabhängig dekodiert/Hash | PASS |
| Foto/Chunk Replay ohne Duplikat | PASS |
| Rechnungsentwurf erstellt/geändert/gelesen | PASS |
| Rechnungsentwurf Replay gleiche ID | PASS |
| Rechnungsentwurf veralteter Hash abgewiesen | PASS |
| Abschluss ohne Bestätigung im Connector abgewiesen | PASS |
| Entwurf ohne PDF nicht teilbar | PASS |
| Entwurf nicht bezahlbar/stornierbar | PASS |
| Angebot erstellt/geändert/Replay | PASS |
| Angebot veralteter Hash abgewiesen | PASS |
| Fehlender Preis/Pflichtangaben blockieren Angebot | PASS |
| Vier Angebotsarten gespeichert | PASS |
| Angebotsabschluss/Umwandlung ohne Bestätigung abgewiesen | PASS |
| Entwurf nicht zum Teilen freigegeben | PASS |
| Katalogbaustein erzeugt/geändert/inaktiv | PASS |
| Katalog veralteter Hash abgewiesen | PASS |
| Backup erstellt/geprüft/Replay | PASS |
| Settings/Backup veraltete Hashes blockiert | PASS |
| Kein unbekannter Konflikt aufgelöst | PASS |
| Altabschluss ohne Bestätigung abgewiesen | PASS |
| Legacy-Sicherung ohne direkten Drive-Beleg blockiert | PASS |
| Originale Geschäftssammlungen unverändert | PASS |
| Altaktionen/Hashes unverändert | PASS |
| Keine zusätzliche Konfliktprüfung | PASS |
| Aussteller-/Geräteeinstellungen unverändert | PASS |
| Bestehender Testkunde/Foto-Liste unverändert | PASS |
| Sechs eigene Einträge archiviert | PASS |

#### Nicht als getestet oder freigegeben ausgeben

- Erfolgreicher Drive-/Medien-Server-Rundlauf, Netzabbruch/Offline-Wechsel, wiederaufgenommener Upload nach Prozessabsturz, echte App-Neustart-/Snapshot-Migration: nicht live nachgewiesen.
- Mehr als 10 bzw. 100 Kundenfotos, komplette Fotopagination, reale Kamera, Wischgesten, Bildschirmdarstellung und Pixel-Leistung: in dieser MCP-Phase nicht getestet. Zwei neue synthetische Originale; frühere Hosttests mit 105 Metadatensätzen/21 Importdateien sind davon strikt getrennt. Wegen defektem Sync keine weiteren Massenaktionen erzeugt.
- Tatsächlicher Entzug/Wiedererteilung von Tunnel-, Android-, Drive- oder Organisationsrechten nicht getestet; laufende produktive Verbindung nicht absichtlich unterbrochen. Authentifizierter privater Tunnel und Write-Gate funktionieren für die angegebenen synthetischen Tests. Direkte Drive-Freigabe/Serverbeleg fehlt.
- Echtes Angebot finalisieren/PDF/Annahme→Auftrag, tatsächlicher zentraler Rechnungsabschluss/Nummernreservierung/PDF/Versand/Zahlung/Korrektur, vollständige Restore-Abnahme und Legacy-Abschluss nicht durchgeführt. Business Settings enthalten noch keine bestätigte Steuer-/Nummernkonfiguration.
- Installations-, Zertifikatsprüfung der installierten APK und Datenerhalt bei Update nicht durch MCP nachweisbar. Es wurde weder installiert noch deinstalliert, keine App-Daten gelöscht und keine neue APK gebaut. Die Originalsignaturprüfung des zuvor bereitgestellten Cloud-Kandidaten bleibt ein gesonderter früherer Nachweis.
- Nächster Schritt: tatsächlichen Drive-Transportfehler diagnostizierbar machen und beheben; Fotorollen-/Journalprojektion sowie Capabilities-Schema korrigieren, gezielt regressieren; danach dieselben synthetischen Aktionen mit echten Belegen abnehmen. Aktueller Build bleibt ohne vollständige P0/P1-Abnahme.


### Eintrag 0 – Initialisierung

Status: OFFEN

Nächster Schritt:
1. `AGENTS.md` lesen
2. `CODEX-AUFTRAG-GARTENPIRAT.md` vollständig lesen
3. aktuellen `main`-Head und Cloud-Branches prüfen
4. Phase A durchführen
5. diesen Status aktualisieren

---

## Vorlage für weitere Einträge

### Eintrag N – <Phase / Thema>

- Datum:
- Commit vorher:
- Commit nachher:
- Status: PASS / FAIL / NOT TESTABLE
- Geänderte Dateien:
- Ausgeführte Tests:
- Ergebnis:
- Offene Probleme:
- Nächster Schritt:

### Eintrag 1 – Phase A / verifizierter Cloud-Ausgangspunkt

- Datum: 2026-10-18 (UTC)
- Commit vorher: `354cb496264d09ed92957aeea09b1b71ad932389` (sauber).
- Commit nachher / Implementierungsbasis: `a38765d586c3ea02b999e770e5ecb22b34642962`. Bestehende Cloud-Branch ausschließlich per Fast-forward vorgezogen; main unverändert. GitHub bestätigt main sowie Reparatur-Branch `90bfc1660e0345d19a461f12f74b7e163492532a`; beide enthalten die bisherige Cloud-Implementierung und den Pflichtvorfahren `05fdcbe`. Veralteter origin/Cloud-Trackingref `36e5567` nicht verwendet.
- Geänderte Dateien: CODEX-STATUS.md (Arbeitsnachweis); alle neuen Vorgaben gelesen, einschließlich Originalsignierung, Reparaturhinweis und Issue #1.
- Ausgeführte Prüfungen: Git-Status/Ancestry, Repository-/Remotevergleich; live ausschließlich get_capabilities, integration_health, get_sync_overview.
- Ergebnis PASS: Repo 1.49.1/70; installierter Pixel 1.49.0/69, Paket at.hausgarten.service, Datenbank 4, Protokoll 3, 83 Werkzeuge, Kataloghash `0741dc4553e5e43baf537d996cd4efbb79eaa6f4fb0bcf6c543f3ea412e11a55`; ready/write_enabled. Snapshot meldet Schema 1. Drive transport android_provider, offers/service_catalog false, cloud_confirmed=false, provider_readback. Queue unverändert 9 (6 Löschprüfungen, 1 Kundenkonflikt, 2 technische Aktionen); archiviert 0. Keine Queue-Aktion geändert.
- Offene Probleme: Produktiver Import/Nummernpfad setzt noch externen Prozessor voraus; Originalsignatur der installierten APK und native Kamera-/Updateabnahme nicht aus MCP prüfbar. Installation ohne neue Sicherung und ausdrückliche Freigabe gesperrt.
- Abnahmekriterien B/C: validierte native Handler und dauerhafte Aktionshash-Belege; versionierter Pixel-Gesamtstand nach Neustart wieder einlesen; alte Originaldatei erhalten; unbekannte Settings/Nummernstände fail-closed; echte Geschäftsdatensätze bleiben unverändert.
- Nächster Schritt: B/C – Pixel/Drive-native Verarbeitung und isolierte Regressionen.

### Eintrag 2 – Phase B / Abhängigkeiten und nativer Zielpfad

- Datum: 2026-10-18 (UTC); Basis weiterhin a38765d.
- Status: PASS für Abhängigkeitsanalyse; Umsetzung/Abnahme in C offen.
- Befunde: DriveSyncCoordinator las immer die alte HausGarten-Laptop.json. SAF publish war leer, direkte REST-Veröffentlichungen wurden beim nächsten Lauf nicht als Ausgangsstand eingelesen. Settings und Compare-Freigaben erwarteten Python-exportierte Capabilities; Abschluss/Korrektur warteten auf invoicing.py.
- Entscheidung: unveränderliche Gesamtstände und verknüpfte Commit-Belege im vorhandenen Drive-Ordner lesen/schreiben; Altdatei unverändert erhalten. Native Handler migrieren das additive Schema und ihre eigenen Capabilities. Settings übernehmen nur nachgewiesene Felder, fehlende Werte bleiben unbekannt. Finanzabschluss benötigt online geschützte, explizit bestätigte Pixel-Nummernhoheit; SAF allein bietet keinen atomaren Nummernvergleich.
- Geänderte Dateien bisher: PixelDriveState.java, DrivePrimaryState.java, DriveSyncCoordinator.java, DriveDirectFiles.java, DriveRestClient.java, DrivePublication.java, DriveJournalRules.java, OwnerJournalRules.java, PixelOwnerAccess.java, MainActivity.java, tools/run-followup-java.py; noch in Arbeit, keine Freigabe.
- Tests: statische Abhängigkeitsprüfung; automatisierte Qualifikation folgt nach vollständiger Integration in C. Keine produktive Aktion. Legacy-Python-/Web-Code und Tests bleiben als Kompatibilitäts-/Testreferenz erhalten.
- Nächster Schritt: C, atomare native Regeln, Wiederanlauf und synthetische Tests.

### Eintrag 3 – Phase C / native Implementierung und isolierte Qualifikation

- Datum: 2026-10-18 18:30 UTC; Ausgangscommit a38765d; Änderungen noch auf derselben Cloud-Branch.
- Status: PASS (statisch/automatisiert), NOT TESTABLE (Gerät/echtes Google).
- Dateien: neue Produktionsklassen PixelDriveState, DrivePrimaryState, DriveNumbering, InvoiceIssueRules; bestehende DriveSyncCoordinator/DirectFiles/RestClient/Publication/JournalRules, OwnerJournalRules, MainActivity, PixelOwnerAccess und MCP-Adapter aktualisiert. Bestehende UI owner-ui, invoice-draft, mobile, sync-dashboard; bestehende native Abnahmen und JVM-/Browser-Tests erweitert. Neue PixelDriveStateTests; Java-Runner ergänzt. Version auf 1.49.2/71 erhöht.
- Ergebnis: Pixel verarbeitet die vorhandenen Entitäten, Settings, Archiv-/Konfliktaktionen und Korrekturentwürfe nativ. Neue Gesamtstände/Commitketten werden im selben Drive-Ordner unveränderlich veröffentlicht und beim nächsten Lauf wieder gelesen; alte HausGarten-Laptop.json wird nicht überschrieben. Parallel geänderte Altquelle, fremder primärer Pixel, Lücken/Forks und andere Aktionshashes stoppen. JSON bleibt frei von Originalmedien. Fehlende Settings bleiben unbekannt, bekannte/zusätzliche Felder bleiben erhalten. CAS auch für Settings/Fotos/Entwürfe und lokale Offline-Vorgängerketten. Native Leseansichten projizieren nur validierte Änderungen; eine verspätete 3-Stunden-Variante kann eine ausgestellte 2-Stunden-Leistung auch in der Ansicht nicht überschreiben. Vorschauen erzeugen keine Belege.
- Neuer Finanzjournaltyp pixel_invoice_issue_v1 wird von alten Prozessoren nicht finalisiert. Internes MCP-Protokoll 4, weiterhin 83 Werkzeugnamen und kompatible MCP-RPC-Verhandlung. Expliziter Abschluss bindet Entwurf, Arbeitsstände, Aussteller und Empfänger; bestätigte alleinige Pixel-Nummernhoheit, aktueller Ausgangszähler und Online-CAS erforderlich. Negative CAS-Probe ausschließlich auf technischer Probe-Datei, nie auf dem echten Zähler. Fehlender ETag oder nicht abgewiesener Vergleich blockiert. Persistente Reservierung bei verlorener Antwort wiederverwenden; PDF separat hashgebunden, Geschäftsmutation/Beleg im gemeinsamen Gesamtstand. Kein automatischer Versand/Bezahltstatus; b.b. bleibt getrennt.
- get_sync_overview ergänzt technische Bestandszahlen und Sammlungshashes ohne Betriebsinhalte; native Kamera-/Bitmap-/PDF-/SAF-Tests nur vorbereitet/kompiliert.
- Tests PASS: 15 JVM-Suiten / 637 Fälle (davon 73 neue Pixel-/Drive-Fälle); 266 Python-Tests ohne schlüsselerzeugende QA-Suite; 21 bestehende Browser-Suiten plus access-integrity (8), sync-customer-gallery (14), offers-photo-library (14). Synthetische Daten/Brücken, keine Betriebsdaten. Pflanzenquelle 181 Profile/362 Bilder unverändert, Spiegel geschrieben/geprüft.
- Offene Probleme: reale Google-CAS-Unterstützung, Android-Speicher-/Providerablauf und Update/Datenerhalt nicht bestätigt. Native Testquellen sind kein Ausführungsnachweis.

### Eintrag 4 – Phase D / technische Aktionen geprüft, Live-Ausführung blockiert

- Datum: 2026-10-18; Basis a38765d plus qualifizierte Cloud-Änderungen.
- Status: PASS synthetisch / NOT TESTABLE live.
- Die Angebotsaktion b6f1…539da bleibt live SYNC_COMPONENT_UPDATE_REQUIRED; der ursprüngliche Aktionshash lautet e1df44d3146a8d0e4ce1dd901f45332093aab65e3e0b0ae4e4f0986ffb6c40c0. Die spätere Kontrollaktion 43e8…e54c bleibt ACTION_ID_CONFLICT (51562d617c3418e3538a009a5b3b0ac149122c2920e8064b2666056be8046208). Vollständige IDs oben erhalten.
- Native Tests übernehmen ein synthetisches Angebot genau einmal und archivieren die überholte Kontrollaktion nur nach geprüftem Originalhash als superseded/no_business_data_changed. Manipulation blockiert; keine blinde Kontrollentscheidung.
- Live läuft weiterhin 69. Keine alte Aktion umgeschrieben, erneut erzeugt, gelöscht oder manuell aufgelöst. Neuer Handler benötigt freigegebenes Update und direkten Drive-Abgleich.

### Eintrag 5 – Phase E / Legacy-Vorschau geprüft, keine Auflösung

- Datum: 2026-10-18; Status Vorschau PASS / Ausführung NOT TESTABLE.
- Live preview_legacy_conflict_resolution bestätigt genau die sieben oben dokumentierten IDs, keep_current, no_business_data_changed. Preview-Hash 68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7. Sechs DELETE_REVIEW_REQUIRED, ein WRITE_CONFLICT; keine Löschung ausgeführt.
- Vorhandenes Geschäftsbackup nur inspiziert: SHA256 unverändert 4683945a1d959e34aeea818768eb8fc618ef2a9cc424e76054c05072e278cb6b; historische Sicherungszahlen 51 Kunden/1 Auftrag/173 Arbeiten/95 Rechnungen/12 Fotos/32 Bausteine/0 Angebote. Das sind Backup-Zahlen, kein neuer vollständiger Geräte-Bestandstest. Alte Live-Leseübersicht: 50 Kunden/1 Auftrag/171 Arbeiten; alte Löschvorschläge verstecken Einträge in der Leseüberlagerung. Neuer Code erhält deren Sichtbarkeit und ändert dadurch keine Datensätze.
- Geprüfter aktueller State-Hash bei inspect_backup: eb8b38a8f16ae473086a2667875081db6c8c0b64205a17cae283ae127dfdb6ea (einschließlich flüchtiger Sync-Metadaten, kein reiner Geschäftshash). Kein vollständiger Vorher/Nachher-Geschäftshash über Build 69 verfügbar.
- Neue Backup-Funktion erfordert erst direkte Drive-Verbindung mit Serverbeleg und ausschließlich sieben historische Restfälle. Sichert SQLite, lokale Medien, aktuelle Pixel-Veröffentlichung samt Commit und erhaltene Altdatei. Backup-/Vorschau-/Aktions-/Datensatzhash werden erneut geprüft. keep_current darf keine Foto-Originale nachträglich bereinigen.
- Es wurde kein neues Backup als vollständige Gerätesicherung ausgegeben und keine Sammelentscheidung gesendet. Queue bleibt 9, historische Archivierungen 0.

### Eintrag 6 – Phase F / Testgrenzen und verbleibende Abnahme

- Datum: 2026-10-18; Status Host PASS / Pixel-End-to-End NOT TESTABLE.
- Isoliert geprüft: Migration ohne Settings-Defaults, unbekannte Felder, Angebot/Katalog/Settings und Offline-Entwurfsänderungen, Hash-Konflikte, Requests/Replays, Infrastruktur-Replay und Supersession, 7 keep_current-Fälle ohne Geschäftsänderung, Crash vor Commit/Antwortverlust, Zähler-Rennen/fehlender Versionsschutz, Rechnungsrennen, Archiv/Beziehungen, Rendering und Medien-Metadaten. PDF-Validierung Python tatsächlich ausgeführt; neue native PDF-Erzeugung nur kompiliert.
- Foto-Browser: 105 synthetische Metadaten, 30/60 Vorschauen, 21 sequenzielle Importe plus fehlerhafte Datei; keine native Abnahme von 100 Originalfotos. Bestehende Komprimierung 3072 px/JPEG 84, kleine Bilder erhalten, getrennte Originale/Vorschaudateien unverändert. Kamera am Pixel NOT TESTABLE.
- Live lesend: 83 Werkzeuge einschließlich aller 11 Angebots-/Kontextwerkzeuge und create_invoice_draft/update_invoice_draft/request_invoice_issue/prepare_invoice_share. Sichtbarkeit beweist keine Schreibfunktion. get_business_settings auf 69 bestätigt SETTINGS_UNAVAILABLE; offers/service_catalog bleiben false. Keine Live-Schreibtests, Timeraktion oder Geschäftsdatenmutation durchgeführt.
- P0 offen: neue App am Pixel abnehmen und direkte Drive-Veröffentlichung nachweisen; technische 2 plus historische 7 sicher abschließen; gesamter MCP-Testplan mit isoliertem Testkunden nach Update. P1 offen: Google-Konto/OAuth-/Ordnerrechte, echte CAS-Negativprobe, vollständige Gerätesicherung und ausdrückliche Installationsfreigabe, nativer Kamera-/In-place-Update-/Datenerhalt-Test. Keine ununterbrochene Verfügbarkeit zugesichert.
- Nächster sicherer Schritt: Originalsignierten Cloud-Prüfkandidaten konkret bereitstellen. Danach vollständige Gerätesicherung und ausdrückliche Updatefreigabe; kein automatisches Installieren.

### Eintrag 7 – Phase G / geprüfter Original-Kandidat und erneute Live-Lesung

- Datum: 2026-10-18 18:38 UTC. Ausgangscommit a38765d586c3ea02b999e770e5ecb22b34642962; Implementierungs-Abschlusscommit **545bd683380244d1e10ca45f75d66a652173eb45**. Artefakt/Status werden anschließend separat auf derselben Branch gesichert. main unverändert, kein Force-Push/History-Rewrite.
- Status: Build/Originalsignierung PASS; produktive Freigabe und Gesamtauftrag ausdrücklich NICHT abgeschlossen.
- Build: :app:assembleRelease, :app:compileReleaseAndroidTestJavaWithJavac, :app:lintRelease offline in Codex Cloud PASS; 0 Lint-Fehler/12 Warnungen. Plant-Sync --write/check, Privacy-Gate, Artefakt-/Assetvergleich PASS. Native Abnahmen kompiliert, nicht ausgeführt.
- APK: downloads/Haus-Garten-1.49.2-71-original-update-kandidat.apk, Paket at.hausgarten.service, Version 1.49.2, versionCode 71 (> Repo 70 und live 69); minSdk 26, target/compile 37, nicht debuggable. Größe **34.401.795 Bytes**. Unsignierte SHA256 5f7e7b0ead99ecd5763981a9bcb33ccbf9b1d8759d19b40b170d77ea9b8e563c. Finale SHA256 **a57973c9268b15e50facbb0f0fd5ccde91e98ec6f7a15dbb2ad9213f54c9e1fa**.
- Vor Signierung ausschließlich das Vorhandensein der fünf dokumentierten HG_ORIGINAL_*-Variablen geprüft, vollständig. Ein tatsächlicher Signierlauf ausschließlich tools/sign_original_apk.py; keine Ersatzschlüssel oder QA-/Debug-/Actions-Signierung. Vorhandenes Keystore-Zertifikat mit konfiguriertem Original abgeglichen; normaler einzelner APK-Signer erneut unabhängig geprüft. Zertifikat-SHA256 **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**. Zusätzlich Übereinstimmung mit erhaltenem Cloud-Kandidaten 69 bestätigt; das ist kein Zugriff auf die installierte Signatur.
- apksigner verify PASS: ein Signer, v2/v3 true; v1/v3.1/v3.2/v4 false. zipalign -c -P 16 4 PASS. Alle 402 Assets bytegleich; neue operative Klassen vorhanden; null operative Seedzeilen, private_markers_checked=0. Cleanup PASS: null hg-original-signing-Tempordner und null Schlüsseldateien nach geprüften Endungen im Repository. Keine Geheimwerte geloggt oder dauerhaft gespeichert.
- Privater [Drive-Download](https://drive.google.com/file/d/1sSRrb97nKbYlh_SEKetxAUkGMLIeiCqF/view) als neue APK-Datei, keine vorhandene Datei verändert; Eigentümerrecht bestätigt, keine öffentliche Freigabe hinzugefügt. Raw-Connector-Rückabruf lieferte vollständige Dateireferenz und dieselbe Größe (34.401.795); lokale APK-SHA geprüft, kein unabhängiger Remote-Bytehash behauptet.
- Quellkatalog: 83 Werkzeuge, interne Version 4; erwarteter tool_catalog_hash **584505048c5d7cc6a3baec85428a0a4633b8254d9f78068b74e82207acd30e7a**. Nach freigegebenem Update initialize/tools/list derselben privaten Verbindung aktualisieren, tatsächliche Version/Hash und Gates erneut lesen.
- Live nach Build weiterhin 1.49.0/69, Protokoll 3, Kataloghash 0741dc4553e5e43baf537d996cd4efbb79eaa6f4fb0bcf6c543f3ea412e11a55. Tatsächlich angebotene Angebotswerkzeuge: list_offers, get_offer, validate_offer, search_offer_templates, get_offer_template, get_offer_context, create_offer_draft, update_offer_draft, finalize_offer, duplicate_offer, prepare_offer_share, convert_offer_to_order. Rechnungswerkzeuge: list_invoices, get_invoice, create_invoice_draft, update_invoice_draft, request_invoice_issue, prepare_invoice_share, create_invoice_correction, update_invoice_payment. **Sichtbar, neue Schreibimplementierung live nicht getestet.**
- Live-Vorher/Nachher: Queue **9 → 9**, archiviert **0 → 0**, sechs DELETE_REVIEW_REQUIRED + ein WRITE_CONFLICT + je eine SYNC_COMPONENT_UPDATE_REQUIRED/ACTION_ID_CONFLICT unverändert. Legacy-Preview-Hash unverändert 68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7; damit Auswahl, Aktionshashes und sieben geprüfte aktuelle Datensatzhashes unverändert. Live-Lesezahlen unverändert 50 Kunden/1 Auftrag/171 Arbeiten, 1.001,75 Stunden/170 als unabgerechnet markiert. Kein vollständiger Geschäftshash über alten Connector vorhanden; keine weitergehende Integritätsbehauptung.
- Kein Produktivschreiben über Pixel-MCP, keine Installation, kein ADB, kein Kamera-/In-place-Update-/Datenerhalt-Test. Erfolgreiche Signierung ist kein Update- oder Datenerhalt-Nachweis.
- Offene P0/P1 aus Eintrag 6 bleiben bestehen. Nächste Geräteaktion benötigt vollständige Sicherung und ausdrückliche Freigabe gemäß AGENTS.md: „keine automatische Installation ohne Sicherung und Freigabe“. Danach direkte Google-Verbindung/Versionsschutz mit synthetischen Daten abnehmen; erst dann D, anschließend neues Backup und bestätigte E-Auflösung und der vollständige F-Plan. Keine Laptop-Handlung erforderlich.


### Technische Hashliste der nur gelesenen Ausgangsfälle

Kein Betriebsinhalt, keine angewendete Entscheidung. Hashes aus get_sync_conflicts; Legacy-Vorschau vor/nach Arbeit identisch.

| client_id | Fehler | Action-Hash | Current-Hash |
| --- | --- | --- | --- |
| `wrk_mobile_913976ba4bd1453ba75a5468b89c3c32` | DELETE_REVIEW_REQUIRED | `d4ad7e0bb45f60ed925cd3fa294343aa44efe84ed0ca2627ac9c63bd74db74e8` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |
| `wrk_mobile_b8445cff3af746718d480fce9be3889a` | DELETE_REVIEW_REQUIRED | `bb84d7424474d267d75981f29cc69914ff4bc7054e82732feae089b46d170364` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |
| `wrk_mobile_325735fd783c4f5fb3d012ab7969ccfd` | DELETE_REVIEW_REQUIRED | `81fdd1bac9a83b6f81502513dacd59e58eac4e1ff10b7889b657372478d519ce` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |
| `delete_change_f82a727e-90ce-4021-a741-2238101b2505` | DELETE_REVIEW_REQUIRED | `06a95cf238bd35098f5b3dd9147b16b26eaf063e4fe504f154c22859ded65c7b` | `78957fa4902bb23535b4e994935a1d9794680abc2f6f1be7ba43f3fb5dbf1123` |
| `delete_change_b1fc39af-3f04-4baa-81bf-a34a20e76479` | DELETE_REVIEW_REQUIRED | `2b45a83fc17f6eb72163f0a58b3779f7904259518b22dece9a7e88120f54bb41` | `d205eab650bc4f727a52ddfa2f29cfcaa7e7d3697820d28076aa01b845313d0e` |
| `customer_change_3b8f4a4bdaca4d5e9b67a4b9b3ac3020` | WRITE_CONFLICT | `258d26b477b41ec0a3f097d893217e8c22c988071eb11a98d6a03d89d9ea0701` | `30599c5c7807db1f9ccc8ceb0381df8c914ba43b38f3f7494b49b08d3324ae6f` |
| `delete_change_9304498d-e90b-47de-be7c-bfd2e627fdf3` | DELETE_REVIEW_REQUIRED | `d5a7f9020c75ec994d0d8b09aee5a94cc2b53648bce8bf6f4cc81f9f8c237a8a` | `e3c8da0a874c1b44a7a1d27cdbe63ff1b06ccbe20fd77508a7d56be87561d7e6` |
| `mcp_owner_43e89c1a01b88694f43a944e5c4109cb1a7e1a8c15378ee0c1116779bb1ce54c` | ACTION_ID_CONFLICT | `51562d617c3418e3538a009a5b3b0ac149122c2920e8064b2666056be8046208` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |
| `mcp_owner_b6f1c56ba6534a250f6f5057175f365623155aa656d55fd0342f2517b4a539da` | SYNC_COMPONENT_UPDATE_REQUIRED | `e1df44d3146a8d0e4ce1dd901f45332093aab65e3e0b0ae4e4f0986ffb6c40c0` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |

### Bereitstellung bestätigt

- GitHub-Commit und normaler Push bestätigt: **8e9a33af6552199d3f8b3b0a521d1b7554f09584** auf codex/original-signing-cloud-setup-20261018. Enthält Implementierungscommit 545bd68 und geprüfte APK. main weiterhin a38765d586c3ea02b999e770e5ecb22b34642962.
- Der folgende reine Statuscommit ergänzt nur diese Hashliste und die Remote-Bestätigung. Code/APK bleiben bytegleich; Gesamtauftrag bleibt wegen der dokumentierten P0/P1-Gates offen.


### Eintrag 13 – Papierkorb/Bedienung, Phase A: geprüfter Ausgang

- 2026-10-18, Ausgang 16854f285991c6a406dee1eb23c45e1ccea4ff32, sauber auf vorgesehener Cloud-Branch; Remote identisch, main a38765d unverändert, Pflichtancestor vorhanden. Keine Übernahme oder Datenmutation.
- Live ausschließlich lesend: Pixel 1.49.3/72, Protokoll 4, 85 Werkzeuge. Queue 51 (43 Übertragungen / 8 Prüfungen), Historie 1; gespeicherter Geschäftshash weiterhin 0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b. 13 zusätzliche Fotoaktionen seit letzter Phase vom Benutzer; nicht als Testdaten benutzt.
- Konkrete Sync-Barriere jetzt sichtbar: ursprünglicher Drive-Gesamtstand parallel verändert; persistierte Ordnerrechte vorhanden. Keine automatische Quellstand-Ersetzung oder Konfliktauflösung.
- Abnahmekriterien: verknüpfte Fotos verhindern Papierkorb für Arbeit/Auftrag nicht; Originale erhalten, nur betroffene Foto-Zuordnungen kontrolliert lösen. Nutzerpräzisierung: Kundenverknüpfung ausschließlich beim Verschieben in den Papierkorb lösen; kein eigener Entkopplungs-Workflow und keine globale Migration. Endgültige Entfernung nur aus Papierkorb mit ausdrücklicher Bestätigung, aktuellem Archivhash, idempotenter Aktion, minimalem Löschbeleg und Schutz gegen Wiederbelebung. Geschützte Rechnungen/finanzielle Beziehungen bleiben geschützt. Dateibereinigung erst nach dauerhaft bestätigter Löschaktion, keine fremden/gemeinsam verwendeten Originale entfernen; historische Sicherungen bleiben gesondert.
- UI: Hauptaktionen sichtbar, Zusatzaktionen gebündelt, Filter bei Bedarf; keine gleichzeitigen Lösch-/Restore-Vorgänge, Fokus/Entwürfe/Zurück erhalten. 320/360/426 px und große Schrift prüfen. Bestehende Architektur und alle Funktionen erhalten.
- Nächster Schritt: gemeinsame Journalregeln und Papierkorb, anschließend kompakte Bedienung und isolierte Regressionen. Keine produktiven Löschtests, Installation oder Legacy-Auflösung.


### Eintrag 14 – Papierkorb/Bedienung, Phase B/C: Implementierung

- Gemeinsame Regeln: Fotozuordnungen atomar innerhalb derselben Archivaktion mit Vergleichshash lösen. Nur betroffene Felder; andere Kunden/Fotos bleiben unverändert. Restore verbindet ausschließlich unveränderte Fotos. Altes Journal bleibt unverändert, alte Kundenarchivierung behält ihre damalige Semantik; sechs historische Löschaktionen weiterhin nicht ausgeführt.
- Endgültige Einzelentfernung über preview_purge/purge_entity und neuen fail-closed Journaltyp purge_v1. Aktueller Archivhash, ausdrückliche Bestätigung, dauerhafte Request-ID/Quittung. Minimaler Tombstone im bestehenden owner_archive, keine zweite Datenbank. Geschützte Belege sowie aktuelle/archivierte Abhängigkeiten blockieren Kaskaden. Alte Formulare/Journale/Backup-Übernahme dürfen purgierte IDs nicht neu anlegen.
- Dateibereinigung nach überprüfter Veröffentlichung und lokaler Snapshot-Übernahme, eigene wiederholbare Schritte/Belege. Referenzprüfung einschließlich aktivem Bestand, Papierkorb und ausstehenden Aktionen; gemeinsam verwendete Originale bleiben erhalten. Native Hash-/Dateinamenprüfung, Drive-REST zusätzlich ETag/If-Match; fehlender Versionsschutz bleibt blockiert. Sicherungen und historische unveränderliche Journale werden nicht überschrieben; keine Zusage einer forensischen Datenvernichtung. Provider-Löschung separat nach Rücklesen prüfen.
- Nutzerantwort verbindlich umgesetzt: keine manuelle Kunden-Entkopplungsfunktion. Fotos werden nur im Papierkorbablauf gelöst. Allgemeine Fotobibliothek unter Mehr hält erhaltene Bilder erreichbar (30 pro Abschnitt, lazy Thumbnails).
- UI: Mehr enthält Navigation; Unternehmens-/Fotospeichereinstellungen auf vorhandener Einstellungsseite. Kundenkontakt/Zusatzaktionen und zusätzliche Fotofilter aufklappbar, Fotoaktion in einem Mehr-Menü. Ein gleichzeitiger Papierkorb-Schreibvorgang; Bestätigung/Fehler lassen ursprüngliche Daten und Wiederherstellungszugang erhalten. Keine Funktion entfernt.
- Bisher PASS: 17 JVM-Suiten (neue TrashWorkflowTests zunächst 67 Prüfungen), neue UI-Prüfungen inkl. 105 unzugeordneter Fotometadaten, nativer Release-/Testquellen-Compile. Native Neustart-/Datei-Cleanup-Abnahme vorbereitet und kompiliert, nicht ausgeführt. Bestehende Browser-Tests werden auf neue sichtbare Navigation angepasst; Gesamt-Regression und finale APK noch ausstehend.
- Neuer Kandidat 1.49.4/73, Paket unverändert, Protokoll 5 mit zwei neuen Tools (87 Quellwerkzeuge). Installierter Pixel bleibt 72/Protokoll 4; neue Methoden dort nicht getestet. Konkrete Drive-Parallelstand-Barriere bleibt geschützt offen.


### Eintrag 15 – Papierkorb/Bedienung, Phase D: Qualifikation und Original-Kandidat

- Cloud-Quellbasis 16854f285991c6a406dee1eb23c45e1ccea4ff32. Vorgesehene Branch unverändert; keine Desktopübernahme, keine Historienänderung oder main-Mutation. Abschluss der Implementierung/Qualifikation: PASS; produktive Geräte-/Drive-Freigabe weiterhin NICHT erteilt.
- Zusätzliche Integritätskorrektur: bereits im Papierkorb befindliche Fotos werden beim Entfernen ihres Kunden/Auftrags/Arbeitseintrags ebenfalls versionsgeschützt entkoppelt. So entsteht keine wechselseitige Foto-/Kunden-Löschsperre. Erhaltenes Foto lässt sich nach endgültigem Entfernen eines unverknüpften Kunden ohne diesen Kunden wiederherstellen. Finanzielle Abhängigkeiten bleiben geschützt. Neuere Fotoentscheidungen blockieren nicht die Wiederherstellung des Elternsatzes und werden nicht überschrieben.
- Fehlgeschlagener Purge besitzt einen sicheren Ausstieg: nur keep_current, Originalaktion plus aktueller Archivhash geprüft; keine Anwendung eines veralteten Löschvorschlags. Queue-Quittung und Konflikthistorie bleiben erhalten. Keine Queue wird blind bereinigt.
- Automatisiert PASS: **266 Python-Tests**, **17 JVM-Suiten / 823 Prüfungen**, darunter **86 neue TrashWorkflow-Prüfungen**; **25 Browser-Suiten**, davon sync-trash mit 18 Prüfungen. Altes Kontakt-/Foto-/Einstellungs-Testing auf den realen neuen Menüweg angepasst; frühe Fehlversuche dokumentierten verborgene Knöpfe, Schlusslauf vollständig PASS. Kein Test mit Betriebsdaten. QA-Signierungstests mit echter Schlüsselerzeugung ausgeschlossen, Regressionstests erhalten.
- Browser: 105 synthetische unzugeordnete Fotometadaten, Begrenzung 30/60, lazy Thumbnails; zusätzlich vorhandene 21 sequenzielle Importe mit fehlerhafter Einzeldatei und 105 Kundenfotos getestet. Bestätigung/Abbruch, Stale Hash, stabiler Request beim Fehler/Retry, erhaltene Restore-Schaltfläche, verknüpftes Foto im Papierkorb, Settings-Navigation, Kontaktmenü, 320/360/426 px und 150%-Schrift bestanden. Screenshot-Kontrolle in Cloud Chromium; keine Aussage über Pixel-Leistung oder reale 100 Originaldateien.
- JVM: Zuordnung/Restore aktiver und archivierter Fotos, keine fremden Kundenänderungen, CAS-Rennen, geschützte Belege, minimaler Tombstone, Wiederholungen und Unterbinden gleicher purgierter IDs, geteilte Medien und Pending-Referenzen, Konfliktrücknahme, Drive-DELETE mit ETag/If-Match, fehlendem ETag/412/403 synthetisch bestanden.
- Android: assembleRelease, compileReleaseAndroidTestJavaWithJavac, lintRelease offline PASS; **0 Fehler / 17 Warnungen**. Native SQLite-/Original-/Purge-Replay-/Neustart-/Cleanup-Abnahme vorbereitet und kompiliert, ausdrücklich NICHT ausgeführt. Echte SAF-/Drive-Dateilöschung, Kamera, Hintergrundbetrieb und Crash-/Updateabnahme am Gerät nicht getestet.
- APK: downloads/Haus-Garten-1.49.4-73-original-update-kandidat.apk; **at.hausgarten.service**, **1.49.4 / 73** (> live 72 und >66), minSdk 26, target/compile 37, nicht debuggable. **34.418.378 Bytes**, SHA256 **131287f91d05d33988393ab3f444f9987f9a7bf0329b51a717dd225a5c901eab**. Unsignierter SHA256 a0e57be35c081e1c835ced6cfce04e1f0bb3e73c02904a3a744ead7011f56ded (34.411.290 Bytes).
- Originalsignierung: ausschließlich Vorhandensein aller fünf dokumentierten Variablen geprüft; ein tatsächlicher Lauf ausschließlich tools/sign_original_apk.py. Keinen Schlüssel erzeugt, keinen Fallback verwendet. Tatsächlich verifiziertes Originalzertifikat **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**. Genau ein Signer, v2/v3 gültig, v1/v3.1/v3.2/v4 false; zipalign -c -P 16 4 PASS. 405 Assets bytegleich und neue operative Klassen enthalten, Source-/APK-Privacy PASS, null operative Seedzeilen/private_markers_checked=0. Cleanup PASS: null private Signierungs-Tempordner und null Schlüsseldateien nach geprüften Endungen. Keine Geheimwerte gespeichert oder geloggt.
- Neue private APK-Drive-Datei: https://drive.google.com/file/d/1F1Dijrkqx9Rf7RoO3bGN8bU-Km6d_YNm/view . Connector-Rückabruf liefert vollständige Dateireferenz und exakt 34.418.378 Bytes. Keine vorhandene Datei oder Freigabe geändert. Lokaler SHA256 geprüft; kein unabhängiger Remote-Bytehash behauptet.
- Live-Abschluss nur lesend: Build **72**, Protokoll **4**, **85** tatsächlich angebotene Werkzeuge, Owner-Write aktiv. Neue Quellversion: Protokoll **5**, **87** Werkzeuge, Hash **0cd1b37e492eb9370c481a636f45f81c129694d60cd2d6bd25e0517a110eaab0**; zwei neue Werkzeuge noch NICHT im installierten Connector angeboten/getestet. Nach gesichertem, ausdrücklich freigegebenem Update Verbindung neu initialisieren und Werkzeugkatalog/Get-Capabilities real erneut prüfen.
- Live-Vorher/Nachher unverändert: gespeicherte Counts 51 Kunden /2 Aufträge /173 Arbeiten /95 Rechnungen /12 Fotos /32 Bausteine /1 Angebot; Geschäftshash **0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b**. Queue **51 →51** (43 Übertragungen /8 Prüfungen), Historie **1 →1**. Sieben alte Prüfungen nicht angewendet/geschlossen. Zusätzliche 13 Fotoaktionen stammen aus Benutzernutzung vor dieser Arbeit, keine erzeugten MCP-Tests.
- P0 offen: bestehender Drive-Gesamtstand parallel verändert (LEGACY_SOURCE_CHANGED, Build72 zeigt den Text noch ohne Fehlercode). Beide Stände und alle 51 Vorgänge erhalten; keine automatische Auswahl/Ersetzung. Keine Garantie, dass die bestehende Warteschlange mit diesem UI-Auftrag schon aufgelöst wurde. Datei-Cleanup wartet bis zur geprüften Veröffentlichung/Übernahme, kann bei fehlenden Provider-/Lösch-/CAS-Rechten ausstehend bleiben; Geschäftserfassung wird davon nicht blockiert. Historische Backups und unveränderliche Journale bleiben getrennt erhalten; endgültig aus der App ist keine forensische Datenvernichtung.
- Keine Installation, kein ADB/direkter Pixel-Zugriff, keine Betriebsdatenänderung; keine tatsächliche Löschung oder Konfliktentscheidung über MCP ausgeführt. Originalsignierung ist kein Update-/Datenerhalt-Test. Installation nach AGENTS.md erst nach vollständiger Sicherung und ausdrücklicher Freigabe.

- Implementierungs-Abschlusscommit: **da039031671c91d438c19f12c5a65414cdc74d9b**. Anschließender Bereitstellungscommit enthält ausschließlich die verifizierte APK, Prüfsumme und aktualisierte zentrale Download-/Abschlussangaben.
