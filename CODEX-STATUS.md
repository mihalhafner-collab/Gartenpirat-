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
- Repository-Version: 1.49.2 / 71 (Cloud-Kandidat, nicht produktiv abgenommen)
- Live-Pixel bei letzter Prüfung: 1.49.0 / 69
- Live `offers_sync_ready`: false
- Live `service_catalog_sync_ready`: false
- Live `cloud_confirmed`: false
- Live Queue: 9 Review-Aktionen
- `get_business_settings`: SETTINGS_UNAVAILABLE
- Quelle: native Pixel-/Drive-Verarbeitung implementiert; live noch Build 69 mit alter Abhängigkeit. **Gesamtauftrag NICHT abgeschlossen; P0/P1-Abnahme offen.**

## Arbeitsstatus

| Phase | Status | Ergebnis |
|---|---|---|
| A – Bestand verifizieren | PASS | Regeln, Issue #1, Historien und Live-MCP geprüft; keine Betriebsdatenänderung |
| B – produktive Laptop-Abhängigkeit entfernen | PASS (Analyse/Zielpfad) | Abhängigkeiten ermittelt; native Ablösung in C in Arbeit, noch keine operative Abnahme |
| C – Pixel/Drive-Sync vervollständigen | PASS Host / NOT TESTABLE Gerät | Native Migration, Gesamtstände, Handler, Belege, Finanzabschluss; Geräte-/Google-Abnahme offen |
| D – 2 technische Testfehler bereinigen | NOT TESTABLE live | Synthetischer Replay/Supersession PASS; beide Live-Aktionen unverändert |
| E – 7 Legacy-Konflikte kontrolliert schließen | Vorschau PASS / Ausführung gesperrt | Genau 7 bestätigt; direkte Drive-Abnahme und neue vollständige Sicherung fehlen |
| F – End-to-End-Abnahme | Host PASS / Gerät NOT TESTABLE | 266 Python, 637 JVM-Prüffälle, 24 Browser-Suiten; echte Pixel-Schreibabnahme fehlt |
| G – neuer Release-Build + Originalsignierung | PASS Kandidat / Release gesperrt | Originalsignierter 1.49.2/71 geprüft; keine Geräte-/Update-/Datenerhaltabnahme |

## Bekannte technische Queue-Einträge

### Historische 7
- `wrk_mobile_913976ba4bd1453ba75a5468b89c3c32`
- `wrk_mobile_b8445cff3af746718d480fce9be3889a`
- `wrk_mobile_325735fd783c4f5fb3d012ab7969ccfd`
- `delete_change_f82a727e-90ce-4021-a741-2238101b2505`
- `delete_change_b1fc39af-3f04-4baa-81bf-a34a20e76479`
- `customer_change_3b8f4a4bdaca4d5e9b67a4b9b3ac3020`
- `delete_change_9304498d-e90b-47de-be7c-bfd2e627fdf3`

### Technische 2
- `mcp_owner_b6f1c56ba6534a250f6f5057175f365623155aa656d55fd0342f2517b4a539da` → `SYNC_COMPONENT_UPDATE_REQUIRED`
- `mcp_owner_43e89c1a01b88694f43a944e5c4109cb1a7e1a8c15378ee0c1116779bb1ce54c` → `ACTION_ID_CONFLICT`

## Letzter geprüfter Sicherungsstand

Pixel-Geschäftsbackup:
- Backup-ID: `backup_4ef99a6925a1c66e0af5c6d10721fd5993cd706bfacef5aa7b1e33d2052cae69`
- SHA-256: `4683945a1d959e34aeea818768eb8fc618ef2a9cc424e76054c05072e278cb6b`
- State Hash: `02c7a22f709b59765e96453acc778f7fae84217bc2772ae4a49ee4e6ee84cd5f`

Drive-Ausgangssicherung:
- `HausGarten-Laptop-before-v1.49-sync-2026-10-18.json`
- SHA-256: `8556108cafc9ad47a6ee82fa3ce49d01d7a057b42407c8098539dc3825f18cc3`

## Fortlaufender Arbeitsnachweis

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
