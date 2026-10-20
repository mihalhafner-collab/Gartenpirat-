# CODEX-STATUS

Stand: 20. Oktober 2026
Repository: `mihalhafner-collab/Gartenpirat-`

> Diese Datei ist der fortlaufende technische Arbeitsnachweis für Codex Cloud.  
> Regeln: `AGENTS.md`  
> Vollständiger Auftrag: `CODEX-AUFTRAG-GARTENPIRAT.md`

## Aktueller P2-Konsolidierungsauftrag

**Sichere technische Konsolidierung bestanden; vollständige P2-Abnahme noch offen.** Ausgang `348b40a`, Wiederaufnahme ab `93ced84`, App unverändert 1.51.2/81. Drei integrierte Remote-Branches, elf alte APKs und vier temporäre Archive entfernt; drei Berichte konsolidiert. Neue Nachprüfung: alle 53 beobachteten Drive-Fotodateien aktiv/archiviert referenziert, 158 Dateien geschützt, nur drei weiterhin unbekannt. Keine Releases; sieben historische Actions-Artefakte mit automatischem Ablauf am 23./25.10.2026 inventarisiert. Alle sechs Altbackup-SHAs stimmen; bei einer alten Sicherung ist das fehlende additive Feld appointments als Ursache des inspect_backup-Fehlers belegt. Vollständige Restore-/globale Referenzabnahme bleibt offen. Vorübergehender Connector-Abbruch beendet; finaler Live-Readback bestätigt unveränderte Geschäftsdaten/139Archivversionen/sieben Backups, aktive Queue/Review/Journalfehler0. 322 Python-Tests,27 JVM-/30 Browser-Suiten/55 MCP-Szenarien und alle P0-Gates PASS, P1-Vertrag unverändert. Keine produktive Löschung. Aktuelle Tests und Grenzen in P2-7 bis P2-9; ältere Abschnitte sind zeitgebundene Nachweise.

## Bestandener P1-Refactoringauftrag

**P1: PASS, alle 12 Abnahmekriterien im definierten Cloud-Umfang erfüllt.** Ausgang `1abf049c1e27167bc16259d8717513aeb5ffe670`, Version 1.51.1/80; operativer Abschluss- und APK-Quellcommit `e8a1d09ba7bb93438fb5aaff1e6008a670339764`, Version 1.51.2/81. R0–R7 bestanden, Außenverträge unverändert: MCP 9 / 117 Tools / 156 UI-Aktionen. Alle drei P0-Gates PASS. Keine produktiven Daten für P1-Tests verwendet. Der physische Pixel wurde anschließend außerhalb dieser Cloud-Installation auf 81 aktualisiert und bei P2 lesend bestätigt; der P1-Schlussabschnitt beschreibt seine damaligen Prüfgrenzen.

## Bestandener P0-Datenschutzauftrag

Ausgang `aa04cbd04d2035a877acd243af612a2fc97f5519`, sauberer aktueller main; Implementierungscommit `7725f425e57d8a40733b0e798def1e279c8488ec`, anschließender Abnahme-/Härtungscommit über `git log -1`. **P0 für aktuellen Checkout und zukünftige Builds: PASS,14/14 Kriterien.**297 Python-Tests/24 JVM-/29 Browser-Suiten/55 MCP-E2E-Szenarien/134 native Android-Proben sowie Emulatorupdate77→80, WebView und isolierter Erststart bestanden. Alle drei Privacy-Gates PASS. Keine produktiven Daten kopiert oder geändert. Historische Git-Befunde und physischer Pixel-/echter Provider-/Connector-Endtest separat offen. Vorherige Zwischenstände/NOT-TESTABLE-Startversuche unten sind historische Phasen.

## Historischer Ausgangsstand

- Auftrag: **P0 Pixel/Drive Cloud-only Sync abschließen**
- Operativer Zielpfad: Pixel ↔ Google Drive direkt
- Entwicklungsweg: ausschließlich Codex Cloud
- Repository-Version: **1.49.5 /74**, Originalpaket; Mehrfacharchivierung, Sync-Normalisierung, WebView-Kompatibilität und Entwurfsreparatur. Cloud-Emulatorabnahme PASS, Pixel-/Google-Abnahme offen.
- Live-Pixel bei neuer Bestandsprüfung: **1.49.4 / 73**, Protokoll 5, 87 Laufzeitwerkzeuge, Owner-Write aktiv (nur gelesen).
- Live `offers_sync_ready`: true
- Live `service_catalog_sync_ready`: true
- Live `cloud_confirmed`: false
- Live Queue vor der aktuellen MCP-Testphase: 8 Review-Aktionen, 0 Uploads; 1 archivierte Prüfung. Sieben historische Fälle unverändert, zusätzlich ein älterer fehlgeschlagener Archivierungsauftrag. Aktuelle synthetische Testaktionen werden gesondert bilanziert.
- Historischer Zwischenstand nach früheren MCP-Tests: **38 = 8 bestehende Prüfungen + 30 synthetische Test-/Archivaktionen**. Kein neuer Drive-Beleg; lokaler Cleanup ist noch nicht synchronisiert.
- Aktuelle Live-Queue: **183 = 175 Übertragungen + 8 Prüfungen**, 1 historische Entscheidung. Neue Benutzeraktionen seit der Stichtagsbereinigung vorhanden; in diesem Prüfauftrag keine produktiven Schreibtests. Drive-Abschluss wegen LEGACY_SOURCE_CHANGED weiterhin offen.
- `get_business_settings`: lesbar; nur bekannte historische Ausstellerfelder, Steuer-/Nummernfreigaben nicht bestätigt.
- Quelle: bestehende operative App vollständig erhalten; 74 ergänzt Mehrfacharchivierung und behebt nativ reproduzierte Fehler. Pixel weiterhin73, reale Queue183 und LEGACY_SOURCE_CHANGED zuletzt nur gelesen. Falschpositiver Normalisierungsfall im Quellcode behoben, tatsächlich abweichende Geschäftsdaten weiter geschützt. Keine Pixel-Installation, keine produktive Sync-/Konfliktentscheidung. Stichtagsbereinigung aus Einträgen16/17 erhalten. **Gesamtauftrag wegen ausstehender Pixel-/Google-Abnahme NICHT vollständig freigegeben.**

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
- `id_sha256_ca46d340bc52b9e93e578b4a47c1afa08b11c87a013e5c9dee7cd7624bc12824`
- `id_sha256_57c833b527f0ca0f4da04e79ff2ca787c57ab9a891833becee4c3e4faccf3914`
- `id_sha256_81dda83b42bd64510013bec7217cae7c4e9b01537341db98abc6f91ed52ca52a`
- `id_sha256_c09057d51603a99907adeef4bb7e9c332c97e4ddd2fbdf687ccfb45e83d35264`
- `id_sha256_2a69731a26c376695ce26e7d1788647b6ac11aa887024a8b3c981d84b2a5e2dd`
- `id_sha256_c28c6391b7e050a97a7034e9ae7622d4f3c9bf73c4d9e5e893fde925e9abe1bd`
- `id_sha256_04ecd644998e4d3962e834a23db5feeb936ea7d85d055acc300c448fd4ea7899`

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
- Synthetische IDs: Kunde `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e`; Auftrag `id_sha256_2248b4da5ada771c10deeb5c060b378603875e3389c0524478469d14844ca9f9`; Arbeit `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f`; Entwurf `id_sha256_abbdad18e36cf5365aa24e00762ad87fcb9f14c656603af9a9b1f1e185d63217`; Fotos `id_sha256_8cbd663a374da3be09cd94c037a3025e77341079fc33f85d08a54ba4e9933abc`, `id_sha256_06e7df1f19442bfb007e6d46dc69a5dc3f771862668078e6906f50bfd14071af`; Angebot `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5`; Baustein `id_sha256_494ef829768228263de344ca1e8feb695eddc5c41ec3398da289b1674618728f`.

<details>
<summary>30 neue synthetische Journalaktionen für die spätere Belegprüfung</summary>

| client_id | Art | Synthetische Entität |
|---|---|---|
| `mcp_pixel_3dbdcb71bcfeeacc77ef16acd4f56ea3409bf6ac` | order | `id_sha256_2248b4da5ada771c10deeb5c060b378603875e3389c0524478469d14844ca9f9` |
| `mcp_pixel_865559d81eba43a3e55fbf1c46d7144c38291f05` | order | `id_sha256_2248b4da5ada771c10deeb5c060b378603875e3389c0524478469d14844ca9f9` |
| `mcp_pixel_f5d406529401d97a4a3e69e3610d3615df0e3007` | worklog | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `mcp_pixel_be8230f383d7a760522af8eb5a9c2d4072376c6d` | worklog | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `photo_change_b173108b-6808-4768-9ac5-a88809254202` | photo | `id_sha256_8cbd663a374da3be09cd94c037a3025e77341079fc33f85d08a54ba4e9933abc` |
| `mcp_owner_e6009ed5da9f9b59ee0052564b7e412fd2c989710652cb8aa5b4e7fcfb0d47a0` | photo | `id_sha256_8cbd663a374da3be09cd94c037a3025e77341079fc33f85d08a54ba4e9933abc` |
| `mcp_pixel_5fad9aac1d3c265ba4e34dbdc549fad70deb3b54` | invoice | `id_sha256_abbdad18e36cf5365aa24e00762ad87fcb9f14c656603af9a9b1f1e185d63217` |
| `mcp_pixel_2fc08b3849ed3422c465b7548755d452c0a14914` | invoice | `id_sha256_abbdad18e36cf5365aa24e00762ad87fcb9f14c656603af9a9b1f1e185d63217` |
| `mcp_owner_109b45a637af58056e51bd1400494fcaeef8640554ac8d8ecf347205c47a1de9` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `mcp_owner_98964f7240f95746b88b46f34461bbf40fb049acd9f108f275f554db76517f38` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `photo_change_d82ee904-3a41-4512-b955-ae039e57dfab` | photo | `id_sha256_06e7df1f19442bfb007e6d46dc69a5dc3f771862668078e6906f50bfd14071af` |
| `mcp_owner_3ba2b1aea9e8eb1833fac42036a37ad554fb18b3e1992ed5f0fca918240ed8a4` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `mcp_owner_9ee3eea31c7922722870a45e5668d178be6a67df88b5c2e59c933cd1d522fa79` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `mcp_owner_4c20f8c5fcf095a01628ed1eb9cc666fdcfeb26dc0bd24aa76a4471cbb839d33` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `mcp_pixel_d5c69e42db79333ee3f84ca62124788c12b26c3c` | customer | `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e` |
| `mcp_pixel_189665c3d9988867a93bbfc5d74ebeefd0c10056` | customer | `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e` |
| `mcp_owner_e334a11ea03e913c49e81ff3f5108661f8918a6fb39fd97d17f523e99a23226a` | service_template | `id_sha256_494ef829768228263de344ca1e8feb695eddc5c41ec3398da289b1674618728f` |
| `mcp_owner_d5cbd686add476c13461db3d6dd2c21bec87fe96d6f1d0fba414dd9069baf0cb` | archive | `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e` |
| `mcp_owner_c058585c0525d592e4274c404d0028504213cba4805e35eb9866592d13e053c1` | service_template | `id_sha256_494ef829768228263de344ca1e8feb695eddc5c41ec3398da289b1674618728f` |
| `mcp_owner_f2d9a9e105654f267f0dfe424d30686a8748a947d956bf466edd55fecf92ef5d` | restore | `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e` |
| `mcp_owner_c79539cc896f180248ec536553c7e195b98dc04610af8203054c6f535a6c1b74` | archive | `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e` |
| `mcp_owner_b3dc470efa9ee77d5a65df1d865f2ff265eb7accc12811df63b006509f4b78f7` | worklog | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `mcp_owner_74d17eed7fa434c230c4e3078b976ae42bda726509a737a79c13199072bacea7` | worklog | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `mcp_owner_545331ff3cc5b786d96f025d96ca7698585aaa1805570f6fcb4c93f1ff8c9a64` | worklog | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `mcp_owner_cfd7d3d752798b94e0a077ac414ba80325124dd06c2f6e1bc1db6fb9052d7e31` | archive | `id_sha256_8cbd663a374da3be09cd94c037a3025e77341079fc33f85d08a54ba4e9933abc` |
| `mcp_owner_a4d7bcf72f83992e29dd876e789bdf588e79599311b0df55e68f109027b962f2` | archive | `id_sha256_06e7df1f19442bfb007e6d46dc69a5dc3f771862668078e6906f50bfd14071af` |
| `mcp_owner_f17a6bee01bd9064f92e786b59092327e91f18e82958fe49cf430ba9d99c5cfe` | archive | `id_sha256_abbdad18e36cf5365aa24e00762ad87fcb9f14c656603af9a9b1f1e185d63217` |
| `mcp_owner_4707ed8c6bbeb5809f441731647f00a2d17b46630d4faa05ecc6f0d7b538cc36` | archive | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `mcp_owner_1011d4301e40f240268203f722c479d9dddae9fc832bdef89c3ee322efa3b667` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `mcp_owner_55d3c61c4271a2a7d775274bb1449c7d0199a7364c711c71c1b10de011b153b5` | archive | `id_sha256_2248b4da5ada771c10deeb5c060b378603875e3389c0524478469d14844ca9f9` |

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
| `id_sha256_ca46d340bc52b9e93e578b4a47c1afa08b11c87a013e5c9dee7cd7624bc12824` | DELETE_REVIEW_REQUIRED | `d4ad7e0bb45f60ed925cd3fa294343aa44efe84ed0ca2627ac9c63bd74db74e8` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |
| `id_sha256_57c833b527f0ca0f4da04e79ff2ca787c57ab9a891833becee4c3e4faccf3914` | DELETE_REVIEW_REQUIRED | `bb84d7424474d267d75981f29cc69914ff4bc7054e82732feae089b46d170364` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |
| `id_sha256_81dda83b42bd64510013bec7217cae7c4e9b01537341db98abc6f91ed52ca52a` | DELETE_REVIEW_REQUIRED | `81fdd1bac9a83b6f81502513dacd59e58eac4e1ff10b7889b657372478d519ce` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |
| `id_sha256_c09057d51603a99907adeef4bb7e9c332c97e4ddd2fbdf687ccfb45e83d35264` | DELETE_REVIEW_REQUIRED | `06a95cf238bd35098f5b3dd9147b16b26eaf063e4fe504f154c22859ded65c7b` | `78957fa4902bb23535b4e994935a1d9794680abc2f6f1be7ba43f3fb5dbf1123` |
| `id_sha256_2a69731a26c376695ce26e7d1788647b6ac11aa887024a8b3c981d84b2a5e2dd` | DELETE_REVIEW_REQUIRED | `2b45a83fc17f6eb72163f0a58b3779f7904259518b22dece9a7e88120f54bb41` | `d205eab650bc4f727a52ddfa2f29cfcaa7e7d3697820d28076aa01b845313d0e` |
| `id_sha256_c28c6391b7e050a97a7034e9ae7622d4f3c9bf73c4d9e5e893fde925e9abe1bd` | WRITE_CONFLICT | `258d26b477b41ec0a3f097d893217e8c22c988071eb11a98d6a03d89d9ea0701` | `30599c5c7807db1f9ccc8ceb0381df8c914ba43b38f3f7494b49b08d3324ae6f` |
| `id_sha256_04ecd644998e4d3962e834a23db5feeb936ea7d85d055acc300c448fd4ea7899` | DELETE_REVIEW_REQUIRED | `d5a7f9020c75ec994d0d8b09aee5a94cc2b53648bce8bf6f4cc81f9f8c237a8a` | `e3c8da0a874c1b44a7a1d27cdbe63ff1b06ccbe20fd77508a7d56be87561d7e6` |
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


### Eintrag 16 – Stichtagsbereinigung / Phase A: Auswahl und Sicherung

- Neuer ausdrücklicher Betriebsauftrag: aktive Arbeitsaufzeichnungen mit Arbeitsdatum bis einschließlich 2026-07-31 entfernen; ab 2026-08-01 vollständig erhalten. Standard ist der wiederherstellbare Papierkorb, keine endgültige Vernichtung und keine Änderung an Kunden, Aufträgen, Fotos oder Rechnungen.
- Ausgang 6f2ae1530e4c065cdba839684069ef87a97840f1, sauber auf vorgesehener Branch, Pflichtancestor vorhanden, Remote identisch, main a38765d unverändert. Pixel 1.49.3/72, Protokoll 4, 85 Werkzeuge, Write aktiv. 73 nicht installiert.
- Vollständige Auswahl durch zwei nicht abgeschnittene Zeitfenster: bis 2026-03-31 49 Einträge; 2026-04-01 bis 2026-07-31 79 Einträge. Zusammen 128 eindeutige IDs, gültige Datumswerte, keine Fotoverknüpfung und keine manuelle billed-Markierung. Neuere sichtbare Einträge: 46, im Arbeitsspeicher für vollständigen Vorher-/Nachher-Vergleich festgehalten; keine Betriebsinhalte in Dateien/Logs gespeichert. Rechnungs-/Versionsschutz wird vor jeder Änderung durch denselben nativen Owner-Pfad geprüft.
- Neue private App-Geschäftssicherung erstellt und inspect_backup bestätigt: backup_7fd0a667d4708f0678e3f3c19e49c9feca3fcbea36b7e9c306cb2dea93b3ea28, 22.711.249 Bytes, SHA256 776685e56ded05e41ae9363dfb9861dffbe83a34862845a57dbd62958c89e19e, mit Snapshot/Pending/lokalen Originalen und Dokumenten, ohne Zugangsdaten. Ausdrücklich keine vollständige Gerätesicherung.
- Ausgangssync 51 (43 Übertragungen /8 Prüfungen), Historie 1, gespeicherter Geschäftshash 0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b. LEGACY_SOURCE_CHANGED weiter offen; keine Drive-Datei/alte Aktion überschreiben, keine Cloud-Bestätigung behaupten.
- AGENTS.md ergänzt: aktuelles Design nicht permanent; eigenständige praktische Design-/Navigationsverbesserungen erwünscht. Neue operative Arbeitsaufzeichnungen künftig über bestehende App; keine selbstständige Anlage per MCP/Import. Bestehende Funktionen und Tests erhalten.
- Nächster Schritt: Datensatzhashes lesen, dann versionsgeschützte Einzelarchivierungen mit stabilen Request-IDs. Ausnahmen nicht übergehen; anschließende vollständige Prüfung des erhaltenen Bestands.


### Eintrag 17 – Stichtagsbereinigung / Phase B/C: tatsächlich ausgeführt und rückgelesen

- Auf ausdrücklichen Nutzerauftrag **128 aktive Arbeitsaufzeichnungen mit work_date <= 2026-07-31** per bestehendem Pixel-MCP in den wiederherstellbaren Papierkorb verschoben. Keine endgültige Löschung, kein Datei-Purge, keine Änderung an Rechnungen, Fotos, Kunden oder Aufträgen und keine neue Arbeitsaufzeichnung erzeugt. Zeitraum bezieht sich auf das Arbeitsdatum, nicht auf Import-/Erstellungszeit.
- Vollständige Auswahl 49 +79 eindeutige Datensätze aus zwei nicht abgeschnittenen Lesefenstern. Neun native entity_hash-Werte mit dem unveränderten nativen Worklog-Guard/Kanonisierungsweg abgeglichen; übrige Vergleichshashes ausschließlich im Arbeitsspeicher aus dem gelesenen Stand berechnet. **Jede einzelne Änderung vom nativen Transaktions-/Hash-/Referenzschutz geprüft**, stabile request_id pro ID/Stand, begrenzte Wiederholung bei Netzfehlern; keine blinde Hashaktualisierung bei geänderten Fachfeldern. Keine Betriebsinhalte in Shell, Quellcode, Fixtures oder Repository geschrieben.
- Ergebnis: **128 Speicherbestätigungen /0 Fehler**. Aktive Liste bis einschließlich 31.07.2026 nach vollständigem erneuten MCP-Abruf **0**. Aktive Liste ab 01.08.2026 weiterhin **46**, sämtliche zurückgelieferten Datensatzfelder exakt gleich zum Ausgang; SHA256 der kanonisch sortierten Vergleichsliste vorher/nachher **8d2383e0f290fc814b700693dba4577bfde2779c125364d9c56db901a87b4007**. Keine fremden oder neueren IDs entfernt.
- Papierkorb anschließend vollständig über zwei Seiten gelesen: **134 =6 vorherige +128 neue Einträge**. Für alle 128 IDs Arbeitsdatum und sämtliche Worklog-Guard-Felder gegen die Ausgangslesung geprüft, 0 Abweichungen; die sechs vorherigen Archive vollständig unverändert. Alle entfernten Arbeitsaufzeichnungen bleiben mit Original-ID und ursprünglichen Fachwerten wiederherstellbar.
- Ebenfalls exakt gleich zum Ausgang: **95 Rechnungen** und **26 aktive Fotometadatensätze**, nicht nur die Anzahlen. Keine Fotoverknüpfung unter den 128 entfernten Arbeiten. Keine Rechnung finalisiert, bezahlt markiert oder verändert; persönliche b.b.-Markierungen und Timer nicht bearbeitet.
- Neue Geschäftssicherung aus Eintrag 16 vor jeder Archivierung bereits erstellt und per inspect_backup zurückgelesen. Keine vollständige Gerätesicherung behauptet; keine Wiederherstellung ausgeführt.
- Alle **51 vorherigen Queue-Einträge** nach erneutem MCP-Abruf vollständig unverändert (Inhalt und technische Zustände), keine alte Aktion umgeschrieben oder entfernt.
- Synchronisation danach **179 =51 bestehende Vorgänge +128 neue Archivierungen**, davon **171 Übertragungen /8 bestehende Prüfungen**, Konflikthistorie **1**. Quelle bleibt der lokal gespeicherte Pixel-Stand mit ausstehenden Journalaktionen; Rohsnapshot-Geschäftshash weiterhin 0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b. Rohsnapshot wird erst nach geprüftem Drive-Abgleich fortgeschrieben; das ist vom tatsächlich geprüften effektiven App-Bestand getrennt.
- **cloud_confirmed=false**, confirmation_scope=provider_readback. Bestehender Parallelstandfehler weiterhin offen. Kein Sync-Erfolg erfunden, keine Drive-Datei überschrieben, keine alten Löschaufträge angewendet oder Konflikte geschlossen. Die autorisierten Archivierungen sind lokal wirksam, ihr Drive-Abschluss bleibt ausstehend.
- AGENTS.md ergänzt: Design ist veränderbar und darf selbstständig sinnvoll optimiert werden; alle Funktionen, Datenintegrität und Entwürfe schützen. Neue operative Arbeitsaufzeichnungen künftig über bestehende App; keine eigenständige Anlage durch MCP/Import ohne späteren abweichenden Auftrag. Bestehende MCP-Funktionen und synthetische Tests erhalten.
- Validierung: echter MCP-Vorher-/Nachher-Vergleich und Archiv-Rücklesen PASS; git diff --check PASS. Nur Regel-/Statusdateien im Quellrepo geändert; kein neuer App-Build/Signierlauf oder irrelevanter Regressionstestlauf erforderlich. Keine Installation, kein ADB/direkter Gerätezugriff. main und Historie unverändert.

### Eintrag 18 – Gesamttest / Phase A: Bestandsprüfung und Abnahmeplan

- Ausgang **fb8f1635a5facee60073d1773030dc5552fde6b5**, sauber, vorgesehene Branch, Pflichtancestor vorhanden. main a38765d unverändert. Remote-Ref zeigt nach fetch noch 36e5567; lokale neuere operative Historie bleibt erhalten, kein Reset/Import. Regeln, Zentralauftrag/-status, Reparatur-/Signierungs-/Sync-/Owner-Dokumentation und Issue #1 gelesen.
- Nur lesend real geprüft: Pixel 1.49.4/73, Protokoll 5, 87 Tools, Write aktiv, DB4, pixel_drive_v1, Angebote/Katalog bereit. Queue 183 (175 Übertragungen/8 Prüfungen), Historie1, Provider-Freigabe vorhanden. LEGACY_SOURCE_CHANGED schützt weiterhin zwei unterschiedliche Gesamtstände, cloud_confirmed=false. Keine Testaktion am Pixel, keine alten Konflikte gelöst.
- Abnahme dieses Auftrags: vorhandene Python-/JVM-/Browser-Regressionssuiten; echte native SQLite-/MCP-/Medien-/Drive-Provider-Instrumentierung auf isoliertem Cloud-Emulator; operative WebView und Navigation/Entwürfe, schmale Ansichten/große Schrift prüfen. Alle Tests synthetisch; keinerlei produktive Datenübernahme. Pixel-/Google-End-to-End-Nachweise separat ausweisen.
- Verbesserungsziel: mehrere ausgewählte Arbeitsaufzeichnungen in einem geprüften lokalen Vorgang in den wiederherstellbaren Papierkorb verschieben; stabile Request-ID, pro Datensatz Version/Fotobeziehungen und Finanzschutz, atomarer Abbruch bei Änderungen. Keine automatische endgültige Massenlöschung. Aufwand/Fehler vorher/nachher messen; alle bestehenden Funktionen/Tests erhalten.
- Emulator: SDK/ADB vorhanden, aber kein /dev/kvm; Softwareemulation wird mit offiziellen, größen-/prüfsummengeprüften Google-Paketen vorbereitet. Keine Verbindung zum Pixel, keine zweite operative App. Erste konkrete statische Fehlfunktion: Instrumentierungsmanifest verweist noch auf alte .qa-Paketkennung statt auf at.hausgarten.service; vor nativem Test korrigieren.
- Status PASS Bestandsprüfung; Tests/Emulator/Fehlerbehebung in Arbeit. Nach jeder abgeschlossenen Phase diesen zentralen Nachweis aktualisieren.

### Eintrag 19 – Gesamttest / Phase B: Ausgangsregression und reproduzierte Fehler

- PASS: 266 Python-Tests (QA-Schlüsselerzeugung ausgeschlossen), 17 JVM-Suiten /823 Prüfungen. Browser zunächst 24/25; Sync-Detailprüfung einmal FAIL (aufgeklappte Prüfung während Status-Neurendering), isolierte Wiederholung PASS. Keine pauschale Erfolgsaussage für den ersten Lauf.
- Fehlerkorrekturen: Instrumentierungsmanifest auf Originalpaket umgestellt. Bestehender androidTest-Runner darf gezielt mit demselben Originalschlüssel über tools/sign_original_apk.py --instrumentation signiert werden; nur feste .test-Kennung, genau ein Original-Runner/Zielpaket, keine Activities/Services/Provider/Receiver, ausdrücklicher Modus, unveränderter Normalmodus und Cleanup. 41 synthetische Signierungsprüfungen PASS, kein Schlüssel erzeugt.
- Zwei weitere konkrete Befunde: reine Papierkorb-Vorschau legte über related/purged ein leeres Archivobjekt an; diese Lesewege verändern ihre Eingabe jetzt nicht mehr. Sync-Zentrale ersetzte unveränderte Queue-DOM bei Statusänderung und verlor dadurch Fokus/Bedienzustand; gleiche Queue wird jetzt stehen gelassen. Native get_capabilities berechnet dieselbe effektive Geschäftssicht einmal statt mehrfach; Löschvorschau ermittelt Fotobeziehungen nur einmal.
- Neue atomare Mehrfacharchivierung bis 200 ausdrücklich ausgewählte Arbeiten: vorhandene einzelne archive-Journale, keine neue Löschpipeline. Vorschauhash bindet IDs, Datensatz-/Fotovergleich und Finanzschutz; ein geänderter/geschützter Eintrag stoppt den ganzen lokalen Vorgang. Dauerhafte Request-Quittung und SQLite-Transaktion; keine endgültige Massenlöschung. UI: Auswählen → angezeigte/Einzelarbeiten markieren → ein bestätigter Papierkorbvorgang. Auswahl/Fehler bleibt sichtbar, b.b./Menüs während Auswahl verborgen, Funktionen nach Abbruch wieder vorhanden.
- Neue JVM-Auswahl-/CAS-/Fotobeziehungs-/Finanzschutz-/Replay-Regression: 21 Prüfungen PASS, 128 synthetische Journalaktionen in 63 ms vorbereitet (Host-JVM, keine Pixelmessung). Native Transaktionsabbruch-/Neustart-/Retry-Prüfung ergänzt; tatsächliche Ausführung noch ausstehend.
- Offizielle Emulatorpakete SHA1/Größe gegen Google-Manifest geprüft; Cloud-Android30 x86_64 läuft ohne KVM, erster Systemstart dauert erheblich länger. Frühe Installation vor fertigem PackageManager wurde vom Emulator mit StorageManager-Startfehler abgelehnt; kein App-Fehler, Installation erst nach Systembereitschaft wiederholen. Kein Pixel-/Betriebsdatenzugriff.

### Eintrag 20 – Gesamttest / Phase C: echter Android-Lauf und ursächliche Reparaturen

- Cloud-Emulator Android11/API30, x86_64, WebView83, ausschließlich dedizierte AVD GartenpiratCloud / emulator-5580. Originalsignierte operative App 73 frisch installiert; vorhandene Instrumentierung mit demselben Originalzertifikat, keine QA-App und kein neuer Schlüssel. Softwareemulation ohne KVM: langsamer Erststart und SystemUI-ANR; keine Pixel-Leistungswerte daraus ableiten.
- PASS echte Fresh-Install-Prüfung: leerer Geschäftsbestand, keine Zugangsdaten/automatische Cloudverbindung. PASS echtes In-place-Update 73→interner 74: vollständiger synthetischer Snapshot/IDs/Pending/Einstellungen/Tunnelkonfiguration und laufender Timer unverändert; keine Deinstallation/Datenlöschung. Dieser Emulatornachweis ersetzt keine Pixel-Sicherung/-Freigabe/-Updateabnahme.
- Erster vollständiger nativer Funktionslauf: **90 PASS /10 FAIL**. Archiv-/Undo-Tests erwarteten noch CANCELLED statt erhaltenem Archivauftrag plus Restore; Capabilities erwarteten Protokoll4 statt5; Rechnungstest erwartete Abschlussvormerkung ohne bestätigten Aussteller und Sync ohne eingerichteten Zugang; zwei PDF-Negativtests erwarteten IllegalArgumentException statt korrekt zurückgewiesener IOException. Erwartungen an aktuelle fachliche Regeln angepasst, Schutzmechanismen beibehalten. Unvollständige alte Basisfixture um leere Fotosammlung ergänzt.
- Echte UI-Fehlfunktion im Emulator: WebView83 konnte vier ||= Stellen nicht parsen, wodurch Navigation/mehrere Funktionsmodule ausfielen. Syntax auf ES2020 zurückgeführt; alle operativen Skripte mit Acorn auf ES2020 geparst. Kleines zuerst geladenes Kompatibilitätsmodul für Array.at, Object.hasOwn, replaceAll und sichere UUIDv4 mit crypto.getRandomValues; kein unsicherer UUID-Fallback. Test mit entfernten modernen APIs und 1000 sicheren IDs/fehlender Entropie PASS. Native WebView-Neuprüfung vorbereitet.
- Echte Sync-Ursache aus drei nativen Wiederholungs-/Neustarttests: run normalisierte fehlende offers/service_catalog, publish/readPrimary verglichen teils den Rohstand. Konsistente additive Normalisierung jetzt direkt in DrivePrimaryState.recover auf einer Kopie. Exakt nachgewiesener alter Rohhash bleibt kompatibel; abweichende Geschäftsdaten weiterhin LEGACY_SOURCE_CHANGED. Neuer Hosttest 9 Prüfungen PASS; Originaleingabe unverändert. Keine produktive Drive-Datei ersetzt, kein Konflikt als gelöst behauptet.
- Weiterer echter Neustartfehler behoben: Bestätigungs-IDs für fehlende alte Fotoanhänge wurden noch aus veralteten Preferences statt dem maßgeblichen SQLite-Snapshot gelesen. Jetzt snapshotText inklusive Legacy-Fallback; Regression mit echtem commitSnapshot, verlorenem Ack und erneut fehlendem Original ergänzt. Unbestätigtes Original bleibt als fehlend sichtbar und blockiert unabhängige Aktionen nicht.
- Mehrfacharchivierung nativ bereits PASS: künstlicher SQLite-Abbruch mitten im Vorgang hinterlässt null Teilaktionen; gleicher Request danach erfolgreich, Replay und Store-Neustart erhalten 128 Archivaufträge. Ein lokaler Commit 9.270 ms im unbeschleunigten Emulator (keine Pixelmessung). Hostvorbereitung 61 ms. UI mit alten WebView-APIs, 128 synthetischen Arbeiten, Auswahl/Abbruch/CAS/Retry und 320/360/426px/großer Schrift PASS.
- Code vereinfacht: archive_entity/delete_entity teilen einen nativen Archivhandler; keine zweite Löschpipeline, keine Funktion entfernt. Additive MCP-Erweiterung auf 89 Quellwerkzeuge bei weiterhin kompatiblem Protokoll5; installierter Pixel meldete zuletzt87, neue Methoden dort nicht behauptet.
- Reale MCP-Lesetests: 14 sinnvolle Leseaufrufe erfolgreich (Gesundheit, Timer, Einstellungen, Business Settings, Angebote, Vorlagen, Pflanzen, Konflikte, Archiv, Kunden mit Suchbegriff, Aufträge, Arbeiten, Rechnungen, Fotos). Leere Kundensuche als ungültige Pflichtangabe zurückgewiesen, danach gültige Suche PASS. Keine produktiven Schreibtests, keine alten Prüfungen geschlossen.
- Aktueller Stand: 267 Python-Tests PASS, 26 Browser-Suiten PASS; zusätzliche Kompatibilitätssuite PASS. Zweiter vollständiger nativer Lauf und finale Artefaktprüfung folgen. P0 Live-Drive-Abnahme und echte Pixel-/Kamera-/Kontofreigaben bleiben offen.


### Eintrag 21 – Gesamttest / Phase D: Qualifikation

- PASS **270 Python-Tests**, **20 JVM-Suiten /864 Prüfungen**, **100 native Android-SQLite/MCP/Provider-Prüfungen /0 Fehler** im isolierten Cloud-Emulator. Frühere native Läufe 90/10 und99/1 bleiben oben nachvollziehbar; nach Ursachen-/Erwartungskorrektur kein fehlgeschlagener Prüffall im abgeschlossenen nativen Schlusslauf.
- PASS tatsächliche native WebView83: Start aller Module, sichere API-Fallbacks, Navigation, Arbeits-Mehrfachauswahl/Abbruch, Kunden-Gartenansicht und Papierkorb. Erstes WebView-Fail deckte zusätzlich fehlendes replaceChildren auf; entsprechender kleiner Element-/Fragment-Fallback plus Browserregression mit entfernten APIs ergänzt. Synthetische UID/Datensätze, keine Pixel- oder Google-Kontotests.
- Rechnungsentwurf bei fehlendem oder leerem zentralen Steuermodus repariert: bleibt ausdrücklich unconfigured/editierbar, kein Steuermodus erfunden. Endgültige Ausstellung weiter blockiert; sechs neue Hostprüfungen plus native Entwurfs-/Abschluss-Trennung PASS. Weitere alte native Assertions an aktuellen Bestätigungs-/Queue-Ablauf angepasst; bestätigte Create-Aktion nicht mehr als Pending gezählt.
- Syntax-/API-Fallback und Status-DOM, gemeinsame Archivhandler und native Snapshot-Belegquelle geprüft. Auswahl ignoriert reine Transport-/Anzeigemetadaten; echte Feld-/Version-/Fotobeziehungsänderungen bleiben geschützt. Mehrfacharchivierung bis200, keine automatische endgültige Sammellöschung. Lokaler Abbruch/Replay/Neustart real geprüft: 128 Aktionen, letzter nativer Commit6.806ms im Softwareemulator; keine Pixel-Leistungsbehauptung.
- Sichtprüfung: Browser320/360/426px plus150%-Schrift und echte Android-WebView. Native Aufnahme zeigte schwebenden Gartenpirat über Stop; anschließend aktiven/pausierten/noch abzuschließenden Timer von Überlagerung befreit. Gartenpirat/alle Pflanzenfunktionen über Mehr weiter erreichbar, beweglicher Knopf im Ruhezustand erhalten. Neun gezielte Browserprüfungen einschließlich altem WebView-API-Profil, Transportwechsel, Stop-Zugriff und Pflanzenmenü PASS; erneute native Bedienungs-/Sichtabnahme dieses letzten UI-Schritts PASS; Stop frei und Pflanzenmenü erreichbar.
- UI-Schlussänderung ändert keine native App-Bytecode-Datei: classes.dex SHA256 cce57dfe7ae9d57da6de664ef8b6eb0e038e80b3b34d1e94dd70c0cdd5860bb6 ist bytegleich zum 100/0 geprüften nativen Stand. Native Instrumentierung nur erweitert; keine Geschäftslogik nach erfolgreichem Lauf geändert.
- Live-Abschluss ausschließlich lesend: Pixel73/Protokoll5/87Tools/Write aktiv. Queue183 (175 Übertragungen/8 Prüfungen) samt jedem Eintrag exakt gleich zur Bestandsprüfung; gespeicherter Geschäftshash unverändert0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b. cloud_confirmed=false, provider_readback, LEGACY_SOURCE_CHANGED weiter gemeldet. Die Quellkorrektur ist dort nicht installiert; keine Aussage über erfolgreiche Übernahme in echtes Drive, keine Konfliktentscheidung ausgeführt.

| Funktionsbereich | Tatsächliche Prüfung | Reale Grenze |
|---|---|---|
| Kunden/Garten/Aufträge | native Create/Update/Dubletten/Hash/Offline-Beziehungen, Browserformulare; Pixel-MCP lesend | kein produktives Testschreiben |
| Arbeitsaufzeichnungen/b.b. | native Speicherung/Version/Finanzschutz/Archiv/Restore; Browserfilter/Jahreswechsel/3000 Datensätze; Mehrfachauswahl | neue operative Arbeiten weiterhin nur über App |
| Timer/Pausen | native Start/Pause/Fortsetzen/Stop/Abschluss/Replay/Neustart; Browserbedienung | echtes Pixel-Hintergrund-/Akkuverhalten offen |
| Fotos/Dateien | native Chunk-Upload, Hash, Original, Thumbnail, Rotation/Optimierung,21 Fotos, Zuordnung/Neustart; Browser105 Metadaten/Pagination | keine100 Originaldateien/physische Kamera oder reale Gerätemedienabnahme |
| Rechnungen | native Entwurf/Änderung/PDF/Korrektur/Zahlung/Share-Handoff/Abschlusssperre; JVM Nummern-CAS/Transaktion | keine echte Rechnung ausgestellt/versendet/bezahlt; Nummernservice im echten Drive nicht abgenommen |
| Angebote/Leistungskatalog | native Create/Replay/Validierung/Finalisieren/PDF getrennt von Rechnung; JVM vier Arten/Katalog/Kontext; Browser Auftragübernahme | keine produktiven Angebote geändert |
| Pflanzen/Neophyten/Bilder |181 Profile/362 Bilder, kanonische Spiegel; Browser Suche/Wischen/Offline/Piratenknopf | keine vollständige botanische Neuprüfung |
| Sync/Konflikte/Medien | echter Android-DocumentsProvider mit synthetischem Speicher,7Wiederholungen,Abbruch/Readback/Originale/Folgestand | kein echter Google-Drive-Serverbeleg;183Produktivaktionen erhalten |
| Sicherung/Restore/Migration | native Geschäftssicherung/Inspektion/identischer Restore/Stale-Abweisung, SQLite-/Altbildmigration | keine Pixel-Gerätesicherung/Wiederherstellung |
| MCP/Rechte/Tunnel | native Dispatch/Schreibpfade, JVM Schema/entzogene Rechte/Dateiblöcke,14reale Leseaufrufe | zwei neue Quelltools erst nach Update/Connector-Refresh; externe Kontorechte separat |
| Oberfläche |27 Browser-Suiten, native WebView-Start/Navigationsprüfung; Timer-Overlay-Abnahme PASS | Softwareemulator ohneKVM ist keine Pixel-Leistungsmessung |


### Eintrag 22 – Gesamttest / Phase E: geprüftes Artefakt und Übergabe (19.10.2026)

- Cloud-Qualifikation abgeschlossen: 270 Python-Tests,20 JVM-Suiten/864 Prüfungen,27 Browser-Suiten und100 native Android-Prüfungen PASS. Zusätzlicher nativer WebView83-UI-Lauf inklusive Mehrfachauswahl, aktivem Timer ohne verdeckten Stop, Pflanzenzugang über Mehr, Garten und Papierkorb PASS. Letzte Timer-UI-Änderung danach vollständig im Browser und auf echter Emulator-WebView nachgeprüft; nativer Bytecode identisch zum100/0-Lauf. Screenshots mit synthetischen Daten visuell geprüft. Kein Pixel-Leistungs-/Google-End-to-End-Nachweis.
- Release-Build und AndroidTest-Build PASS; Release-Lint0 Fehler/17 Warnungen. Kanonischer Pflanzencheck181 Profile/362 Bilder/370 Spiegel PASS; notwendige gartenpirat.js/sw.js-Spiegel ausschließlich über tools/sync_plant_assets.py --write erzeugt. Kein Desktopstand übernommen, kein Feature/Regressionstest gelöscht.
- Originalkandidat downloads/Haus-Garten-1.49.5-74-original-update-kandidat.apk: **at.hausgarten.service,1.49.5/74**, min26/target37, nicht debuggable,34.422.544 Bytes. SHA256 **48cdd6d96f7c5eb91bf64e79f8981823b18453fe7e9fe4f7d567d0dbfea6c527**. Alle406 Assets bytegleich zur Quelle; classes.dex SHA256 cce57dfe7ae9d57da6de664ef8b6eb0e038e80b3b34d1e94dd70c0cdd5860bb6.
- Signierung sämtlicher interner Emulator-/Instrumentierungskandidaten ausschließlich tools/sign_original_apk.py mit fünf nur auf Vorhandensein geprüften Variablen, vorhandenem Originalschlüssel und anschließendem Cleanup. Keine Ersatz-/Debug-/QA-Schlüsselerzeugung/Actions-Signierung. Finale APK unabhängig erneut verifiziert: genau ein Originalsigner **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**, v2/v3 gültig, v1/v3.1/v3.2/v4 false,16KiB-zipalign PASS. Source-/APK-Privacy PASS,null operative Seedzeilen. Cleanupnull private Signiertempordner und null Keystore-/Privatschlüsseldateien nach geprüften Endungen im Repository. Test-APK nur bestehende Instrumentierung für Originalpaket, nicht bereitgestellt und keine zweite operative App.
- Version74 ist höher als tatsächlich gelesener Pixel73; Zertifikat mit erhaltenem Cloud-Kandidaten73 identisch. Tatsächliche Fresh-Install-/73→74-In-place-Update-Abnahme im isolierten Emulator einschließlich synthetischem Datenerhalt PASS. Installierte Pixel-Signatur nicht direkt abgefragt; kein Pixel-Update/Datenerhalt behauptet. Kein direkter Pixel-/ADB-Zugriff, keine produktive Installation oder Datenänderung.
- Neuer privater Drive-Download über vorhandene Google-Drive-Anbindung: https://drive.google.com/file/d/11ASToMZ3qHlpmgq7dkPoKl7-A28klzm-/view?usp=drivesdk . Upload, Metadaten und tatsächlicher Rohdatei-Referenz-Rückabruf mit34.422.544 Bytes PASS. Nur neue APK hochgeladen, keine Betriebsdatei/Freigabe verändert. Lokaler SHA256 geprüft, kein unabhängiger Remote-Bytehash behauptet.
- Reale Betriebsdaten und jeder der183 Queue-Einträge exakt unverändert gegenüber Eintrag18. Acht bestehende Prüfungen und die128 Stichtagsarchivierungen nicht erneut angewendet/gelöscht/aufgelöst. Drive meldet auf installiertem73 weiter LEGACY_SOURCE_CHANGED und nur provider_readback. Quellkorrektur behebt nachgewiesene falsche Normalisierungskonflikte; echte Paralleländerungen bleiben geschützt und dürfen nicht automatisch ausgewählt werden.
- **Offen/P0-P1:** neuer Pixelstand/Connector-Refresh auf 89 Tools und echter Google-Drive-Rundlauf mit diesen 183 Aktionen; notwendige Geräte-/Kontofreigaben, physische Kamera/Dateiauswahl/Share-App, Hintergrund-/Akkuverhalten und Pixel-Update-/Datenerhaltabnahme. Installation am Pixel weiterhin erst nach vollständiger Sicherung und ausdrücklicher Freigabe. Kein Vollzugriff-/Dauerverfügbarkeitsversprechen, keine produktive Alt-Konfliktlösung. Die Cloud-Umsetzung ist geprüft, der Gesamt-P0-Auftrag nicht als produktiv abgenommen markieren.

- Implementierungs-/Qualifikationscommit: **55e16b3a9c20240c67e05bcdce8c7498f67baf23**. Folgender Bereitstellungscommit ergänzt ausschließlich geprüfte APK/Prüfsumme, zentrale Downloadangaben und diesen Abschlussverweis. main unverändert; kein History-Rewrite/Force-Push.

- GitHub-Bereitstellung: Code/Qualifikation55e16b3 und Downloadstatus476a056 normal auf vorgesehene Branch gepusht. Allgemeiner *.apk-Schutz schloss zunächst die neue Datei aus; ausschließlich dieser geprüfte Kandidat erhält eine explizite Ignore-Ausnahme und einen separaten normalen Artefaktcommit. Keine Veröffentlichung ungeprüfter Builds.

### Eintrag 23 – Bereinigung / Phase A: Bestand und Sicherung (19.10.2026)

- Nutzerauftrag: App und Google Drive bereinigen. Ausgang **32dddee50bb0f7c521d29141a05b0ccbe80c560d**, sauber auf der vorgegebenen Cloud-Branch; Remote-Branch per ls-remote identisch, main unverändert a38765d. Pflichtancestor vorhanden. Keine Installation, kein direkter Pixel-/ADB-Zugriff, keine Code-/APK-Änderung.
- Jetzt tatsächlich installiert: **at.hausgarten.service,1.49.5/74,DB4,Protokoll5**. get_capabilities liefert89 Werkzeuge; hier tatsächlich aufrufbarer Connector weiterhin83. Es fehlen preview_delete/delete_entity,preview_purge/purge_entity sowie preview_archive_work_records/archive_work_records; archive_entity-Schema ebenfalls ohne expected_photo_links_hash. Keine Umgehung durch zusätzliche, nicht registrierte Parameter. App-/Tunnel-Katalog und ChatGPT-Registrierung sind weiterhin getrennt zu aktualisieren.
- Ausgang184 Aktionen =176 Übertragungen/8 Prüfungen; Papierkorb134; ursprüngliche sieben Legacy-Hashes unverändert. Android-Provider mit persistierter Ordnerfreigabe, provider_readback/cloud_confirmed=false, letzter alter Fehler LEGACY_SOURCE_CHANGED. Rohsammlungen weiterhin alter gespeicherter Stand; für Bereinigungsentscheidungen zusätzlich effektive Datensätze und Verknüpfungen gelesen.
- Vor erster App-Archivanforderung neue Geschäftssicherung **backup_3fa4460d4db94ee5f9b0e5fd8f124e7d539bfc997f99948c0f9acafbe5f685b5**,22.987.905 Bytes,SHA256 **58e99ac2e57d4215106391daea6d4ebe25a8c4a96998e73050b6f8aec15f6045**; inspect_backup bestätigt. Nach regulärer Übernahme zusätzliche Sicherung **backup_275dabc15814269805c1f1fee46aeb6c79287aa14a323cebbd244b7287b4de85**,22.796.693 Bytes,SHA256 **3c6e59ef604c25919f0f10cfdf223eacb72c9555899c9b8c2e966e1d78ebfc01**. Scope Geschäftssnapshot/offene Aktionen/lokal vorhandene Medien und Dokumente, keine vollständigen Gerätesicherungen. Vorhandene Sicherungen erhalten.

### Eintrag 24 – Bereinigung / Phase B: ausgeführt und rückgelesen (19.10.2026)

- **Drive geordnet:** vier frühere Original-APK-Kandidaten69/71/72/73 aus My Drive in vorhandenen Ordner Haus & Garten App → APK-Backups → Codex-Cloud-Builds verschoben. Ausschließlich überprüfte Ausgangseltern entfernt; Ziel vorab gelesen. Jede Datei anschließend einzeln per ID/Name/Größe/Eltern rückgelesen. Zusammen137.619.731 Bytes weiterhin vorhanden; keine Speicherfreigabe behauptet. IDs und bisherige Download-Links bleiben erhalten, keine Inhalts-/Freigabeänderung. Aktueller Kandidat74 separat per Metadaten unverändert bestätigt. Keine Betriebs-JSONs, Commit-/State-Ketten, Originalfotos, Dokumente oder Sicherungen aus Drive entfernt. Drive-Suche zeigte nicht alle bekannten APK-/JSON-Dateien; Suchleere ausdrücklich kein Nachweis eines leeren Ordners oder verwaister Dateien.
- **Realer Pixel-/Drive-Lauf:** einmalige explizite request_sync-Anforderung. Installierter74 verarbeitete die vorhandenen176 Übertragungen im regulären geschützten Journal-/Medienweg; alter LEGACY_SOURCE_CHANGED verschwand. Zwischenstände185→20→16→13 wurden tatsächlich gelesen. Keine Queue gelöscht und kein Beleg erfunden; Medien liefen in weiteren automatischen Abschnitten weiter. Nach technischer Konfliktnachbereitung und abschließender Fotoarchivierung: **Upload0,Prüfung7,gesamt7**,phase idle,kein Transportfehler/Retry/Medien-Cleanup offen. Letzter gelesener Provider-Abgleich **2026-10-19T04:35:04.039474Z**; **provider_readback,cloud_confirmed=false,last_full_sync leer**. Kein unabhängiger Google-Serverbeleg behauptet.
- **Testreste:** sechs fehlgeschlagene Archivaufträge ausschließlich für dokumentierte synthetische IDs über resolve_sync_conflict mit jeweils frisch gelesenen expected_action_hash/expected_current_hash und keep_current zurückgenommen. Originalaktionen und Entscheidungen erhalten; überholte Kontrollaktionen vom vorhandenen Prozessor als superseded quittiert. Konflikthistorie zuletzt12 Einträge einschließlich solcher Kontrollbelege; das sind nicht12 historische Geschäftskonflikte. Sieben geschützte Alt-Fälle unverändert.
- Erste neue Testfoto-Archivanfrage wurde hinter einem alten fehlgeschlagenen Archivvorgänger mit DEPENDENCY_BLOCKED angehalten; sie war zunächst nur lokal vorgemerkt, nicht erfolgreich archiviert. Diese Anforderung ebenfalls hashgebunden zurückgenommen. Nach Vorgängerprüfung erneute aktuelle Anfrage erfolgreich verarbeitet. Dokumentierte synthetische Arbeit **id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f** und Foto **id_sha256_8cbd663a374da3be09cd94c037a3025e77341079fc33f85d08a54ba4e9933abc** jetzt im bestehenden Papierkorb tatsächlich rückgelesen; Originale erhalten, keine physische Löschung. Papierkorb **136**. Alle vorherigen134 Archiv-IDs und die128 Stichtags-Arbeitsarchive unverändert erhalten.
- Zwei verbleibende technische Testaufträge haben noch aktive oder bereits archivierte Fotobeziehungen. Ein aktueller Archivversuch für id_sha256_c6ccbf58d78cbaaeeec27aa7d60d0d30639ddee2faeaba44a74c4fefe8224705 wurde vor Mutation korrekt mit **PREVIEW_REQUIRED** blockiert, obwohl aktive Foto-/Arbeitslisten leer waren: archivierte Fotozuordnungen zählen ebenfalls. Ohne exponierte preview_delete/expected_photo_links_hash keine Umgehung, kein Löschen von Beziehungen und kein weiterer blind wiederholter Auftrag.
- **Integritätsnachweis:** alle45 nicht synthetischen aktiven Arbeitsaufzeichnungen ab01.08.2026 feldweise mit kanonischer Schlüsselreihenfolge unverändert gegenüber der Eingangsliste; ausschließlich die eindeutig dokumentierte zusätzliche synthetische Testarbeit wurde archiviert. Rechnungssammlung einschließlich aller95 Datensätze hat unveränderten Sammlungshash. Ursprüngliche sieben client_ids/action_hashes/current_hashes weiterhin exakt gleich; Legacy-Vorschau7,keep_current,no_business_data_changed,Hash68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7. Gespeicherte Sammlungen jetzt52 Kunden/3 Aufträge/45 Arbeiten/95 Rechnungen/25 Fotos/33 Bausteine/0 Angebote; der Unterschied zum alten Rohsnapshot folgt der regulären Übernahme bestehender Aktionen und den genannten technischen Bereinigungen, nicht einer pauschalen Datenlöschung.
- **Legacy-Abschluss weiterhin gesperrt:** prepare_legacy_conflict_backup tatsächlich versucht, ohne Änderung abgewiesen: „Zuerst direkten Pixel-/Drive-Abgleich mit Serverbeleg prüfen. Keine Altentscheidung ausgeführt.“ Der vorhandene Sicherungs-/Abschlussweg verlangt einen direkten Drive-Serverbeleg; Provider-Rücklesen genügt nicht. Keine sieben Altentscheidungen erzeugt. Nächste notwendige Gerätehandlung: Sync-Zentrale → Drive-Verbindung einrichten, vorhandenes Google-Konto und denselben Ordner bewusst bestätigen; tatsächliche OAuth-/Projekt-/Kontorechte und Serverbeleg anschließend prüfen. Danach erst frische vollständige Backup-/Hash-Vorschau und die vom Nutzer verlangte einmalige ausdrückliche Bestätigung. Keine Geheimnisse anfordern, kein Rechte-Bypass.
- **Weiter offen:** denselben ChatGPT-MCP-Zugang neu initialisieren/Toolkatalog aktualisieren bzw. neu verbinden;89 tatsächlich aufrufbare Werkzeuge und aktuelle Archivschemata nachweisen. Zwei technische Testaufträge erst mit korrekter Fotovergleichsvorschau archivieren. Endgültige Papierkorbleerung/physische Medienlöschung nicht durchgeführt. Keine neuen Kosten/Freigaben, kein neuer Build, keine Installation. Diese Phase prüft echte autorisierte Bereinigung per MCP und Drive-Metadatenrücklesen; keine neuen automatisierten Tests erforderlich, da ausschließlich diese zentrale Statusdatei und bestehende operative Bereinigungswege verwendet wurden. Vorherige automatisierte Qualifikation bleibt unverändert; keine Vollabnahme des restlichen Funktionsumfangs behauptet.

### Eintrag 25 – sieben Altprüfungen ausdrücklich freigegeben, technisch blockiert (19.10.2026)

- Nutzer: „Entferne die 7 bestehenden synceinträge“. **Ausdrückliche Freigabe für die bereits konkret bezeichneten sieben historischen Fälle liegt jetzt vor**: keep_current, aktueller Geschäftsdatenstand bleibt erhalten, keine ursprüngliche Löschaktion anwenden; Originalaktionen und Entscheidungshistorie erhalten. Keine erneute Nutzerbestätigung für genau diese unveränderten sieben Fälle erforderlich. Technische Sicherungs-/Hashprüfungen bleiben Voraussetzung.
- Ausgang7c653e97f46fa411c0a6917dd541e1c0cbf3f7c2 auf vorgegebener Branch sauber; Remote identisch, main a38765d unverändert, Pflichtancestor vorhanden. Live74/Protokoll5/Write aktiv. Erneute Vorschau exakt7,keep_current,no_business_data_changed,Hash68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7; sämtliche sieben Aktions-/Datensatzhashes entsprechen Eintrag24.
- Vor Abschluss **prepare_legacy_conflict_backup** mit stabiler Request-ID close7_20261019_owner_authorized_backup_v1 tatsächlich erneut ausgeführt. App weist vor Änderung zurück: „Zuerst direkten Pixel-/Drive-Abgleich mit Serverbeleg prüfen. Keine Altentscheidung ausgeführt.“ Laufzeit weiterhin android_provider/provider_readback/cloud_confirmed=false. LegacyConflictBackup.requireReady und derselbe Guard im Sammelabschluss verlangen direkte Drive-Konfiguration samt drive_server_manifest. Keine Umgehung über sieben Einzelentscheidungen, keine erfundene Backup-ID/Cloud-Bestätigung, kein Entfernen der Queue.
- Nachprüfung: weiterhin **0 Uploads/7 Prüfungen**,phase idle,kein Transportfehler. Geschäftssammlungen hashgleich **0535a37257a7f47758f5f3bf0afba0a691d70de237f03d0847c9922dd54dc6a9**. Keine Geschäfts-, Archiv-, Medien- oder Konflikthistorienänderung durch diesen Auftrag. Kein Build/Signieren/Installieren und keine Quellcodeänderung; ausschließlich dieser zentrale Status ergänzt.
- Nächste notwendige Gerätehandlung: Sync-Zentrale → **Drive-Verbindung einrichten**, vorhandenes Google-Konto und denselben Ordner bestätigen; direkte Rechte/OAuth-Einrichtung und tatsächlichen Serverbeleg prüfen. Anschließend frische vollständige Legacy-Sicherung und unveränderte Hashvorschau, dann bereits autorisierten keep_current-Abschluss ausführen. Falls sich Auswahl/Aktions- oder Datensatzhash ändern: neu prüfen statt Freigabe auf andere Inhalte zu übertragen. Der aktuelle Auftrag ist wegen dieser technischen Sperre **nicht ausgeführt**, keine Erfolgsmeldung über entfernte Einträge.

### Eintrag 26 – Altfall-/Papierkorb-/Menüreparatur, Phase A (19.10.2026)

- Ausgang8b27060516aeb3f947706e2d693b94f08b7500e4, sauber auf vorgegebener Branch; Remote identisch/main a38765d unverändert/Pflichtancestor vorhanden. Nutzer autorisiert Altfallabschluss und endgültige Papierkorbbereinigung sowie deren Bedienung; Freigabe aus Eintrag25 bleibt gültig. Keine erneute Rückfrage für die Implementierung nötig. Keine automatische Pixel-Installation.
- Live ausschließlich erneut gelesen:74/Protokoll5/89 Runtime-Werkzeuge,0 Uploads/7 historische Prüfungen,136 Papierkorbeinträge. Geschäftshash0535a37257a7f47758f5f3bf0afba0a691d70de237f03d0847c9922dd54dc6a9 unverändert; Android-Provider-Rücklesen,kein Transportfehler,keine Cloud-Serverbestätigung. Keine Geschäftsdaten für Tests verwenden.
- Ursachen: Legacy-Sicherung verlangt pauschal REST/Servermanifest vor ihrer bereits vorhandenen inhaltlichen Snapshot-/Commit-Prüfung; funktionierender SAF-Zugang wird ausgeschlossen. Papierkorb bietet nur Einzel-Purge und kann interne Archivbeziehungen nicht als gemeinsame Löschmenge behandeln. Mehr-Menü wächst durch unabhängig eingefügte Links ohne gemeinsame Gruppierung.
- Abnahme: Altfallabschluss auch nach vollständig überprüfter Provider-Sicherung möglich, ohne cloud_confirmed zu erfinden; aktuelle Daten und unveränderliche Belege behalten. Papierkorb leeren mit einer vollständigen Hashvorschau, einer Bestätigung, dauerhafter Request-ID, atomarer lokaler und Drive-Verarbeitung; interne Archivbeziehungen blockieren sich nicht, aktive/finanzielle Referenzen bleiben geschützt. Alle Menüzugänge bleiben vorhanden, gleiche Aktionen gebündelt und320/360/426px/große Schrift geprüft. Isolierte Host-/Browser-/native Regressionen vor Originalkandidat75; tatsächliche Live-Ausführung erst über angebotene installierte Funktionen.

### Eintrag 27 – Altfall-/Papierkorb-/Menüreparatur, Phase B: Implementierung

- LegacyBackupPolicy trennt tatsächliche Ordnerrechte/abgeschlossenen Abgleich von Transportwahl. Vollständige Sicherung liest und verifiziert aktuelle State-/Commit-Kette auch über SAF, liest dieselbe Veröffentlichung nach Medienprüfung erneut und hält bei Änderungen an. Fehlende aktive oder archivierte Originale/PDFs werden ausschließlich hashgeprüft aus demselben Drive-Ordner nachgelesen; fehlende Dateien stoppen weiterhin. Snapshot, SQLite, Queue und lokale Dateien werden privat gesichert. confirmation_scope bleibt provider_readback oder drive_server_manifest, cloud_confirmed wird nicht erfunden. Fachlich keine alte Löschung; keep_current und Historie bleiben bestehen. UI verwendet stabile Backup-/Abschluss-Request-IDs je Vorschau statt erneuter Zufallsanfragen bei Fehlern.
- TrashPurgeBatch: vollständige Archivmenge plus aktive Datensatzversionen bilden preview_hash. Gemeinsamer Fixpunkt schützt aktive Referenzen/finale Belege und deren abhängige Archive, erlaubt aber Beziehungen/Zyklen innerhalb der ausdrücklich bestätigten Löschmenge. preview_empty_trash/empty_trash, eine atomare purge_trash_v1-Journalaktion, eindeutige Tombstones je Original, dauerhafte Request-Belege; bei Stale Hash keinerlei Teiländerung. Gescheiterte Sammelaktion hat hashgebundenen keep_current-Ausgang. Nur erfolgreich projizierte Vorgänger werden als Abhängigkeit gebunden; unabhängige sieben Altprüfungen blockieren die Papierkorbbereinigung nicht.
- Originaldateien werden erst nach überprüfter Veröffentlichung/lokalem Commit und erneuter Referenzprüfung bereinigt. Vorhandener Foto-Cleanup ergänzt um unreferenzierte Entwurfs-PDFs, jeweils Hashprüfung/Provider-Löschrücklesen bzw. REST-ETag. Gemeinsam verwendete Dateien, Original-Sicherungen und unveränderliche Journal-/State-Historie bleiben erhalten. Keine Zusage forensischer Datenvernichtung.
- Mehr: vorhandene Zugänge in vier sichtbaren Gruppen Arbeit & Planung, Angebote & Rechnungen, Fotos & Pflanzen, Daten & App. Bestehende Handler/Formulare bleiben erhalten, kein neuer Navigationszwischenraum. Papierkorb dort zentral, doppelter Link auf Sync-Seite entfernt. Mehr-Markierung auch in Papierkorb/Fotobibliothek/Abrechnung erhalten. Papierkorb leeren prüft alle Seiten und nennt geschützte Restfälle vor einer einzigen Bestätigung.
- Neuer Quellstand **1.49.6/75,Protokoll6,91 Werkzeuge**, neue trash_empty_v1-Capability; alte Handler bleiben kompatibel, ältere Verarbeiter dürfen neue Sammelaktion nicht ausführen. 270 Python-Tests,21 JVM-Suiten einschließlich163 neuer Sammellösch-/Providerpolicy-Prüfungen sowie gezielte Browser-Suite zunächst PASS. Release-/Instrumentierungsbuild und Lint zunächst PASS; zusätzliche native Provider-/SQLite-Abnahme und gesamter Browserlauf laufen noch. Keine produktive Queue-/Papierkorbänderung durch diese Entwicklungsphase und keine Pixel-Installation.

### Eintrag 28 – Altfall-/Papierkorb-/Menüreparatur, Phase C: Qualifikation und Kandidat

- PASS **273 Python-Tests**,**21 JVM-Suiten** (davon163 neue Sammellösch-/Providerpolicy-Prüfungen),**27 Browser-Suiten**,zusätzlicher gezielter Browserlauf21 Prüfungen. 136 synthetische Papierkorbeinträge statt nur erster30er-Seite, Abbruch/Fehler/identische Retry-ID, keine aktive Datenänderung; interner Kundenzyklus und aktive Rechnungsreferenzen geprüft. Mehr-Menü320/360/426px,150%-Schrift: alle Zugänge/Handler erhalten, lesbare Textbreiten und Zeilenhöhen sowie Überlauf geprüft; Screenshot visuell kontrolliert.
- PASS **103 native Android-SQLite/MCP/Provider-Prüfungen,0 Fehler**, vollständiger Schlussbericht vorhanden. Ein einziger Journalauftrag für136 Einträge; injizierter SQLite-Abbruch lässt null Teilaktionen/Quittungen, Replay/Neustart erhalten Resultat und aktive Arbeit. Provider-PDF-Test entfernt ausschließlich unreferenzierten Entwurfsinhalt nach Veröffentlichung und lokalem Commit; gemeinsam mit aktivem Beleg verwendete Datei bleibt lokal/remote erhalten. Wiederholung führt keine doppelte Dateilöschung aus.
- Sieben synthetische Altfälle über sieben normale Wiederholungen geschützt; anschließend vollständige private Provider-Sicherung mit geprüfter State-/Commit-Kette, fehlendes lokales Original tatsächlich hashgeprüft nachgeladen, sieben keep_current-Entscheidungen durch denselben nativen Owner-/Journalweg verarbeitet. Aktive Geschäftshashes unverändert,0 Restaktionen,7 Historieneinträge; Originaljournale/Belege erhalten,cloud_confirmed=false. Dies ist ein isolierter Android-DocumentsProvider, kein echter Google-/Pixel-Abschluss. Tatsächlicher Bulk-Backup-/Entscheidungsaufruf auf produktivem74 weiterhin nicht als erfolgreich ausgegeben.
- Native Original-Update-/Datenerhaltprüfung und tatsächliche WebView83-Navigation PASS; Mehr mit vier Gruppen/einem Papierkorbzugang, vorhandene Pflanzen-/Kunden-/Auswahl-/Timerwege und Papierkorb-leeren-Schaltfläche vorhanden. Erste Updateprüfung erkannte neue additive Laufzeit-Capability trash_empty_v1 im alten Gesamthash; ausschließlich dieses neue Metadatum ist nun explizit vom historischen Baselinevergleich ausgenommen. Alle vorherigen Capabilities, Geschäftsfelder und Pending-Bytes weiter vollständig verglichen. Ein neuer Provider-Test verwendete zunächst einen alten Publikationsvorgänger und wurde korrekt abgewiesen; Fixture liest jetzt wie der echte Sync den aktuellen Primärstand. Keine Schutzregel entfernt.
- Ein Emulatorlauf wurde durch irrtümlich überlappenden UI-Start unterbrochen. Hoststarter konnte zuvor Teil-PASS-Zeilen trotz Process crashed akzeptieren. Behoben: exklusives flock je expliziter Emulator-ID über Installation/Lauf und verpflichtender vollständiger Abschlussbericht; sechs Guardtests einschließlich Parallelstart/Teilbericht/Crash PASS. Unterbrochener Lauf gilt nicht als Erfolg; abschließender vollständiger103/0-Lauf nach der Korrektur tatsächlich ausgeführt. Erstlauf102 PASS/1 Fixturefehler und Unterbrechung bleiben in lokalen synthetischen Logs nachvollziehbar. Keine produktiven Daten verwendet.
- Release-/Instrumentierungsbuild PASS,Lint **0 Fehler/17 Warnungen**,Pflanzenspiegel181 Profile/362 Bilder/370 Ausgaben geprüft. Fertige APK406 Assets bytegleich zur Quelle, geforderte operative Klassen einschließlich LegacyBackupPolicy/TrashPurgeBatch vorhanden, null operative Seedzeilen/Privacy PASS. Keine parallele App/Datenbank und kein Test-/Featureabbau.
- Kandidat **downloads/Haus-Garten-1.49.6-75-original-update-kandidat.apk**,at.hausgarten.service,1.49.6/75,min26/target37,nicht debuggable,**34.426.640 Bytes**. SHA256 **db9035109b9206fbfe0cf621ad0198bd14f0d5baf45e5812553d4396f27f69a0**. DEX-SHA2567958fa5537cea3601eccf01d06bb239f57b9f9cd777412d18bd24707efe6f52c. Version75 über tatsächlich gelesenem Pixel74; Paket/Signatur auch gegen erhaltenen74-Kandidaten verglichen, kein direkter Zugriff auf installierte Pixel-Signatur.
- Vor jeder tatsächlichen Signierung ausschließlich Vorhandensein der fünf HG_ORIGINAL_*-Variablen geprüft; vollständig. Ausschließlich tools/sign_original_apk.py, vorhandener Originalschlüssel; interne Instrumentierung ebenfalls nur mit dokumentiertem --instrumentation-Modus, nicht bereitgestellt. Kein neuer Debug-/QA-/Ersatzschlüssel. Finale unabhängige Prüfung: genau ein Signer **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**,v2/v3 gültig,v1/v3.1/v3.2/v4 false,16-KiB-zipalign PASS. Private Signierungstempordner0,Schlüsseldateien nach geprüften Endungen im Repository0. Originalsignierung und Emulatorupdate sind keine Pixel-Installations-/Datenerhaltfreigabe.
- Privater Drive-Download tatsächlich hochgeladen: **https://drive.google.com/file/d/1Q8Ttp8Bm1xlwIeW3e_AXY2B11a0a6HHX/view?usp=drivesdk**. Vorhandener Ordner APK-Backups/Codex-Cloud-Builds,keine neue Ordner-/Freigabestruktur. ID/Name/MIME/Eltern/34.426.640 Bytes und ausschließlich Owner-Freigabe rückgelesen; Rohdatei-Referenz mit vollständiger Größe erneut erhalten. Keine Betriebs-/Sicherungsdateien ersetzt/gelöscht und kein unabhängiger Remote-Bytehash behauptet.
- **Produktive Schlusslesung:** weiterhin74/Protokoll5/89 Runtime-Tools,hier83 registrierte Tools. **0 Uploads/7 Prüfungen,136 Papierkorbeinträge**,Legacy-Previewhash unverändert68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7,Geschäftshash unverändert0535a37257a7f47758f5f3bf0afba0a691d70de237f03d0847c9922dd54dc6a9. Die produktive Entfernung wurde in dieser Entwicklung **nicht ausgeführt**: alter installierter Backup-Guard und fehlende Purge-/Sammeltools. Keine direkte Pixel-/ADB-Aktion oder Installation. Nach vollständiger Gerätesicherung und ausdrücklicher Updatefreigabe75 verwenden; dann Backup/Hashprüfung und bereits autorisierten unveränderten sieben keep_current-Abschluss ausführen, Papierkorb vollständig vorprüfen/leeren und tatsächliche Drive-Übernahme nachweisen. Neue MCP-Registrierung91/aktuelle Schemata separat refreshen. Keine erneute fachliche Freigabe für dieselben sieben Fälle verlangen. Gesamt-P0 bleibt bis realer Geräte-/Google-Abnahme offen.

### Eintrag 29 – Termin-/Kalender-/Wetterkonzept: abgeschlossene Planungsphase (19.10.2026)

- Auftrag ausdrücklich zuerst planen. Ausgang **8b23ab8151131303e35fa6203a9f771dc94dcc8d**, sauber auf vorgesehener Cloud-Branch, Pflichtancestor vorhanden; Remote-Branch identisch/main a38765d unverändert. Quelle1.49.6/75, letzter belegter Pixel74 in dieser Phase nicht erneut abgefragt. AGENTS/Auftrag/Status, Reparatur-/Signier-/zentrale Sync-/MCP-Dokumentation und Issue1 gelesen; historische Versionsstände nicht als aktuell übernommen.
- PASS statische Bestandsprüfung: OfferTools/OfferJournalRules mit bereits vorhandener ausdrücklich bestätigter Auftragsübernahme; scheduled_date noch ohne strukturierte Mehrfachtermine; bestehende Navigation und Android-JobScheduler. Neue Termin- und Wetterwerkzeuge nur geplant, keine Capability aktiviert und kein Funktionsnachweis behauptet.
- Konzept in **docs/SYNC-DRIVE-KUNDENAKTE-1.48.md**, Abschnitt „Konzept: Aufträge, Termine, Google Kalender und Wetter“ ergänzt; kein neues Dossier. Kunde→Angebot→Auftrag→mehrere Termine→tatsächliche App-Arbeit, ein Editor/Tag-Woche-Stunden, eigene Google-Kalenderprojektion mit minimalen echten OAuth-Rechten/ETags/Request-IDs und bestehendem Drive-Journal. Gemeinsame manuelle/MCP-Regeln, Wochenwetter14Tage, tätigkeitsbezogene Risiken, alternative Zeitfenster und deduplizierte Benachrichtigungen; keine automatische Kundenabsage/Rechnung/Ist-Zeit.
- PASS Recherche offizieller Google-Calendar-/Android-/Open-Meteo-/GeoSphere-Dokumentation. Calendar-Connector vorhanden, dessen Schemakatalog gelesen; weder Kontoinhalte noch App-OAuth-Rechte verifiziert. Open-Meteo-Produktivnutzung benötigt zulässige gewerbliche Lizenz; kein Vertrag/Schlüssel angefordert. Android-Laufzeitfreigabe und Hintergrundbeschränkungen bleiben reale Abnahmebedingungen.
- Geändert ausschließlich diese Statusdatei und vorhandene zentrale Konzeptdokumentation. Keine Quellcode-/Versions-/APK-Änderung, keine neuen Tests notwendig für reine Dokumentation; neue Funktionsabnahmen **NOT TESTED / geplant**. Keine Geschäfts-/Drive-/Kalenderdaten gelesen oder verändert, keine Pixel-/ADB-Aktion, Signierung oder Installation. Nächster Schritt erst nach Umsetzungsauftrag: Grundlage/Termindatenmodell und isolierte Akzeptanztests, dann Kalender/Wetter/MCP in beschriebenen Phasen. Frühere produktive Altfall-/Papierkorbblockaden bleiben unberührt.


### Eintrag 30 – Termine / Phase A: Bestand und Abnahmen (19.10.2026)

- Ausgang90f2eff3756e1f035e5dfebba09fac5ad228fae7, sauber auf vorgesehener Cloud-Branch, Remote identisch/main a38765d unverändert/Pflichtancestor vorhanden. Umsetzungsauftrag für das dokumentierte Konzept; externe Konto-/Kostenfreigaben und Installation separat.
- Reale MCP-Lesung: jetzt ebenfalls **1.49.6/75, DB4, Protokoll6,91 Runtime-Werkzeuge**, appointments noch nicht vorhanden. Keine produktiven Schreibtests oder Kalender-/Kundendatenänderung.
- Abnahmen: eine bestätigte Angebotsübernahme/ein Auftrag bei Replay; mehrere Einsätze ohne Ist-Stunden/Rechnung; gemeinsame UI/MCP-/Drive-Versionsprüfung und atomare lokale Request-Belege; keine Kalenderdubletten nach Antwortverlust; externe ETag-/Terminüberschneidung sichtbar;14Tage Wetter mit Alter/fehlenden Werten und tätigkeitsbezogenen Hinweisen, keine automatische Absage; unabhängige Drive-/Kalender-/Wetterfehler; schmale UI/Entwürfe/Timer erhalten. Ausschließlich synthetische Regressionen und dedizierter Cloud-Emulator.


### Eintrag 31 – Termine / Phase B: gemeinsame Geschäfts- und MCP-Pfade

- Implementiert im Originalpaket: additive appointments-Sammlung in bestehender DB4/Snapshot2/Drive-Verarbeitung, alte Snapshots ohne Termine bleiben lesbar. Gemeinsame Termin-/Zeitzonen-/Beziehungs-/Überschneidungsregeln, voller Record-CAS auch bei zwischenzeitlich neu hinzugefügten Feldern, dauerhafte Request-Belege und transaktionale begrenzte Wochenserien. Keine Ist-Zeiten oder Rechnungen durch Terminplanung.
- Angebotsübernahme behält genau einen Auftrag bei neuen Wiederholungs-Request-IDs; angenommene Nachträge mit Versionsreferenz am vorhandenen Auftrag, Hauptreferenz erhalten. Terminarchiv/Restore und Purge-Verknüpfungsschutz im vorhandenen Papierkorb. Neue13 MCP-Werkzeuge (Quelle104, internes Protokoll7), keine Behauptung einer bereits aktualisierten ChatGPT-Registrierung.
- PASS erste22 JVM-Suiten einschließlich73 neuer Termin-/DST-/Wetter-/Calendar-CAS-/Antwortverlustprüfungen; Bestands-Python273 PASS. Zusätzliche Gesamt-/native Abnahme läuft. Ein früherer Test deckte unvollständigen Vergleich gegen nachträglich hinzugefügte Terminfelder auf; vollständiger Appointment-CAS ergänzt, konkurrierende Variante bleibt erhalten. Alte Katalogzählungen in Tests additiv angepasst, vorhandene Werkzeuge weiter geprüft.

### Eintrag 32 – Termine / Phase C: Oberfläche und Geräteadapter

- Implementiert Tag/Woche/Ist-Stunden,30er Terminlisten, Kunden-/Auftrags-/Angebotszugang, persistenter Editor mit unveränderlicher Ausgangsversion und Retry-ID, Wettergründe/Alternativen/Kenntnisnahme sowie geschützte Timerübergabe. Früher Browserlauf deckte einen außerhalb seines bisherigen Moduls nicht verfügbaren Auftragshelper auf; auf gemeinsamen orders-Zugriff korrigiert. Danach17 gezielte Browserprüfungen PASS, einschließlich115 Metadaten, Stale-/Retry-/Entwurferhalt und320/360/426px mit150%-Schrift. Nachfolgende Schutz-/Menüänderungen werden erneut geprüft.
- Nativer Google-Calendar-Adapter mit expliziter Konto-/Scope-Einrichtung, App-Kalender, stabiler Event-ID, ETag, inkrementellem Token/410, dauerhaftem Restabgleich und getrennten Konfliktentscheidungen; kein externer Schreibaufruf in SQLite-Transaktion. Daten bleiben auf Pixel/Drive maßgeblich. Kalender-/Wetter-Wiederholungen getrennt begrenzt, eigenes JobScheduler-Jobpaar.
- Open-Meteo-Kundenzugang nur nach Geräte-Einrichtung/gewerblicher Freigabe, AES-GCM-geschützter Geräteschlüssel außerhalb von MCP/Snapshot,14Tage stündliche Werte und tätigkeitsbezogene Bewertung; ergänzende GeoSphere-Warnungen mit explizitem unbekanntem Zustand bei nicht bestätigtem Abruf/Format. Echte API-/OAuth-/Wetterrechte hier nicht aktiviert. Android-Benachrichtigungen dedupliziert, Ruhezeit21–6Uhr, keine automatische Terminänderung/Kundeneinladung.
- Quellstand1.50.0/76. Release-/Instrumentierungsbuild und Lint bisher erfolgreich; gesamte Qualifikation und neue native SQLite/Provider-Proben folgen. Keine Produktivdatenaktion, keine Installation am Pixel, kein kostenpflichtiger Dienst abgeschlossen.


### Eintrag 33 – Termine / Phase D: abgeschlossene Cloud-Qualifikation und Originalkandidat

- **PASS273 Python-Tests,22 JVM-Suiten**, einschließlich75 neuer Termin-/DST-/Wetter-/Calendar-CAS-/Antwortverlustprüfungen. **PASS28 Browser-Suiten**, darin19 gezielte Planungsprüfungen mit115 Metadatensätzen,30er Pagination, getrennten Entwürfen, versionsfesten Wiederholungen, Timer-Schutz, Alternativvorschau und320/360/426px/150%-Schrift. Screenshots der echten operativen Assets visuell geprüft; zu große Datumsnavigation vereinfacht, lange Entwurfsschaltflächen umbrechen. Alle bisherigen Pflanzen-/Foto-/Rechnungs-/b.b.-/Timer-/Privacy-Regressionen erhalten.
- **PASS120 native Android-/SQLite-/MCP-/Providerprüfungen,0 Fehler** im vorhandenen isolierten Cloud-Emulator: nun17 neue native Proben zusätzlich zur bestehenden Abnahme, einschließlich Angebotsübernahme mit anderer Request-ID ohne zweiten Auftrag, Nachtrag/umgekehrter Journalreihenfolge, atomarer Serie und injiziertem SQLite-Abbruch, Neustart/Idempotenz, echter App-Timer→Arbeitsnachweis mit Terminreferenz, Kalender-Ack-Verlust ohne doppelte Anlage, Paralleländerungen/ETag-Auflösung/410/entzogene Rechte und erhaltener Queue. Android-Benachrichtigung mit drei Aktionen tatsächlich erzeugt, unveränderte Warnung nicht erneut gepostet, Kenntnisnahme ohne Terminänderung geprüft. Provider/Geschäftsprüfungen synthetisch, kein echtes Google-Konto verwendet.
- Erster nativer Lauf116 PASS/1 FAIL erkannte eine veraltete Protokoll6-Testannahme. Auf tatsächlichen Runtime-Protokollwert umgestellt, ohne Zugriffs-/Datenprüfungen zu entfernen. Vollständiger abschließender120/0-Lauf tatsächlich ausgeführt. Frühe Browserfehler an Auftragshelper/Entwurfsresume und vollständigem Versionsvergleich dokumentiert/korrigiert; keine Teil-PASS-Logs als Erfolg akzeptiert.
- Zusätzlicher frisch erstellter Cloud-Android-Datenbereich aus SDK-Stockimage; vorhandene Emulator-/App-Daten erhalten, kein Wipe/Uninstall/Downgrade. SDK-AVD-Manager meldete fehlende Paketregistrierung des bereits vorhandenen Emulatorprogramms; neue AVD ausschließlich aus bekanntem Cloud-Hardwareprofil und unverändertem SDK-Stockuserdata angelegt. Zuerst Original75/Fresh-PASS, synthetische Upgrade-Baseline, danach **tatsächliches In-place-Update75→76 PASS** mit allen vorigen Geschäftsfeldern/IDs/Pending-Bytes/Einstellungen/Tunnelkonfiguration/laufendem Timer. Nur ab Baseline neu eingeführte leere appointments-Sammlung/appointments_v1-Capability dürfen fehlen; alle vorherigen Capabilities bleiben verglichen. Tatsächliche Android-WebView83: bestehende Navigation/Timer/Garten/Papierkorb und neuer Planungseditor mit korrektem Kunden/Auftrag sowie Entwurferhalt PASS. Zusätzlich **Fresh76-Appdaten in eigenem Android-Testprofil PASS**, ursprünglicher Benutzer-Datenbereich unverändert. Keine Pixel-Installation oder Pixel-Leistungsmessung.
- Release-/Instrumentierungsbuild PASS, Lint **0 Fehler/27 Warnungen**; kanonische Pflanzenspiegel181 Profile/362 Bilder/370 Ausgaben unverändert geprüft. Finale APK408 Assets bytegleich zur Quelle, alle elf neuen operativen Planungsklassen vorhanden. DEX-SHA256 **0ef95be1fe59e62bf68a413f9a40c21a555937ff4aaf0c5732de703e1b49f36f**. Source-/APK-Privacy PASS,null operative Seedzeilen, keine reale Kundendatei als Fixture oder in Git. Keine zweite operative App/Datenbank oder Desktopübernahme.
- Originalkandidat **downloads/Haus-Garten-1.50.0-76-original-update-kandidat.apk**, **at.hausgarten.service,1.50.0/76**, min26/target37,nicht debuggable, **34.475.927 Bytes**, SHA256 **3ae076024cec4ae38b51ea93f23a91fbbfce3582c7d169e80282423248f0ea42**. Interne erste Revision vor den letzten Nachbesserungen nicht veröffentlicht; ausschließlich abschließend120/0 geprüfte zweite Revision als Download kopiert, Hash erneut verglichen. Instrumentierungs-APK nur vorhandener Testweg, nicht bereitgestellt.
- Vor jeder echten Signierung ausschließlich Vorhandensein der fünf HG_ORIGINAL_*-Variablen geprüft; vollständig. Ausschließlich tools/sign_original_apk.py, vorhandener Originalschlüssel; Instrumentierung nur dokumentierter Modus. Kein Ersatz-/QA-/Debug-Schlüssel erzeugt und keine Actions-Signierung. Finale unabhängige Prüfung plus Vergleich zu75: genau ein Originalsigner **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**, v2/v3 gültig,16-KiB-zipalign PASS. Cleanup unabhängig bestätigt: private Signiertempordner0,Keystore-/Privatschlüsseldateien nach geprüften Endungen im Repository0. Zertifikatsvergleich gegen Cloud75, kein direkter Signaturzugriff auf den Pixel.
- Privater Download **https://drive.google.com/file/d/1HkgAS3mxW4qaT7m6n6qgEh-m8Cn5WPIJ/view?usp=drivesdk** tatsächlich in vorhandenen APK-Backups/Codex-Cloud-Builds hochgeladen. ID/Name/MIME/Eltern/34.475.927 Bytes, Owner-only/shared=false und vollständige Rohdateireferenz mit derselben Größe rückgelesen. Keine Freigabe geändert, keine Betriebs-/Sicherungsdatei ersetzt oder gelöscht; kein unabhängiger Remote-Bytehash behauptet.
- **Reale Schlusslesung ausschließlich MCP-Metadaten:** Pixel weiterhin **1.49.6/75,Protokoll6,91 Runtime-Werkzeuge**, tatsächlich hier aufrufbarer Connector83. Neue13 Werkzeuge daher **nicht im realen Connector nachgewiesen**. Cloud-Kandidat enthält104/Protokoll7. Kalender-/Wetterkonto nicht verbunden, kein API-Vertrag/Schlüssel angefordert, kein produktiver Termin/Kalendereintrag/Arbeitsnachweis/Snapshot verändert. Früherer Altfall-/Papierkorbauftrag durch diesen Entwicklungsauftrag nicht ausgeführt.
- **Verbleibende reale Abnahme:** vollständige Gerätesicherung und ausdrückliche Pixel-Updatefreigabe; Calendar API/OAuth-/Originalzertifikats-/Scope-/Organisationsfreigabe und tatsächlicher Google-Kalender-/Wetter-Rundlauf; zulässiger gewerblicher Wetterzugang, Android-Benachrichtigungs-/Hintergrundverhalten; verwaltete ChatGPT-Verbindung/Toolregistrierung refreshen und104 Tools real prüfen. Google-Serien/ganztägige Umformungen oder extern entfernte Events bleiben prüfpflichtig; App-Wiederherstellung ist keine behauptete Google-Event-Wiederherstellung. Unbekannte/veraltete Wetterdaten bleiben unbekannt, keine automatische Kundenabsage oder Ausweichbuchung. Gesamt-Pixel-/Providerfreigabe weiterhin offen; Cloud-Umsetzung und isolierte Qualifikation abgeschlossen.

### Eintrag 34 – Termine / Phase E: Bereitstellung abgeschlossen

- Implementierung, Regressionen, Original-APK und Downloadangaben im Commit **38e99294c5e84a10bcd20f85dfc0d198d9da8b58** normal auf **codex/original-signing-cloud-setup-20261018** gepusht; Remote-Ref identisch geprüft. main unverändert **a38765d586c3ea02b999e770e5ecb22b34642962**, kein Force/Rewrite. Folgender Statuscommit ändert ausschließlich diesen Veröffentlichungsnachweis.
- Downloaddatei76 vorhanden,34.475.927 Bytes/SHA256 gemäß Eintrag33, privater Drive-Link tatsächlich rückgelesen. Bestehende Kandidaten75/74 und sämtliche Betriebsdateien/Sicherungen erhalten. Kein weiterer Bericht/PDF oder paralleles Projekt erzeugt; vorhandene zentrale Dokumentation aktualisiert.
- Cloud-Arbeit abgeschlossen. Pixel bleibt bei letzter realer Metadatenprüfung75/91 Tools, ChatGPT-Katalog83; Konto-/Provider-/Pixelabnahmen aus Eintrag33 bleiben offen. Keine produktive Datenänderung oder Geräteinstallation aus dieser Bereitstellung ableiten.


### Eintrag 35 – MCP-Parität / Phase A: Ausgang und Abnahme

- Cloud-Branch sauber, Ausgang **db5fcd3d0035df4d8ed01c48da0648da690c19b3**, erforderlicher05fdcbec enthalten; Remote identisch, main unverändert a38765d. Live MCP: **1.50.0/76, Protokoll7,104 Werkzeuge**, Hash **9eade6bb724836aee976f9a912c80a0d40d8e0cc1dead509a55f8f7069fc3df2**, Schreiben aktiv. DB4/Snapshot2.
- Sieben Reviews, null ausstehende Uploads; Vorschau68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7 unverändert. Drive action_required/LEGACY_SOURCE_CHANGED, Provider-Rücklesen, cloud_confirmed=false. Keine Produktivmutation.
- Abnahme: dedizierte vollständige Sicherung bei festem Vorschauhash und ruhendem Sync trotz exakt dieses Fehlers; laufende Jobs, andere Fehler, geänderte Hashes oder unvollständige Medien bleiben gesperrt. Leere additive Schemafelder dürfen keinen falschen Legacy-Konflikt auslösen; echte Fremdänderungen bleiben sichtbar. Sieben synthetische keep_current-Entscheidungen müssen Geschäftshashes erhalten und die Queue nach echtem Provider-Rundlauf leeren.
- UI-/Bridge-Inventar gegen tatsächliche MCP-Dispatchpfade, zusätzliche Rechte-/CAS-/Replay-Negativtests. Quellkatalog, installierter Katalog und ChatGPT-Registrierung getrennt nachweisen. Kein Pixel-Update ohne Sicherung und ausdrückliche Freigabe.


### Eintrag 36 – MCP-Parität / Phase B: Implementierung und Hostprüfungen

- Drei zyklische Sperren getrennt: dedizierte Sicherung akzeptiert bei ruhendem Sync ausschließlich LEGACY_SOURCE_CHANGED plus unveränderte Siebener-Vorschau, bestehende RW-Freigabe und geprüften Providerstand. Sicherung liest die unveränderliche Veröffentlichungskette auch als reine Evidenz abweichender Legacy-Quelle; beide Snapshots bleiben im geprüften privaten ZIP. Keine Freigabe echter Fremdänderungen für den normalen Sync. ZIP-Einträge per CRC, Archiv-SHA und Geschäftshash vor Abschluss geprüft. Lokaler atomarer keep_current-Sammelabschluss verlangt diese Sicherung, keine erfolgreiche Netzwerkoperation.
- Normaler Drive-Leser akzeptiert exakt passende historische Hashes bei ausschließlich fehlenden/leeren additiven Sammlungen offers/service_catalog/appointments. Nichtleere Sammlungen, unbekannte Felder und echte Datensatzänderungen bleiben geschützt. Regression bildet75→76-Normalisierung und echten Fremdstand getrennt ab.
- Quellstand **1.50.1/77, Protokoll8,111 Werkzeuge**: zusätzlich get_ui_action_catalog, inspect_order_review, save_reviewed_order, get_planning_settings, update_planning_settings, request_weather_refresh, finish_work. Vorhandene Werkzeuge erhalten; start_work unterstützt Terminreferenz; Listen erhalten Versatz, Fotos zusätzlich Rolle/Sitzung/Arbeit/Zeitraum. Planungseinstellungen verwenden denselben SQLite-Eigentümerspeicher und native Prüfschicht; vorhandene externe Kontorechte/Schlüssel werden dadurch nicht erteilt. finish_work stoppt und speichert tatsächliche Timerarbeit in einer Transaktion; ungültige Eingaben rollen auch den Stopp zurück. Der aktuelle ausdrückliche Sprach-/MCP-Auftrag autorisiert diesen realen Timerablauf; keine erfundenen produktiven Arbeitsnachweise.
- Versioniertes Laufzeitinventar ui-mcp-actions.json:156 semantische Aktionsgruppen,145 fachliche MCP-Pfade,11 ausdrücklich erklärte Plattform-/Darstellungsgruppen;31 vollständige UI-/Native-Quellen versionsgebunden. Das sind fachliche Aktionen, keine Zählung duplizierter HTML-Schaltflächen. Statischer Pfad-/Schema-/Rechte-/Katalog-/Listenvertrag mit990 Prüfungen PASS; alle23 JVM-Suiten PASS,273 Python-Tests PASS. Android-Build/Lint PASS; vollständige Browser- und native Qualifikation folgen.
- Echte bestehende Verbindung104/104 Namen identisch; Health und Capabilities melden Hash9eade6bb724836aee976f9a912c80a0d40d8e0cc1dead509a55f8f7069fc3df2. Sicher scheiternde Live-Schreibpfade: unbekannter Rechnungsentwurf REFERENCE_NOT_FOUND; unmöglicher Legacy-Abschluss weiterhin alte Sync-Sperre. Keine Geschäftsdaten-/Queueänderung. Quellkatalog noch nicht auf Pixel installiert.

- Ergänzende Prüffunde in Phase C: natives Planungsformular verwendete beim Speichern eine neue Basis unter alten Eingaben. Öffnungs-Hash bleibt jetzt bis zum erfolgreichen atomaren Speichern fest; gemeinsamer CAS-Saver für UI/MCP, Eingaben bei Konflikt erhalten. Zusätzliche native Regression. Wetterwahrscheinlichkeit wird auch im nativen gemeinsamen Validator auf0–100 begrenzt. Erster interner77-Kandidat noch nicht veröffentlicht; abschließende Revision erst nach diesen Prüffunden und nativer Abnahme.

- Erster vollständiger nativer Lauf124/0 PASS. Abschließender Lauf123/1: der neu ergänzte Drift-Negativtest wurde von der echten Sicherungsroutine korrekt mit IOException abgewiesen; die Hilfsfunktion rejected akzeptierte nur IllegalArgumentException. Erwarteten konkreten IOException-Pfad explizit geprüft, ohne Produktionsschutz zu lockern. Abschließender vollständiger Lauf mit korrigierter Instrumentierung folgt; kein Teil-PASS als Endabnahme.


### Eintrag 37 – MCP-Parität / Phase C: abschließende Qualifikation

- Quellcommit **90b550e897aada6434fdf8a0ea81558720ff02d3**, normal auf vorgesehene Branch gepusht und Remote-Ref geprüft. main unverändert a38765d. Alle31 geprüften UI-/Native-Quelldateien entsprechen dem Inventar;156 semantische Gruppen,145 fachliche Werkzeugpfade,11 erklärte Plattform-/Darstellungsgruppen. Keine Behauptung, jede einzelne Schaltfläche auf dem Pixel betätigt zu haben.
- **PASS273 Python-Tests,23 JVM-Suiten mit993 gezielten Paritätsprüfungen,28 Browser-Suiten.** Darin14 Legacy-Hashprüfungen,168 Papierkorb-/Backup-Policyprüfungen, echte Fremdänderung weiterhin gesperrt, fehlende Rechte/aktive Jobs/stale Vorschau gesperrt;125 synthetische Foto-Metadatensätze vollständig über Seiten lesbar. Bestehende Rechnungs-/Angebots-/Pflanzen-/b.b.-/Foto-/Timer-/Privacy-Regressionen erhalten.
- **Final PASS124 native Android-/SQLite-/MCP-/Providerprüfungen,0 Fehler** auf der endgültigen operativen APK und korrigierter Instrumentierung. Siebener-Providerfall bildet75er Veröffentlichung vor appointments-Normalisierung ab; sieben alte Aktionen behalten Originale bei wiederholtem Sync. Bei action_required/LEGACY_SOURCE_CHANGED feste Vorschau geprüft, Sicherung mit Originalnachladen und SHA/CRC erstellt, Quelländerung während Sicherung verworfen, false-Bestätigung abgewiesen, Sammelabschluss samt Replay geprüft. Danach aktive Queue0, sieben Entscheidungen in Historie, originale Journale erhalten, Geschäftshash gleich, nächster Provider-Sync ebenfalls PASS. Provider_readback/cloud_confirmed=false korrekt erhalten.
- Native zusätzliche Abläufe: Planungseinstellungen CAS/Replay, offenes natives Formular gegen neueren MCP-Stand gesperrt, fehlende Kalender-/Wetterrechte abgewiesen; finish_work speichert tatsächliche Sitzung genau einmal und rollt Stopp bei ungültiger Tätigkeit zurück; gemeinsame Auftragsprüfung mit Token/Replay; Katalog-Pagination. Die native Abnahme blieb vollständig in isolierten Datenbanken/Dateien/Preferences des Cloud-Emulators, Ziel-Geschäftssnapshot unverändert.
- **Fresh77 PASS** in neuem Android-Benutzer10 des separaten Cloud-Emulators5582, bestehender Benutzer erhalten; keine Geschäftsdaten/Zugangsdaten/automatische Verbindung. **Update-/Datenerhalt PASS** auf vorherigem synthetischen76er Emulatorstand, vorhandene Upgrade-Baseline mit Snapshot/IDs/Queue/Einstellungen/Tunnelkonfiguration/laufendem Timer unverändert. Tatsächliche Android-WebView-Navigation PASS auch mit endgültigem Kandidaten. Kein Pixel-ADB, keine Pixel-Installation, keine Google-Konto-/Wetter-End-to-End-Abnahme.
- Release/Instrumentierung/Lint PASS, **0 Fehler/29 Warnungen**. Finale operative APK: **at.hausgarten.service,1.50.1/77**, **34.488.287 Bytes**, SHA256 **0024d12ca3137f326f61e1c454f062855357c73e385a179e34c20bb1230b818f**, DEX **65048dfcede2126776181baa6be004e0f51ddd1649149dd656757c830776c41a**. Alle409 Assets bytegleich zur Quelle;181 Pflanzenprofile/362 Bilder unverändert geprüft. Source-/APK-Privacy PASS,null operative Seeds. Nur endgültige operative Revision wird veröffentlicht; frühere interne Revision bleibt unverteilt.
- Originalsignierung ausschließlich tools/sign_original_apk.py, fünf Variablen ausschließlich auf Vorhandensein geprüft. Vorhandener Originalsigner **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**, v2/v3 und16-KiB-Ausrichtung unabhängig geprüft. Cleanup0 Signiertempordner/0 private Schlüsseldateien im Repository. Ein erster Instrumentierungsaufruf verwendete einen nicht vorhandenen unsigned-Dateinamen und stoppte vor Ausgabe; anschließend korrekten vorhandenen Android-Testpfad signiert. Keine neuen Signierschlüssel/QA-App/CI-Signierung. Instrumentierung nicht bereitgestellt. Original-Cloud-Update ist kein Pixel-Datenerhalt-Nachweis.
- **Realer Connector:**104/104 Namen und oberste Parameternamen/Pflichtfelder der tatsächlich veröffentlichten Deklarationen stimmen mit Pixel76 überein. Hash9eade6bb724836aee976f9a912c80a0d40d8e0cc1dead509a55f8f7069fc3df2 zwischen Health/Capabilities identisch; eigenständiger vollständiger ChatGPT-Schemahash nicht abrufbar. App-Berechtigungsverwaltung ausdrücklich geprüft: eigener Override **Allow all actions** aktiv, globale Low-risk-Voreinstellung übersteuert. Keine Berechtigungseinstellung geändert. Pixel write_enabled=true. Neue Quellfassung111/Protokoll8, Hash **392fdfacf116c49e13306fcfc8c84ab49ee0ce20b35ca62d250fa069ecd1f2ff**, noch nicht live installiert/registriert.
- **Tatsächlicher letzter Drive-Rundlauf auf Pixel76:** request_sync ausgelöst, danach abgeschlossen zurückgelesen: action_required/LEGACY_SOURCE_CHANGED, Uploads0, Reviews7, Historie12, provider_readback, cloud_confirmed=false. Geschäftshash **4fd509c09e7a65d0aaa87af7c533d19a66229e7b208a5a0a365bd9ba684ab91a** unverändert; Counts52 Kunden/3 Aufträge/45 Arbeiten/95 Rechnungen/25 Fotos/33 Bausteine/0 Angebote/0 Termine. Keine alte Löschung und keine Produktivbereinigung ausgeführt.
- **Offen:** Gerätesicherung und ausdrückliche Pixel-Updatefreigabe; Original77 installieren, vorhandenen Tunnel/ChatGPT-Katalog neu verbinden und111 Tools/Hash/Parameter tatsächlich nachweisen. Danach frische Siebener-Vorschau, vollständige geprüfte Konfliktsicherung und autorisierten keep_current-Abschluss real ausführen, Drive-Endstatus erneut prüfen. Bei tatsächlich inhaltlich fremdveränderter Legacy-Datei bleiben beide Stände erhalten und eine gesonderte fachliche Prüfung notwendig; kein automatisches Wegquittieren eines realen Fremdstands. Google-/Android-/Wetter-Erstfreigaben bleiben Geräte-/Kontohandlungen. Cloud-Code und isolierte Qualifikation abgeschlossen, reale P0-Abnahme noch offen.


### Eintrag 38 – MCP-Parität / Phase D: geprüfte Bereitstellung

- Ausschließlich endgültige operative Revision als **downloads/Haus-Garten-1.50.1-77-original-update-kandidat.apk** aufgenommen; SHA2560024d12ca3137f326f61e1c454f062855357c73e385a179e34c20bb1230b818f erneut nach Kopie geprüft. Kandidaten76/75 und frühere Dateien bleiben erhalten. README/SHA256SUMS/gezielte Gitignore-Ausnahme aktualisiert.
- Privater Drive-Link **https://drive.google.com/file/d/1s4GCUx3YcqtRE8elKCLuCXLjruuHW35i/view?usp=drivesdk**, tatsächlich in bestehendem Codex-Cloud-Builds hochgeladen. Metadaten34488287 Bytes, Name/MIME/Eltern und Owner-only/shared=false rückgelesen; vollständige Rohdateireferenz mit derselben Größe verfügbar. Kein unabhängiger Remote-Bytehash behauptet. Keine Freigaben geändert und keine Betriebs-/Sicherungsdateien ersetzt oder gelöscht.
- Codeänderungen im Implementierungscommit90b550e: LegacyBackupPolicy/LegacyConflictBackup, DrivePrimaryState/Coordinator, PixelMcpReadTools/OwnerTools/SessionTools/PixelOwnerAccess, MainActivity, AppointmentTools/Runtime/PlanningJob/PlanningConnectionActivity, Legacy-UI, versioniertes UI-MCP-Inventar und Versionsnummer. Tests: DriveProviderAcceptance, PlanningNativeAcceptance, DriveLegacyNormalizationTests, TrashPurgeBatchTests, UiMcpParityTests, bestehende Daily-/Write-Verträge angepasst. AGENTS präzisiert die ausdrückliche aktuelle MCP-Erfassungsautorisierung, zentrale Eigentümerdokumentation und CODEX-STATUS aktualisiert; keine zusätzliche operative Datenbank/App oder weiteres Dossier.
- Cloud-Code, Kandidat und isolierte Qualifikation fertig. Reale Pixel-P0-Abnahme aus Eintrag37 bleibt ausdrücklich offen. Kein neuer produktiver Konfliktabschluss und keine Installationsfreigabe durch APK-Bereitstellung behauptet.


### Eintrag39 – Auftrag main b2c37a2 / Phase A: Bestand und Kriterien

- Aktuelles main **b2c37a2f909cc8ac6defabe68dbdd3ce6a42db14**, zentralen Auftrag1343 Zeilen vollständig gelesen. main enthielt nur ältere operative Basis70 plus Preisquellenfix; vorhandenen veröffentlichten77-Stand5fbe0a3 auf lokalem main verlustfrei normal zusammengeführt. Keine neue Entwicklungsbranch, kein Reset/Force/Desktopimport. Aktueller main-Auftrag bleibt erhalten.
- Reale lesende MCP-Prüfung: **1.50.1/77,111 Werkzeuge,Protokoll8**, Hashae7cb69f7994ae8141d2d8c925594982f40236921d65abb3739eee270b5b312f. Schreiben und Angebote/Katalog bereit. Queue7 Reviews/0 Uploads,review_required,kein technischer Syncfehler,provider_readback/cloud_confirmed=false. Nur Counts/Hashes ausgegeben; kein Angebot angelegt oder versendet.
- Abnahmekriterien: gemeinsamer Vertrag für beide KV-Typen, explizite Bindungsbestätigung, ein UI/MCP/PDF-/Journalpfad, getrennte Validierung/Finalisierung/Versandbelege; alle156 Inventargruppen deterministisch paginiert. Testbelege nur isoliert, kein produktiver Testexport/Versand. Enum-/Preisquellenvertrag und alte Snapshots prüfen. Neuer Build>77 mit Git-/Source-Fingerprint und Originalsignatur; physische Pixel-/Connectorabnahme nur bei tatsächlichem Zugriff behaupten.
- Gefunden: main-Preisfix enthielt wörtliches Backslash-n im Java-Code; vorhandene KV-Typen bislang nur Label/Defaulttext; fehlende persistierte Validierungs-/Versandzustände und zentrale Enumverträge. Arbeitsstand verwendet die vorhandenen Angebote weiter, kein zweites Datenmodell.

## Eintrag 40 – gemeinsame Dokumentregeln und MCP-Verträge implementiert (19.10.2026)

- Bestehende `offers`-Entität für alle vier Dokumentarten beibehalten; kanonischer `offer-contract.json` mit generierten Java-/Browser-Adaptern. `price_source=user_confirmed`, Preisgrundlagen, Dokument-/Steuer-/Kundenarten und Vertragswege gemeinsam definiert. Verbindliche/unverbindliche Kostenvoranschläge speichern Bindungsmodus, explizite Bestätigung und editierbaren Hinweis. Keine Annahme einer Bindungsart und keine neue Estimate-Toolfamilie.
- Validierter Entwurf, unveränderlicher finaler Datensatz samt eingefrorenem Aussteller/Empfänger und SHA-geprüftem PDF, getrennte Share-/Versandstatus; `sent` ausschließlich nach Gmail-SENT-Readback mit identischem Anhang, Aussteller und bestätigtem Empfänger. Pixel benötigt hierfür zusätzlich seine eigene ausdrücklich freigegebene Gmail-Leseberechtigung. Noch kein realer Versand getestet oder ausgelöst.
- Neue atomare Create+Validate-Operation, Schema-/Domain-Grenzen, read-only Self-Test, Build-Fingerprint und versionCode-Baseline-Gate. Quellkatalog jetzt 117 Werkzeuge / Protokoll 9; physischer Pixel unverändert 111 / Protokoll 8.
- UI-Katalog vollständig über 100+56 Einträge, Hashbindung und `next_cursor`; 156 semantische Aktionen, 145 fachliche MCP-Pfade, 11 begründete Plattform-/Darstellungsgruppen. Build stoppt bei nicht geprüften UI-Quelländerungen/fehlenden Zuordnungen.
- Testdokumente erfordern klar markierte Testkunden und Testempfänger, bleiben ausschließlich lokal und können nicht extern versendet oder produktiv in Aufträge übernommen werden. Keine produktiven Testdaten erzeugt; bestehendes reales Angebot unangetastet.
- Zwischenprüfung: Release/Instrumentation/Lint gebaut; 273 isolierte Python-Tests bestanden, Java-Suite einschließlich 96 neuer Domänen-/Providerbeleg-Prüfungen bestanden. Browser-/native Abnahme läuft. Diese Angaben sind kein physischer Pixel-/Update-Nachweis.

## Eintrag 41 – native Kostenvoranschlagsabnahme und Isolation repariert (19.10.2026)

- Die neuen echten SQLite-/PDF-/Chunk-/Neustartprüfungen deckten eine Folgeaktionslücke auf: `offer_validate` erbte beim neu lokal angelegten Testangebot noch nicht dessen Lokal-Provenienz. Korrigiert durch gemeinsame Zuordnung der Offer-/Invoice-Folgeaktionen zum Ursprungsdatensatz; keine falsche Drive-Vormerkung. Isolierter erneuter Android-Lauf: alle vier Dokumentarten einschließlich PDF/SHA/Readback, Share ohne Versand, direkte Journal-Wiederholung, Neustart und Ausschluss vom Drive-Export bestanden (8 Proben, 0 Fehler).
- Vollständiger vorheriger Android-Lauf: 127 bestanden, genau diese vier neuen Isolationstests fehlgeschlagen; dieser interne Kandidat wurde nicht freigegeben. Nach Reparatur gezielt bestanden; abschließender vollständiger Lauf folgt auf dem endgültigen Build. Ein älterer interner Lauf wurde wegen unnötiger globaler Journalprojektion bei Batch-Provenienzprüfungen abgebrochen; diese Prüfung wurde auf Metadaten begrenzt. 128er-Batch-Abnahme besteht wieder.
- 24 JVM-Suiten, 273 Python-Tests und 28 Browser-Suiten bestanden. Zusätzlich 130 synthetische Angebote paginiert/gefiltert und 105 Foto-Metadaten mit Lazy Loading geprüft. Visuelle Kontrolle fand zusammengequetschte Angebotstitel bei 320px/150%; gemeinsame Dokumentzeilen jetzt untereinander, mit eigener Mindestbreiten-/Höhenprüfung bei 320/360/426px.
- Rechtsquellen am 19.10.2026 tatsächlich gelesen: RIS ABGB §1170a und KSchG §5. Keine automatische Annahme der Verbindlichkeit; bestehende Standardtexte zentralisiert, editierbar und vor Finalisierung ausdrücklich bestätigt. Gesetzes-/Regelversion und Quellen im gemeinsamen Dokumentvertrag.
- Physischer Pixel bleibt 1.50.1/77, 111 Tools/Protokoll8. Katalog live vollständig 100+56, sieben Keep-current-Vorschläge erneut gelesen, keine produktive Testschreiboperation, kein realer Mailversand, kein Geräteupdate.

## Eintrag 42 – vollständige Abnahme und reale Backup-Barriere (19.10.2026)

- Commit `1ff18d1d8ddb1c0b23f3eac2108fe08152c02917`: 24 JVM-Suiten, 273 Python-Tests, 28 Browser-Suiten sowie **131 Android-SQLite/MCP-Proben, 0 Fehler** auf dem originalsignierten Cloud-Emulator-Build. Release/Instrumentation/Lint und APK-Privacy-Gate bestanden. Alle vier Dokumentarten, synthetische Provider-/Gmail-Verträge, Journal-/Neustartpfade und lokale Testdaten-Grenzen geprüft; keine Aussage über ein physisches Pixel-Update.
- Reale, ausdrücklich autorisierte Legacy-Sicherung mit frischem unverändertem Preview `68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7` versucht. Installierter Build 77 verweigert sie mit `READ_ERROR: Ungültige Foto-Dateireferenz.` Kein vollständiger Beleg, deshalb **keine** Altentscheidung. Sieben Reviews bleiben erhalten. Aktive Zeiterfassung ist IDLE.
- Nur lesende Metadatenanalyse: 25 aktive Fotos, davon zwölf ältere Snapshot-Zeilen ohne heutige SHA-/Storage-Metadaten. Daraus folgt nicht automatisch, dass die Originaldateien verloren sind. Bestehender Backup-Code verlangt aber auch für solche Altzeilen den neuen Content-addressed-Dateinamen.
- Ergänzter Sicherungsweg liest ausschließlich den exakt referenzierten sicheren Bilddateinamen im bereits freigegebenen `HausGarten-Fotos`-Ordner. Zwei identische begrenzte Provider-Lesevorgänge, Bilddekodierung, beobachtete SHA/Größe und Herkunftsindex im selben privaten Backup; kein erfundener historischer Hash und keine Geschäfts-/Foto-Migration. Fehlende, doppelte, veränderte oder unsichere Originale blockieren die Sicherung weiterhin. Remote-Snapshot/Publikation werden auch nach der Medienprüfung erneut verglichen.
- Erweiterter vorhandener Android-Provider-Regressionsfall prüft Legacy-Originalbytes im ZIP, fehlende Datei, Pfad/URL-Abweisung, geänderten Readback und danach unveränderte Keep-current-Auflösung. Abschließende Abnahme dieses ergänzten Weges läuft; real erst nach Geräteupdate prüfbar. Kein Backup-Schutz entfernt.

## Eintrag 43 – Legacy-Originale und vollständiger Katalogvergleich (19.10.2026)

- Erweiterter Android-Provider-Lauf: **12 Proben, 0 Fehler**. Enthält vollständige Sicherung eines alten Foto-Dateinamens ohne SHA-Snapshotmetadaten, exakte Originalbytes/Herkunftsindex im ZIP, fehlende Datei, unsichere Namen/URLs und veränderten zweiten Readback; danach sieben synthetische Keep-current-Entscheidungen, unveränderte Geschäftshashes, Queue 0 und Historie 7. Zwei Fehler im neuen synthetischen Fixture (Data-URI-Dekodierung und Legacy-Basishash) korrigiert; keine produktiven Daten für die Reparatur verwendet.
- Tatsächlichen vollständigen Pixel-Katalog (111 Toolobjekte mit Schema/Annotationen) gegen die neue Quelle verglichen: **kein Werkzeug fehlt**, sechs neue Werkzeuge, 14 beabsichtigte Schema-/Beschreibungsänderungen. Damit werden die vorhandenen operativen MCP-Funktionen beibehalten, statt einen älteren Funktionsstand einzusetzen.
- Zusätzlicher Browserfall deckt neue Leistungsbausteine ab: Auswahl `unit`/`text` aus demselben Vertrag; leerer Preis bleibt `null`, keine freie Enum-Eingabe. Dadurch auch echten Rücksprungfehler im neuen paginierten Katalog gefunden und behoben: Klickereignis darf nicht als `more=true` behandelt werden. Neue schmale Dokumentzeilen und 130er-Pagination bestehen im gezielten Browserlauf.
- Test-Isolation auch auf Papierkorb-Folgeaktionen erweitert: Archiv-/Purge-Provenienz aus dem Original erhalten; reiner Test-Papierkorb bleibt lokal. Gemischte Test-/Drive-Sammellöschung wird vor Speicherung abgewiesen; einzelne versionsgeschützte Test-Purges bleiben als auflösbarer Pfad erhalten. JVM-Verträge bestanden, neue native Archiv-/Empty-trash-Probe Teil des letzten Gesamtlaufs.
- Reales Legacy-Backup im unveränderten installierten Build 77 bleibt mangels dortiger Legacy-Foto-Unterstützung unvollständig. Keine sieben Altaktionen geschlossen, keine alte Löschung ausgeführt. Neues Gerät-/Connector-Endtestfenster erst nach autorisiertem Update.

## Eintrag 44 – kontrollierte echte Testentwurfs-Bereinigung (19.10.2026)

- Nach erfolgreichem vollständigem Android-Lauf mit 132 Proben/0 Fehlern ausschließlich den ausdrücklich freigegebenen falschen 1-EUR-Entwurf `id_sha256_760622aaec5679a80d4929b9852789a5354a8823cbbef7fb23defb5d49a08b45` frisch gelesen, exakten Titel/Entwurfsstatus/Betrag und Hash `acfa1c3c0e366e7d3f9556e1f032f08116e6da395417c1f2332342d1d6d0ead6` geprüft. Löschvorschau erlaubt, keine Fotoverknüpfungen. Reversibel archiviert, keine physische Löschung; Request-Beleg `mcp_owner_f762da2ce0789749b5969bf83bccfa3d86f2ae6d5cad0ec709e2ef7aa171e06e`.
- Frischer Vorher-/Nachher-Integritätsvergleich: ausschließlich Angebotsgruppe geändert, aktive Angebote 2→1. Unverändert 53 Kunden, 3 Aufträge, 45 Arbeitsaufzeichnungen, 95 Rechnungen, 25 Fotos, 33 Bausteine, 0 Termine; ihre jeweiligen Gruppenhashes identisch. Kein echtes Angebot neu erstellt, finalisiert oder versendet. Auto-Drive-Abgleich lieferte `transferred`/`uploaded_to_drive=true`, Gesamtstand weiterhin Provider-Readback ohne Cloud-Behauptung. Sieben Legacy-Reviews, keine ausstehenden Uploads.
- Reale Statusinkonsistenz des installierten Builds 77 reproduziert: Owner-Aktionsbeleg zeigte trotz Übertragung `confirmation_scope=local_only`. Quellfix verwendet tatsächliche `drive_delivered_ids` für Provider-Belege, unterscheidet davon `applied_on_pixel` und erhält Original-Owner-Entitätsangaben. Nur lokales Anwenden behauptet keine Übertragung; unabhängige Serverbelege bleiben erforderlich. JVM-Regressionsprüfung und Build/Lint bestanden; eigener Android-Provider-Positiv-/Negativfall im abschließenden Lauf.
- Keine sieben Altentscheidungen ausgeführt, da die vollständige reale Sicherung im installierten alten Build weiterhin an Legacy-Fotoreferenzen scheitert. Neues Originalupdate und realer Connector-/Legacy-Backup-Endtest bleiben offen.

## Eintrag 45 – finale native Abnahme und unabhängige Live-Änderung (19.10.2026)

- Originalsignierter Stand mit kompatiblen alten Transferbelegen: **133 native Android-/SQLite-/MCP-/Providerproben, 0 Fehler**, anschließend vorhandener synthetischer Updatebestand vollständig erhalten, echte WebView-Navigation und frischer getrennter Android-Datenbereich bestanden. Keine Pixel-Installation und kein Pixel-ADB.
- Letzter realer Readback änderte den Ausgang unerwartet von sieben auf **sechs aktive Legacy-Reviews**. Historie 12→15. Für `id_sha256_ca46d340bc52b9e93e578b4a47c1afa08b11c87a013e5c9dee7cd7624bc12824` enthält der Dienst `apply_proposed`, `resolved_at=2026-10-19T10:33:04.591674Z`, Resolution-Aktion `mcp_owner_ad17a4d7e84b3a8e5bcad2b615ea55ec5e80b83ec552f940b78df49036755ffc`. Diese Aktion wurde in diesem Auftrag nicht aufgerufen; hier wurde ausschließlich der bekannte Testentwurf archiviert. Keine Zuschreibung an eine Person und keine Umschreibung dieser Historie.
- Neuer Preview: `e6752c0dc5a04bc3f729caccdbc3187d87327f6adc0792ccc7f7f722b0a51c8b`, Count 6. Die ursprüngliche Siebener-Vorschau ist ungültig; **keine Sammelentscheidung und kein weiterer Legacy-Schreibaufruf**. Geschäftsgruppenhashes außerhalb der erlaubten Angebotsbereinigung bleiben im Vergleich identisch. Kein Nachweis unabhängiger Google-Cloud-Bestätigung.
- Damit eine künftig ausdrücklich neu bestätigte Sechser-Auswahl nicht an einer künstlichen Siebener-Konstante hängen bleibt, Backup-Policy auf 1–100 vollständig historische aktive Einträge mit exakt passendem Queue-Umfang begrenzt. Für eine von sieben abweichende Anzahl zwingend aktueller Previewhash; alte Siebener-Hashes und zusätzliche technische/neue Aktionen bleiben gesperrt. Frischer Preview, Backup, Hashprüfung und `confirmed=true` für den Abschluss bleiben unverändert Pflicht. Host-Regressionsfall: 7→6 mit altem Hash FAIL, ohne frischen Hash FAIL, mit neuem Hash PASS, zusätzliche Aktion FAIL.
- Diese Policy-Änderung führt keine tatsächliche Entscheidung aus und autorisiert keine automatische Anpassung der alten Sammelfreigabe. Realer Backup-/Cleanup-Endtest nach gesichertem Geräteupdate weiterhin offen.

## Eintrag 46 – finaler Build 79 und unveränderte Installationssperre (19.10.2026)

- Finaler Release erhält **1.51.0 / 79**. Build 78 bleibt interner, nicht freigegebener Prüfkandidat; nach der zusätzlich notwendigen dynamischen Legacy-Auswahl wird dessen Versionsnummer nicht für die endgültige Auslieferung wiederverwendet. Originalpaket und Signer unverändert. Aktuell tatsächlich verbundener Pixel bleibt 1.50.1/77, Protokoll8/111 Tools.
- Für einen weiteren frischen Abnahmedatenbereich wird ein neuer isolierter Context des Originalcodes benutzt; kein vorhandenes Android-Testprofil oder App-Datenbestand wird gelöscht. Bereits zwei neu angelegte Betriebssystem-Testprofile sowie vorhandene Upgrade-Daten bleiben erhalten. Kein neues APK-Paket und keine zweite App-Codebasis.
- Die erstmals beobachtete, nicht durch diesen Auftrag aufgerufene Apply-proposed-Entscheidung betraf einen schon in der ursprünglichen Vorschau nicht vorhandenen Datensatz (`current_hash=SHA256(null)`). Die bestehenden Geschäftsgruppen außer dem ausdrücklich archivierten Fehl-Testentwurf blieben hashgleich. Die Audit-Historie wird nicht umgeschrieben. Sechs Reviews sind der neue beobachtete Zustand; keine automatische Übernahme der alten Siebener-Freigabe.
- Geschütztes reales Update und unabhängige Sicherung/Originalfoto-Prüfung bleiben Voraussetzung für den echten Connector-/Cleanup-Endtest. Keine Installation auf dem physischen Pixel erfolgt.

## Eintrag 47 – endgültige Original79 qualifiziert und Auslieferung konsolidiert (19.10.2026)

- Finaler APK-Quellcommit `5ae296394b5a9103f94421ea04ba01e55d06f8f0`, **1.51.0/79**, Paket `at.hausgarten.service`, Quellhash `b8b68448d1b6971c8cde6ebd7a00c7ccd88ecb6cf15c9a7b0065a9962c05f4da`, source_dirty=false, release. Späterer Liefercommit ergänzt ausschließlich APK und zentrale Dokumentation; dessen SHA aus `git log -1`, nicht mit eingebettetem APK-Commit verwechseln.
- Endgültige79: **133 native Android-/SQLite-/MCP-/Providerproben,0 Fehler**, anschließend In-place-Update mit erhaltenen synthetischen IDs/Beziehungen/Queue/Einstellungen/Tunnel/aktivem Timer, native WebView und frischer isolierter Start bestanden. Erneut24 JVM-Suiten/273 Python-Tests erfolgreich;28 Browser-Suiten der unveränderten betroffenen UI-Implementierung erfolgreich. Neue Estimate-Verträge121, Paritätsprüfungen1032, Papierkorb-/Backup-Policy173. Gradle Release/Instrumentation/Lint erfolgreich,33 nichtfatale Hinweise ausdrücklich verbleibend.
- APK **34.504.813Bytes**, SHA256 `b06798341ac929d00145f566db26d15377c3472b676e4d96b275a677291cc891`; bestehendes Zertifikat `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`, ein Signer, v2/v3,16-KiB-Ausrichtung und411 bytegleiche Assets geprüft. Privacy Source/Artefakt PASS, keine Betriebs-Seeds. Ausschließlich Originalsignierwerkzeug, keine neue Signatur. Signier-Tempverzeichnisse0/getrackte Schlüssel0.27 eigene alte interne78-APKs entfernt; alte Originaldownloads, automatisierte Tests und vorhandene Emulatorprofile erhalten.
- Finalen privaten Download im vorhandenen Buildordner unter derselben eigenen Kandidaten-ID `14t3aDwlKqPZkPWNH2qYRg8pXbdlr0H3v` auf79 aktualisiert. ID/Name/MIME/Eltern/Größe/Owner-only und vollständige Rohdateireferenz34.504.813Bytes rückgelesen; keine öffentliche Freigabe/Inline-Binärdaten und kein unabhängiger Remote-SHA behauptet. Downloads/Prüfsumme und bestehende zentrale Dokumentation aktualisiert; sämtliche Review-Fälle in Abschnitt31 des Auftrags eindeutig eingeordnet.
- Quellkatalog **117/Protokoll9**, Hash `235f683d99d5170801d1e34c19ad386d9c4acf68b60a6992f72a3e5d2ea14314`. UI **156**, davon145 fachliche MCP-Pfade und elf explizite Plattform-/Darstellungsgruppen, vollständig100+56, Hash `34c9cdd7494658bb1a3ec0a71db267e1ffa1044fd31e6a735c5be2ce0c8a0d91`. Neuer Katalog/Fingerprint im Originalcode geprüft, nicht auf dem unveränderten physischen Pixel behauptet.
- Reales abschließendes Readback19.10.2026 **11:03:55UTC**: Pixel **1.50.1/77**,111Tools/Protokoll8, ready/write_enabled=true, persistierte Drive-Berechtigung, kein technischer Syncfehler, **0 Uploads/6 Reviews/15 Historie**, Provider-Readback/cloud_confirmed=false. Vorschau weiterhin sechs/e6752c0d… . Kalender nicht verbunden/Wetter nicht eingerichtet. Einzige reale Änderung dieses Auftrags bleibt die ausdrücklich erlaubte reversible Archivierung des falschen1-EUR-Testentwurfs; außer Angebotsgruppe alle Geschäftsgruppenhashes unverändert. Keine Legacy-Abschlussoperation durch diesen Auftrag, keine echte Mail, kein neues/finalisiertes/erneut versendetes echtes Angebot.
- **Gesamtauftrag nicht vollständig abgenommen:** unabhängige Gerätesicherung und ausdrückliche Installationsfreigabe, physischer Pixel-Update-/Datenerhalt-/Kamera-/Connector-Test, tatsächlicher117er-Katalog nach Refresh, reale Legacy-Originalsicherung und frisch bestätigter Sechser-Keep-current-Abschluss, echte Gmail-Konto-/API-Freigabe/Versandreadback sowie Kalender-/Wetter-/Hintergrundverhalten bleiben offen. Die ursprüngliche Siebener-Freigabe gilt nicht automatisch für veränderte Hashes. Keine Sicherungs-/Konfliktschutzprüfung entfernt und kein Cloud-Abschluss erfunden.


## Eintrag 48 – P0-Datentrennung / Bestandsprüfung (19.10.2026)

- main per Fast-forward auf aa04cbd aktualisiert; keine vorhandenen Änderungen, neue Branch oder Historienänderung. Regeln/Auftrag/Status gelesen.
- Reproduziert: export_mobile_snapshot schreibt Betriebsdaten ins Android-Bootstrap und outputs; db.py fällt auf Repository-data zurück; Geräte-Backup-/Update- und Audithelfer schreiben Rohdaten oder rohe Beziehungen in den Checkout. Vorhandenes Privacy-Gate liest optional eine produktive DB und prüft ignorierte Dateien nicht.
- Read-only Workspaceinventar ohne Werteausgabe: keine Geschäfts-SQLite mit betrieblichen Tabellen gefunden; ältere ausschließlich synthetische Browserprofile enthalten allgemeine Chromium-DBs. Ein synthetisches Kunden-/Auftragsfixture vorhanden. Kein Produktivbestand kopiert, migriert oder verändert.
- Abnahme: fehlender/interner/symlink-umgeleiteter Datenroot blockiert vor Dateizugriff; Bootstrap exakt leer; Exporte/Backups nur extern; zentrale synthetische Profile und temporäre Teststores; drei verpflichtende Gates einschließlich ignored/untracked und Archivprüfung; technische Reports ohne Geschäftsinhalte. P0 bleibt bis zum vollständigen Schlusslauf offen.


## Eintrag 49 – P0-Datentrennung / Pfade, Fixtures und Gates implementiert (19.10.2026)

- Host-Pfade zentral über lazy runtime_paths; fehlender/interner/relativer/Symlink-Root und QA-Zugriff auf fremde/private Stores fail-closed. Tests registrieren ausschließlich neu angelegte eigene Tempstores, unbekannte nichtleere Ordner werden nicht umgewidmet. Original-Pixel-/Drive-Adapter und IDs/Formate unverändert. Kein privater Root für reale Daten konfiguriert oder produktiver Bestand migriert.
- Snapshotexport read-only nur extern/exklusiv, kein db.init/Thumbnail-/Assetschreiben. Leerer Bootstrap mit eigenem konstanten Generator/Vertrag. DB, Bilder, Drive-/MCP-Hostkanäle, historische Importe, Backup-/Update-/Audithelfer auf externe Pfadprüfung umgestellt; technische Audit-/Vergleichsberichte nur Counts/Hashes/Schema/Relationsinformation. Reale Aussteller-Testwerte und personenbezogener Angebotsname aus aktuellen Quellen entfernt, historische Angaben als technische Nachweise erhalten.
- Zentrale JSON-Factory minimal/edge_cases/scale, Python-Tempadapter, Browseradapter und generierte JVM-/AndroidTest-Adapter. Test-Eingabe-IDs in QA-Namespace überführt, example.invalid und künstliche Aussteller-/Adressdaten. Fachliche Legacyformat-/IDgenerator-Proben bleiben künstliche isolierte Vertragsfälle; keine produktiven IDs als Fixtures. 25 nachweislich lokale synthetische Browserprofile und38 entbehrliche alte QA-Screenshots entfernt; keine Originalmedien oder Betriebsdateien angefasst.
- Erster Source-/Workspace-/Artifact-Schlusslauf PASS: gesamter Checkout einschließlich ignorierter/untracked Dateien, umbenannte SQLite-Dateien, Archive und bestehende79-APK (0 operative Seeds). Dokumentierte synthetische Bildnachweise erhalten Bytehash/Fixture-Herkunft; unbekannte Bilder bleiben gesperrt. Gates zusätzlich vor/nach Quality-Suite und im Releaseprozess verankert.
- Neue negative Privacy-/Pfad-/Export-/Reportregressionen;288 Python-Tests zuletzt PASS vor abschließender Gate-Verschärfung. JVM-Migrationsläufe fanden nur inkonsistente alte Fixture-Referenzen/Enum- bzw. HTML-ID-Ersetzungen; zurück auf unveränderte fachliche/DOM-Verträge korrigiert. Letzter kompletter24-JVM-Lauf PASS. Browser-Gesamtregression läuft erneut; ein alter GPS-Test musste nach Adress-Anonymisierung konsistent auf dieselbe künstliche Straße umgestellt werden. Kein App-Fehler daraus behauptet. P0 bleibt bis finalen vollständigen Tests/Gates OFFEN.

## Eintrag 50 – P0-Regressionsphase und native Umgebungsgrenze (19.10.2026)

- Nach Fixture-Migration alle24 JVM-Suiten und alle29 Browser-Suiten PASS.18 gezielte Privacy-/Pfadfälle einschließlich unbekanntem nichtleerem Store, Rootrechten, umbenannter SQLite/JSON, Archivfoto, Reportdaten und Source-Writer-Prüfung Bestandteil der abschließenden Python-Abnahme.
- Zusätzliche Pfadnachprüfung: historischer physischer Native-Diagnosehelfer schreibt ausschließlich unter privaten externen Root; alter verify.py verwendet jetzt denselben read-only Hash-/Counts-Audit statt Initialisierung und festen Umsatz-/Importwerten. Source-Pakethelfer übernimmt geprüften Originalcode ohne anonymisierte Ersatzruntime; alle drei Gates vor Freigabe der tatsächlichen ZIP-Bytes.
- Native Android-Abnahme derzeit NOT TESTABLE: /dev/kvm fehlt, Emulator meldet fehlende CPU-Beschleunigung. Begrenzter Softwarestartversuch läuft; kein physischer Pixel berührt, keine App-Daten migriert. Bisherige133 Android-Proben gehören ausdrücklich zum historischen79-Nachweis, nicht zur aktuellen P0-Abnahme.
- Historienprüfung rein lesend:8 unterschiedliche bekannte Bootstrap-/Ausstellerobjekte geprüft,4 sensible frühere Aussteller-Fixture-Befunde. Kein befülltes historisches Bootstrap in diesem begrenzten Prüfumfang. Klassifikation: produktive sensible Daten in Historie gefunden; keine vollständige forensische Historienprüfung behauptet und keine Historie umgeschrieben. Separate berechtigte Historienbereinigung bleibt offen.

## Eintrag 51 – P0-Artefaktbefunde überprüft (19.10.2026)

- Release-Gate stoppte fünf ignorierte alte AndroidTest-APKs: deren DEX enthielt frühere feste synthetische Tunnel-/Organisations-IDs. Vor Entfernung jeweils tatsächlich Paketkennung at.hausgarten.service.test und Übereinstimmung sämtlicher Treffer mit alten Testquell-Literalen geprüft; untracked, ausschließlich abgeleitete Testartefakte entfernt. Operative APK-Downloads, Kundenmedien und Emulator-Datenbereiche erhalten. Neue Testverbindungen werden erst zur Laufzeit deterministisch erzeugt und nicht als opake Werte eingebettet.
- Öffentliche Bildausnahmen auf SHA-geprüfte Markenbilder und explizite lizenzierte Pflanzenkataloge begrenzt; ein beliebiges neu eingelegtes Bild erhält keine Freigabe durch den Ordnernamen. Unklassifizierte Bilder und Archivmedien bleiben FAIL.
- Software-Emulator ohne Beschleunigung startet den Android-Boot in dieser Sitzung nach begrenztem Versuch nicht vollständig; eigener Prozess beendet, vorhandenes AVD nicht gelöscht. Aktuelle native Abnahme weiter NOT TESTABLE. Erste Python-Gesamtprobe fand eine noch alte künstliche Rechnungsnummernerwartung und eine übergreifend gemockte Git-Toolfunktion im Signer-Unit-Test; Testgrenzen/erwartete synthetische Nummer korrigiert, fachliche Rechnungs-/Signaturlogik unverändert. Abschließende Regression erneut gestartet.


## Eintrag 52 – P0-Build-/Hostabnahme (19.10.2026)

- Release-APK, Release-AndroidTest, AAB und lintRelease tatsächlich erfolgreich gebaut (94 Tasks, Gradle5m03s). Source-/Workspace-/APK-/AAB-Gates PASS;0 operative APK-Seeds.19 gezielte Privacy-Fälle PASS, zuletzt291 Python-Tests vor zusätzlichem verschachteltem Drive-Symlink-Fall PASS; abschließender292er-Quality-Lauf folgt.24 JVM-/29 Browser-Suiten und55 isolierte MCP-stdio-E2E-Szenarien PASS. Keine QA-Ersatzsignierung ausgeführt.
- Externe Pfadprüfung auch für verschachtelte Medien-/Dokument-/Thumbnail-/Tempdateien und SQLite-Readbacks; Repository-Symlink vor Export blockiert. Neue Regression PASS. Bootstrap-Writer-Gate umfasst direkte/aliasierte Writes, Copy/Move/Replace und bekannte Shell-/JS-/PowerShell-Schreibpfade. Leere ältere APK-Bootstraps bleiben kompatibel, unbekannte Felder/Versionswerte sind blockiert.
- Kandidat1.51.1/80, unveränderte App-ID at.hausgarten.service, MCP9/117. APK-Fingerprint kennzeichnet den tatsächlichen Arbeitsstand aa04cbd/source_dirty=true; kein behaupteter sauberer Releasecommit und keine Auslieferungs-/Installationsfreigabe. Der bisher qualifizierte Download79 bleibt unverändert. Originalsignaturprüfung dieses P0-Kandidaten läuft ausschließlich über tools/sign_original_apk.py.
- Vollständige P0-Abnahme weiter OFFEN: Vertrags-/Schema-/Relations-/Buildprüfungen erfolgreich, neue native Android-Laufzeit-/Update-Abnahme dieser migrierten Tests wegen fehlender Cloud-Beschleunigung NOT TESTABLE. Physischer Pixel und produktiver Drive unberührt. Historische sensible Git-Objekte separat offen; keine Historie umgeschrieben.


## Eintrag 53 – P0-Schlusslauf ohne produktive Daten (19.10.2026)

- Quality-Suite tatsächlich294 Tests PASS,0 Fehler/Skips, darunter21 Datenschutz-/Pfadfälle.37 historische QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen, damit keine Ersatz-/QA-Schlüssel entstehen; Originalsignierungsregressionen enthalten. Vorher-/Nachher-Source und vollständiger Workspace PASS (1126 Quellen,3011 Workspace-Dateien einschließlich ignored/untracked), tatsächliche originalsignierte APK PASS,0 operative Seeds. Einzelne JSON-Datensätze und bekannte private Literale auch in DEX/Klassen/Ressourcen werden geprüft.
- Source-Review-ZIP aus1114 aktuellen Originaldateien tatsächlich erzeugt und byteweise Artifact-Gate PASS, SHA256 fec1b8728934d30277450f19ac5bc1d959164a46f6b6dc61c1e6fe8cf4fcfc11. Nur vorübergehendes QA-Artefakt außerhalb des Checkouts; keine Ersatzruntime und kein zusätzliches Dossier. APK/AAB und Sourcepaket-Gates damit jeweils tatsächlich ausgeführt.
- Originalkandidat1.51.1/80, APK-SHA256 d8208fd3c5b42e52688778195ce7d276cde09ddaf2416c783003de6b1bf133aa; Zertifikat a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8 identisch mit bestehender79. Signatur v2/v3 und16-KiB-Alignment überprüft,411 aktuelle Assets bytegleich, Signier-Tempverzeichnisse0. Originalsignierwerkzeug allein verwendet. Keine Pixel-Installation oder Produktivdatenaktion.
- Zweiter begrenzter Software-Emulatorversuch: system_server und Bootanimation laufen, bislang0 beobachtete Java-/native Crashmarker; Bootabschluss noch nicht bestätigt. Native Abnahme weiter offen, keine alte133er-Probe als P0-Nachweis wiederverwendet. Temporärer bestehender AndroidTest-Runner wird für einen möglichen reinen Cloud-Emulatorlauf ausschließlich originalsigniert; keine neue operative App oder QA-Signatur.


## Eintrag 54 – P0 echtes Cloud-Emulatorupdate, Datenvergleich nur technisch (19.10.2026)

- Software-Emulator schließlich vollständig gestartet; frühere NOT-TESTABLE-Startbefunde durch diesen tatsächlichen Nachweis überholt. QEMU=1, exakt AVD GartenpiratCloud, expliziter emulator-5582 und exklusives Installations-/Testlock geprüft. Kein physisches Gerät.
- Vorhandene operative QA-App tatsächlich1.50.1/77, nicht ungeprüft79 angenommen. Vor Update ausschließlich eindeutig künstliche QA-Kunden im vorhandenen Emulatorstore geprüft; Original-AndroidTest-Runner mit Originalzertifikat installiert. Danach ausschließlich normales Originalpaket-Update -r auf1.51.1/80, keine Deinstallation/Datenspeicherlöschung und kein neues operatives Paket.
- Business-Readbacks vor/nach Update nur im Prozessspeicher ausgewertet, keine Base64-/Rohdaten gespeichert oder ausgegeben. Geschäftsmaster ohne technische capabilities und gesamte Pending-Queue kanonisch SHA256 be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af unverändert.3 künstliche Kunden/1 Auftrag/2 Arbeitsaufzeichnungen/1 Pending-Aktion erhalten,0 Fotos/Rechnungen/Angebote;32 öffentliche Bausteine und0 Termine unverändert. IDs/Beziehungen/sonstige Masterfelder durch identischen Ledgerhash mitgeschützt.
- Vollständige native Android-SQLite-/MCP-/Provider-Abnahme jetzt auf diesem originalsignierten80er-Build gestartet; Ergebnis folgt separat. Keine physische Pixel-Kompatibilität oder echte Google-Providerfreigabe behauptet.


## Eintrag 55 – P0 native Fixture-Abnahme nach echter Ausführung korrigiert (19.10.2026)

- Erster neuer vollständiger Android-Lauf:128 PASS/5 FAIL. Ursache ausschließlich migrierter Testprovider: Datei-IDs wurden zu qa_docN geändert, drei Referenzen blieben doc1; drei Schreibmodusprüfungen wurden versehentlich von w auf qa_w umbenannt. Dadurch falsch simulierte Providerfehler und fehlende Testdatei. Alle Referenzen konsistent, echte Android-Dateimodi wieder unverändert; keine operative Drive-Logik verändert.
- Native WebView-Probe sucht eindeutig benannte QA-Einsatzort-/Pflege-Datensätze statt eine Änderung erhaltener älterer synthetischer IDs zu verlangen. Keine bestehenden Datensätze für einen passenden Test überschrieben. AndroidTest-Neubuild mit Pflichtgates erfolgreich; erneute Originalsignierung/native Gesamtabnahme läuft.
- Zusätzliche Workspace-Regressionsprüfung: nur die tatsächliche .git-Historie des Checkoutroots ist separat; verschachtelte Ordner mit Namen .git können keine SQLite-Dateien verstecken.22 gezielte Privacy-Fälle PASS, neuer Python-Gesamtlauf295 Tests PASS vor abschließendem Nachher-Gate; endgültiger Gatebeleg folgt.


## Eintrag 56 – P0 native133 bestanden, vollständige QA-Backupisolation nachgeschärft (19.10.2026)

- Wiederholung nach konsistenten synthetischen Dokumentreferenzen und unveränderten echten Dateimodi:133 native Android-SQLite-/MCP-/Providerproben PASS,0 Fehler. Root-Drive-Snapshot vor/nach Proben unverändert. Enthalten Originalfoto/Rotation/21 Uploads, Offers/Estimates/PDF/Restart/Journal, Provider-Retry/Readback und sieben vollständig synthetische Legacyfälle; keine tatsächlichen Pixel-/Drive-Altaktionen.
- Zusätzliche Isolationanalyse: der vorhandene native QA-Kontext überschreibt Dateien/Cache/Prefs/DB, leitete getNoBackupFilesDir aber noch an den Basiscontext weiter. Nun eigener QA-Backupordner und neue native Pfadprüfung für alle vier Speicherarten. Operative App-Backupimplementierung unverändert. Letzter AndroidTest-Neubuild/134er-Lauf folgt; bis dahin kein P0-Abschluss.
- Originalsigner-Privacyfehler darf nur seine eigene unveränderte Ausgabedatei entfernen. Ein nachträglich fremd ersetztes Ziel bleibt erhalten; tatsächlicher Artefakt-SHA muss zum signierten Resultat passen.42 synthetische Signer-Tests PASS, darunter Gate-Rejection ohne Fremdverlust. Keine zusätzliche Schlüsselidentität erzeugt.
- Letzter vollständiger296er-Quality-Lauf mit22 Privacy-/Pfadfällen läuft. Native WebView-/Fresh-Proben ebenfalls gestartet. Frühere133er-PASS-Angabe ist tatsächlicher neuer P0-Lauf; die zusätzliche134. Speicherisolation wird ausdrücklich noch nicht als bestanden behauptet.


## Eintrag 57 – P0 WebView/Fresh und Bootstrap-Erhaltsschutz (19.10.2026)

- Tatsächliche native WebView-Probe auf80 PASS: kompatibles JavaScript, Navigation, Auswahl/Abbruch, Kunden-Garten, Papierkorb und auftragsbezogener Terminpfad. Eindeutige QA-Datensätze dynamisch gefunden, ursprüngliche IDs erhalten. Anschließend fresh_isolated PASS:0 operative Startdaten, keine Credentials, kein automatischer Drive-/Tunnelzugriff.
- Backupisolations-Erweiterung kompiliert und ausschließlich originalsigniert, zusätzliche134. Pfadprobe im nun gestarteten Gesamtlauf. Produktiver App-Code und80er-APK gegenüber der erfolgreichen133er-/Update-/UI-Probe unverändert.
- Konstanter Bootstrapgenerator verweigert jetzt zusätzlich Symlinks und vorhandenen nichtleeren/unbekannten Inhalt. Er kann einen versehentlich befüllten Seed nicht still durch Leerinhalt ersetzen; Bestand bleibt zur Prüfung erhalten.23 gezielte Privacy-Tests PASS,42 Signer-Tests PASS, abschließender297er-Gesamtlauf gestartet.


## Eintrag 58 – P0 vollständig im festgelegten Abnahmeumfang bestanden (19.10.2026)

- P0.13-Tabelle vollständig14/14 PASS.297 Python-Tests,0 Fehler/Skips, darunter23 Privacy-/Pfad-/Bootstrapfälle und42 Originalsignierungsregressionen;37 historische QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen.24 JVM- und29 Browser-Suiten sowie55 synthetische MCP-stdio-E2E-Szenarien bestanden. Keine Testdaten von realen Kunden verwendet.
- Endgültiger originalsignierter80er-Stand:134 Android-SQLite-/MCP-/Providerproben,0 Fehler. Neue Speicherprobe bestätigt eigene QA-Pfade für Datenbank, Medien, Cache und NoBackup. Danach erneut native WebView-Navigation und fresh_isolated PASS. Vollständiger Schlussreadback nur im RAM: Geschäftsmaster ohne capabilities plus gesamte Pending-Queue weiterhin SHA256 be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af. Alle synthetischen IDs/Relationen des geprüften Geschäftsmaster und1 ausstehende QA-Aktion nach Update und sämtlichen Proben erhalten; keine Rohdatensätze/Base64 gespeichert.
- Source und Workspace vor/nach letzter Quality-Suite PASS; Workspace3032 Dateien einschließlich ignored/untracked (Dateizahl variiert durch ausschließlich geprüfte Build-/Testartefakte). Source1126 Dateien; untergeordnete .git-Namensverzeichnisse eingeschlossen, tatsächliche Git-Objekte separat. Tatsächliche originalsignierte APK, AAB und Source-ZIP jeweils Artifact-Gate PASS; operative APK-Seeds exakt0. Pflichtgates im normalen Quality-/Build-/Signier-/Sourcepaketweg.
- Release/AndroidTest/AAB/Lint gebaut;0 Lintfehler/33 bestehende Hinweise. App1.51.1/80, Paket at.hausgarten.service, MCP9/117 unverändert. Produktions-App-Quellhash d329f9714af8ab6370e76a5a0d358cff79c0f7618feb5a8635a826f1af626ebd passt zum endgültigen App-Quellinhalt; APK-Fingerprint dokumentiert korrekt den Build aus aa04cbd mit damaligem source_dirty=true. Kein behaupteter anderer eingebetteter Commit. Bisheriger qualifizierter Download79 bleibt unverändert; neuer80er-P0-Kandidat ist lokal qualifiziert, nicht auf Pixel installiert/veröffentlicht.
- Original80-APK-SHA d8208fd3c5b42e52688778195ce7d276cde09ddaf2416c783003de6b1bf133aa; Originalzertifikat a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8 identisch mit79, ein Signer/v2/v3/16-KiB-Alignment,411 Assets bytegleich. Sole Signierweg tools/sign_original_apk.py, kein Ersatz-/QA-Schlüssel. Vorhandene nichtleere Bootstrapinhalte und fremd ersetzte Signerausgaben bleiben erhalten; Gatefehler blockieren statt blind zu löschen.
- Historienprüfung nach Implementierungscommit erneut nur lesend:12 unterschiedliche bekannte Bootstrap-/Ausstellerobjekte,4 sensible ältere Aussteller-Fixture-Befunde,0 nichtleere historische Bootstraps in diesem begrenzten Umfang. Klassifikation produktive sensible Daten in Historie gefunden. Kein vollständiger forensischer Audit und kein History-Rewrite; separater Sicherheitsauftrag bleibt erforderlich. Das ist ausdrücklich getrennt vom bestandenen aktuellen Checkout-/Build-P0 gemäß P0.11.
- Physischer Pixel, produktiver Drive, echte Kalender-/Gmail-/Tunnelkonten und tatsächlicher ChatGPT-Connector wurden in diesem P0-Auftrag nicht verwendet oder aktualisiert. Reale Legacyaktionen unverändert. Der Emulatornachweis ersetzt keinen physischen Update-/Datenerhalt-/Provider-Test; frühere offene Funktionsabnahmen bleiben bestehen. Keine produktive Datenmigration durchgeführt; HG_PRIVATE_DATA_ROOT für echte Hostdaten absichtlich nicht eingerichtet.


## Eintrag 59 – P0 Cleanup und Übergabe (19.10.2026)

- Eigenen Software-Emulator nach134er-/WebView-/Fresh- und erneut hashgleichem Ledger-Schlussreadback normal beendet; vorhandenes AVD samt synthetischen Daten nicht gelöscht. Eigenes temporäres Source-ZIP und drei eigene signierte Zwischen-Test-APKs nur nach Übereinstimmung ihrer bekannten SHA entfernt; bestehende operative Downloads und automatisierte Tests erhalten.
- Signier-Tempverzeichnisse0, getrackte Keystore-/Privatschlüsseldateien0. Kein privater Root eingerichtet, keine produktiven Dateien migriert, kopiert oder gelöscht. Aktuelle Betriebssystem-/Build-/Testartefakte im Checkout durch Workspace-Gate geprüft; reine Code-/synthetische Artefakte zulässig. Letzter Checkout-Gatelauf bestätigt Source/Workspace/Signed-APK PASS; abschließender normaler Abnahmecommit und Remote-Abgleich folgen ohne Historyänderung.

## P1 – R0 Baseline und Charakterisierung (19.10.2026)

- Ausgang main `1abf049c1e27167bc16259d8717513aeb5ffe670`, sauber per Fast-forward; nur neuer P1-Auftrag seit P0. Quelle1.51.1/80, App-ID unverändert, MCP9. Keine Betriebsdaten und kein privater Hostroot verwendet.
- Analyse: MainActivity→LocalStore→PixelOwnerAccess→MainActivity.LocalStore ist eine unerwünschte konkrete Kopplung; PixelOwnerAccess mischt SQL-Dateitransfer, Use Cases, Provider und Transportbelege. MCP-Reader mischt Filter/Projektion mit RPC; OwnerTools mischt Schema/Registrierung und Konfliktabfragen. UI-Module duplizieren synchrone ownerCall-/Fehleradapter. Domainregeln und ursprüngliche Journale bleiben maßgeblich, keine neue Datenquelle.
- Ausgangsmetriken (statische Quellmessung, keine Laufzeitabdeckung): OwnerAccess3 Switches/58Cases/38 Projektabhängigkeiten/8SQL-Aufrufe; ReadTools2/32/14/0; OwnerTools2/66/7/0; MainActivity2/10/43/30,117635Bytes. Größter Hotspot MainActivity1255Zeilen. Nicht nur Zeilenzahlen: Ziel sind getrennte Abhängigkeitsrichtungen und atomare bestehende Storegrenzen.
- Neue Charakterisierung:23 eingefrorene synthetische Lese-/Fehler-/Write-off-Antworten, unveränderte Eingabe. Alle bisherigen CAS/Replay/Referenz-/Provider-/Isolationstests bleiben erhalten. Maschinenlesbare unveränderliche Baseline `tests/fixtures/refactoring-baseline.json`:117Tools,52Read/65Write, vollständige Schemas/Annotationen,156UI-Aktionen,12 Domain-/Formatquellhashes, Bootstrap/Offervertrag und Antworten. Gesamtvertrag SHA256 `70130d3fb75eda3d425d9d5a4f982e61e47b3cbcff421723b243baaa959f1820`. Java-Runner erzwingt nach jedem Lauf den Vergleich.
- Bisher PASS:24 bestehende JVM-Suiten plus neue Charakterisierung,29Browser-Suiten,55MCP-stdio-Szenarien. Vollständige Python-/Privacy-Baseline läuft noch; **R0 noch nicht abgeschlossen, kein struktureller Umbau begonnen**.

### R0 abgeschlossen

- PASS297Python/0Skips,25JVM einschließlich Charakterisierung,29Browser,55MCP; zusätzlich3Negativtests des neuen Vertragsvergleichers PASS.37 Schlüssel erzeugende QA-Tests wie P0 ausdrücklich ausgeschlossen. Source/Workspace vor und nach Quality sowie tatsächliche Original80-APK Artifact PASS,0Seeds.
- P0-Gate wies zunächst zwei eigene neue QA-Antwortdateien ohne durchgängige Testmarkierungen ab. Diese ausschließlich synthetischen Nachweise auf kanonische Antwort-Hashes umgestellt, keine Gateausnahme. Der erneute komplette Lauf bestand. Noch keine strukturelle Änderung.
- Endgültige Vorher-Baseline zusätzlich57öffentliche JavaScript-Bridge-Signaturen und5SQLite-DDL-Anweisungen, Gesamtvertrag SHA256 `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`. Die zuvor genannten Gesamt-Hashes sind historische R0-Zwischenerfassungen, keine aktuelle Referenz. Baseline wird nach Beginn des Refactorings nicht neu aufgenommen.
- Nächste Phase R1: reine Snapshotabfragen, Owner-Schemabuilder und zentrale kompatible Fehlerabbildung. Original80 bleibt native Vorher-APK; Software-Emulatorstart läuft noch.

## P1 – R1 Contracts/Routing abgeschlossen (19.10.2026)

- Reine Snapshot-Auswahl/Binärredaktion in SnapshotReadSupport; fünf domänenspezifische Queryhandler in BusinessQueries, kein Android/SQL/Provider. Reader koordiniert weiterhin Validierung, Autorisierung und unveränderte Antwortdekoration. OwnerToolSchemas verwendet registrierte Feldbuilder statt globalem Feldswitch; McpFields ist gemeinsame schemaabhängigkeitsfreie Quelle für Owner/Offer/Planning/Entity-Tools. Keine Enum-/Schemaänderung. McpErrors vereinheitlicht intern die Abbildung, bewahrt bestehende unterschiedliche RPC-/Bridgecodes und begrenzte Provider-Retry-Metadaten.
- PASS26JVM-Suiten (neue14Fehler-/Redaktionsvergleiche),55MCP-Szenarien, unveränderter Gesamtvertrag `899906c2…aa813`. Source/Workspace und bestehende Original80-APK erneut PASS,0Seeds. Native Ausgangsabnahme noch offen: Software-Android meldete Bootproperty vor erreichbarem ActivityManager; erster Aufruf ausdrücklich fehlgeschlagen, kein Teilerfolg behauptet. Kein physisches Gerät.
- Messung: Reader2→1Switches,32→14Cases,31279→20263Bytes; OwnerTools2→1Switches,66→28Cases,25283→14781Bytes. Fachliche Queryzweige existieren einmal je Domäne, Schemafelder einmal je Builder; keine parallele Runtime. UI- und Domain-/Datenformatverträge unverändert.
- Nächster Schritt R2: Use Cases nacheinander hinter einen Android-/SQL-freien Repositoryvertrag stellen; nach jeder Domäne vollständige JVM-/Vertragsregression.

- R2 Teilabnahme Offer: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme Work: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme Photo: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme SyncArchive: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme Settings: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme Invoice: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme Planning: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

## P1 – R2 Application Services abgeschlossen (19.10.2026)

- Alle sieben Ownerdomänen einzeln herausgelöst, jeweils komplette26JVM-/Vertragsregression danach PASS (Teilabnahmen oben). EntityApplicationService/EntityContract trennen zusätzlich Kunden-/Auftrags-/Rechnungsentwurfsabläufe von MCP-Schemata; nach dieser Domäne ebenfalls vollständige Regression PASS. Gemeinsame OwnerCommand-/OwnerRepository-Ports enthalten keine Android-/SQL-/UI-Typen. Router hängt von den Ports ab; Services hängen nicht vom Router ab. Atomare SQLite-/Replay-Grenze unverändert in nativer Implementierung.
- Schlussprüfung PASS27JVM-Suiten einschließlich79 direkter Service-/Port-/CAS-/Referenz-/Datei-/Konto-/Backup-/Seiteneffektprüfungen,55MCP-E2E; Vertrag weiterhin exakt `899906c2…aa813`. Erster neuer Service-Test übergab berechnete/stored Felder als MCP-Eingabe und wurde korrekt abgewiesen; Fixture auf erlaubte Eingabefelder begrenzt, kein Schema oder Schutz geändert.
- Gradle Release/AndroidTest PASS; Source/Workspace und tatsächliche neue unsigned Zwischen-APK Artifact PASS,0Seeds. Nicht signiert/ausgeliefert/installiert. Bestehende Original80 unverändert. OwnerAccess61731→34720Bytes,58→21Cases,38→29Projektabhängigkeiten; fachliche Kommandos hinter testbarem Effektport statt im nativen Dispatch.
- Zusätzliche native Vorher-Abnahme jetzt tatsächlich134/0 PASS auf unveränderter Original80. Eingangsledger nur im RAM verarbeitet: SHA256 `be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af`,3QA-Kunden/1Auftrag/2Arbeiten/1Pending,0Fotos/Rechnungen/Angebote,32öffentlicheBausteine/0Termine. Frühere Boot-/Timeoutversuche keine Erfolge; nach vollständiger Systembereitschaft gelaufen. Dies ist die Vorher-Abnahme, noch kein nativer Nachher-Nachweis des Refactorings.
- Nächster Schritt R3: vorhandenen SQLite-Store aus Activity-Lifecycle lösen, native Effekte/Receipts hinter LocalOwnerRepository und Snapshotprojektion gemeinsam halten. Keine Datenformat-/Pfad-/IDmigration.

## P1 – R3 Storage-Grenzen abgeschlossen (19.10.2026)

- PixelLocalStore ersetzt die bisherige verschachtelte MainActivity.LocalStore-Klasse vollständig, keine Kopie/zweiter Store. Gleiche SQLite-Datei/Version4, Tabellen/DDL, IDs, Snapshots, Journale, Migrationen und Preferences. Zwei bisherige MainActivity-/Tunnel-Instanzprüfungen durch expliziten McpSyncHost-Callback genau dieser beiden Klassen ersetzt; bestehender Vordergrund-/500ms-Abgleichweg erhalten.
- LocalOwnerRepository besitzt native SQL-/Datei-/Original-/Backup-/Request-Belegarbeit. PixelOwnerAccess validiert/routet und plant erst nach Commit; keine SQL-Aufrufe, kein fachlicher Dispatch-Switch mehr. NativeOwnerQueries getrennte native Querygrenze ohne SQL. SnapshotProjection ist die bisherige unveränderte Projektion, jetzt transportunabhängig; kein neuer Datastore, keine automatische Migration.
- PASS27JVM-Suiten einschließlich79Servicetests und vollständigem Vorher-/Nachher-Vertrag `899906c2…aa813`; vier Architekturtests sichern Android-/SQL-freie Use Cases, SQL-freie MCP-Router, entkoppelten Einzelstore und reine Projektion. Release und AndroidTest gebaut, Source/Workspace/aktuelles APK-Artifact-Gate PASS,0Seeds. Beide nativen Quellsets gegen den neuen Store kompiliert; Ausführung auf refaktorierter APK erst R7.
- Messung: OwnerAccess3→0Switches,58→0Cases,61731→2183Bytes,38→11Projektabhängigkeiten,8→0SQL. MainActivity117635→51587Bytes,43→31Abhängigkeiten,30→3SQL; die verbleibenden lokalen Bridge-/Altmethoden werden in R4/R6 behandelt. UI-Aktionen156/57Bridge-Signaturen/5DDL-Anweisungen unverändert. UI-Quellreview ergänzt zwei tatsächliche Adapterdateien, nur Review-Fingerprint geändert.
- Nativer Vorher-Schlussreadback nach134Proben erneut exakt gleicher QA-Ledger `be1523c9…407af`,alleCounts/Pending/IDs/Relationen erhalten. Keine Rohdaten gespeichert. Nächste Phase R4: BusinessDataBridge getrennt von Plattform-/Lifecycleaktionen.

## P1 – R4 Android Bridge abgeschlossen (19.10.2026)

- 35 fachliche Methoden in öffentliche BusinessDataBridge ausgelagert. Bestehende DataBridge erbt diese annotierten Methoden; Kamera/Picker, Share, Google-/Android-Consent, Standort, Navigation und Activity-Lifecycle bleiben native Plattformadapter. Kein neuer Bridge-Endpunkt, gleiche57öffentliche Signaturen/156Aktionen. Persönliche Preference-Migration mit unveränderter Transaktion im vorhandenen Storageadapter; gemeinsame kompatible McpErrors-Bridgeabbildung verwendet.
- PASS27JVM-/29Browser-/5Architektursuiten bzw. Checks, eingefrorener Gesamtvertrag exakt unverändert. Release/AndroidTest mit Source/Workspace/Artifact-Gates PASS,0Seeds. Neue zusätzliche native Probe für öffentliche Vererbung (52DataBridge-/35Businessmethoden), Query-/Fehlerpfad, geschützte b.b.-Migration und isoliertes UI-Speichern vorbereitet/kompiliert; endgültige Ausführung als135.Probe in R7, noch nicht als bestanden zählen.
- Zusätzlicher echter Host-Kameralauf29Fälle PASS, einschließlich CameraChooser/Provider/Dateicleanup. Der bislang separat nicht gestartete Runner hatte zwei P0-Fixtureverdrahtungsfehler: Rootimportpfad fehlte und zentrale SyntheticBusinessData wurde nicht kompiliert. Suchpfad sowie vorhandene gepinnte JSON-Abhängigkeit/echte zentrale Factory eingebunden; keine Prüfung entfernt. Erfolgreich aus fremdem cwd ausgeführt. Dies ist weiterhin keine physische Kameraabnahme.
- MainActivity jetzt40192Bytes/23Projektabhängigkeiten gegenüber117635/43vorher. Die Zwischenmetrik direct_sql enthielt auch zwei Kamera-File.delete-Aufrufe; im Abschluss getrennt von tatsächlichem SQL ausweisen. Der verbleibende echte Main-SQL-Lesepfad ist ein unbenutzter privater Altbeleghelper, Prüfung/Entfernung in R6. Nächster Schritt R5: bestehende UI-Owner-Adapter zuerst charakterisieren, dann stateless Request-/Error-/Clone-Helfer vereinheitlichen.

## P1 – R5 JavaScript/UI abgeschlossen (19.10.2026)

- Vor Änderung30Beobachtungen der fünf bestehenden Owner-Call-Adapter eingefroren; danach130Äquivalenzprüfungen für dieselben Requests/Hashes/Antworten/Fehler und unabhängige Entwurfskopien PASS. owner-client.js ist ein zustandsloser nativer Request-/Error-/Clone-Adapter, keine Datenquelle, kein neuer Request-ID-Generator. Fünf duplizierte native Calladapter konsolidiert, Offer-/Planning-Kopierhilfe gemeinsam. Bestehende Workflow-Entwürfe, Retry-IDs und LocalStorage-Schlüssel unverändert; abweichende Spezialadapter nicht blind umgebaut.
- PASS27JVM-Suiten,30Browser-/JavaScript-Suiten einschließlich neuer130Vergleiche,5Architekturchecks; eingefrorener Hauptvertrag `899906c2…aa813` identisch,156Aktionen/57Bridge-Signaturen erhalten. Keine Design-/Navigation-/Frameworkänderung. Source/Workspace und vorhandene letzte R4-Zwischen-APK Artifact PASS,0Seeds. Aktuelle UI kommt erst mit dem R7-Neubuild in eine APK; alter Zwischenbuild wird nicht als neuer UI-Nachweis ausgegeben.
- Nächster Schritt R6: nachgewiesene unbenutzte private Methoden samt ausschließlich internem Helferpfad entfernen; notwendige historische Regression/Migration unverändert erhalten.

## P1 – R6 nachgewiesene Altpfade abgeschlossen (19.10.2026)

- Vier private, nicht annotierte Methoden entfernt: assertEditableInvoice ohne Aufrufer, dessen ausschließlich dort verwendete readAsset→readStream-Kette und unbenutztes isoNow. Referenzen vor Entfernung exakt1/2/2/1 (einschließlich Definition), keine Reflexions-/JS-/Runtimepfade. Fachlicher Rechnungs-/CAS-/Nummernschutz bleibt in unveränderten zentralen Regeln. No-op-Schemabuilder durch identischen vorhandenen leeren Default ersetzt; ausschließlich nachgewiesen unbenutzte Imports entfernt. Foto-Use-Case nutzt vorhandenen reinen Transfer-SHA statt HTTP-Providerklasse.
- Historische db.py/server.py/mcp_integration und Migrationstests bleiben notwendig für Python-, Cross-Language- und Formatregressionen; nicht als tot gelöscht. Keine Regressionstests/operativen Funktionen entfernt und keine zweite Runtime aufgebaut.
- PASS27JVM-Suiten,6Architekturchecks und vollständiger Vertrag `899906c2…aa813`. Release/AndroidTest/Lint mit Source/Workspace/Artifact-Gates PASS,0Seeds,0Lintfehler/33bestehendeHinweise. Finale native Ausführung folgt in R7.
- Reproduzierbare Abschlussmetrik korrigiert frühere manuelle Zwischenzählungen: MainActivity117635→38136Bytes,43→23Projekt-/23→3fachliche Abhängigkeiten,54→13direkteStoreaufrufe (verbleibend native Share/Medien),28→0SQL-Query/Insert/Delete-Aufrufe. Zwei Kamera-Dateilöschungen werden nicht mehr fälschlich als SQL gezählt. OwnerAccess61731→2183Bytes,39→11Projektabhängigkeiten,42→0Storeaufrufe,8→0SQL,3/58→0/0Switches/Cases. Reader31279→16768Bytes und2/32→1/14; OwnerTools25283→14781Bytes und2/66→1/28 (übriger Switch beschreibt nur Tools). Zusätzliche kleine Helperabhängigkeiten sind bewusst sichtbar, kein pauschaler Rückgang aller Einzelmetriken behauptet.
- MCP117 (52Read/65Write), Protokoll9, Toolhash `235f683d99d5170801d1e34c19ad386d9c4acf68b60a6992f72a3e5d2ea14314` unverändert. UI156/145fachlich/11Plattformgruppen; jetziger quellengebundener UI-Kataloghash `028c2151903c9cc07d7ed3761102002b79fd5d8bd900810ed3bf5094a7a6c11a` ändert ausschließlich Reviewprovenienz, Aktionsvertragsvergleich bleibt identisch. Nächster Schritt R7: abschließende Gesamtregression und neuer81erOriginalkandidat mit synthetischem Update.

## P1 – R7 Gesamtabnahme gestartet (19.10.2026)

- Remote-main erneut unverändert1abf049, alle bisherigen Phasen als normale Commits auf main. Neuer1.51.2/81-Quellstand für nachvollziehbaren sauberen Buildcommit, kein Protokoll-/Datenformatwechsel. Abschließende Python-/JVM-/Browser-/MCP-/Release-/Lint-/Privacyläufe und Original-/Emulatorupdate folgen. Noch keine Abschlussbehauptung.


## P1 – R7 Abschluss und konsolidierte Cloud-Abnahme (19.10.2026)

**PASS: alle 12 Kriterien aus P1.13 im definierten Cloud-Abnahmeumfang.** Ausgang `main` mit Commit `1abf049c1e27167bc16259d8717513aeb5ffe670`, Version 1.51.1/80. Operativer Abschluss-/APK-Quellcommit `e8a1d09ba7bb93438fb5aaff1e6008a670339764`, Version **1.51.2/81**, `source_dirty=false`, Quellhash `1a4d752fce32b09b08972fdabb4349917d8123f2c770986db78b894a327dcc54`. Der anschließende normale Abnahmecommit ändert ausschließlich zentrale Dokumentation; dessen SHA ist über `git log -1` verfügbar und nicht der eingebettete APK-Commit.

### Tatsächlich ausgeführte Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| Python Quality | 306 Tests PASS, 0 Skips; 37 historische Tests mit QA-Schlüsselerzeugung ausdrücklich ausgeschlossen, Originalsignier- und Privacy-Tests enthalten |
| JVM | 27 Suiten PASS, einschließlich 23 eingefrorener Antworten/Fehler, 14 Fehleradapter- und 79 direkter Application-Service-Prüfungen |
| Android-unabhängige Services | Die 79 direkten Serviceprüfungen zusätzlich ohne Android-SDK im Runtime-Classpath PASS |
| Browser / JavaScript | 30 Suiten PASS; gemeinsamer Ownerclient mit 130 Äquivalenzvergleichen gegen 30 Vorher-Beobachtungen |
| MCP stdio E2E | 55 synthetische Szenarien PASS |
| Kamera-/Provider-Hosttests | 29 Fälle PASS; vorhandenen Runner an Projektimport und zentrale synthetische Factory angeschlossen, keine Tests entfernt oder abgeschwächt |
| Native Android / SQLite / MCP / Provider | 135 Proben, 0 Fehler auf der endgültigen Original-81-APK; öffentliche Bridgevererbung und isolierter UI-Schreibpfad enthalten |
| WebView | Native Navigation, Arbeitsauswahl/Abbruch, Kunden-Gartenansicht und Papierkorb PASS |
| Isolierter Erststart | `fresh_isolated` PASS: neuer privater Testkontext ohne Geschäftsdaten/Zugangsdaten/automatische Providerverbindung; kein Löschen bestehender Profile |
| Release / AndroidTest / AAB / Lint | PASS; 0 Lintfehler, 33 bestehende Warnungen |
| Datenschutz | Source-, Workspace- und Artifact-Gates PASS; Workspace einschließlich ignorierter/untracked Dateien, tatsächliche APK und AAB geprüft, 0 operative Seed-Datensätze |

Keine prozentuale Zeilenabdeckung oder physische Kameraprüfung behauptet. Frühere Boot-/Timeoutversuche waren keine erfolgreichen Abnahmen; die endgültigen vollständigen Durchläufe sind oben ausgewiesen.

### Unveränderte Verträge und synthetisches Update

Der vor R1 eingefrorene Gesamtvertrag bleibt exakt **`899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`**: alle 117 Toolnamen/Schemas/Annotationen und ihre Reihenfolge, 156 semantische UI-Aktionen, 57 Bridge-Signaturen, fünf DDL-Anweisungen, zwölf Domain-/Formatquellhashes, Offer-/Bootstrap-Vertrag sowie 23 synthetische Antwort-/Fehlerhashes. Die Baseline wurde nach Refactoringbeginn nicht neu aufgenommen.

MCP bleibt **Protokoll 9, 117 Tools (52 Read / 65 Write, 17 destruktiv annotiert)**, Toolhash `235f683d99d5170801d1e34c19ad386d9c4acf68b60a6992f72a3e5d2ea14314`. Der vollständig paginierte UI-Katalog behält 145 fachliche MCP-Pfade und elf Plattformgruppen. Nur die aktualisierte Reviewprovenienz der jetzt 51 geprüften Quelldateien ändert seinen Kataloghash auf `028c2151903c9cc07d7ed3761102002b79fd5d8bd900810ed3bf5094a7a6c11a`; Aktionen und Bedienverhalten bleiben gleich. DB 4, Snapshot 2 und Drive-/Journalformate unverändert.

Tatsächliches normales Emulatorupdate **80 → 81 PASS**, ohne Deinstallation oder Datenlöschung. Vor Update, danach und nach allen nativen Tests identischer Hash des Geschäftsmasters ohne technische `capabilities` einschließlich vollständiger Pending-Queue: `be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af`. Unverändert: drei QA-Kunden, ein Auftrag, zwei Arbeitsaufzeichnungen, eine ausstehende Aktion, 32 öffentliche Katalogeinträge; keine Fotos/Rechnungen/Angebote/Termine. Laufende Zeiterfassung bleibt `RUNNING`, stabiler Sitzungshash `78070c6fcd158b6e8e24d651716c5277905bf57bed3fa8386628e5a3514925b1`; nur berechnete `worked_ms`, `hours`, `break_minutes` aus diesem Vergleich ausgeschlossen. Rohzustände ausschließlich im RAM verarbeitet, Berichte enthalten nur Counts/Hashes. Kein produktiver Hostroot eingerichtet oder geöffnet, keine Betriebsdaten importiert, kopiert oder verändert.

### Architektur und messbare Entkopplung

MCP-Verträge, Schemafelder und Fehlerabbildung führen zu reinen Queries und domänenspezifischen Application Services. Diese verwenden `OwnerRepository`/`OwnerCommand` als Ports zum bestehenden nativen `LocalOwnerRepository`, `PixelLocalStore` und Drive-/Dateispeicher. `MainActivity` hält Lifecycle, Navigation und Android-Integration; `BusinessDataBridge` übersetzt bestehende UI-Aufrufe. Der gemeinsame JavaScript-Ownerclient ist zustandslos; Entwürfe, Requests und ihre Speicherschlüssel bleiben in den vorhandenen Workflowmodulen. Keine zweite Runtime, DB oder Dokumentlogik.

| Hotspot | Vorher → nachher | Aussage |
| --- | --- | --- |
| PixelOwnerAccess | 61.731 → 2.183 Bytes; 39 → 11 Projektabhängigkeiten; 3 Switches / 58 Cases → 0; 42 → 0 Storeaufrufe; 8 → 0 SQL-Aufrufe | Native Grenze enthält keine Fach- oder Persistenzimplementierung mehr |
| MainActivity | 117.635 → 38.136 Bytes; 23 → 3 fachliche Abhängigkeiten; 54 → 13 Storeaufrufe; 28 → 0 SQL-Aufrufe | Verbleibende Storeaufrufe betreffen native Share-/Medienadapter; SQL-Metrik zählt kein `File.delete` |
| PixelMcpReadTools | 31.279 → 16.768 Bytes; 2 Switches / 32 Cases → 1 / 14; 13 → 15 Projektabhängigkeiten | Abfragen und Projektion getrennt; zusätzliche kleine Helperabhängigkeiten ausdrücklich sichtbar |
| PixelMcpOwnerTools | 25.283 → 14.781 Bytes; 2 Switches / 66 Cases → 1 / 28; 7 → 8 Projektabhängigkeiten | Übriger Switch beschreibt Tools; Felddefinitionen in 37 registrierten Schemabuildern |
| Größte Klassen | Vorher MainActivity 117.635 / PixelOwnerAccess 61.731 Bytes; nachher PixelLocalStore 64.506 / DriveSyncCoordinator 46.596 Bytes | Keine pauschale Gesamtschrumpfung oder willkürliche Zeilengrenze behauptet |
| UI-Helfer | Fünf gleiche Native-Calladapter → einer; zwei gleiche Clonehilfen → eine | Gleiche Rückgaben/Fehler, keine zusätzlichen globalen Geschäftsdaten |

Direkte Referenzen auf `DriveSyncCoordinator`/`DriveDirectFiles`/`DriveRestClient` sinken im Reader von sechs auf null, in `PixelOwnerAccess` von sechs auf zwei und in `MainActivity` von zwölf auf zehn. Die verbleibenden Aufrufe betreffen native Sync-Steuerung und Dokumentbereitstellung; dies sind statische Referenzzahlen, keine gemessenen Netzaufrufe. Die ursprüngliche konkrete Rückkopplung über `MainActivity.LocalStore` entfällt; der Store kennt Activity und Tunnelservice nicht mehr und verwendet für die bisherige Sync-Benachrichtigung allein `McpSyncHost`.

Sieben Schema-Primitiven bleiben einmal definiert, jetzt unabhängig von der Toolregistrierung in `McpFields`. `EntityContract` bündelt drei Entity-Feldverträge, Record-Hashregeln und Positionsprüfung. `OfferContract` bleibt unveränderte zentrale Definition. Sechs Architekturtests sichern die beschriebenen Grenzen, darunter keine Android-Imports, direkten SQL-Aufrufe oder direkten `DriveRestClient`-Zugriffe in Services und Ports; native Tests prüfen die tatsächliche Persistenz zusätzlich zu Porttests.

Bewusst verbleibende Schulden: `PixelLocalStore` orchestriert weiterhin bestehende atomare Saver-, Timer- und Receipt-Abläufe. Ältere Domainhilfen verwenden kompatible MCP-Hash-/Fehlerfassaden; die reine Adressprüfung verwendet weiterhin den statischen Helfer `OfferDeliveryClient.oneAddress`, tatsächlicher Mailzugriff erfolgt über den Repository-Port. Bestehende Render-Hooks, Workflow-Spezialadapter und unterschiedliche historische Validator-/Fehlerstring-Verträge bleiben zur Wahrung des Außenverhaltens erhalten. Historische Python-/Web-Komponenten werden für Regression und Migration benötigt. Weitere Entkopplung nur inkrementell unter denselben Gates.

Geändert wurden MCP-/Query-/Schema-/Fehleradapter, Application Services/Ports/EntityContract, nativer Store/Repository/Bridge und typgleiche Aufrufer, fünf UI-Module mit gemeinsamem Ownerclient und Quellreview, Charakterisierungs-/Service-/Architektur-/UI-/Native-Tests und Runner sowie die App-Version. Vollständige Liste der 75 Dateien bis zum APK-Quellcommit: `git diff --name-only 1abf049..e8a1d09`; abschließende zentrale Dokumentationsänderungen kommen hinzu. Keine bestehenden Regressionstests entfernt oder abgeschwächt.

### Originalartefakt und Grenzen

- APK: `outputs/Haus-Garten-1.51.2-81-original-p1-kandidat.apk`, 34.513.073 Bytes, SHA-256 `7e569519f8f3328335499a84181f6eb6746c860e4325e80d67f0f44ba59e865e`.
- Paket `at.hausgarten.service`, Originalzertifikat `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8` identisch zu Original 80. Ein Signer, v2/v3 gültig, 16-KiB-Ausrichtung PASS. Alle 412 APK-Assets stimmen bytegenau mit dem aktuellen Quellstand überein; neue operative Klassen vorhanden, die alte verschachtelte `MainActivity$LocalStore`-Klasse nicht mehr im DEX.
- Ausschließlich `tools/sign_original_apk.py`; vorher nur das Vorhandensein der fünf dokumentierten Variablen geprüft. Instrumentierung ebenfalls mit bestehender Originalsignierung, kein neuer Schlüssel. Cleanup bestätigt: keine privaten Signiertempordner oder Schlüsseldateien im Repository. Vorhandene Downloadartefakte bleiben unverändert; keine neue APK in Drive/GitHub-Downloads veröffentlicht.

**Extern NOT TESTABLE / offen:** physischer Pixel nicht aktualisiert; realer ChatGPT-Connector und echte Google-Drive-/Kalender-/Gmail-/Kamera-/Hintergrund-Endtests nicht ausgeführt. Produktive Legacyaktionen und Warteschlangen nicht angefasst oder als geschlossen ausgegeben. Vor einer ausdrücklich freigegebenen Pixelinstallation bleiben Sicherung und Geräte-/Kontofreigaben erforderlich. Originalsignierung und synthetisches Emulatorupdate sind keine physische Update- oder Datenerhaltfreigabe. Diese externen Grenzen ändern nicht den bestandenen P1-Cloud-Vertrags- und Refactoringumfang.


## Original-APK 81 über Drive bereitgestellt (20.10.2026)

- Auf ausdrücklichen Nutzerauftrag die bereits geprüfte Original-APK 1.51.2 / Build 81 unverändert im bestehenden privaten Ordner `APK-Backups / Codex-Cloud-Builds` ergänzt. Kein neuer Build und keine erneute Signierung.
- [Privater Drive-Download](https://drive.google.com/file/d/1eVAH39SCVFCNCHJHh-ey5C5AR3s6W70K/view?usp=drivesdk). Name, MIME, Zielordner, 34.513.073 Bytes sowie ausschließlich Owner-Freigabe / shared=false per Metadaten rückgelesen. Vollständiger Rohdatei-Rückabruf liefert eine Dateireferenz mit derselben Größe. Kein unabhängiger Remote-Bytehash behauptet.
- Lokale APK vor Upload erneut SHA-256-geprüft: `7e569519f8f3328335499a84181f6eb6746c860e4325e80d67f0f44ba59e865e`. Keine ältere APK, Betriebsdatei, Sicherung oder Freigabe verändert. Die frühere Aussage „nicht in Drive veröffentlicht“ beschreibt den Zeitpunkt der P1-Abnahme und ist durch diese spätere Bereitstellung überholt.
- Physischer Pixel und realer Connector weiterhin nicht aktualisiert oder nach einem Update getestet; keine Installation durchgeführt oder freigegeben.


## P2 – sichere Konsolidierung gestartet (20.10.2026)

- P1 auf dem realen Pixel gelesen: 1.51.2 / Build 81, MCP9/117, write_enabled=true. UI-Katalog 156/145 fachlich/11 Plattformgruppen; Queue0, Review0, Journalfehler0. Drive aktuell provider_readback/idle ohne Transferfehler; cloud_confirmed=false bleibt externe Grenze.
- Startinventar Geschäftsdaten:58 Kunden,3 Aufträge,46 Arbeitsaufzeichnungen,95 Rechnungen,31 Fotos,33 Leistungskatalogeinträge,0 aktive Angebote,1 Termin. Keine Schreib-/Löschaktion durch P2-Start.
- Archiv read-only:2 Angebote,3 Kunden,129 Worklogs,3 Fotos,2 Rechnungen,0 Aufträge. Sechs vorhandene Backups gelistet. Noch keine Archiv-/Backup-/Dateilöschung freigegeben.
- GitHub read-only: main plus drei alte codex/*-Branches. Alle drei waren beim letzten Vergleich vollständig in main enthalten; Löschung erst nach frischem Vergleich im P2-Schritt. downloads/ enthält zwölf alte APK-Kandidaten Build66–79 plus README/SHA256SUMS; P1/81 liegt privat in Drive und nicht im GitHub-downloads-Ordner.
- Neuer verbindlicher P2-Block in CODEX-AUFTRAG-GARTENPIRAT.md: GitHub-/Codex-Konsolidierung, sichere Drive-/Archiv-/Backupklassifikation, read-only Orphan-Audit vor jeder Dateilöschung, kein History-Rewrite und keine pauschale Löschung geschäfts-/steuerrelevanter Unterlagen.
- Noch keine destruktive P2-Aktion ausgeführt. Nächster Schritt: frischer Mergevergleich der drei alten Branches und Klassifikation der APK-/Dokumentationskandidaten.


## P2-1 – frisches read-only Gesamtinventar (20.10.2026)

- Ausgang `348b40abcb7be78e6444407de58d96c6f07dd080`, sauber auf aktuellem `main`; nur zentrale P2-Anweisungen per Fast-forward übernommen. Keine Geschäftsdaten geschrieben, keine Datei/Branch gelöscht. P0 Source-/Workspace-/Original-81-Artifact-Gates PASS; P1-Gesamtvertrag unverändert `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`, 117 Tools / 156 UI-Aktionen.
- Branches erneut direkt vom Remote gelesen: `codex/original-signing-cloud-setup-20261018` bei `5fbe0a3a8c1f9073dc83f83b37b741a59e935ee4`, `codex/preserve-cloud-28504c9-20261018` bei `28504c97278a7deebdfdbf0d9d5eeb57c5c319e4`, `codex/v1.46-functional-optimization-20261016` bei `49d8cb965844a32c708dde5b52867d7c0ff85769`. Alle echte Vorfahren von main, jeweils 0 eigene / 32, 79, 82 zusätzliche Main-Commits. Veralteten lokalen Trackingref nicht als Nachweis verwendet.
- Zwölf getrackte Original-APKs 66–79 inventarisiert und gegen SHA256SUMS verglichen. Zielklassifikation: 81 privat in Drive als aktueller freigegebener Referenzbuild, 79 als letzter GitHub-Referenzbuild erhalten; ältere elf Binaries sind überholte Entwicklungsartefakte, keine Appdaten. Keine Behauptung einer Android-Downgradefreigabe. 30 Markdown-Dateien erfasst, Referenzen geprüft; technische Legacy-Komponenten weiterhin tatsächlich von Tests/Migration benötigt.
- Pixel frisch gelesen: Originalpaket, 1.51.2/81, MCP9/117, Write aktiv, eingebetteter Quellcommit e8a1d09. Aktive Queue/Upload/Review 0/0/0, keine Sync-/Journalfehler; Provider-Readback, cloud_confirmed=false. Rohzähler `pending_actions=1` ist vom gefilterten Queue-Status zu unterscheiden; kein Eintrag entfernt oder quittiert. Geschäftsgruppenhash `2b8310a538134a49966b736ae660758e2863a1d9fe62e973c11cc1f4ddb07932`, Counts 58/3/46/95/31/33/0/1. Live-Toolhash `8780ff5fa46d421c4a1ea858db30b232e0272303065d80f28f5f43e242a139aa` ausdrücklich vom JVM-Quellhash unterscheiden; kein Vertragsumbau in P2.
- Archiv vollständig 100+39 paginiert: 139 Einträge, davon 129 historische Arbeiten, zwei geschützte Rechnungen und acht unklare Einträge (zwei Angebote, drei Kunden, drei Fotos); keine sicher ausdrücklich markierten Testdatensätze. **Alle KEEP**, keine Purge-Freigabe daraus ableiten.
- Sechs Backups vollständig gelistet, alle sechs inspect_backup-Aufrufe READ_ERROR. Vier umfangreichere/ältere Referenzen und zwei bytegleiche kleine Altbackups bleiben geschützt; Metadaten allein sind kein aktueller vollständiger Restore-Nachweis. Kein unterstützter Backup-Löschweg gefunden. Aktuelle prüfbare Sicherung vor Löschgruppen noch offen.
- Drive ausschließlich Metadaten: Hauptordner 116 Einträge, Fotoordner 53, APK-Backups 34, Codex-Cloud-Builds 9, geschützter Sicherungsordner 6; zusammen 218 Einträge einschließlich vier Ordnern. 99 State-/Commit-/Mobile-/Legacy-Dateien geschützt, 47 Releaseartefakte, 53 Fotos, 15 sonstige/unklare Dateien. 44 Foto-/Dokumentdateien lassen sich schon aktiven/archivierten Storage-Referenzen zuordnen. Unvollständige Backup-/Historienreferenzen und nicht zugesicherte globale Connector-Vollständigkeit verhindern jeden sicheren Orphan-Löschnachweis. Keine JSON-/Foto-/PDF-/Backup-Rohdatei in den Workspace kopiert.
- Status: Inventar PASS soweit durch vorhandene Lesewege erreichbar, vollständige Sicherungs-/Orphan-Abnahme OFFEN. Nächster Schritt: bestehende lokale Backupfunktion einmal mit stabiler Request-ID verwenden und prüfen; dann ausschließlich nachgewiesen freigegebene technische Bereinigungen.


## P2-2 – Sicherung vor Bereinigungen bestätigt (20.10.2026)

- Bestehenden autorisierten App-Weg einmal mit stabiler Request-ID `p2_20261020_pre_cleanup_current_backup_v1` verwendet. Neue private Geschäftssicherung `backup_240b8a9bedea701bd45a7a92536916328f4cb6319e5dfaee8eff900e1ea0b0be`, 33.524.508 Bytes, SHA-256 `d965237fc10717e58e0897ebf14b13bddfcb18ff298a5139ca955750f252d780`. inspect_backup PASS; Umfang Snapshot/Pending/lokale Originale und Dokumente, keine Zugangsdaten und kein vollständiges Gerätebackup. Counts 58/3/46/95/31/33/0/1. Keine Rohbytes heruntergeladen oder im Repository gespeichert.
- Geschäftsdatenhash vor/nach Sicherung identisch `2b8310a538134a49966b736ae660758e2863a1d9fe62e973c11cc1f4ddb07932`; aktive Queue/Review/Journalfehler weiterhin 0. Alle sechs früheren Backups unverändert erhalten. Deren READ_ERROR-Ursache nicht ungeprüft als beschädigte Datei ausgegeben; Quellcode setzt bei Inspektion alle heutigen Collections voraus, ältere Sicherungen können darunter scheitern.
- PASS: Voraussetzung einer aktuellen prüfbaren Geschäftssicherung für die nachfolgenden rein technischen Löschgruppen erfüllt. Kein Restore, kein Purge, kein Drive-Datei-Cleanup und keine Installation.


## P2-3 – integrierte Branches und Releaseartefakte konsolidiert (20.10.2026)

- Frischer Remote-/Ancestryvergleich unmittelbar vor normalem atomarem Branch-Delete; alle drei dokumentierten SHA-Spitzen unverändert und vollständig in main enthalten. Drei alte codex/*-Branches entfernt, Remote-Readback bestätigt nur main. Main dabei unverändert 348b40a, keine Commit-/Historylöschung, kein Force-Push.
- Zwölf APK-Prüfsummen automatisiert gegen vorhandenes Manifest verifiziert. 81 bleibt bewusst privat in Drive; letzter GitHub-Originalbuild 79 bleibt als Referenz mit unverändertem Hash erhalten. Elf ältere APKs 66–77 aus dem aktuellen downloads-Baum entfernt; Prüfsummen/Historienzuordnung in downloads/README.md, Wiederherstellung aus Commit 348b40a möglich. Dies verkleinert den aktuellen Baum, ausdrücklich nicht die Git-Historie. Keine Android-Downgradefreigabe. Original-80-/81-Cloud-Prüfkandidaten bleiben erhalten.
- .gitignore-Ausnahmen auf den tatsächlich aufbewahrten Originalbuild79 reduziert; keine erneute Signierung und keine operative Quelländerung. Neue Geschäftssicherung aus P2-2 ist geprüft, aktive Queue0; keine App-/Drive-Datei gelöscht.


## P2-4 – Dokumentation und Legacy-Nutzung (20.10.2026)

- Alle 30 Markdown-Dateien mit Referenzsuche klassifiziert. KEEP: AGENTS, Auftrag, Status, Originalsignierung, README, downloads/README, Integrationsnachweis, Reparaturnachweis, MCP-Eigentümernachweis, beide Sync-/Abnahmedokumente, Android-Build-/Emulatoranleitung, QA-Signierung (weiter von qa_signing.py referenziert), österreichische Rechnungsquellen und übrige historische Architektur-/Pflanzen-/Kamera-/Foto-/MCP-/Timer-/Pixel-first-Berichte. Deren exklusive historische Nachweise oder Test-/Migrationsbezüge bleiben erhalten; alte Runtime-Anweisungen gelten nicht für den aktuellen Betrieb.
- MERGE → REMOVE: RECHNUNGS-WORKFLOW-v1.24.md, RECHNUNGS-OBERFLAECHE-v1.25.md, DESIGN-v1.35.md. Vorher keine eingehenden Referenzen im aktuellen Baum, vollständig gelesen. Dauerhafte UI-/Dokumentschutzregeln in README und bestehendem MCP-Eigentümernachweis konsolidiert, heutige Regeln aus AGENTS/Auftrag bleiben vorrangig. Historische Details/Prüfungen unverändert aus Commit348b40a lesbar. Keine aus alten Dokumenten kopierten Kunden-/Bankdaten. 30 → 27 Markdown-Dateien, downloads/README und zentrale README verkürzt und auf den tatsächlichen Stand aktualisiert.
- Legacy KEEP bestätigt: db.py/server.py/mcp_integration sowie Rechnungs-/Angebots-/Drive-Migrationsmodule werden tatsächlich von Python-/MCP-/HTTP-/Relations-/Sync-/Backup-Regressionen importiert bzw. aufgerufen. Keine nachweislich vollständig unbenutzte zusätzliche Legacy-Komponente gefunden; keine Tests, Migrationen oder Produktivfunktionen entfernt. Keine operative Java-/JS-/APK-Änderung.


## P2-5 – Cloud-Artefakte, Archiv/Backups und read-only Medienaudit (20.10.2026)

- Nur regenerierbare Gradle-APK/AAB-Ausgaben und den anhand seines P1-SHA belegten eigenen Instrumentierungsrunner aus dem Workspace entfernt; Original-80/81, getrackter Referenzbuild79 und technische Abnahmeberichte bleiben erhalten. Keine Emulatorprofile, fremden Tasks oder veröffentlichte private Cloud-Umgebung entfernt. Keine administrierbare Codex-Task-Archivierungsschnittstelle vorhanden; keine Aufgaben als gelöscht behauptet.
- Archiv139 bleibt vollständig unverändert. Sechs alte Backups bleiben geschützt; zwei gleiche SHA-Werte allein sind keine Löschfreigabe. Neue siebte Sicherung zusätzlich über get_file_metadata vollständig auf Bytezahl/SHA geprüft, unverändert d965237f…2d780. Kein unterstützter Backup-Purge exponiert, kein manueller Drive-Ersatz. Steuer-/Geschäftsunterlagen und Journale unverändert erhalten.
- Ergänzt: tools/audit_media_inventory.py, ausschließlich normalisierte Metadaten im RAM oder ausdrücklich extern unter HG_PRIVATE_DATA_ROOT; kein produktiver Root-Fallback, kein Download-/Löschmodus. Bericht enthält nur Typ-/Klassenanzahlen, Bytes, Referenz-/Inventarhash und Coverage. Bei fehlender Inventar-/Referenz-/Backup-/Historienvollständigkeit, offener Queue, Versionsrennen, Metadatenkollision oder fehlendem referenziertem Objekt kein Orphan-Löschnachweis. Auch vollständiger Audit autorisiert niemals eine Löschung.
- Zehn synthetische Audit-Tests PASS: alle Klassen, aktive/archivierte/finale/pending/Backup-/Historienreferenzen, deterministisches Replay, fehlende Coverage, Rennen, fehlende Objekte, widersprüchliche Metadaten, unbekannte Größen, ungültige Schemawerte, keine Rohdaten in Ausgabe, kein Schreiben/Repository-Fallback. Keine App-/MCP-/UI-/DB-/Snapshotänderung.
- Live-Metadaten-Audit auf allen 214 vom Connector beobachteten Dateien: 44 referenced / 24.605.212 Bytes, 151 protected / 529.373.512 Bytes, 19 unknown / 5.761.826 Bytes, 0 nachgewiesene orphaned/temporary. Zwei vollständige Ordner-Metadatenreads identisch. Inventarhash 55569b9974b53d57194fb3bfc744528b628f9b140e5c5c344dd2cb11d2caa78d; Referenzmengensha 18224433603a496c77c6f19b24696726dd11fd9b84b83d58c9ba6fde97a4f6aa. Status ausdrücklich INCOMPLETE: globale Connector-Abdeckung und vollständige Backup-/Historien-/Dokumentreferenzen nicht belegt. Zero Orphans bedeutet hier keinen Nachweis, dass keine existieren. **Keine Drive-/Medien-/Dokumentlöschung.**

- Ergänzende externe Grenze: kein Remote-Git-Tag vorhanden; gesonderte GitHub-Release-/Asset-Liste über vorhandenes gh-CLI tatsächlich versucht, direkte api.github.com-Anfrage mit `Forbidden` blockiert. Kein Umgehen der Cloud-Netzwerkpolitik und keine Aussage, dass es deshalb keine Releases/Drafts gebe. Keine Release-/Actions-Artefakte remote gelöscht.
- Dokumentdateien bleiben im neuen Audit auch ohne aktuelle Referenz geschützt; finanzielle Aufbewahrung wird nicht aus einem fehlenden Link abgeleitet.

### P2-5 Vorher-/Nachher-Nachweis temporärer Archive

Vier nachweislich regenerierbare Dateien entfernt, zusammen 69.151.796 Bytes; alle vier Pfade danach abwesend. Keine Profil-/Geschäftsdaten betroffen.

| Datei | SHA-256 vor Entfernung |
| --- | --- |
| `outputs/qa-p1-original-instrumentation.apk` | `a60d1250ea9c82eb494c6d848332a46c97c859118fecd1fb25257ec6df250da7` |
| `android/app/build/outputs/apk/release/app-release-unsigned.apk` | `09eaaabb295b07d9a891949ee10b35cf9b04aa5371d44dd4883f99f063320cd8` |
| `android/app/build/outputs/apk/androidTest/release/app-release-androidTest.apk` | `9e4b3dc647274594aefe002ac0c418ef110e87a5b5b22154cbbbc8b2620de700` |
| `android/app/build/outputs/bundle/release/app-release.aab` | `186d117bca2ac301dc9db2270063ebd990365e6ab85be3900e4073334ee86b8b` |

Zusammen mit elf alten APKs 447.526.641 Bytes Dateiinhalte aus dem aktuellen Workspace entfernt, kein behaupteter Größenabbau der Git-Historie. README 171→67 und downloads/README 321→56 Zeilen; drei historische Einzelberichte zusammengeführt, keine operative Funktion entfernt.


## P2-6 – Regression und finaler Live-Readback (20.10.2026)

- PASS **316 Python-Tests, 0 Skips**, einschließlich zehn neuer read-only Audit-Tests; 37 historische Tests mit QA-Schlüsselerzeugung unverändert ausdrücklich ausgeschlossen. **27 JVM-Suiten, 30 Browser-/JS-Suiten, 55 isolierte MCP-stdio-Szenarien PASS**. Keine Regressionstests entfernt/abgeschwächt, ausschließlich synthetische Stores. Source/Workspace vor und nach Quality sowie Original-81-Artifact PASS, ignoriert/untracked eingeschlossen; operative Seeds0.
- P1-Gesamtvertrag weiterhin exakt 899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813, 117 Tools / MCP9 / 156 UI-Aktionen. Zusätzlich echte get_capabilities-Tooldefinitionen des Pixels vollständig gegen Baseline verglichen: alle Namen, Schemas, Annotationen und Reihenfolge semantisch identisch; kanonischer Hash235f683d…14314 auf beiden Seiten. Der nativ gemeldete Hash8780ff5f…139aa wird nicht mit diesem kanonischen Vergleich gleichgesetzt; unterschiedliche Darstellung allein ist kein nachgewiesener Vertragsfehler. Keine Katalog-/Runtimeänderung in P2.
- Finaler echter MCP-Readback: Geschäftsdatenhash vor/nach allen Schritten identisch 2b8310a538134a49966b736ae660758e2863a1d9fe62e973c11cc1f4ddb07932, Counts58/3/46/95/31/33/0/1. Alle139 Archivschlüssel/Record-Hashes identisch. Sechs alte Backup-IDs/Metadaten identisch, neue geprüfte Sicherung zusätzlich:6→7. Aktive Queue/Review/Journalfehler0/0/0; provider_readback/cloud_confirmed=false. Keinen neuen Abgleich angefordert, keine Quittung erfunden.
- Neue native/Gradle-/Pixel-Installationsprüfung nicht ausgeführt: operative Java-/JS-/Assets/Gradle unverändert, kein neuer APK-Build erforderlich. Vorhandene P1-Nativabnahme ist ein historischer Nachweis, keine neue P2-Geräteausführung. Reale MCP-Leseprüfung tatsächlich durchgeführt; keine produktiven Schreibtests; einzige speichernde Betriebsaktion war die ausdrücklich notwendige neue private Geschäftssicherung.
- **Noch offen:** globale Vollständigkeit der Drive-/Dokument-/Backup-/Journalreferenzen; 19 beobachtete unbekannte Dateien bleiben KEEP. Alle sechs historischen inspect_backup-Aufrufe READ_ERROR trotz lesbarer Metadaten, Wiederherstellungswert daher nicht vollständig prüfbar. Direkte GitHub-Release-/Asset-Liste Forbidden; keine Aussage über abwesende Releases/Drafts. Fremde Codex-Tasks nicht administrierbar. Kein Bypass, keine manuelle Daten-/Backup-/Medienlöschung. P2 nicht pauschal als vollständig abgenommen markieren.

### Vollständige Dokumentklassifikation

| Datei | Entscheidung |
| --- | --- |
| `ABSCHLUSSBERICHT-v1.46.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `AGENTS.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `ARCHITEKTUR-PRUEFBERICHT-v1.34.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `CHATGPT-INTEGRATION.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `CODEX-AUFTRAG-GARTENPIRAT.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `CODEX-STATUS.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `DESIGN-v1.32.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `DESIGN-v1.35.md` | MERGE → REMOVE; gültige Regeln in zentralem MCP-Nachweis/README erhalten |
| `FOTODOKUMENTATION-v1.47-PLAN.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `GARTENPIRAT-PFLANZEN-v1.46.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `INTEGRATIONSNACHWEIS-2026-10-18.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `KAMERA-KORREKTUR-v1.46.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `MCP-ALLTAG-v1.36.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `ORIGINAL-SIGNIERUNG-CLOUD.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `PHASE-0-PIXEL-FIRST-BASELINE.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `PHASE-1-BACKUP-UND-ARBEITSSITZUNG.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `PLUGIN-SCHREIBRECHTE-v1.31.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `QA-SIGNIERUNG.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `README.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `RECHNUNGEN-OESTERREICH.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `RECHNUNGS-OBERFLAECHE-v1.25.md` | MERGE → REMOVE; gültige Regeln in zentralem MCP-Nachweis/README erhalten |
| `RECHNUNGS-WORKFLOW-v1.24.md` | MERGE → REMOVE; gültige Regeln in zentralem MCP-Nachweis/README erhalten |
| `REPAIR-v1.49.1-2026-10-18.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `TIMER-STANDORT-v1.33.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `android/ANDROID-17-BUILD.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `docs/MCP-EIGENTUEMERZUGRIFF-2026-10-18.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `docs/SYNC-DRIVE-KUNDENAKTE-1.48.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `docs/SYNC-KUNDENAKTE-ABNAHME.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `downloads/README.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `tests/Android-Emulator.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |

### Vollständiges Altbackup-Metadateninventar

Alle sechs bleiben KEEP; Scope jeweils Geschäftssnapshot/Pending/lokale Fotos und Dokumente, keine Zugangsdaten. Die native Inspektion scheitert jeweils mit READ_ERROR, daher keine vollständige Wiederherstellbarkeitsbehauptung. Kleine Dateigröße allein beweist weder Vollständigkeit noch Unbrauchbarkeit; gleiche SHA allein autorisiert keine Löschung.

| Technische Referenz (gekürzt) | Erstellt UTC | Bytes | Metadaten-SHA-256 |
| --- | --- | ---: | --- |
| `backup_275dabc158142698…` | 2026-10-19T04:32:46.944085Z | 22796693 | `3c6e59ef604c25919f0f10cfdf223eacb72c9555899c9b8c2e966e1d78ebfc01` |
| `backup_3fa4460d4db94ee5…` | 2026-10-19T04:28:58.537608Z | 22987905 | `58e99ac2e57d4215106391daea6d4ebe25a8c4a96998e73050b6f8aec15f6045` |
| `backup_7fd0a667d4708f06…` | 2026-10-18T20:23:14.763926Z | 22711249 | `776685e56ded05e41ae9363dfb9861dffbe83a34862845a57dbd62958c89e19e` |
| `backup_9778e41a06e30d40…` | 2026-10-18T18:45:27.892581Z | 372514 | `3a4d578b87ec40d3a4ce83e5e277664888aecbe75b4a3b6f92ff8925609eb56b` |
| `backup_4ef99a6925a1c66e…` | 2026-10-18T17:34:49.618231Z | 362292 | `4683945a1d959e34aeea818768eb8fc618ef2a9cc424e76054c05072e278cb6b` |
| `backup_ed1e21387e525d05…` | 2026-10-18T17:30:05.649173Z | 362292 | `4683945a1d959e34aeea818768eb8fc618ef2a9cc424e76054c05072e278cb6b` |


## P2-7 – offene Zugangs- und Backupfragen erneut geprüft (20.10.2026)

- Ausgang main `93ced845b7718b0a7d824220e5508057f1a97306`, sauber, Remote identisch. Frisches ls-remote bestätigt ausschließlich main. Den ebenfalls vollständig integrierten lokalen codex/original-signing-cloud-setup-20261018-Zeiger und drei nicht mehr existierende Remote-Trackingrefs entfernt; Commitobjekte unverändert erhalten. Lokaler work-Zeiger ist ein vollständig integrierter Umgebungscheckpoint, keine zweite operative Remote-Branch. Keine Historyänderung/Force-Push.
- Die bisherige direkte API-Sperre ist weiterhin gültig. Über den vorhandenen unterstützten GitHub-GET-Connector ist die Release-Liste aber erreichbar: 0 Releases. Alle 14 Actions-Läufe und jeweils deren vollständige Artefaktliste gelesen: sieben unexpired Artefakte, 240.642.864 Bytes. Vier UNSIGNED-NOT-RELEASED- und drei QA-Entwicklungsartefakte, sämtliche Quellcommits echte Vorfahren von main. Keine Behauptung einer APK-/Signatur-Inhaltsprüfung dieser Archive. Kein verfügbarer Actions-Artefakt-DELETE im Connector; kein Umgehen der Cloud-Netzwerkpolitik. KEEP bis automatischem Ablauf oder unterstützter Actions-Schreibfreigabe. Retention laut Provider: QA am 23.10.2026, unsigned am 25.10.2026; tatsächlich erfolgter Ablauf noch nicht geprüft.

| Actions-Run | Artefakt-ID | Bytes | Ablauf UTC |
| --- | --- | ---: | --- |
| 39150055162 | 12145390411 | 34298321 | 2026-10-25T17:35:48Z |
| 39149764763 | 12145100469 | 34298320 | 2026-10-25T17:31:38Z |
| 39149646442 | 12145310027 | 34298320 | 2026-10-25T17:30:18Z |
| 39149370858 | 12144637627 | 34298216 | 2026-10-25T17:26:43Z |
| 38958704554 | 12071757106 | 34484052 | 2026-10-23T19:59:37Z |
| 38956328082 | 12071085369 | 34483229 | 2026-10-23T19:39:44Z |
| 38953870720 | 12070645377 | 34482406 | 2026-10-23T19:19:53Z |

- Pixel zuerst lesend erreichbar: 1.51.2/81, Protokoll9/117, Counts58/3/46/95/31/33/0/1 und Geschäftshash `2b8310a538134a49966b736ae660758e2863a1d9fe62e973c11cc1f4ddb07932` identisch zur P2-Abnahme; aktive Queue/Review/Journalfehler0, provider_readback, cloud_confirmed=false. Keine speichernde Betriebsaktion in dieser Nachprüfung.
- Alle sechs alten Backups erneut über get_file_metadata vollständig nach Bytezahl/SHA geprüft: 6/6 stimmen mit dem bisherigen Inventar überein. Beim kleinsten Altbackup `backup_ed1e21387e525d058319b103fa645fd221f93cbed44b294117ba5b09c220a6ea` wurde der State ausschließlich temporär im Connector-Arbeitsspeicher untersucht, ohne Rohdaten in Workspace/Logs: alte Collections vorhanden, nur appointments fehlt. Counts51/1/173/95/12/32/0, Pending9. LocalOwnerRepository.backupCounts verlangt dagegen getJSONArray("appointments"); dieser tatsächliche Formatunterschied erklärt den READ_ERROR dieser Datei. Keine Beschädigung aus dem Fehler ableiten. Gleicher SHA beim zweiten kleinen Backup ist ein Bytegleichheitsnachweis, keine Löschfreigabe; keine separate Restaurationsprobe.
- Umfangreichere Inhaltsprüfung anderer Altbackups lieferte keine verwertbare Antwort und wurde abgebrochen; keine unbeschränkten Wiederholungen. Anschließendes get_sync_overview meldet McpServerError: Connection failed. Ursache des vorübergehenden Verbindungsabbruchs nicht bewiesen. Der spätere erneute Health-/Sync-/Archiv-/Backup-Readback in P2-9 war erfolgreich; kein echter Restoretest daraus ableiten.
- Funktionaler Altbackup-Inspektionsfix bleibt OFFEN: P2 erlaubt ausdrücklich nur Konsolidierung, keine operative Weiterentwicklung. Fachlich begrenzte spätere Korrektur: ein in alten Sicherungen fehlendes additives appointments-Feld lesend als leer behandeln; vorhandene falsche Typen weiterhin ablehnen, Backupbytes/Restore-/DB-Pfad unverändert lassen und synthetisch absichern. In dieser P2-Nachprüfung keine Android-/MCP-/Restoreänderung und keine neue APK.

## P2-8 – Foto-/Dokumentreferenzen präzisiert (20.10.2026)

- Bestehenden read-only Audit um resolve_photo_references erweitert: ausschließlich explizite canonical storage_name/thumbnail_storage_name oder sichere Legacy-filename-Referenz ohne storage_name. Entspricht der bestehenden PhotoAttachmentStore-Dateinamenregel; keine URL-, Basename- oder unscharfe Zuordnung. Ein neuer Storage-Verweis wird niemals durch einen alten Anzeigenamen ersetzt.
- Doppelte Namen schützen alle Treffer und verhindern Vollständigkeitsbehauptung; widersprüchliche Objektidentitäten, fehlende/ungültige Namen ebenso. Adapterdaten bleiben privat im RAM; öffentliche Ausgabe nur Counts/Hashes. Kein zusätzlicher produktiver Store und kein Löschmodus. Sechs neue synthetische Regressionen, insgesamt16 Audit-Tests PASS; bestehende zehn Tests unverändert erhalten.
- Tatsächliche Metadatenabgleichung:31 aktive Fotozeilen (19 modern,12 legacy) referenzieren47 Drive-Dateien, sechs weitere Dateien sind bereits über Archivfotos referenziert. Alle53 beobachteten Fotodateien damit referenziert, keine fehlende aktive Storage-Datei und keine Namensmehrdeutigkeit im beobachteten Fotoordner. Drei Legacy-Zeilen teilen bereits referenzierte Dateien; Zeilen- und Dateizahl nicht gleichsetzen. Keine Originalbilder heruntergeladen, verändert oder gelöscht.
- Elf native/Text-/Markdown-Dokumente gemäß vorhandener Schutzregel als document klassifiziert; die explizite vorhandene Journaldatei ebenfalls geschützt. Übrige drei JSON-Dateien ohne vollständigen Verwendungsnachweis bleiben unknown/KEEP, keine Klassifikation nach geratenen Inhalten.
- Erneuter Audit auf214 beobachteten Dateien:53 referenced /30.299.197 Bytes;158 protected /529.414.214 Bytes;3 unknown /27.139 Bytes. 0 nachgewiesene orphaned/temporary; Gesamtbytes559.740.550 unverändert. Referenzhash `0a735eb6a3f9477fec39fc8a9df23f4ec6616991785d82d6ea12bfac188e03ad`, Inventarhash `d46975dfd5542eecf194c27344ca6e0a3502756294772b9aeb99a0016afdfecb`. Lokale Auditausführung nur über eine technisch SHA-pseudonymisierte ID-/Dateinamenprojektion, keine Rohdatensätze im Workspace. Deren eigene Audit-Hashes unterscheiden sich bewusst von den hier ausgewiesenen ursprünglichen RAM-Referenzhashes.
- Status weiterhin INCOMPLETE: keine zugesicherte globale Connector-Inventarvollständigkeit, nicht sämtliche Backup-/Historien-/Dokumentreferenzen verifiziert. Nach vorübergehendem Verbindungsabbruch finaler Geschäftshash erneut identisch gelesen: before_revision/after_revision entsprechen diesem tatsächlichen Geschäftshash, stable_revision=true und queue_empty-Coverage=true. Alle fünf beobachteten Drive-Ordner zusätzlich unverändert zurückgelesen. Snapshot-Stabilität wird nicht aus Git-HEAD oder identischen Dateizahlen abgeleitet. Referenzsicherheit der beobachteten Fotos ist kein globaler Orphan-Nachweis. Keine Drive-/Archiv-/Backup-/Medienlöschung.


## P2-9 – erneute Regression und finaler tatsächlicher Readback (20.10.2026)

- PASS322 Python-Tests/0 Skips,27 JVM-Suiten,30 Browser-/JS-Suiten und55isolierte MCP-stdio-Szenarien. Die37 historischen Tests mit QA-Schlüsselerzeugung unverändert ausdrücklich ausgeschlossen; kein neuer Signaturschlüssel erzeugt. Sechs neue Auditregressionen, insgesamt16 Audit-Tests. Keine produktiven Daten für Schreibtests und keine Abschwächung bestehender Tests.
- P0 Source/Workspace/Original-81-Artifact vor/nach Qualität PASS, ignoriert/untracked eingeschlossen; operative Seedzeilen0. Original-81-APK bytegleich SHA `7e569519f8f3328335499a84181f6eb6746c860e4325e80d67f0f44ba59e865e`. P1-Gesamtvertrag unverändert `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`,117 Tools/Protokoll9/156 UI-Aktionen. Keine Android-/JS-/MCP-/DB-/Snapshot-/Drive-Vertragsänderung, daher keine neue APK/Signierung/Installation und kein neuer nativer Emulatorlauf. Historische native Abnahmen nicht als neue P2-Ausführung ausgeben.
- Nach dem vorübergehenden Verbindungsfehler begrenzte erneute Healthabfrage erfolgreich:ready, danach get_sync_overview erfolgreich. Geschäftshash weiterhin `2b8310a538134a49966b736ae660758e2863a1d9fe62e973c11cc1f4ddb07932`, sämtliche Collection-Counts58/3/46/95/31/33/0/1 unverändert. Aktive Queue/Upload/Review/Journalfehler0/0/0/0; provider_readback, cloud_confirmed=false. Kein neuen Sync angefordert, keine Cloud-Bestätigung erfunden.
- Archiv erneut vollständig100+39 paginiert; vollständige139Einträge einschließlich Record-Hashes gegen bisheriges P2-Inventar identisch. Kanonischer Vollinventarhash vorher/nachher `d33a72b59135ad2928d01b3d6919537848aef35544000bfa8342e17061cdea50`. Alle sieben Backups weiter gelistet, sämtliche sechs alten IDs/Metadaten unverändert; neue geprüfte Sicherung ebenfalls erhalten. Keine neue Sicherung/Restore/Purge oder andere speichernde Betriebsaktion in dieser Nachprüfung.
- Alle fünf bekannten Drive-Ordner erneut vollständig im angebotenen top_k1000-Leseumfang abgefragt:218beobachtete Einträge inklusive vier Ordnern/214Dateien. Vergleich von IDs, Namen, MIME, Größe, Änderungsdatum und Parent-IDs vor/nach identisch; Metadatenhash `b302e09dd8dcb5550c07ff9d372f48a99b8d2edfb80d8599ba984912f7e06877`. Keine globale Vollständigkeitsgarantie aus einer begrenzten Connector-Liste ableiten. Keine Medienoriginale heruntergeladen und keine Drive-Datei geändert/gelöscht.
- Ergebnis: technisch sichere P2-Nachprüfung PASS; volle P2-Abnahme bleibt OFFEN. Noch offen: native Altbackup-Inspektion/ungeprüfter Restorewert, drei unbekannte JSON-Dateien und globale Backup-/Historien-/Dokumentreferenzen, tatsächlicher Ablauf der sieben alten Actions-Artefakte. Keine unterstützte Actions-Artefakt-DELETE-/Backup-Purge-/Codex-Task-Archivierungsschnittstelle vorhanden; kein Netzwerk- oder Berechtigungsbypass. Historische sensible Git-Objekte weiterhin separater Sicherheitsauftrag, kein Rewrite.
- Geändert ausschließlich tools/audit_media_inventory.py, tests/test_media_inventory_audit.py und die beiden zentralen CODEX-Dateien. Keine neue Projektdokumentation oder parallele Architektur.


## UAT und Black-Box-Usability – Analyseauftrag vom 20.10.2026

### Phase A – geprüfte Basis und Testmaßstab

- Ausgang/Remote-main identisch `d8960c46d42b5f6c99d8b6c2f3217686a6e4cf13`, sauber; operative Quelle/Originalartefakt1.51.2/81, Paket at.hausgarten.service, MCP9/117,156 UI-Aktionen. AGENTS, vollständiger Zentralauftrag und vollständiger bisheriger Status gelesen. Dieser neue Nutzerauftrag beschränkt die Arbeit auf Analyse/Test und diesen abgegrenzten Statusabschnitt; keine Reparatur, Design-/Architektur-/Vertrags-/Versionsänderung und keine produktiven Daten/Konten verwenden.
- Methode: nachvollziehbarer kognitiver Persona-Walkthrough plus reale Browserbedienung derselben operativen Assets mit bestehenden synthetischen Bridges, erhaltene Regressionen und dedizierter GartenpiratCloud-Emulator5582. Ein Agentenwalkthrough ist keine Studie mit echten Erstbenutzern; Auffindbarkeit/Verständlichkeit sind begründete Heuristik. Browserdoubles nicht als native Persistenz oder echten Google-/Kamera-/Versandnachweis ausgeben. Externe Freigaben ohne QA-Konto bleiben NOT TESTABLE.
- Abnahmekriterien: alle115 nummerierten Aufgaben einzeln erfassen, sämtliche sichtbaren Einstellungen inventarisieren, Technikexposition im normalen/Fehler-/Diagnosemodus unterscheiden, tatsächlich beobachtete Aktionszahlen von vorgeschlagenen Zielzahlen trennen. Keine Benutzerzeiten oder Fehlerquoten erfinden. Sicherheits-/Privacyschutz gilt auch dann als notwendig, wenn seine technische Darstellung UX-Reibung erzeugt.
- Zählregel: ein Tippen/Klicken, Feldbefüllen, Auswählen, Scrollen oder Zurück jeweils eine Benutzeraktion; programmgesteuerte Zustandsabfragen und Testdatenvorbereitung zählen nicht. Navigation am jeweiligen Ausgangspunkt ausdrücklich ausweisen; keine Summe über115 Aufgaben als Zahl unabhängiger Gesamtworkflows ausgeben. Unnötige Aktionen sind eine Persona-Bewertung, keine Erlaubnis zum Entfernen von Bestätigung/CAS/Backup.
- Emulator ohne KVM aus vorhandenem erhaltenem AVD gestartet, keine Profile/Stores gelöscht und keine Pixelverbindung. Testausführung, tatsächlicher Bootabschluss und abschließende Privacy-/P1-Vertragsnachweise noch offen. Der Auftrag endet mit einem priorisierten Bericht, nicht mit Umsetzung.

### Phase B – synthetische Funktions- und Bildschirmprüfung

- Erneut ausgeführt: 322 Python-Prüfungen PASS (0 Skips; 37 ausdrücklich ausgeschlossene historische QA-Schlüssel-/Parallelpaket-Prüfungen), 27 JVM-Suiten PASS, alle 30 Einträge des Browser-/JS-Regressionsrunners PASS und 55 Szenarien des isolierten MCP-stdio-Rundlaufs PASS. Dieser stdio-Rundlauf prüft den vorhandenen eingeschränkten Kanaladapter und ist kein Nachweis eines aktuell verbundenen Pixel-Connectors; dessen 117 Werkzeuge werden zusätzlich durch JVM-/native Contracts geprüft.
- Ergänzender kognitiver Durchlauf auf unveränderten operativen Assets bei 360 × 900: Kunde mit Name/Adresse anlegen, Kontakt nachtragen; Arbeitsdetail und alle fünf Zusatzgruppen öffnen; Kundenaktion → Angebotsentwurf → Prüfung; Planungsformular; Offene Leistungen → Rechnungserstellung; Timer bei verweigertem Standort → manuelle Auswahl → Pause → Fortsetzen → Ende → Speichern. Sechs Durchläufe PASS auf der bestehenden synthetischen Bridge. Anfängliche Selektor-/Fixture-Extraktionsfehler des temporären Auswertungsskripts wurden dort korrigiert, ohne App- oder Testcode zu ändern; sie sind keine Appfehler.
- Sichtprüfung tatsächlicher synthetischer Screenshots für Heute, Einstellungen, Kundenakte, Angebots- und Terminformular. Keine Produktivnamen, Originalfotos oder Kontozugänge verwendet. Browser-Fiktivversionen stammen aus bestehenden Fixtures und ersetzen nicht die nativ geprüfte Version 1.51.2/81.
- Der ausschließlich ausgewählte Cloud-Emulator emulator-5582 wurde mit QEMU-, AVD-Namens- und Bootprüfung bestätigt; vorhandene operative App 1.51.2/81 und Instrumentierung gefunden. Zu diesem Phasenzeitpunkt liefen die nativen SQLite-/MCP-/Medien-/Provider-Tests noch; Endergebnis siehe Phase D. physischer Pixel, echter Google-Account, echter Drive und externer Versand wurden nicht berührt.

### Phase C – Ergebnis des User-Acceptance- und Black-Box-Usability-Tests

#### Prüfgrenzen, Nachweise und Zählweise

Dies ist ein durch automatisierte Regressionen gestützter kognitiver Experten-Durchlauf mit der vorgegebenen Betreiberpersona, keine beobachtete Studie mit einer technisch uninteressierten Erstperson. Auffindbarkeit und unnötige Schritte sind deshalb begründete Usability-Bewertungen, keine gemessenen Erfolgsquoten oder Bearbeitungszeiten realer Benutzer. Es wurde kein Ein-Minuten-Versprechen bestätigt. Die unten vorgeschlagenen Abläufe sind ausschließlich Empfehlungen, nicht implementiert.

Nachweise: **B** = tatsächlich ausgeführtes Chromium-UI auf operativen Assets mit vorhandener synthetischer Android-Bridge; **N** = vorhandene Android-Instrumentierung auf dem dedizierten Cloud-Emulator; **J** = JVM-/Contract-Test; **P** = Python-/isolierter Kanaltest; **S** = Quellcode-/Bedienelementprüfung. B bestätigt keine Android-Berechtigung, keine Kameraaufnahme und keinen echten Google-Upload. N mit synthetischem Provider bestätigt keine Erreichbarkeit eines echten Google-Kontos. Ein Katalog-Mapping bestätigt keine tatsächlich sichtbare Schaltfläche.

**P = PASS**, **R = PASS MIT REIBUNG**, **F = FAIL des beschriebenen Benutzerziels**. Die angegebenen N-Nachweise sind die erfolgreichen isolierten Fach-/Neustarttests, keine pauschale Freigabe der nativen Sichtprüfung oder des zusätzlich fehlgeschlagenen alten Update-Verifiers (Phase D). **NT = NOT TESTABLE** bezeichnet den ausdrücklich nicht ausgeführten externen Teil, auch wenn Teilprüfungen PASS sind. Ein R kann technisch korrekt und trotzdem unnötig kompliziert sein. Kein F wird allein aus einer unvollständigen Browser-Bridge abgeleitet. Keine aktuelle Produktivwarteschlange oder historische Originalkonflikte wurden geprüft oder verändert.

**A/U** zählt Benutzeraktionen/als vermeidbar bewertete Aktionen vom jeweils genannten Ausgangspunkt: Tap/Klick, Auswahl, Feldeingabe, Bestätigung je eine Aktion; zusammengesetzter App-Auswahldialog üblicherweise zwei. Betrachtung ohne Eingabe und automatisch wiederhergestellter Stand: null. **≥** ist die im ausgeführten Weg belegte Untergrenze; **~** eine aus dem geprüften Bedienpfad abgeleitete Spanne, ausdrücklich keine vollständig gemessene Bedienfolge. Die Browserautomation scrollt Bedienelemente teilweise automatisch in Sicht; manuelles Scrollen, Android-Dateiauswahl, Tastatur, Kontofreigabe und OS-Dialoge erhöhen die tatsächlichen Zahlen. Für nicht ausführbare Ziele ist „—“ ehrlicher als eine erfundene Klickzahl. U ist eine Entwurfsbewertung und keine Aufforderung, Bestätigung, Preisprüfung oder Rechtsentscheidung wegzulassen. Zusammenhängende Teilaufgaben nicht addieren, wenn dieselbe Navigation mehrfach enthalten ist.

Die Profilspalte legt für **jede zugeordnete Zeile** Vorwissen, Auffindbarkeit, Verständlichkeit, Fehlerrisiko, Risiko unbeabsichtigter Änderung und Selbstkorrektur fest; abweichende Ursachen stehen in der Zeile. **V/N/A/E** nennt je Zeile: technische Informationen sichtbar / für das Benutzerziel notwendig / sicher automatisch erledigbar / fachliche Entscheidung auf EIN/AUS reduzierbar (J/N). „Automatisch“ bei einer Suche oder Dokumentprüfung meint nur Kontext-/Standardübernahme bzw. Prüfung, niemals erfundene Preise, rechtliche Bindung, Versand, endgültiges Löschen oder Konfliktüberschreiben. Profile:

| Profil | Vorwissen | Auffindbarkeit | Verständlichkeit | Fehlerrisiko | unbeabsichtigte Änderung | Selbstkorrektur |
|---|---|---|---|---|---|---|
| R – Lesen | keines | gut | gut | niedrig | niedrig | Zurück/Filter zurücksetzen |
| E – Eingabe | eigene Betriebsangaben | gut | überwiegend gut | mittel bei Auswahl | niedrig durch explizites Speichern | Bearbeiten/Abbrechen/Entwurf |
| X – versteckter Weg | Menü-/Bereichskenntnis | eingeschränkt | mittel | mittel | niedrig | Zurück/Entwurf; Ziel zunächst finden |
| D – Dokument | Preise, Empfänger, Bindung/Steuer fachlich kennen | mittel | erklärungsbedürftig | mittel bis hoch fachlich | begrenzt durch Prüfung/Bestätigung | Entwurf editieren; nach Abschluss Korrekturweg |
| T – Technik | derzeit Verbindungs-/Übertragungswissen | schlecht für Persona | unzureichend | mittel | niedrig bei Lesen; Fehlentscheidung möglich | Wiederholen/Einrichtung, teilweise Hilfe nötig |
| G – Entfernung | Papierkorb/Endgültigkeit unterscheiden | mittel | überwiegend gut | hoch bei endgültigem Löschen | durch Vorschau/Bestätigung begrenzt | Undo/Wiederherstellen; nach Purge nicht in App |
| A – Hintergrund | keines | keine Bedienung erforderlich | gut bei wahrer Rückmeldung | niedrig | niedrig | Entwurf/Sitzung wieder aufnehmen |
| L – fehlender Bedienpfad | derzeit MCP-/Hilfswissen nötig | nicht auffindbar | Ziel nicht ausführbar | mittel bei Workaround | durch Umwege erhöht | nur vorhandener alternativer Fachweg |
| O – externe Grenze | Geräte-/Kontofreigabe | nur teilweise geprüft | teilweise technisch | nicht vollständig bewertbar | nicht vollständig bewertbar | neu freigeben; kein Erfolg behauptet |

Severity bewertet den Befund, nicht den maximal denkbaren Schaden jeder Rechnung: **S0** = nachgewiesenes Daten-/Sicherheitsrisiko, **S1** = Benutzerziel blockiert, **S2** = deutlich unnötig kompliziert, **S3** = leichte Reibung, **S4** = kosmetisch; **–** = kein Befund. Es wurde kein neuer S0-Datenverlust nachgewiesen.

#### Aufgabenmatrix A–N: alle 115 Aufgaben

| Nr. | Aufgabe und Ausgangspunkt / Befund | Urteil; Nachweis | A/U | Profil | V/N/A/E | Severity |
|---:|---|---|---|---|---|---|
| 1 | Erster Start: leerer isolierter Bestand; keine Produktivdaten | P; N/B | ~1/0 | R | N/N/J/N | – |
| 2 | Heute: Tages-/Wochensumme, nächster Einsatz, letzter Eintrag | P; B/N | 0/0 | R | N/N/J/N | – |
| 3 | Heute: große Schaltfläche Arbeit starten; Leertext nennt abweichend „Arbeit erfassen“ | R; B/N | 0/0 | R | N/N/N/N | S3 |
| 4 | Heute: laufende/pausierte Sitzung und Abschluss sind sichtbar | P; B/N | 0/0 | R | N/N/J/N | – |
| 5 | Heute: lokalen Speicherzustand von erstem Drive-Abgleich unterscheiden | R; B/S | ~0–1/0 | T | J/N/J/N | S2 |
| 6 | Startbelastung: Drive-Warnpille ohne fachlichen nächsten Schritt; sonst kompakt | R; B | 0/0 | T | J/N/J/N | S3 |
| 7 | Heute → Kunden → Neu → Name/Straße/PLZ/Ort → speichern | P; B/N | ≥7/0 | E | N/N/N/N | – |
| 8 | Name und Adresse genügen; Telefon/E-Mail optional | P; B/N | 0/0 | E | N/N/N/N | – |
| 9 | Kundenliste → Akte → bearbeiten → Telefon → speichern | P; B | ≥4/0 | E | N/N/N/N | – |
| 10 | Geöffneter Kundeneditor → E-Mail ergänzen → speichern | P; B | ≥2/0 | E | N/N/N/N | – |
| 11 | Kundenliste: Suchfeld unten, ein Suchbegriff | P; B | ≥1/0 | R | N/N/N/N | – |
| 12 | Kundenliste: Treffer öffnet Kundenakte | P; B/N | ≥1/0 | R | N/N/N/N | – |
| 13 | Akte: letzte Arbeiten sichtbar; ältere über vollständige Arbeitsliste/Verlauf | P; B | ~0–2/0 | R | N/N/J/N | – |
| 14 | Akte → Fotos & Dateien; 30 Vorschauen, weitere laden | P; B | ≥1/0 | R | N/N/J/N | – |
| 15 | Akte → alle Arbeiten; fremde Zeit-/Suchfilter werden zurückgesetzt | P; B | ~1–2/0 | R | N/N/J/N | – |
| 16 | Rechnungen in Übersicht, Angebote unter Fotos & Dateien, Termine als Kopfaktion | R; B/S | ~1–3/~1 | X | N/N/J/N | S2 |
| 17 | Akte → Kundendaten bearbeiten → Änderung → speichern | P; B | ≥3/0 | E | N/N/N/N | – |
| 18 | Kundeneditor: Kunde aktiv AUS; Entfernung separat über Papierkorb | R; B/S/N | ~4–6/~1 | X | N/N/N/J | S2 |
| 19 | Inaktive Kunden bleiben suchbar; Papierkorb hat Wiederherstellen | P; B/N | ~2–4/0 | G | N/N/N/N | – |
| 20 | Heute → Start → Kundenauswahl → Start; kein Start allein durch Ortsermittlung | P; B/N | ≥3/0 | E | N/N/N/N | – |
| 21 | Startdialog/Kundenauswahl; Auftrag optional, ggf. separater Zusatzbereich | R; B/S | ~2–4/~1 | X | N/N/J/N | S3 |
| 22 | Laufende Sitzung → Pause | P; B/N | ≥1/0 | E | N/N/N/N | – |
| 23 | Pausierte Sitzung → Fortsetzen | P; B/N | ≥1/0 | E | N/N/N/N | – |
| 24 | Laufende Sitzung → Ende; Arbeitsaufzeichnung erst nach ergänzen/speichern | P; B/N | ≥1/0 | E | N/N/N/N | – |
| 25 | Abschluss → Tätigkeit → speichern; Timer-Nettozeit wird übernommen | P; B/N | ≥2/0 | E | N/N/J/N | – |
| 26 | Abschluss → Material und Kilometer → Material | P; B | ≥2/0 | E | N/N/N/N | – |
| 27 | Offene Materialgruppe → Kilometer eintragen | P; B | ≥1/0 | E | N/N/N/N | – |
| 28 | Arbeit → Fotos und Notizen → Datei; Kamera-/Pickergrenze separat | R; B/N/S; OS NT | ~3–5/0 | O | N/N/J/N | S3 |
| 29 | Arbeit → Fotos/Notizen → Notiz; zusätzlicher Nachtrag im Detail getrennt | R; B | ~2–3/~1 | E | N/N/N/N | S3 |
| 30 | Arbeiten → Eintrag → Bearbeiten → Korrektur → Speichern | P; B/N | ≥4/0 | E | N/N/N/N | – |
| 31 | Editor → Uhrzeiten/Zeit und Pause → Beginn/Ende; Dauer und Uhrzeiten erklärt | R; B/J | ~4–6/~1 | E | N/N/J/N | S3 |
| 32 | Editor → Zeit und Pause → Pause → speichern; Nettozeit nicht doppelt reduzieren | P; B/N | ~3–4/0 | E | N/N/J/N | – |
| 33 | Editor → Tätigkeit → speichern | P; B/N | ≥2/0 | E | N/N/N/N | – |
| 34 | Editor → Kunde/Auftrag; Auftrag wird nur passend übernommen | P; B/N | ~3–6/0 | E | N/N/J/N | – |
| 35 | Persönliches b.b. unabhängig von Arbeits-/Rechnungsstatus; Abkürzung verlangt Vorwissen | R; B/J/N | ~1–2/0 | X | N/N/N/J | S3 |
| 36 | b.b., App: verrechnet und Rechnung bezahlt sind fachlich getrennt, aber erklärungsbedürftig | R; B/J/N | 0/0 | D | N/N/N/N | S2 |
| 37 | Aktive Arbeit → Foto; Sitzung wird nach Abschluss mit Arbeit verbunden | R; B/N/S; Kamera NT | ~3–5/0 | O | N/N/J/N | S3 |
| 38 | Kundenakte → + Foto → Galerie/Kamera; Kunde vorgegeben | P; B/N; Kamera NT | ≥2/0 | E | N/N/J/N | – |
| 39 | Foto hinzufügen → Aufnahmephase wählen; Vorher/Während/Nachher | P; B | ~1–2/0 | E | N/N/N/N | – |
| 40 | Beim Import Beschreibung eingebbar; vorhandenes Foto: kein Bearbeiten im Betrachter | R; B/S | ≥1/0; später — | X | N/N/N/N | S2 |
| 41 | Vorhandenes Foto später einer Arbeit zuordnen: kein sichtbarer UI-Weg; MCP update_photo vorhanden | F; B/S/J | —/— | L | N/N/N/N | S1 |
| 42 | Akte/Fotobibliothek → Filter/Vorschau → Vollbild, Blättern und Zoomen | P; B | ~2–4/0 | R | N/N/J/N | – |
| 43 | Foto → Mehr → Papierkorb → Bestätigung; danach Undo/Wiederherstellen | P; B/N | ~3–4/0 | G | N/N/J/N | – |
| 44 | Kamera-/Dateiberechtigung: Speicherfehler/Entwurf getestet; echter OS-Entzug nicht durchgeklickt | NT; B/S Teilprüfung | —/— | O | J/N/N/N | offen |
| 45 | Bildbetrachter braucht weder Hash noch Pfad; Fallback kann Dateiname/Drive-Ladehinweis zeigen | R; B/S | 0/0 | R | J/N/J/N | S3 |
| 46 | Mehr → Projekte & Aufträge → Neu → Angaben → speichern | R; B/S | ~6–9/~1 | X | N/N/J/N | S2 |
| 47 | Auftrag aus Kundenakte übernimmt Kunden; globaler Dialog erfordert Auswahl | P; B/S/N | ~0–2/0 | E | N/N/J/N | – |
| 48 | Arbeit → optional Auftrag; Auswahl auf Kunden eingeschränkt | P; B/J | ~2–3/0 | E | N/N/J/N | – |
| 49 | Auftrag → Überblick → Bearbeiten → Änderung → speichern | R; B/S | ~4–5/~1 | X | N/N/N/N | S3 |
| 50 | Auftragsstatus Erledigt; Archivierung/Entfernung extra und wiederherstellbar | R; S/N/B | ~3–5/~1 | X | N/N/N/N | S2 |
| 51 | „Projekte & Aufträge“ ist eine gemeinsame Entität, kein zweites Projektsystem | R; B/S | 0/0 | X | N/N/J/N | S3 |
| 52 | Mehr → Planung → Neu → Kunde/Tätigkeit/Beginn/Dauer → speichern | R; B/N | ~8–11/~1 | E | J/N/J/N | S2 |
| 53 | Termin aus Kunde/Auftrag übernimmt Kontext; global bewusst auswählen | P; B/N | ~0–2/0 | E | N/N/J/N | – |
| 54 | Termin → Beginn/Dauer → speichern; Alternative bleibt zunächst Entwurf | P; B/N | ~3–5/0 | E | N/N/N/N | – |
| 55 | Termin absagen mit Bestätigung; Auftrag/Arbeit/Rechnung bleiben erhalten | P; B/N | ~2–3/0 | G | N/N/N/N | – |
| 56 | Wiederholung unter „Ort, Anfahrt, Pause und Wiederholung“ mit Intervall/Anzahl | R; B/S/N | ~4–6/~1 | X | N/N/N/N | S2 |
| 57 | Kundenakte → Termine → Neu, Kundenkontext vorgegeben | P; B/S | ~2–3/0 | E | N/N/J/N | – |
| 58 | Kalender: native Einrichtung/Konto/OAuth; tägliche Freigabe könnte EIN/AUS sein | R; B/S/N; Google NT | ~2 bis Einrichtung/~1 | O | J/N/J/J | S2 |
| 59 | Wetter: Funktion/Benachrichtigung EIN/AUS sinnvoll; Lizenz und Schlüssel bleiben Erstfreigaben | R; S/N; Anbieter NT | ~2 bis Einrichtung/~1 | O | J/N/J/J | S2 |
| 60 | Akte → Kontakt/weitere Aktionen → Angebot → umfangreiches Formular | R; B/N | ≥3/~1 | X | N/N/J/N | S2 |
| 61 | Dokumentart unverbindlicher Kostenvoranschlag + verständliche Bindungsbestätigung | P; B/N/J | ~2–3/0 zusätzlich | D | N/N/N/N | – |
| 62 | Dokumentart verbindlicher Kostenvoranschlag + erneute Bestätigung nach Wechsel | P; B/N/J | ~2–3/0 zusätzlich | D | N/N/N/N | – |
| 63 | Dokumentart Fixpreis; keine automatische Bindungsannahme | P; B/N/J | ~1–2/0 zusätzlich | D | N/N/N/N | – |
| 64 | Dokumentart Regie; Mengen als geplante Stunden und Preisgrundlage | P; B/N/J | ~1–2/0 zusätzlich | D | N/N/N/N | – |
| 65 | Baustein wählen → Menge; bestätigter Preis wird übernommen | P; B/N/J | ≥2/0 | E | N/N/J/N | – |
| 66 | Eigene Position: Titel/Beschreibung/Einheit/Menge/Preis/Steuer/Grundlage | R; B/S/N | ~5–9/~2 | D | N/N/J/N | S2 |
| 67 | Positionspreis ändern; Katalogbezug wird korrekt gelöst; Preisgrundlage wirkt technisch | R; B/J/N | ~1–3/~1 | D | N/N/N/N | S3 |
| 68 | Texte und Hinweise → editierbarer Text oder Baustein | P; B | ~2–3/0 | E | N/N/N/N | – |
| 69 | Prüfen und Vorschau; fehlende Angaben inline, keine erfundene Summe | P; B/N | ≥1/0 | D | N/N/J/N | – |
| 70 | Finalisieren → Prüfung → Vorschau/Bestätigung; keine automatische Rechnung | P; B/N/J | ~2–3/0 | D | N/N/N/N | – |
| 71 | Entwurf/finalisiert/geteilt/gesendet im Modell getrennt; Button „Finalisieren“ erklärungsbedürftig | R; B/S/N | 0/0 | D | N/N/J/N | S3 |
| 72 | Teilen-App ist kein Versandbeweis; Text sagt ausdrücklich „nicht bestätigt“ | P; S/N/J; realer Versand NT | ~1–2/0 | D | N/N/J/N | – |
| 73 | Offene Leistungen hat keine Rechnung-CTA; über Rechnungen → Neu → Leistungen → Arbeiten übernehmen | R; B/S/J/N | ~9–13/~3 | X | N/N/J/N | S2 |
| 74 | Rechnung: Kunde und Rechnungsadresse kontrollieren, Einsatzort getrennt erhalten | P; B/J/N | ~0–2/0 | D | N/N/J/N | – |
| 75 | Leistungen → Arbeitsaufzeichnungen übernehmen → Auswahl → übernehmen | R; B/S/J | ~3–5/~1 | X | N/N/J/N | S2 |
| 76 | Positionen/Mengen/Preis kontrollieren; keine erfundenen Preise | P; B/S/J/N | ~0–3/0 | D | N/N/J/N | – |
| 77 | Zahlungsziel zentral vorbelegt, im Entwurf prüfbar | P; B/J | ~0–1/0 | E | N/N/J/N | – |
| 78 | Abschließen nur nach Nummern-/Ausstellerfreigabe; lokaler Test prüft Blockade und Abschluss | R; N/J/S; produktiver Dienst NT | ~2–4/0 | D | J/N/J/N | S2 |
| 79 | Mehr → Rechnungen → Suchfeld/Statusfilter | P; B/S | ~3–4/0 | R | N/N/N/N | – |
| 80 | Entwurf/Gestellt/Bezahlt/Storniert getrennt; Versand nicht automatisch behauptet | P; B/J/N | 0/0 | D | N/N/J/N | – |
| 81 | Zahlung per MCP fachlich getestet; sichtbare Rechnungsaktionen bieten keine Zahlungsschaltfläche | F UI; S/N/J | —/— | L | N/N/N/N | S1 |
| 82 | Entwurf zeigt Steuerart, Empfängertyp, UID und Inlandsfall in normalem Formular | R; B/S | 0/0 | D | N/N/J/N | S2 |
| 83 | Kunden: ein lokales Suchfeld, sichtbare Treffer | P; B | ≥1/0 ab Liste | R | N/N/N/N | – |
| 84 | Arbeiten: Suche plus Zeitraum; fremde Filter zurücksetzbar | P; B | ~1–3/0 ab Liste | R | N/N/N/N | – |
| 85 | Fotos: Akte/Phase/Zeitraum; globale Bibliothek nur alle/ohne Kunde, keine freie Suche | R; B/S | ~3–6/~2 | X | N/N/J/N | S2 |
| 86 | Rechnungen: Textsuche und Statusfilter | P; B/S | ~1–2/0 ab Liste | R | N/N/N/N | – |
| 87 | Termine: Zeit-/Kundenkontext und Liste; übergreifender Suchpfad fehlt | R; B/S | ~3–5/~1 | X | N/N/J/N | S2 |
| 88 | Fünf getrennte Wiederfindewege; globale Suche nur als belegter Kandidat empfehlen | R; B/S/P | —/— | X | N/N/J/N | S2 |
| 89 | Archivieren führt intern in vorhandenen Wiederherstellungsbestand; Wörter variieren | R; S/N/B | ~2–4/~1 | G | N/N/N/N | S2 |
| 90 | Entfernen/Papierkorb direkt möglich, auch mit Fotos; Originale geschützt | P; B/N | ~1–3/0 | G | N/N/J/N | – |
| 91 | Mehr → Papierkorb → Wiederherstellen; bestehende Verknüpfungen werden geprüft | P; B/N | ~3–4/0 | G | N/N/J/N | – |
| 92 | Einzel-Purge oder Papierkorb leeren → eine mengen-/versionsgebundene Bestätigung | P; B/N/J | ~2–4/0 | G | N/N/N/N | – |
| 93 | Kunde aktiv AUS, Auftrag Erledigt, Archiv, Papierkorb nicht selbsterklärend zusammenhängend | R; B/S | 0/0 | X | N/N/N/N | S2 |
| 94 | Endgültiges Löschen geschützt; aktive Referenzen und gestellte Belege bleiben erhalten | P; B/N/J | ~1 Bestätigung/0 | G | N/N/J/N | – |
| 95 | Hash/Preview/CAS im normalen Papierkorb unsichtbar; Konflikt zeigt nur fachliche Entscheidung | P; B/N/J | 0/0 | G | N/N/J/N | – |
| 96 | Offline fachlich arbeiten; lokale Speicherung unabhängig von Netz | P; N/B/P | 0/0 zusätzlich | A | N/N/J/N | – |
| 97 | Offline Arbeit speichern; Wiederholung keine Doppelbuchung | P; N/B/P | ~1/0 zusätzlich | E | N/N/J/N | – |
| 98 | Offline Kunde speichern und später ändern; Vorgängerkette erhalten | P; N/B/P | ~1/0 zusätzlich | E | N/N/J/N | – |
| 99 | Offline Foto sichern; getrennte Medienwarteschlange, Fehler bewahrt Entwurf | P; N/B | ~1–3/0 zusätzlich | E | N/N/J/N | – |
| 100 | App-/Store-Neustart nach synthetischen Änderungen | P; N/B | ~1–2/0 | A | N/N/J/N | – |
| 101 | Testdaten, IDs, Beziehungen nach Wiederöffnung erhalten | P; N/B/P | 0/0 | A | N/N/J/N | – |
| 102 | Netz/Provider wieder verfügbar: synthetischer Retry, kein echtes Google-Konto | P Teilstrecke; N/P; Google NT | 0/0 | A | N/N/J/N | – |
| 103 | Drive auslösen: UI jetzt abgleichen; Provider- und Direkt-HTTP-Test doubles getrennt | P Teilstrecke; N/P; Drive live NT | ~1–3/~1 | T | J/N/J/N | S2 |
| 104 | Unterbrechung/401/429/Schreibfehler im isolierten Provider, alte Daten/Queue bleiben | P; N/P/J/B | 0/0 für Fehlerauslösung | A | N/N/J/N | – |
| 105 | Fehlerstatus unterscheidet Konflikt und Netz; rohe Fehlermeldung kann ins normale UI gelangen | R; B/S/N | ~0–2/~1 | T | J/N/J/N | S2 |
| 106 | Keine klare alltägliche Aussage „letzte geprüfte Sicherung“; Sync ist kein Vollbackupnachweis | F UX; B/S/N | —/— | L | J/N/J/N | S1 |
| 107 | Automatische Geschäftssicherung prinzipiell sinnvoll; umfassender automatischer Backupzyklus nicht nachgewiesen | R; S/N | 0/0 | A | N/N/J/J | S2 |
| 108 | EIN/AUS kann Sicherungsabsicht abbilden, nicht Freigabe/Erfolg/Restore ersetzen | R; B/S | 0/0 | T | J/N/J/J | S2 |
| 109 | Sync-/Verbindungs-/Historienflächen sichtbar; keine eigenständige verständliche Backupverwaltung gefunden | R; B/S | ~2–4/~1 | T | J/N/J/N | S2 |
| 110 | Alle gefundenen Einstellungen einzeln bewertet, siehe Inventar unten | R; B/S/N/J | unterschiedlich; unten | T | J/N/J/N | S2 |
| 111 | Bestehenden synthetischen Bestand schließen; keine Deinstallation/kein Löschen | P; N/B | ~1–2/0 | A | N/N/J/N | – |
| 112 | Operative App wieder öffnen, gleicher Testbestand | P; N/B | ~1/0 | A | N/N/J/N | – |
| 113 | Arbeits-/Angebots-/Termin-Entwürfe und private Dateien bleiben im geprüften Scope erhalten | P; N/B | 0/0 | A | N/N/J/N | – |
| 114 | Laufende/pausierte Sitzung bzw. offener Abschluss rekonstruierbar, kein zweiter Timer | P; N/B/J | ~0–1/0 | A | N/N/J/N | – |
| 115 | Normaler Neustart ohne technischen Wiederherstellungsdialog; Foto-Fehler fordert nur gezielte Aktion | P; B/N/S | ~0–1/0 | A | N/N/J/N | – |

Bei 28/37/38 bedeutet das P/R ausschließlich den tatsächlich geprüften Import-/Zuordnungs- und Speicherpfad. Ein Kameragerät wurde nicht vorgetäuscht. Bei 70/72/78 wurden synthetische Dokumente lokal erzeugt bzw. native Grenzen geprüft; keine echte Nachricht versendet und keine produktive Nummer vergeben. 78 bleibt für echte Konten ein offener Endtest. Bei 111–114 belegen native Store-Neustarts und der WebView-Lifecycle den jeweiligen Scope, keinen Boot-/Akku-/Speicherstress über mehrere Tage.

Zusätzlich zum nummerierten Alltagsszenario: Pflanzenbibliothek/Neophyten, Pflanzenbild-Blättern, Gartenpirat-Fotooptionen, Leistungskatalog, b.b.-Persistenz, Batch-Papierkorb und schmale Ansichten wurden über ihre vorhandenen Regressionssuiten erneut geprüft. Rollen-/MCP-Katalog, Berechtigungen, erlaubte Felder und Rückgaben bleiben durch P1-Contract/JVM/native Tests geschützt. Die 156 semantischen Katalogaktionen verteilen sich auf Kunden 13, Aufträge 12, Arbeiten 14, Zeiterfassung 10, Rechnungen 13, Angebote 13, Fotos 12, Sicherung 6, Synchronisierung 10, Papierkorb 6, Einstellungen 7, Termine 20, Leistungskatalog 5, Pflanzen 4 und Plattform/Darstellung 11. Das ist eine Vertragsinventur, keine Behauptung von 156 erfolgreich durchgeklickten Benutzerabläufen. Insbesondere Backup, Fotoänderung und Zahlung illustrieren die Differenz zwischen vorhandenem MCP-Fachweg und auffindbarer Oberfläche.
#### Einstellungen: vollständiges gefundenes Bedieninventar zu Aufgabe 110

56 einzelne Einstellungs-/Einrichtungsentscheidungen wurden erfasst: 2 allgemeine, 25 Unternehmens-/Rechnungsfelder, 21 Planungs-/Kalender-/Wetterfelder, 3 Drive-/Mailfreigaben, 4 ChatGPT-Verbindungsfelder und 1 Gartenpirat-Präferenz. Kontoauswahl und Freigabe sind Einrichtungsschritte, keine gewöhnlichen dauerhaften Formularfelder. Filter, Kunde aktiv, Foto-Aufnahmephase, Dokumentart und Vertragsweg sind Fachentscheidungen im jeweiligen Datensatz und bereits in der Aufgabenmatrix bewertet. Geheimnisfelder wurden ausschließlich anhand ihrer Definition bewertet; keine gespeicherten Werte gelesen.

Nachweis W/B/G = B/S, PL/D/T = S plus passende native Grenz-/Berechtigungstests; echte Konto-/Schlüssel-Freigaben NT. „Entfernen“ bedeutet hier nur aus täglicher Darstellung entfernen, niemals Werte, Rechte oder Datenmodell löschen. Alle Zeilen haben bei derzeitiger Darstellung mindestens leichte Reibung, soweit „Erweitert“ empfohlen wird; notwendige Fachangaben sind kein UX-Fehler. Standard „beibehalten“ darf keinen unbekannten Wert erfinden.

| ID | Einzelne Einstellung | Muss Eigentümer entscheiden? | Sicherer Standard / Automatik | EIN/AUS ausreichend? | Empfohlener Ort / technische Notwendigkeit |
|---|---|---|---|---|---|
| W1 | Seitlich zwischen Hauptseiten wischen | optional | bisher AUS behalten; explizites Opt-in | ja | Bedienung; keine technische Kenntnis nötig |
| W2 | Fotoqualität | optional | speicheroptimiert, kleine Bilder schonen | nein, 3 sinnvolle Profile | Fotos/Erweitert; keine Pixel-/Qualitätszahlen im Alltag |
| B1 | Geschäftsname | ja, einmalig | vorhandenen geprüften Wert übernehmen | nein | Unternehmen; fachlich nötig |
| B2 | Rechtlicher Ausstellername | ja, einmalig | nie erfinden | nein | Unternehmen/Ersteinrichtung |
| B3 | Geschäftsstraße | ja, einmalig | geprüften Bestand übernehmen | nein | Unternehmen/Ersteinrichtung |
| B4 | Geschäfts-PLZ | ja, einmalig | geprüften Bestand übernehmen | nein | Unternehmen/Ersteinrichtung |
| B5 | Geschäftsort | ja, einmalig | geprüften Bestand übernehmen | nein | Unternehmen/Ersteinrichtung |
| B6 | Geschäfts-E-Mail | ja | vorhandenen Wert übernehmen | nein | Unternehmen/Kontakt |
| B7 | Geschäftstelefon | ja | vorhandenen Wert übernehmen | nein | Unternehmen/Kontakt |
| B8 | Bankname | ja, falls verwendet | vorhandenen Wert übernehmen | nein | Unternehmen/Zahlung, nicht täglicher Editor |
| B9 | IBAN | ja | nie ableiten/erfinden | nein | Unternehmen/Zahlung; Prüfung erhalten |
| B10 | BIC | falls fachlich benötigt | leer erlaubt, wenn nicht erforderlich | nein | Zahlungsdetails/Erweitert |
| B11 | UID des Unternehmens | abhängig vom bestätigten Steuerfall | keine fiktive UID | nein | Unternehmen/Steuer |
| B12 | Steuerbefreiungshinweis | ja/fachlich prüfen | bestätigten Text wiederverwenden | nein | Unternehmen/Steuer, nicht jedes Dokument neu |
| B13 | Standard-Zahlungsziel in Tagen | ja, einmalig | bestätigten Standard übernehmen | nein | Zahlung; sicher vorbelegen |
| B14 | Standard-Zahlungsbedingungen als Text | ja, einmalig | aus bewusst gepflegtem Standard, Konsistenz prüfen | nein | Zahlung; keine zweite tägliche Abfrage desselben Ziels |
| B15 | Hinweis verbindlicher Kostenvoranschlag | ja, bei Einrichtung/Änderung | bestätigten Text wiederverwenden | nein | Dokumenttexte/Erweitert; Bindung weiter bewusst wählen |
| B16 | Hinweis unverbindlicher Kostenvoranschlag | ja, bei Einrichtung/Änderung | bestätigten Text wiederverwenden | nein | Dokumenttexte/Erweitert |
| B17 | Hinweis Regieangebot | ja, bei Einrichtung/Änderung | bestätigten Text wiederverwenden | nein | Dokumenttexte/Erweitert |
| B18 | Rechnungspräfix | ja, Nummernkreis festlegen | bestehenden Kreis erhalten | nein | geschützte Rechnungs-Ersteinrichtung |
| B19 | Zuletzt vergebene Rechnungsnummer/Zähler | ja, geprüft übernehmen | nie blind hoch-/zurücksetzen | nein | geschützte Einrichtung/Diagnose, nicht tägliche Einstellung |
| B20 | Weitere Unternehmensangaben | falls nötig | vorhandenen Text übernehmen | nein | Unternehmen/Erweitert |
| B21 | Steuermodus | ausdrücklich ja | ungeklärt bleibt ungeklärt | nein | Unternehmen/Steuer; niemals automatisch Kleinunternehmer annehmen |
| B22 | Bestehender Nummernkreis: eigen/sevDesk | ausdrücklich ja | bestehende Autorität erhalten | nein | geführte einmalige Einrichtung |
| B23 | Ausstellerdaten geprüft | ausdrücklich ja | keine automatische Zustimmung | ja als Bestätigung, kein beliebiger Alltagsschalter | Ersteinrichtung; bei relevanter Änderung erneut prüfen |
| B24 | Nummernkreis/Überschneidungen geprüft | ausdrücklich ja | keine automatische Zustimmung | ja als Bestätigung | Ersteinrichtung, fachlich sicherheitsrelevant |
| B25 | Pixel führt allein Rechnungsnummernkreis | ausdrücklich ja | kein automatischer Autoritätswechsel | nein, fachliche Einrichtung mit Voraussetzungen | zusammen mit B22/B24; nicht technische Providerwahl verlangen |
| PL1 | Kalender-Google-Konto | ja, bei Verbindung | vorhandenes autorisiertes Konto vorschlagen | nein | Ersteinrichtung; danach Status und EIN/AUS |
| PL2 | Hauptkalender-Belegtzeiten lesen | optional bewusst | AUS bis Freigabe | ja | Kalender/Erweitert; Zusatzrecht klar benennen |
| PL3 | Montag Arbeitstag | ja, falls Standard abweichend | bestehenden Wochenplan verwenden | ja | eine gemeinsame Wochenplanauswahl |
| PL4 | Dienstag Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL5 | Mittwoch Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL6 | Donnerstag Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL7 | Freitag Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL8 | Samstag Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL9 | Sonntag Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL10 | Vorschlags-Arbeitsbeginn | optional | vorhandenen validierten Wert, sonst 08:00 | nein | Wochenplan, nicht jedes Mal abfragen |
| PL11 | Vorschlags-Arbeitsende | optional | vorhandenen validierten Wert, sonst 17:00 | nein | Wochenplan |
| PL12 | Anfahrtpuffer | optional | vorhandenen Wert verwenden, keine erfundene Fahrzeit | nein | Planung/Erweitert |
| PL13 | Wetterprüfung aktivieren | ja | AUS bis echter Zugang/Lizenz bestätigt | ja | normale Einstellung „Wetterhinweise“ |
| PL14 | Gewerblicher Wetterzugang freigegeben | ausdrücklich ja | niemals automatisch bejahen | nein als täglicher Schalter | einmalige Zugangseinrichtung |
| PL15 | Wetter-API-Schlüssel | Kontoinhaber einmalig am Gerät | privat lassen, vorhandenen Schlüssel behalten | nein | geschützte Einrichtung; nie Chat/Diagnoseausgabe |
| PL16 | Wetterrisiko-Benachrichtigungen | ja | respektiert vorhandene Präferenz und OS-Recht/Ruhezeit | ja | normale Einstellung, kein technisches Wissen nötig |
| PL17 | Regen mm/h | nur fortgeschrittene fachliche Anpassung | geprüfter Ausgangswert 1, nicht Sicherheitsgarantie | nein | Wetter/Erweitert; im Alltag verbergen |
| PL18 | Regenwahrscheinlichkeit % | wie PL17 | Ausgangswert 60 | nein | Wetter/Erweitert |
| PL19 | Böen km/h | wie PL17 | Ausgangswert 40 | nein | Wetter/Erweitert |
| PL20 | Frost °C | wie PL17 | Ausgangswert 0 | nein | Wetter/Erweitert |
| PL21 | Hitze °C | wie PL17 | Ausgangswert 30 | nein | Wetter/Erweitert |
| D1 | Drive-Google-Konto | ja, einmalig | autorisierte Verbindung erhalten | nein | Sicherung/Verbindung einrichten; danach EIN/AUS der Absicht |
| D2 | Link/ID des bestehenden Drive-Ordners | einmalig fachlichen Zielordner wählen, keine ID verstehen | bestehende geprüfte Zuordnung weiterverwenden | nein | Einrichtung mit Ordnerauswahl; rohe ID danach Diagnose |
| D3 | Gmail-Versandbelege nur lesen | optional ausdrücklich | AUS bis Freigabe; kein Versandrecht implizieren | ja für aktivieren/deaktivieren | Versand/Erweitert; keine tägliche Kontowahl |
| T1 | Pixel-Tunnel-ID | Administrator bei Einrichtung | bestehende bestätigte Verbindung erhalten | nein | ausschließlich Erweitert/Verbindung |
| T2 | OpenAI-Organisation | Administrator bei Einrichtung | verifizierte Zuordnung erhalten | nein | ausschließlich Erweitert/Verbindung |
| T3 | Runtime-Schlüssel | Kontoinhaber sicher am Gerät | vorhandenen verschlüsselten Schlüssel behalten | nein | geschützte Einrichtung, nicht normale Bedienung |
| T4 | ChatGPT darf Eigentümerfunktionen lesen/schreiben | Eigentümer ausdrücklich | vorhandene bestätigte Freigabe erhalten | ja | „ChatGPT-Steuerung“; Kontofreigabe bleibt zusätzlich erforderlich |
| G1 | Eigenes Gartenpirat-Bild automatisch anzeigen | optional | vorhandene Auswahl erhalten, nicht mit Geschäftsfotos vermischen | ja | Bildoptionen; kein normaler Betriebsdialog nötig |

Weitere gespeicherte Zustände sind **keine zusätzlichen Benutzerentscheidungen**: Launcherposition, Such-/Zeitraumfilter, Formularentwürfe, stabile Request-IDs, Vergleichshashes, Vorgängerkette, Wiederholungszähler und Bestätigungsbelege. Diese gehören automatisch verwaltet. `default_activity` und `default_location` sind über vorhandene Owner-Einstellungen verfügbar und werden im Formular übernommen; kein separater sichtbarer Einstellungseditor dafür wurde gefunden. Das Inventar erfindet deshalb keine zwei weiteren UI-Schalter. Kamera, Mikrofon, Benachrichtigungen, Dateien und Akku/Hintergrundbetrieb bleiben Android-Rechte; nachgewiesene Freigabe darf nicht durch ein dekoratives App-EIN ersetzt werden.

#### 1. Gesamturteil zur Bedienbarkeit

**Für die tägliche Kernarbeit brauchbar, das geforderte Black-Box-Prinzip jedoch nur teilweise erfüllt.** Vier Hauptziele, große Start-/Pause-/Ende-Aktionen, Kundenkontext, optionale Kontaktfelder, Entwurfserhalt, Foto-Pagination und echte Schutzgrenzen sind tragfähig. Die stärkste Belastung liegt in Einrichtung/Sync, Dokumenterstellung und uneinheitlich platzierten Folgeaktionen. „Die Technik interessiert mich nicht“ funktioniert beim Timer überwiegend, bei Sicherung und Rechnungsabschluss noch nicht zuverlässig aus Sicht dieser Persona. Erfolgreiche Regressionen sind kein Beweis guter Auffindbarkeit. Die visuelle Lesbarkeit bei 320/360/426 px und vergrößerter Schrift ist in den geprüften Chromium-Fällen gut; die native Sichtprüfung bleibt durch den in Phase D beschriebenen System-UI-Fehler eingeschränkt; der frühere extrem schmale b.b.-Zeilenfehler wurde hier nicht wieder als aktueller Fehler behauptet.

#### 2. Blockierende UX-Probleme

- **U01 / S1:** Vorhandenes Foto später einer Arbeit zuordnen bzw. Beschreibung korrigieren ist im Betrachter nicht bedienbar. `update_photo` existiert, aber der sichtbare Weg endet bei Blättern/Zoom/Papierkorb. Ein erneuter Import wäre ein schlechter, duplizierender Workaround.
- **U02 / S1:** Rechnungszahlung hat einen getesteten MCP-Fachweg, aber im untersuchten Rechnungsdetail/-aktionsbereich keinen gleichwertig auffindbaren UI-Weg. b.b. darf diesen Weg nicht ersetzen.
- **U03 / S1:** Die Oberfläche liefert keinen klaren allgemeinen Sicherungs-/Wiederherstellungszugang mit letzter geprüfter Geschäftssicherung. Die sechs Katalogeinträge unter Sicherung und vorhandene Owner-Tools machen diesen Ablauf nicht automatisch für einen manuellen Benutzer auffindbar. „Drive eingerichtet“ beweist keine vollständige Sicherung.
- **U04 / S2, Einrichtung kann S1 erreichen:** Rechnungsabschluss hängt von bewusst bestätigtem Aussteller/Nummernkreis und geeignetem Drive-Zugang ab. Die Schutzbedingung ist richtig; wechselnde Texte über Nummernservice, Pixel, Rücklesen und erneutes Synchronisieren liefern keinen einheitlichen nächsten fachlichen Schritt. Ein aktueller technischer Deadlock wurde in den synthetischen Tests nicht reproduziert.

Kein nachgewiesener neuer S0. Wiederherstellbare Entfernung, unveränderliche Originalrechnungen, bewusste Bindungswahl, ausstehender Versandnachweis und versionsgebundene Löschbestätigung bleiben positiv und dürfen nicht einer kürzeren Bedienung geopfert werden.

#### 3. Top 10 unnötig komplizierte bestehende Funktionen

| Rang | Befund | Weshalb unnötig kompliziert? |
|---:|---|---|
| 1 | U05 Sync-/Speicherstatus | Nutzer muss lokal, Übergabe, Rücklesen und Cloud selbst gedanklich zusammensetzen |
| 2 | U06 Rechnungsabschluss-Einrichtung | gemeinsames fachliches Ziel verteilt auf Unternehmensdaten, Nummernkreis und technische Verbindung |
| 3 | U07 Rechnung aus mehreren offenen Arbeiten | Abrechnungsübersicht zeigt Arbeiten, aber startet keine gemeinsame Rechnung |
| 4 | U08 Angebotsformular | vier Dokumentarten, Kundentyp, Vertragsweg, Preisgrundlage und viele Folgebuttons gleichzeitig |
| 5 | U09 Angebot in Kundenakte finden | Erstellung unter Kontakt, Bestand unter Fotos & Dateien |
| 6 | U10 Planung/Wiederholung | Serie steckt gemeinsam mit Ort, Anfahrt und Pause in einem langen Zusatzbereich |
| 7 | U11 Drei Bezahl-/Abrechnungsbegriffe | b.b., App: verrechnet und Rechnungszahlung fachlich verschieden, optisch ähnlich |
| 8 | U12 Archiv/Entfernen/Deaktivieren | ein Wiederherstellungsbestand und mehrere Lebenszyklusbegriffe ohne gemeinsamen Einstieg |
| 9 | U13 Historische Konfliktbedienung | auch ohne aktive Fälle sichtbare Wartungsaufgabe im normalen Sync-Bereich |
| 10 | U14 Verstreutes Wiederfinden | Kunde, Arbeit, Foto, Rechnung und Termin benötigen unterschiedliche Bereiche/Filter |

#### 4. Top 10 zu vereinfachende Funktionen/Workflows – konkrete Vorschläge

Alle Nachher-Zahlen sind **Zielwerte**, keine gemessenen Verbesserungen. Aufwand klein/mittel/groß relativ zur bestehenden App, kein Terminversprechen. Zeitnutzen ist ohne Benutzerstudie nicht in Sekunden belegt. Nummern-/Bindungs-/Löschbestätigungen und ungeklärte Konflikte werden nicht automatisch erledigt.

| ID / Priorität | Problem / aktueller Ablauf | Vereinfachter Ablauf, nur Vorschlag | Aktionen vorher → nachher | Nutzen | Aufwand | Regressionsrisiko |
|---|---|---|---|---|---|---|
| U05 / P1 | Heute/Settings → Drive → Übertragungszustände deuten | überall ein wahrer Zustand: „Auf Gerät gespeichert“, „Mit Drive gesichert“ nur mit passendem Nachweis, sonst klare nächste Aktion; Details erweitert | ~2–4 → 0 im Normalfall, 1 bei Fehler | täglich weniger Prüfbedarf, keine falsche Sicherheit | mittel | mittel: Nachweisstufen weiter intern getrennt |
| U06 / P1 | Unternehmen prüfen → Nummernkreis prüfen → technische Drive-Verbindung → zurück → Abschluss wiederholen | einmalige geführte fachliche Einrichtung, danach „Rechnung abschließen“ mit einer Zusammenfassung | ~8–15 Einrichtung → ~4–7; normal ~2–4 → 2 | weniger Abbrüche, eindeutiger nächster Schritt | mittel | hoch: Autorität/Parallelkreis unverändert schützen |
| U07 / P1 | Offene Leistungen lesen → Rechnungen → Neu → Kunde → Leistungen → Arbeiten auswählen → übernehmen | bei Empfänger „Rechnung vorbereiten“ → vorgeschlagene offene Arbeiten/Preise prüfen → Entwurf | ~9–13 → ~4–6 | wiederkehrende Abrechnung deutlich kürzer | mittel | hoch: Empfänger, bekannte Preise, Doppelabrechnung |
| U08 / P1 | Alle Angebotsfelder und Prüfen/Finalisieren gleichzeitig | Kunde → bewusste Dokumentart → Leistungen → Prüfen; geprüfte Stammdaten nur bei Abweichung; eine nächste Hauptaktion | ~12–18 → ~8–11 für Katalogfall | weniger Such-/Scrollschritte, Rechtswahl bleibt sichtbar | mittel | hoch: Typ-/Preis-/B2C-Validierung |
| U09 / P1 | Akte → Kontakt → neues Angebot; Akte → Fotos & Dateien → vorhandenes Angebot | Akte → Dokumente mit Angebot/Kostenvoranschlag/Rechnung und „Neu“ | ~2–3 → 1–2 | tägliches Wiederfinden ohne Umweg | klein–mittel | mittel: Kontext/Rückkehr/Entwürfe |
| U10 / P2 | Mehr → Planung → Termin → verstreute Zusatzfelder/Serie | bestehender Kunden-/Auftragskontext → Datum/Dauer → optionale klar benannte Wiederholung | ~8–11 → ~5–7; Serie ~4–6 zusätzlich → ~2–3 | Termine schneller, Serien leichter auffindbar | mittel | mittel: Zeitzone, Wiederholung, Kalender |
| U11 / P1 | b.b.-Knopf, App-Verrechnet-Flag und Rechnung separat erklären | „Persönlich erledigt/bezahlt“ eindeutig von „Rechnung offen/bezahlt“ unterscheiden, bestehende Bedeutung beibehalten | ~1–2 → 1, Verständnisaufwand sinkt | weniger falsche Zahlungsannahmen | klein–mittel | hoch bei fachlicher Vermischung; keine Datenkonvertierung |
| U12 / P1 | Aktiv-Checkbox/Erledigt/Aktionen/Archiv/Papierkorb getrennt suchen | klare Datensatzaktionen „Nicht mehr aktiv“ oder „In Papierkorb“; Wiederherstellen an einem Ort | ~3–6 → ~2–3; Purge bleibt bestätigt | weniger versehentliche falsche Aktion | mittel | hoch: Beziehungen, Belegschutz, Restore |
| U13 / P2 | Drive öffnen → Alt-Konfliktfunktionen trotz 0 Vorgängen | nur „Handlung nötig“ bei tatsächlichem Fall; Historie in Erweitert | ~1–3 Prüfaktionen → 0 ohne Fall | weniger unnötige Wartungsentscheidungen | klein | mittel: aktive Konflikte dürfen nicht verschwinden |
| U14 / P2 | Zielbereich erraten → lokaler Filter → zurück → anderer Bereich | übergreifender Lesezugang zu vorhandener Suche, gruppierte Treffer; keine neue Datenhaltung | ~3–6 → ~2–3 | weniger Fehlwege bei alten Einträgen | mittel | mittel: Sichtbarkeit/Archiv/Entwurfsschutz |

Weitere konkret belegte Ergänzungen: **U01** Foto → Mehr → Bearbeiten → Beschreibung/Arbeit → speichern statt derzeit nicht ausführbar (Ziel ~4–6, P1, mittel, mittleres Relationsrisiko); **U02** Rechnung → Zahlung erfassen → bestätigen statt derzeit nicht ausführbar (Ziel ~2–3, P1, klein–mittel, hohes Risiko bei b.b.-Verwechslung); **U03** Einstellungen → Sicherung zeigt geprüften Zeitpunkt und „Wiederherstellen“ führt über bestehende Prüfung statt technischem Suchweg (Normalstatus Ziel 0–1, Restore nicht verkürzen, P1, mittel, hohes Restore-/Nachweisrisiko). **U15** bestehendes Arbeitsdetail → Bearbeiten → Zurück speichert, Auftrag/Kunde dagegen haben Verwerfen-Dialoge: konsistente sichtbare Rückmeldung/Begriffe vorschlagen, ohne das bestehende Speicherverhalten zu ändern (Schrittzahl ~2–4 → ~2–3, P1, mittel, hohes Entwurfsrisiko). **U16** gleiche große Startaktion mit zwei ähnlichen Textzeilen und abweichendem Leertexthinweis: Begriffe vereinheitlichen (0 Aktionen Unterschied, P3, klein, niedrig). **U17** Wetter-/Kalendereinrichtung unter einer Betriebsoption anbieten, technische Felder erweitert (Einrichtungszahl von Freigaben abhängig; keine pauschale Klickzahl, P2, mittel, mittleres Rechte-/Kostenrisiko).

#### 5. Funktionen, die EIN/AUS werden sollten

- **Google Drive-Verbindung/Sicherungsabsicht:** EIN/AUS nach verifizierter Einrichtung; Erfolg weiterhin separat mit verständlichem Datum/Fehler anzeigen. AUS löscht keine Daten und beendet nicht stillschweigend erforderliche lokale Sicherung. Synchronisation ersetzt keine versionierte Vollsicherung.
- **ChatGPT-Steuerung:** EIN/AUS als Betriebsfreigabe, vorhandene Owner-Autorisierung und Client-/Kontorechte behalten. Tunnel-ID/Organisation/Schlüssel kein tägliches Bedienmodell.
- **Kalenderabgleich:** EIN/AUS, bestehende Kalendertermine beim Trennen erhalten; zusätzliche Hauptkalender-Lesefreigabe getrennt optional.
- **Wetterhinweise / Benachrichtigungen:** jeweils EIN/AUS; keine automatische Lizenzzustimmung, keine versteckten Kosten und keine vorgetäuschte OS-Freigabe.
- Bereits angemessen: Seitenwischen und eigenes Gartenpirat-Bild automatisch anzeigen. Fotoqualität bleibt bewusst dreistufig; Dokumentart, Steuermodus, Nummernkreis und rechtliche Bindung sind **keine** sinnvollen EIN/AUS-Entscheidungen.

#### 6. Funktionen, die automatisch laufen sollten

Vorhandene Wiederholung/Idempotenz/Hashprüfung/Medienreihenfolge, sichere lokale Speicherung, Entwurfserhalt, Fotooptimierung, Vorschauladen und Kunden-/Auftragsübernahme ohne Konflikt automatisch weiterführen. Ohne Benutzerentscheidung veröffentlichen, was bereits fachlich bestätigt wurde; nicht allein wegen einer ausstehenden unabhängigen Medienübertragung andere Arbeitsdaten blockieren. Automatische Hintergrundwiederholung bei Netzfehlern ist bereits vorhanden, nicht als neue Funktion verkaufen. Sichtbare neue Meldung nur bei tatsächlichem Handlungsbedarf oder Speicherfehler.

Automatischer Sicherungsplan wäre sinnvoll, ist aber erst nach geprüfter Vollständigkeit, Aufbewahrung und Wiederherstellbarkeit als aktiv darstellbar. Niemals ungeklärte Konflikte, alte Löschaufträge, Preise, Rechtsart, Nummernautorität, Kalender-Verschiebung oder Versandempfänger automatisch entscheiden. Der Nutzer entscheidet den Betrieb; die App prüft und transportiert im Hintergrund.

#### 7. Aus normalem UI nach Erweitert/Diagnose verschieben

Übertragungs-/Warteschlangendetail, Provider-/Transportnachweise, technische Dateinamen, Alt-Konflikt-Wartung bei null aktiven Fällen, Konflikt-ID-/Hashhistorie, Tunnel-/Organisation-/Runtime-Einrichtung, Akku-/Hintergrunddiagnose, Drive-Ordner-ID, Wetter-API-/Schwellenkonfiguration und Rechnungsnummern-Zähler. Bei echtem Fehler darf die normale UI verständlich sagen, was zu tun ist; technische Diagnose ist zusätzlich aufklappbar.

Schon richtig gelöst und beibehalten: `#sync-technical` ist eingeklappt; `#sync-reviews` ist grundsätzlich eingeklappt; Rohdaten/Hashes sind im Papierkorb nicht Voraussetzung. Diese Bereiche nicht fälschlich als ständig offene Technikflächen zählen. Der normale Settings-Absatz und die historische Wartung außerhalb dieser Details bleiben trotzdem sichtbar.

#### 8. Doppelte oder überlappende Funktionen

Keine zweite operative Datenbank, kein zweites Projektsystem und kein zweites Angebotsmodell gefunden bzw. vorgeschlagen. Überlappungen betreffen **Zugänge und Begriffe**: Sync über Statuspille, Settings und Mehr; Angebot-Neuanlage unter Kontakt und Bestand unter Fotodokumenten; Fotos im Kundenkonto und globale Bibliothek; Rechnung aus Einzeleintrag und separater Rechnungseditor; Gartenpirat über schwebenden Knopf und Mehr; „Projekt/Auftrag“ als Doppelname. Mehrere kontextuelle Einstiege sind nützlich, wenn sie in denselben Workflow führen. Nicht einfach entfernen. b.b. und Rechnungszahlung sind absichtlich unterschiedliche Fachziele, keine Daten-Dubletten.

#### 9. Einstellungen, die entfernt werden können

Aus der täglichen Seite entfernbar: Warteschlangenzählung, Übertragungsstufe und technische Erklärung sowie technische Verbindungs-/Schwellenfelder. Aus der Anwendung fachlich **nicht** ersatzlos entfernen: geprüfte Aussteller-/Steuer-/Nummernkreisangaben, Datenschutz-/Kontofreigaben, Originalqualität-Option, Benachrichtigungspräferenz. Kein belegter Grund, die 56 Entscheidungen aus dem Datenmodell zu löschen. Gemeinsam geführte Einrichtung kann B22/B24/B25 bündeln, muss aber alle drei Schutzvoraussetzungen weiter prüfen. B13/B14 gemeinsam pflegen und Widersprüche anzeigen; freie Zahlungsbedingungen nicht kommentarlos überschreiben.

#### 10. Workflows mit zu vielen Schritten

Größte belegte Wegelängen: Mehr → Offene Leistungen → anderer Bereich → Rechnung → Arbeiten übernehmen (~9–13); globaler Termin (~8–11); Angebot mit Katalog (~12–18 einschließlich fachlicher Entscheidungen); Deaktivieren/Archivieren bei unklarer Wortwahl (~3–6). Dokumentprüfung und endgültige Bestätigung sind keine unnötigen Schritte. Timerpause/-fortsetzen sind bereits je eine Aktion und sollten keine neue Zwischenbestätigung erhalten. Kundenanlage bleibt mit ≥7 Aktionen im geprüften Name-/Adressfall vertretbar; keine zusätzlichen Pflichtfelder hinzufügen. Eine Zeitersparnis in Minuten wurde nicht gemessen.

#### 11. Top 10 Kandidaten für neue Funktionen mit belegtem Alltagsbezug

Es sind **zehn geprüfte Kandidaten**, keine künstliche Empfehlung zehn neuer Produktfunktionen. Nur fehlende Bedienwege und wiederkehrende nachgewiesene Such-/Abrechnungsarbeit begründen Erweiterungen. Häufigkeit ist eine Persona-Annahme; Zeitnutzen nur als Aktions-/Fehlerreduktion, nicht als erfundene Sekundenmessung. Bewertung N=Nutzen/H=Häufigkeit, Aufwand/UI/Wartung/Risiko niedrig/mittel/hoch.

| Rang | Kandidat / konkrete Evidenz | N / H | Zeit-/Fehlernutzen | Aufwand / zusätzliche UI / Wartung / Risiko | Empfehlung |
|---:|---|---|---|---|---|
| 1 | Vorhandenes Foto bearbeiten/Arbeit zuordnen; Aufgabe 41 ohne UI-Weg | hoch / regelmäßig | derzeit unmögliche Korrektur wird ~4–6 Aktionen, kein Reimport-Duplikat | mittel / niedrig im Mehr-Menü / niedrig / mittel | P1: bestehenden update_photo-Fachweg bedienbar machen |
| 2 | Rechnungszahlung im Detail; Aufgabe 81 ohne UI-Weg | hoch / regelmäßig | ~2–3 statt Hilfsweg; b.b.-Fehlersatz vermeiden | klein–mittel / niedrig / niedrig / hoch fachlich | P1: vorhandenen Zahlungsfachweg exponieren |
| 3 | Sicherungsübersicht/Wiederherstellungs-Einstieg; Aufgabe 106 | hoch / seltene Aktion, tägliche Sicherheit | Normalprüfung 0–1 statt Techniklesen; weniger falsche Sicherheit | mittel / niedrig / mittel / hoch | P1: vorhandene Backupdienste nutzen, keinen zweiten Speicher |
| 4 | Rechnung aus Empfängergruppe; Aufgabe 73 | hoch / regelmäßig | ~3–7 Aktionen weniger, weniger falsche Empfänger | mittel / niedrig / mittel / hoch | P1: Kontextübergabe, keine neue Rechnungslogik |
| 5 | Ein gemeinsamer Dokumente-Einstieg in Kundenakte; Aufgabe 16 | hoch / regelmäßig | ~1–2 Suchaktionen weniger | klein–mittel / sinkend / niedrig / mittel | P1: Navigation konsolidieren, keine neue Entität |
| 6 | Übergreifende Suche; Aufgaben 83–88 | mittel–hoch / regelmäßig | ~1–3 Bereichswechsel weniger | mittel / niedrig / mittel / mittel | P2: vorhandene Suchdaten nutzen; Nutzertest vor Ausbau |
| 7 | Guided Setup für Rechnungsbereitschaft; Aufgabe 78 | hoch / selten | weniger Fehlversuche; keine feste Sekundenangabe | mittel / sinkend / mittel / hoch | P1: bestehende Einrichtung bündeln |
| 8 | Hintergrund-Sicherungsplan mit verifiziertem Restore; Aufgaben 106–109 | hoch / automatisch | entlastet tägliche Kontrolle; keine falschen Erfolgsanzeigen | mittel–hoch / niedrig / hoch / hoch | P2 nach eigenem Nachweis; nicht blind „Backup EIN“ ergänzen |
| 9 | Natürlichsprachlicher Sammelassistent für jeden Screen | Nutzen nicht zusätzlich belegt; MCP/Sprachfelder existieren | derzeit keine gemessene Mehrersparnis | hoch / hoch / hoch / hoch | NICHT neu ergänzen; vorhandene Wege konsistent halten |
| 10 | Automatische Wetter-Umbuchung mit Kundenversand | bisher nur Wetterhinweisbedarf belegt | vermeintliche Einsparung, hohes Fehlplanungsrisiko | hoch / mittel / hoch / hoch | NICHT ergänzen; Vorschlag und bewusste Terminentscheidung behalten |

Ränge 4/5/7 sind bewusst Verbesserungen vorhandener Workflows, keine neuen Subsysteme. Mehr neue Funktionen als die belegten Lücken wären diesem Auftrag und der Persona abträglich.

#### 12. Ausdrücklich nicht ergänzen

Kein Technikdashboard im Heute-Bildschirm, kein zweiter Sync-Modus für alltägliche Wahl, kein zweiter Backup-/Fotospeicher, keine automatische Alt-Konflikt- oder Löschbereinigung, kein automatischer Versand allein durch „Teilen“, keine automatische Rechnungsnummer oder Steuerannahme, kein neuer b.b.-Abrechnungsstatus, keine automatische Wetterabsage/-umbuchung, kein weiterer schwebender Betriebsassistent und kein zusätzlicher Haupttab pro Modul. Kein Build, keine neue APK und keine Änderung von MCP-Verträgen für diesen Analyseauftrag.

#### 13. Vorgeschlagene ideale Hauptnavigation

Die bestehenden vier Ziele **Heute – Kunden – Arbeiten – Mehr** behalten. Unter Mehr fachlich gruppiert: **Planung**, **Dokumente** (Angebote/Kostenvoranschläge/Rechnungen/offene Leistungen), **Aufträge**, **Fotos & Pflanzen**, **Einstellungen**. Das ist ein Navigationsvorschlag, keine Zusammenlegung von Datenmodellen. Papierkorb über Datenverwaltung und kontextuelle Rückgängig-Aktion erreichbar halten. Diagnose nur unter Einstellungen/Erweitert. Kontextuelle Direktwege aus Kunde, Auftrag und Arbeit bleiben erhalten.

#### 14. Vorgeschlagener idealer Heute-Bildschirm

Oben laufende Arbeit mit Kunde, Dauer und genau den passenden Aktionen Pause/Fortsetzen/Beenden; ohne Sitzung eine dominante Aktion **Arbeit starten**. Danach nächster Termin, heutige Arbeiten/Summe, **Arbeit nachtragen**. Kleiner wahrer Speicherhinweis **Auf diesem Gerät gespeichert**. Nur bei nötiger Handlung eine verständliche Problemmeldung; keine Queue-/Providerzählung. Die bestehende Startansicht erfüllt einen großen Teil bereits. Gartenpirat bleibt erreichbar und darf Timeraktionen nicht überdecken; vorhandene native UI-Prüfung behalten.

#### 15. Vorgeschlagene ideale Kundenakte

Kopf: Name/Adresse, Kontakt, **Arbeit starten/erfassen**, **Termin**, optional Menü. Danach **Übersicht – Arbeiten – Fotos – Dokumente** als verständliche Ziele; Garteninformationen auf Übersicht/Details, ohne sie als neue technische Entität zu behandeln. Übersicht: letzter Einsatz, offene Aufträge, Gartenfläche, nächste Termine. Dokumente enthalten Angebote und Rechnungen, Fotoansicht ausschließlich Bilder/zugehörige Dateien. Kontakt/Neues Angebot nicht unter demselben unbestimmten Sammelpunkt verstecken. Kunde und Einsatzort bleiben auch bei abweichendem Rechnungsempfänger eindeutig.

#### 16. Vorgeschlagener idealer Arbeitszeit-Workflow

**Start → Kunde bestätigen (Auftrag optional) → Pause/Fortsetzen → Ende → Tätigkeit prüfen → Speichern.** Zeit/Netto-Pause automatisch, Material/Kilometer/Fotos/Notiz als optionale direkte Ergänzungen. Kein Zwang zu GPS oder vorhandenem Auftrag. „Später ergänzen“ bewahrt den offenen Abschluss, benennt ihn aber nicht als fertige Arbeitsaufzeichnung. Korrektur aus Arbeitsdetail in genau einen Editor mit eindeutiger Speicher-/Abbrechen-Rückmeldung. b.b. bleibt persönliche Markierung; „App: verrechnet“ bleibt kein Beweis eines ausgestellten Dokuments. Bestehende Versionsprüfung im Hintergrund.

#### 17. Vorgeschlagener idealer Dokument-Workflow

**Kunde/Kontext → bewusst Dokumentart wählen → bekannte Leistungen/Preise übernehmen → fehlende Angaben ergänzen → Vorschau/Prüfung → ausdrücklich abschließen → optional Teilen/Versenden → bestätigten Status sehen.** Unterschiedliche Fachlogiken nicht vermischen: Angebot/Kostenvoranschlag haben bewusste Bindungsart; Rechnung hat zentrale Nummernautorität und unveränderliches Original. B2B/B2C und Vertragsweg nur bei fehlender/abweichender Datenlage nachfragen, keine stillen Rechtsannahmen. Ausgabe „Zum Teilen bereit“ ist nicht „Gesendet“; „Ausgestellt“ ist nicht „Bezahlt“. Nach Abschluss Korrektur-/Stornoweg statt freier Bearbeitung. Material-/Stunden-/Preisprüfung bleibt erforderlich.

#### 18. Technische Interna: vollständiges gefundenes Expositionsinventar

Die folgende Liste umfasst die bei dieser Prüfung gefundenen UI-Stellen und Fehlerrückgabefamilien; eine Garantie über jede zukünftig vom Provider gelieferte Fehlermeldung wäre unredlich. Nichts enthält echte Kontowerte, IDs, Pfade oder Hashes aus Betriebsdaten.

| Stelle / Quelle | Sichtbarkeit heute | Konkrete technische Information | Bewertung |
|---|---|---|---|
| Heute-Status / mobile.html + daily-usability.js | normal | erster Drive-Abgleich, Übertragungs-/Prüfzustand | besser fachlicher Speicherstatus/Handlungsbedarf |
| Einstellungen / daily-usability.js:15–18 | normal | Warteschlange, Übertragungsstand, Übergabe an Drive-App, Rücklesen vs Online-Upload | deutlich zu viel Erklärung im Alltag |
| Einstellungen Fotos / photo-settings.js:8–9 | normal | lokale Bilddateien, MB/GB frei, Drive noch nicht vollständig bestätigt | Kapazität bei Bedarf sinnvoll; Transportbegründung erweitert |
| Sync-Grundtext / mobile.html | normal | HausGarten-ChatGPT.json, Übernahmeweg, „kein dauerhafter Fernzugriff“ | historischer Kanaltext passt nicht als alleinige Beschreibung des heutigen Owner-Zugangs |
| Pixel-Verbindungskarte / pixel-tunnel-ui.js:8–12 | normal auf Sync-Seite | Pixel, letzter gespeicherter Drive-Stand, eingeschränkte Liste alter Schreibfunktionen | unvollständige Erklärung gegenüber aktuellen 117 Tools |
| Sync-Liste / sync-dashboard.js:9–17 | normal beim Öffnen der Seite | Übertragungen/Prüfungen, Cloud-Abschluss offen, nächste Abgleichphase/Serverprüfung | nur offene Handlungsfälle normal zeigen |
| Sync-Verbindungsdetails / sync-dashboard.js:17–20 | ausdrücklich aufgeklappt | Android-Speicheranbieter/direct_drive, Fehlercode, Phasen, Retry-Anzahl | angemessener Diagnoseort; nicht als dauernd offen gewertet |
| Sync-Fehler / mobile.html:191 + sync-dashboard.js | Fehler, normales UI | ungefiltertes result.error/last_error kann technische Codes/Providertext enthalten | verständliche Primärmeldung fehlt für alle Fehlerfamilien |
| Historische Prüfungen / legacy-conflicts-ui.js:5 | normal, auch bei 0 Fällen | unverifizierbare Alt-Konflikte/Wartungsfunktion | unnötige ständige Wartungssicht |
| Historienliste / legacy-conflicts-ui.js:5 | nach „Konflikthistorie anzeigen“ | client_id, keep_current, reason, action_hash als Rohtext | nach bewusster Aktion, aber noch kein klarer Diagnosebereich |
| Alt-Konflikt-Detail / legacy-conflicts-ui.js:8 | aufgeklappte Details nach Vorschau | geprüfte IDs und Action-/Record-Hashes | Diagnose nützlich, für Bestätigung nicht nötig |
| Alt-Konflikt-Bestätigung / legacy-conflicts-ui.js:9 | kritische Bestätigung | Warteschlange, Drive-Snapshot, Speicheranbieter-Readback, Cloud-Bestätigung | Datenbestand/Effekt/Sicherungsstatus verständlich zusammenfassen |
| Konfliktvergleich / sync-dashboard.js:24–29 | echter Entscheidungsfall | unbekannte Feldnamen fallen auf Rohschlüssel zurück; Beziehungen können IDs, Objekte JSON zeigen | normale Fachwerte statt Rohobjekte; beide Varianten erhalten |
| ChatGPT-Einrichtung / PixelTunnelActivity.java:40–60 | native Einrichtung | Tunnel-ID, Organisation, Runtime-Schlüssel, Akku-Ausnahme, Hintergrunddaten, Tunnelverwaltung | sicher geschützte Experteneinrichtung, kein normales Betriebsmodell |
| Drive-Einrichtung / DriveConnectionActivity.java:17–38 | native Einrichtung/Fehler | gespeicherte Ordner-ID im Linkfeld, Konto-/App-/Ordnerrechte, Providerfehlermeldung | Konto/Ordner fachlich wählen; Details erweitert |
| Planung / planning-ui.js:43 | normales Terminformular | Europe/Vienna, optionale Koordinaten/UTC-Offset in Zusatzgruppe | Ortszeit genügt täglich; DST-Ausnahme nur bei echter Mehrdeutigkeit |
| Planungs-Zugang / PlanningConnectionActivity.java:20–46 | native Einrichtung | API-Schlüssel, Nutzungsfreigabe, 5 Schwellen, OAuth-/Calendar-API-/Organisationsfehler | Betriebsoptionen normal; Zugang/Diagnose getrennt |
| Unternehmenseditor / owner-ui.js:22–32 | normales langes Editorformular | Nummernkreiszähler, Pixel als Nummernautorität, direkte Drive-Verbindung; Fehler „Vergleichsstand“ | fachliche Einrichtung bündeln; Versionstechnik aus Text entfernen |
| Rechnungsabschluss / invoice-draft.js:98–101 | normale Bestätigung/Fehler | Nummernservice, Pixel-/Drive-Abgleich, nach Service erneut synchronisieren, sevDesk-Parallelkreis | zentrale Schutzregel bewahren, nächste Aktion fachlich erklären |
| Rechnung fehlt lokal / invoice-draft.js:66–67 | Daten-/Dateifehler | Original-PDF nicht lokal, Datenstand abgleichen | kein Fake-PDF ist richtig; „Original wird geladen/derzeit nicht verfügbar“ verständlicher |
| Angebotsstatus / offers-ui.js:30,43 | normal nach Speichern/Abschluss | privater Dokumentspeicher, Drive-Abgleich folgt, keine Rechnungsnummer vergeben | „Gespeichert/Abgeschlossen“ genügt; kein Versand vorgeben |
| Foto-Ladestatus / design-concept.js:129–147 | Foto öffnen/Fehler | Original nach Drive-Abgleich, ggf. Dateiname statt Beschreibung | fachlicher Lade-/Fehlertext, kein Dateipfad nötig |
| Foto-Fehlerwiederholung / mobile.html:178–187 | echter Speicherfehler/Neustart | Wiederholauftrag, Datenstand, dauerhafte Wiederherstellung nicht verfügbar | Handlung erforderlich; Arbeit und Foto getrennt verständlich benennen |
| Papierkorb / trash-ui.js:14,24,58 | Bestätigung bzw. erklärende Details | Dateibereinigung nach geprüftem Abgleich, historische Sicherungen separat | sachlich notwendige Reichweite, keine Hash-/CAS-Bedienung; Text kürzen möglich |
| Owner-Client-Fehler / owner-client.js und Konsumenten | Fehler | serverseitige Fehlermeldungen werden in Status/Toast durchgereicht | gemeinsame fachliche Fehlerübersetzung als spätere Empfehlung |
| Versions-/Buildinfo / daily-usability.js | ausdrücklich Settings | Version/Build/Fingerprint | sinnvoll in Info/Diagnose, keine Betriebsentscheidung |

Die Herkunftsnachweise bei Pflanzen/Neophyten und Hinweise zur bewussten Dokumentbindung sind fachliche Informationen, keine sinnlos sichtbare Technik. Fehlende Kundenzusage, ungeklärte Preise oder fehlende Rechte dürfen verständlich sichtbar bleiben. Ein echtes Problem darf nicht zugunsten einer grünen EIN-Pille versteckt werden.

#### 19. Welche Informationen vollständig verborgen werden können

Im normalen Betrieb vollständig verborgen: Request-IDs, Record-/Action-/Preview-Hashes, CAS, interne Queue-/Journal-/Snapshot-Bezeichnungen, JSON-Dateiname, Transportklasse, Provider-Readback als Fachbegriff, interne Phasen und Wiederholungszähler, rohe Feldschlüssel/UUIDs und Dateipfade. Die internen Nachweise bleiben unverändert gespeichert und diagnostizierbar.

Ersetzen durch Antworten auf vier Fragen: **Ist gespeichert? Ist Sicherung/Verbindung eingerichtet? Ist der Vorgang erledigt? Muss ich etwas tun?** Beispiel für Netzfehler: **„Synchronisierung derzeit nicht möglich. Deine Daten sind auf diesem Gerät gespeichert. Wir versuchen es später erneut.“** Nur sagen, wenn die lokale Speicherung tatsächlich bestätigt ist und Wiederholung tatsächlich geplant ist. Bei vollem Speicher stattdessen **„Nicht gespeichert. Eingaben bleiben hier erhalten. Bitte Speicher freigeben und erneut speichern.“** Bei Kontoentzug **„Google Drive braucht erneut deine Freigabe. Auf diesem Gerät gespeicherte Daten bleiben erhalten. Verbindung prüfen.“** Kein automatischer Retry gegen dauerhaftes 401, keine Umgehung von Rechten.

Sichtbar bleiben müssen: fehlende echte Sicherung, nicht gespeicherte Fotos, erforderliche Kontofreigabe, aktueller Konflikt mit fachlichen Alternativen, bewusste Dokumentart/Bindung, fällige Rechnung/fehlende Nummernfreigabe, endgültiger Löschumfang, tatsächlicher Versand-/Zahlungsstatus. Sicher automatisch lösbare Fälle brauchen keine Rückfrage; konkurrierende fachliche Varianten bleiben ausdrücklich Entscheidungssache.

#### 20. Priorisierung und Abnahmeentscheidung

| Priorität | Befunde / vorgeschlagene nächste Arbeit | Abnahmekriterium für einen späteren, separat autorisierten UX-Auftrag |
|---|---|---|
| P0 UX-Sicherheitsprüfung | U15 uneinheitliches Zurück/Speichern; U11 b.b./Rechnung nicht verwechseln; U03 keine falsche Sicherungsbestätigung; U06 Nummernautorität erhalten | Keine unbemerkte Fachänderung, kein falsches Bezahlt-/Gesichert-/Gesendet; Charakterisierungstests bleiben PASS. Kein neuer S0 wurde nachgewiesen. |
| P1 hoher Alltagsnutzen | U01 Fotoänderung, U02 Zahlung, U03 Sicherungszugang, U05 verständlicher Speicher-/Syncstatus, U06 Rechnungsbereitschaft, U07 offene Leistungen → Entwurf, U08 Dokumentführung, U09 Kunden-Dokumente, U12 Löschbegriffe | Ziele ohne Technikvorwissen ausführbar; Kontext korrekt; keine zusätzlichen Pflichtdaten; Schutzparameter und Originale erhalten |
| P2 sinnvoll | U10 Wiederholung auffindbar, U13 konfliktfreie Oberfläche ruhig, U14 Suche, U17 Kalender/Wetter-Ersteinrichtung; verifizierbare automatische Sicherung konzipieren | weniger Navigation, kein zweites Subsystem; Rechte/Kosten/Zusagen weiter explizit |
| P3 optional | U16 konsistente Kurztexte, Speicher-MB-Rundung, Feinordnung der Mehr-Gruppen | messbar klarer, ohne neue UI-Komplexität oder Regressionsrisiko |

**Dieser Auftrag endet mit dieser Analyse.** Es wurden weder die genannten Bedienlücken noch UX-Reibungen behoben. Die bestehenden fachlichen Regeln, Datenmodelle, App-ID, Signatur, 117 MCP-Verträge/Protokoll 9 und 156 UI-Katalogaktionen bleiben unverändert. Der nächste Schritt wäre eine ausdrücklich beauftragte Prioritätsauswahl oder ein darauf begrenzter UX-Umsetzungsauftrag, keine automatische Fortsetzung in Produktcode.

### Phase D – abschließende Nachweise, Abweichungen und Grenzen

**Ausgeführte Funktionsprüfungen dieses Auftrags:**

| Prüfung | Tatsächliches Ergebnis / Grenze |
|---|---|
| Python-Qualitätsrunner | 322 PASS, 0 Skips; 37 historische QA-Signing-/Parallelpaket-Prüfungen durch den vorhandenen expliziten Schalter ausgeschlossen, nicht geändert oder als PASS gezählt |
| JVM-Runner | 27 Suiten PASS; darin UI-/MCP-Parität 1.036 Prüfungen und eingefrorener P1-Vergleich |
| Browser-/JS-Runner | alle 30 Runner-Einträge Exit 0; enthält Chromium- und reine JS-Prüfungen, deshalb nicht „30 native E2Es“ |
| Zusätzlicher kognitiver Chromium-Durchlauf | 6/6 beschriebene Pfade abgeschlossen; operative Assets und bestehende synthetische Bridges, keine neuen Geschäftsregeln im Test simuliert |
| MCP-stdio E2E | 55/55 synthetische Szenarien PASS; eingeschränkter vorhandener Kanaladapter, kein realer ChatGPT-Pixel-Connector |
| Cloud-Android-Instrumentierung acceptance | **135 PASS; acceptance failures=0**, eigener QA-Namensraum; SQLite, Timer, Versionen, Rechte, Papierkorb/136 Einträge, Fotooptimierung/-rotation, Medienretry/Readback, PDF, vier Dokumentarten, Kalender/Wetter/Testprovider, sieben synthetische Legacy-Fälle |
| Cloud-Android fresh_isolated | PASS: leerer isolierter Erststart, keine Credentials und kein automatischer Drive-/Tunnelzugriff; bestehender AVD-Bestand nicht gelöscht |
| Cloud-Android ui | Assertion-Runner PASS: echter WebView, vier Tabs, Mehr-Gruppen, Auswahl/Abbrechen, Garten, Papierkorb, Planungsentwurf, aktiver Timer und verdeckter Launcher |
| Native Screenshot-Sichtprüfung | **EINGESCHRÄNKT / weitere Prüfung nötig:** Screenshot enthält Systemdialog „System UI isn't responding“. Events-Log belegt ANR von `com.android.systemui/.keyguard.KeyguardService`. Programmgesteuerte WebView-Assertions erkennen diese OS-Überlagerung nicht. Deshalb keine uneingeschränkte manuelle native UI-Abnahme behaupten. |
| Zusätzlicher historischer release_lifecycle verify | **FAIL:** `AssertionError`, nur Sammelstelle ReleaseLifecycleProbe.require:33 gemeldet. Keine Neuinstallation/kein Update dieses Auftrags. Der bestehende alte Verifier schließt technische capabilities und weitere vorbereitete Präferenzen ein; die konkret gescheiterte Bedingung ist mit dieser Ausgabe nicht lokalisiert. Keine Baseline neu geschrieben, kein Test abgeschwächt, keine Ursache als bewiesen ausgegeben. |
| Lesender Abschlussvergleich des bestehenden synthetischen AVD-Bestands | PASS: vollständiger Geschäftsmaster ohne technische capabilities **einschließlich ganzer Pending-Queue** weiterhin SHA-256 `be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af`, identisch zum dokumentierten P1-Ledger. Nur im RAM über vorhandene Business-Capture-Instrumentierung verarbeitet; keine Rohzeilen/Base64 gespeichert oder ausgegeben. 3 QA-Kunden, 1 Auftrag, 2 Arbeitsaufzeichnungen, 1 Pending-Aktion, 32 öffentliche Katalogbausteine, 0 Fotos/Rechnungen/Angebote/Termine. IDs der Geschäftsentitäten ausschließlich synthetisch. Der fehlgeschlagene alte Verifier ist damit kein nachgewiesener Geschäftsdatenschaden, bleibt aber als fehlgeschlagene Prüfung offen. |
| P1-Vertragsvergleich | PASS, `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`, 117 Tools = 52 Lesen/65 Schreiben, Protokoll 9, 156 semantische UI-Aktionen; keine Baselineaktualisierung |

**UAT-Zusammenfassung:** 115/115 Aufgaben bewertet; 69 PASS + 2 PASS auf ausdrücklich begrenzter synthetischer Sync-Teilstrecke, 40 PASS MIT REIBUNG, 3 FAIL des manuellen Benutzerziels (Foto nachträglich zuordnen, Rechnungszahlung manuell, Sicherungsstatus/-zugang) und 1 ausdrücklich NOT TESTABLE für echten Kamera-/Dateirechteentzug. Weitere externe Teilgrenzen stehen in den einzelnen Zeilen; diese Zahlen sind keine 114/115-Geräteabnahme. 56 Einstellungsentscheidungen einzeln inventarisiert, alle 20 verlangten Berichtsbereiche enthalten. Zählspannen/Verbesserungsziele nicht als empirisch gemessene Klickzeiten ausgeben.

**Privacy und Unverändertheit:** Source PASS (1.148 Dateien, 0 operative Seedzeilen), Workspace PASS (3.139 Dateien einschließlich ignorierter/untracked Inhalte), vorhandenes Original-81-Artefakt PASS (0 operative Seedzeilen), vor den Tests sowie nach den Tests geprüft. APK weiterhin bytegleich SHA-256 `7e569519f8f3328335499a84181f6eb6746c860e4325e80d67f0f44ba59e865e`. Keine neue APK, keine Signierung, keine Installation. Auf main d8960c46d42b5f6c99d8b6c2f3217686a6e4cf13 ausschließlich diesen neuen UAT-Abschnitt in CODEX-STATUS.md ergänzt; kein Produkt-/Test-/Architekturcode geändert, bestehende Statusabschnitte erhalten. Keine produktiven Daten, realen Drive-Dateien, historischen Originalkonflikte oder Konten gelesen/geschrieben. Standardisierte synthetische QA-Ausgaben des vorhandenen Runners bleiben in den vorhandenen Outputpfaden; keine zusätzliche Projektdokumentation erzeugt.

**Offen:** physischer Pixel und aktueller ChatGPT-Connector vollständig unberührt; echte Kamera/Dateifreigaben, Google-Konto/OAuth, Kalender-/Wetteranbieter, Drive-Cloudrundlauf, echte Zustellung und Akku-/Langzeitbetrieb nicht verifiziert. Der Cloud-Emulator läuft ohne KVM; ANR/Interaktionslatenz sind nicht auf Pixel-Alltagsleistung übertragbar. Native Sichtprüfung nach System-UI-Stabilisierung sowie genaue Diagnose des historischen Verifier-FAIL bleiben erforderlich. Keine Umgehung von Rechten, kein Reset/AVD-Wipe, keine produktive Reparatur. Die empfohlenen UX-Änderungen bleiben vollständig unimplementiert.


## P3 – Black-Box-UX und Workflow-Vereinfachung angelegt (20.10.2026)

- Grundlage: vollständiger Codex-UAT mit 115/115 bewerteten Aufgaben sowie unabhängiger Gemini-Produktbericht. Der UAT bestätigte 3 konkrete FAILs: U01 Foto nachträglich bearbeiten/zuordnen, U02 Rechnungszahlung im UI, U03 verständlicher Sicherungs-/Wiederherstellungszugang. Zusätzlich 40 PASS MIT REIBUNG.
- Neuer verbindlicher P3-Block in CODEX-AUFTRAG-GARTENPIRAT.md. Produktprämisse: normaler Benutzer bedient den Betrieb, nicht die Technik; technische Interna nur unter Erweitert/Diagnose. Normale UI beantwortet: gespeichert? Sicherung/Verbindung eingerichtet? Vorgang erledigt? Handlung nötig?
- P3 umfasst sieben begrenzte Pakete: Black-Box-Status, Heute/Arbeitsworkflow, Kundenakte, drei UAT-FAILs, Dokumente/Abrechnung, Begriffe/Lebenszyklus sowie Suche/Wiederholung/Einstellungen.
- Geminis Drei-Tab-Vorschlag wird nicht blind übernommen. Für die erste P3-Umsetzung bleibt der im UAT empfohlene Hauptzugang Heute – Kunden – Arbeiten – Mehr, bis ein erneuter UAT einen belastbaren Vorteil der Entfernung von Arbeiten zeigt.
- Bestehende Terminserien, Foto-Rollen, GPS, Wetter, Kalender und Spracheingabe gelten nicht als neue Funktionen; P3 verbessert primär Auffindbarkeit und Bedienung. Arbeitsvorlagen, Pflege-Turnusse, Material-Schnellwahl, digitale Unterschrift, Umsatzmonitor, Tourenoptimierung und neue Karten/KAGIS-Funktionen sind nicht Teil der ersten P3-Umsetzung.
- Außenverträge bleiben geschützt: Paket-ID, Originalsignatur, DB/Snapshot/Drive/Journal, IDs/Relationen, MCP9/117 Tools, P1-Vertragsbaseline und P0-Privacy-Gates.
- P3 ist mit diesem Eintrag **nur definiert, noch nicht implementiert**. Keine App-/Android-/JS-/MCP-/DB-/Drive-Funktion wurde durch das Anlegen des P3-Plans geändert.
- Ziel nach vollständiger Abnahme bei unverändertem Versionsverlauf: 1.52.0 / Build 82, originalsignierter Kandidat erst nach kompletter Regression, erneutem 115-Aufgaben-UAT und synthetischem In-place-Update 81→82. Physische Pixelinstallation bleibt separat freigabepflichtig.


## P3-R0 – Baseline und Charakterisierung (20.10.2026)

- Aktueller `main` vor P3-Implementierung: `d1c57034f7c1e74152f96d62a3cecaaaaccf511e`, sauber per Fast-forward übernommen. Operativer Quellstand unverändert 1.51.2 / 81, Paket `at.hausgarten.service`, MCP9 / 117 Werkzeuge, 156 semantische UI-Aktionen. P0/P1 bleiben maßgeblich; P2-Restgrenzen werden nicht als erledigt umgedeutet.
- Unveränderliche synthetische Vorher-Fixture `tests/fixtures/p3-ux-baseline.json`: alle 115 bisherigen UAT-Aufgaben, U01–U17 und Quellhashes vor der ersten Produktänderung. Drei neue Charakterisierungsprüfungen sichern die vollständige Aufgabenmenge, die ursprünglichen FAILs 41/81/106, die Herkunft der Quellhashes und die unveränderte P1-Fixture. Keine Betriebsdatensätze enthalten.
- Neu ausgeführt: Python-Qualitätssuite 322 PASS (bestehende QA-Schlüsseltests ausdrücklich ausgeschlossen), 27 JVM-Suiten PASS, 30 Browser-/JavaScript-Testläufe PASS, 3 P3-Baselineprüfungen PASS. UI-MCP-Inventarprüfung PASS. P1-Vertragsvergleich unverändert PASS: `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`.
- Source-, Workspace- (einschließlich ignoriert/untracked) und Artifact-Privacy-Gate PASS. Geprüftes bestehendes Originalartefakt 81: SHA-256 `7e569519f8f3328335499a84181f6eb6746c860e4325e80d67f0f44ba59e865e`, leerer operativer Bootstrap. Kein neuer Kandidat erstellt.
- Abnahmeziele der folgenden Phasen: verständliche belegbare Statusaussagen, technische Details nur explizit erweitert; drei UAT-FAILs über bestehende fachliche Funktionen schließen; alle 156 Aktionen und Außenverträge erhalten; schmale Ansichten, Entwürfe und Fehlerpfade prüfen.
- Offen unverändert: früherer synthetischer Emulator-Lifecycle-Nachweis mit nicht lokalisiertem Verify-FAIL sowie SystemUI-ANR bei visueller Abnahme; erneute native Abnahme erforderlich. Kein physischer Pixel-, Konto-, Drive- oder Versandtest durchgeführt. Die historische UAT-Bewertung ist keine erneute P3-Abnahme.


## P3-R1 – Verständlicher Betriebsstatus und ausdrücklich geöffnete Diagnose (20.10.2026)

- Kleine gemeinsame Präsentationsschicht `business-feedback.js` übersetzt Speicher-/Versions-/Freigabe-/Netzfehler und Sync-Nachweise. Keine Änderung an MCP-Antworten, fachlichen Validatoren oder Datenformaten. Rohfehler bleiben begrenzt im Sitzungsspeicher für ausdrücklich geöffnete Diagnose erhalten; keine Fehlerlogs mit Betriebsdaten angelegt.
- Normale Statusanzeige trennt lokale Speicherung, geprüfte Übertragung und bestätigten Online-Abgleich. Keine automatische Vollsicherungs-/Cloud-Behauptung. Ausstehende Benutzerentscheidung bleibt sichtbar; fehlende Freigabe erhält einen verständlichen nächsten Schritt. Warteschlangen-, Provider-, Retry- und Hashdetails sind nur erweitert sichtbar. Historische Wartung ist ohne aktive Fälle eingeklappt.
- Bestehende native Verbindungen erhalten verständliche EIN/AUS-Betriebsoptionen; Konto-/Schlüssel-/Schwellen-/Akku-Einrichtung bleibt unter Erweitert / Diagnose. Drive-AUS pausiert ausschließlich die Automatik, erhält alle Daten und Konto-/Ordnerfreigaben; ausdrücklich angeforderter manueller Abgleich bleibt möglich. Standardverhalten bestehender Installationen bleibt EIN. ChatGPT verwendet unverändert den vorhandenen Vertrauensanker/Schreibschalter; kein stilles Hochstufen von Rechten. Kalender/Wetter/Benachrichtigungen verwenden bestehende versionsgeschützte Planungseinstellungen.
- Nachgewiesen: 325 Python-Prüfungen PASS, 27 JVM-Suiten PASS, 55 isolierte MCP-stdio-Szenarien PASS; Android-Release-Java-Kompilierung PASS. Browserregressionen einschließlich gezielter Wiederholungen für absichtlich geänderte Statustexte PASS; neue Präsentationstests schützen fehlende Freigaben, Versionskonflikte und fehlende Online-Bestätigung. Diagnose muss ausdrücklich geöffnet werden und bleibt lesbar. Vorhandene Sicherheits-/Entwurfsprüfungen nicht entfernt.
- P1-Vertragsbaseline weiterhin exakt PASS `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`; MCP9/117 und 156 Aktionen unverändert. UI-Quellreview-Fingerprints nach Prüfung aktualisiert, ursprüngliche P1-/P3-Vorher-Fixtures nicht verändert. Source-, Workspace- und bestehendes Artifact-Privacy-Gate PASS.
- Native Isolationsprüfung für Automatik-AUS/manuelle Anforderung ergänzt; Ausführung des neuen Android-Codes auf originalsigniertem Kandidaten folgt in R7. Kompilierung ist kein Funktionsnachweis am physischen Pixel. Für den stdio-Lauf musste die bereits vorhandene Cloud-venv verwendet werden (Host-MCP-Paket bietet den benötigten Import nicht); kein Produktcode-Fallback eingeführt. P2-Restgrenzen bleiben offen.


## P3-R2 – Drei bestätigte UAT-FAILs: Bedienpfade ergänzt (20.10.2026)

- U01: Foto → Mehr → Bearbeiten öffnet Beschreibung, Rolle und passende Auftrags-/Arbeitszuordnung desselben Kunden. Bestehendes `get_photo`/`update_photo`, Öffnungs-Hash, gezielte Änderungen und bei unverändertem Inhalt stabile Request-ID; kein Dateiimport und keine neue Foto-ID. Unbekannte historische Zuordnungen werden erhalten. Entwürfe überleben Fehler; ein ausdrücklich bestätigter Vergleich mit dem aktuellen Stand übernimmt nur bewusst geänderte Entwurfsfelder. Keine stille Aktualisierung der Vergleichsbasis. Überlagerung der beiden Foto-Menüaktionen im realen Browsertest gefunden und korrigiert.
- U02: Ausgestellte gültige Rechnung → Zahlung erfassen/korrigieren → ausdrückliche Bestätigung. Vorhandenes `update_invoice_payment` mit `entity_hash`, keine Änderung an b.b., Arbeitsstatus, Rechnungstext oder Original-PDF. Bestehenden Invoice-Read über die lokale Bridge zugänglich gemacht; keine neue öffentliche JavaScript-Methode und kein neues MCP-Werkzeug.
- U03: Einstellungen → Sicherung zeigt nur tatsächlich inspizierte, gegen Dateigröße/SHA geprüfte Geschäftssicherungen, Erstellungszeit und ausdrücklich lokalen Speicherort. Jetzt sichern benutzt vorhandene idempotente Sicherung; Wiederherstellen wählt Sicherung, inspiziert sie und bindet die explizite Bestätigung an den aktuellen State-Hash. Beschädigte/unlesbare Altsicherungen verhindern keinen neuen Sicherungsversuch; bestehende Restore-Schutzregeln unverändert. Keine automatische Vollgeräte-/Cloud-Sicherungsbehauptung. P2-Altsicherungsgrenzen bleiben erhalten.
- Abnahme dieser UI-Pfade: gezielter Chromium-Test PASS für Foto-CAS/Retry, unveränderte Datei, korrekte Relationsauswahl, expliziten Versionsvergleich, Rechnungsbestätigung/Hash/b.b.-Trennung, Sicherungsprüfung, Restore-Abbruch/Bestätigung sowie beschädigte Sicherung; 320/360/426 px und vergrößerte Schrift geprüft. Gesamter Browser-/JS-Runner: 32/32 PASS; 27 JVM-Suiten PASS, 55 synthetische MCP-stdio-Szenarien PASS und 3 unveränderte P3-Baselineprüfungen PASS. Release-Java und androidTest-Java kompilieren erfolgreich.
- Source-/Workspace-/bestehendes Artifact-Privacy-Gate PASS. P1-Vertrag weiterhin exakt `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`, 117 Tools / 156 semantische UI-Aktionen. Keine Geschäftsregel, DB-Struktur oder externen Daten geändert. Neue native Bridge-/Zahlungsprüfung kompiliert; tatsächliche Ausführung mit dem späteren Originalkandidaten in R7 noch offen. Browser-Doubles sind kein physischer Foto-/Pixel-/Drive-Nachweis und keine vollständige erneute 115-Aufgaben-Abnahme.


## P3-R3 – Heute und Arbeitsworkflow (20.10.2026)

- Heute zeigt die laufende Sitzung vor Kennzahlen, mit Kundenname, Netto-Laufzeit und passenden Pause/Fortsetzen/Beenden-Aktionen. Anschließend nächster Einsatz, Tagesübersicht und Arbeit nachtragen. Die vier Hauptziele Heute/Kunden/Arbeiten/Mehr bleiben erhalten. Leere Texte und Hauptaktion heißen konsistent Arbeit starten; Nachtragen bleibt als separate fachliche Alternative erreichbar.
- Arbeitsstart fragt zuerst den Kunden. Auftrag/Arbeitsort sind optional; ein Standortvorschlag wird ausschließlich auf ausdrücklichen Wunsch angefordert. Keine zusätzliche Berechtigung oder GPS-Pflicht; bestehende präzise/ungenaue/abgelehnte Standortprüfungen und Schutz gegen falsche Kundenzuordnung erhalten. Der Start überträgt die gewählte stabile Auftrags-ID über dieselbe vorhandene Timerfunktion.
- Sekündliche Laufzeitanzeige ersetzt nicht mehr die Aktionsschaltflächen und erhält damit den Fokus. Keine unnötige Wiederplatzierung des Timer-DOM bei unveränderter Reihenfolge. Korrektur bleibt im bestehenden einzigen Arbeitseditor; im Editor nur Speichern/Abbrechen im Fußbereich, Zurück bleibt mit ausdrücklich benannter Speicherwirkung verfügbar. Keine zusätzliche Speicherung oder Änderung der bestehenden Zeit-/Pausen-/CAS-Regeln.
- b.b. erhält eine zugängliche Erklärung und eindeutige Rückmeldung: persönliche Geräteübersicht, keine Rechnungszahlung und kein Abrechnungsnachweis. Heute zeigt weiterhin bezahlt. Rechnungs-, billed- und persönliche Markierungen bleiben getrennt.
- Prüfungen: Browser-/JS-Regressionen einschließlich gezielter Wiederholung PASS. Neue Prüfung bestätigt manuellen Start ohne Standortanforderung, optionalen Auftrag, Timerposition und Fokus nach Laufzeitaktualisierung. Vorhandener Timerabschluss mit Pause, später ergänzen, Fortsetzen, Entwurf und Undo weiter PASS. 27 JVM-Suiten, 55 MCP-stdio-Szenarien und 3 Baselineprüfungen PASS; P1-Vertrag unverändert PASS. Source-/Workspace-/bestehendes Artifact-Privacy-Gate PASS.
- Zusätzlich ausschließlich lesender Nachweis des erhaltenen synthetischen Cloud-AVD: 3 Kunden, 1 Auftrag, 2 Snapshot-Arbeiten, 1 ausstehende Aktion; 0 Rechnungen/Angebote/Fotos/Termine. Geschäftshash ohne rein technische Capabilities, einschließlich unveränderter Pending-Aktionen: `be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af`, identisch zum vorherigen UAT. Sämtliche Geschäfts-IDs synthetisch. Dies ist kein Zugriff auf den physischen Pixel. Native P3-Abnahme und 115-Aufgaben-Gesamtdurchlauf bleiben R7.


## P3-R4 – Kundenakte als zentrale Betriebsansicht (20.10.2026)

- Vier Hauptbereiche der Akte: Übersicht – Arbeiten – Fotos – Dokumente. Keine fünfte Hauptnavigation. Angebote/Kostenvoranschläge und Rechnungen sind in Dokumente gemeinsam, eindeutig beschriftet und auf 30 sichtbare Einträge mit Nachladen begrenzt. Die Fotoansicht lädt ausschließlich Fotos. Kein neuer Speicher oder neues Dokumentmodell.
- Kopfaktionen Arbeit starten, Termin und Foto übernehmen den exakten Kundenkontext. Arbeit startet erst nach Bestätigung, verhindert parallele aktive Sitzungen und erfordert keinen Standort. Termin öffnet den bestehenden Editor mit gewähltem Kunden. Nachtragen bleibt unter Kontakt & weitere Aktionen. Keine automatische Geschäftsdatenerstellung.
- Übersicht enthält Fläche, Pflegehinweis, nächsten Termin, offene Aufträge und letzte Arbeiten. Vollständige Auftrags-/Arbeitsgeschichte und bisherige Filter/Batchgrenzen bleiben unter Arbeiten erhalten. Garteninformationen bleiben von Übersicht aus bearbeitbar. Gartenentwürfe werden zusätzlich im flüchtigen Ansichtsspeicher gehalten, damit auch bei verweigertem localStorage ein Tabwechsel den aktuellen Entwurf nicht verwirft; Neustartpersistenz wird bei einem Speicherfehler weiterhin nicht behauptet. Öffnungsbasis/CAS bleiben unverändert.
- Neuer gezielter Kundenakten-Test PASS: vier Bereiche, Kundenkontext ohne Schreiben, Dokumente außerhalb der Fotos, vollständige Arbeiten, Gartenentwurf nach Tabwechsel und Lesbarkeit bei 320/360/426 px mit vergrößerter Schrift. Bisherige Browserregressionen einschließlich Wiederholungen der bewusst veränderten Historien-/Dokumentwege PASS; Tests prüfen weiterhin vollständige Aufträge/Rechnungen und Schutz begonnener Arbeitsangaben. 27 JVM-Suiten, 55 MCP-stdio-Szenarien und 3 P3-Baselineprüfungen PASS.
- P0 Source/Workspace/Artifact PASS; P1-Vertrag unverändert PASS, MCP9/117 und 156 semantische UI-Aktionen erhalten. Kein Pixel-/Google-/Versandnachweis und keine Änderung produktiver Daten; R7-Gesamtabnahme bleibt offen.
