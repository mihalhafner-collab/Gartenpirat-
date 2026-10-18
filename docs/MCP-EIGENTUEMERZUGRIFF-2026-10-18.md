> Historischer Nachweis 1.47. Aktueller Cloud-only-/Pixel-Drive-Auftrag und Abnahme: [CODEX-STATUS.md](../CODEX-STATUS.md), Umsetzung [Cloud-only 1.49.2 / 71](SYNC-DRIVE-KUNDENAKTE-1.48.md#cloud-only-1492--71-18102026). Frühere Laptop-/Web-Rolloutvoraussetzungen sind aufgehoben.

# Eigentümerzugriff und Datenintegrität – Cloud-Stand 1.47.0 / 67

Fortsetzung mit denselben 66 Werkzeugnamen: [Drive-Abgleich und Kundenakte
1.48.0 / 68](SYNC-DRIVE-KUNDENAKTE-1.48.md). Der nachfolgende Nachweis beschreibt
den früheren Stand 67; neue Serverbelege, Archivabläufe und externe Google-
Voraussetzungen sind im Fortsetzungsnachweis getrennt dokumentiert.

## Ausgang und Abnahme

Ausschließlich `mihalhafner-collab/Gartenpirat-`, Branch
`codex/original-signing-cloud-setup-20261018`. Vor Änderungen sauberer Arbeitsbaum,
HEAD `36e55670bf95158a1f9e8583ac8b2cdc1e43f0b9`; der vorgeschriebene Commit
`05fdcbec77e7c26a039945cbdfcd09c955859b54` ist enthalten. Kein Desktopimport,
kein History-Rewrite und keine Änderung an `main`.

Abnahmekriterien: keine stillen Überschreibungen bei den drei reproduzierten
Datenrennen; Nachtrag erst nach bestätigter lokaler Speicherung leeren;
Kalenderjahr korrekt filtern; lesbare Arbeitszeilen bei 320/360/426 px und
vergrößerter Schrift; große Listen begrenzen; fachliche MCP-Operationen mit
vergleichbaren Versionen, dauerhaften Request-Belegen und echter Laufzeitfreigabe.
Keine produktiven Schreibtests, Installation oder ADB-Verbindung.

## Zugang, Identität und tatsächlicher Zustand

Der bestehende Pixel-Dienst wurde ausschließlich lesend geprüft. Er meldete
`ready`, `pixel_business_write`, `write_enabled=true`. Sieben Aktionen blieben
ausstehend; ein Kundenjournal meldete `WRITE_CONFLICT`: eine als Neuanlage
übermittelte ID existiert bereits. Keine automatische Zusammenführung. Der
installierte Dienst liefert den ursprünglichen Vergleichsstand nicht vollständig;
eine sichere Entscheidung über diesen konkreten Konflikt wurde deshalb nicht
vorgenommen. Die neuen Konfliktwerkzeuge stehen erst nach der bereitgestellten
App-/Connector-Aktualisierung zur Verfügung.

Lokaler Pixel-Snapshot, Provider-Rücklesen und Cloud-Bestätigung sind getrennt.
Der gelesene Stand meldete `cloud_confirmed=false`. Ein erfolgreicher Lesezugriff
oder `write_enabled=true` beweist keine produktiv getestete Schreibfunktion.
Der ältere Zugang meldete HTTP 401 `tunnel_use_forbidden`; er wurde nicht verändert
oder als Ausweichweg wiederhergestellt.

Die Autorisierungsgrenze bleibt der private, kontogebundene OpenAI-Tunnel mit
seiner Connector-Freigabe und dem geräteseitigen Schreibschalter. Vor jedem
MCP-Schreibaufruf prüft der Service den aktuellen Schalter erneut. Es gibt keinen
SQL-, Shell-, freien Dateipfad- oder Zugangsdatenzugriff über MCP. Die Wire-
Schnittstelle liefert dem App-Server keine unabhängig überprüfbare individuelle
Aufruferidentität. Exklusive Eigentümernutzung muss deshalb zusätzlich durch die
privaten Connector-/Tunnel-Freigaben des Kontos gewährleistet sein; ein lokales
Rollenetikett ersetzt diese Freigaben nicht.

## Funktions- und Berechtigungsmatrix

Die aktualisierte Quelle registriert 66 Werkzeuge: 30 lesende und 36 schreibende.
Alle Schreibwerkzeuge benötigen die bestehende private Freigabe. Schreibaufrufe
verwenden stabile `request_id`; veränderte Wiederholungen werden zurückgewiesen.
`record_hash` schützt fachliche Änderungen, `entity_hash` Archivierung und
Rechnungs-Zahlungsmarkierung. Die jeweilige Eingabespezifikation benennt den Hash.

H = tatsächlich ausgeführte isolierte Host-/Browser-/SQLite-Prüfung.
N = nativer Android-Pfad kompiliert und Abnahme vorbereitet, nicht ausgeführt.
L = bestehender Dienst tatsächlich nur lesend geprüft. Ein H-Nachweis ist keine
Geräte- oder Drive-End-to-End-Abnahme.

| Bereich | MCP-Werkzeuge / Operationen | Prüfung | Grenze / Voraussetzung |
| --- | --- | --- | --- |
| Version und Rechte | `integration_health`, `get_capabilities` | H, N, L Gesundheit | Installierte App und Connector müssen neue Schemata verwenden |
| Kunden | suchen, lesen, anlegen, gezielt ändern; `archive_entity`, `restore_entity` | H Python/JVM, N | Referenzen, Dubletten und Vergleichsstand prüfen; Kundenhistorie erhalten |
| Aufträge | suchen, lesen, offene Aufträge, anlegen, ändern, archivieren/wiederherstellen | H Python/JVM/Browser, N | Kundenwechsel mit verknüpften Arbeiten/Rechnungen/Fotos gesperrt |
| Arbeitsaufzeichnungen | lesen, suchen, zusammenfassen, anlegen, ändern | H Python/JVM/Browser, N | Keine erfundenen IDs; ausgestellte Leistungen bleiben unveränderlich |
| Ausdrückliche Arbeitszuordnung | `reassign_work_record` | H Schema/gemeinsame Regeln, N | Einsatzkunde, Rechnungsempfänger, Hash und Begründung erforderlich; Beziehungen geschützt |
| Manuelle Abrechnungsmarkierung | `set_work_record_billed` | H Schema/gemeinsame Regeln, N | Markierung ist kein Rechnungsnachweis; ausgestellte Belege schützen Leistungsdaten |
| Zeiterfassung | starten, Pause, fortsetzen, stoppen, abschließen; `continue_work`, `discard_work_session`, `restore_work_session` | H JVM/Browser, N | Bestehende Sitzung, Zustandsprüfung, dauerhafte Belege; Stop allein erzeugt keine Arbeit |
| Persönliches b.b. | `get_settings`, `set_personal_paid` | H Browser/Schema, N | Rein gerätelokal; bestehende WebView-Markierungen vor erstem MCP-Schreiben übernehmen |
| Fotos | `list_photos`, `get_photo`, `update_photo` | H Schema/Beziehungen, N, L Liste | Metadatenänderungen erfordern bestätigten Backend-Vergleichsschutz |
| Foto-Originale | `begin_file_upload`, `write_file_chunk`, `create_photo` | H Transfer-/Schema-/Foto-Regeln, N | Bis 20 MiB, gültiges Bild, MIME-/Hash-/Zuordnungsprüfung; keine Veröffentlichung unvollständiger Uploads |
| Dateien und Original-PDF | `get_file_metadata`, `read_file_chunk` | H Transferregeln, N | Fachliche Ressourcen-ID, SHA256, höchstens 48 KiB je Block; Original muss verfügbar sein |
| Rechnungsentwürfe | `create_invoice_draft`, `update_invoice_draft`, lesen | H Python/JVM/Browser, N, L Liste | Gemeinsame Berechnung/Beziehungsregeln; Änderungen benötigen Backend-Vergleichsschutz |
| Rechnungsabschluss | `request_invoice_issue` | H Python-Transaktionen/PDF/Versionen, N | Nur Vormerkung bis zentraler Nummernservice bestätigt; keine Offline-Finalisierung |
| Korrektur/Storno | `create_invoice_correction`, danach gesonderter bestätigter Abschluss | H Python mit echten synthetischen PDFs, N | Eigener Entwurf mit Originalbezug/Grund; Original-PDF unverändert, keine automatische Rücksetzung der Arbeits-Abrechnungsmarkierung |
| Rechnungszahlung | `update_invoice_payment` | H Schema/Journalregeln, N | Nur ausgestellte gültige Rechnung; eigener Status, vollständig getrennt von persönlichem b.b. |
| Versand | `prepare_invoice_share` | H Schema, N | Bereitet Geräteübergabe vor; Versand-App und Benutzerhandlung erforderlich; weder Versand noch Zustellung behaupten |
| Synchronisation | `get_sync_overview`, `get_sync_status`, `request_sync` | H Python/JVM/Browser, N, L Status | Provider-Rücklesen ist keine unabhängige Cloud-Bestätigung |
| Konflikte | `get_sync_conflicts`, `resolve_sync_conflict` | H Python/JVM, N | Originalaktion und aktueller Stand mit Hashes; beide Varianten behalten; Entscheidung erneut prüfen |
| Papierkorb | `preview_delete`, `delete_entity`, `archive_entity`, `list_archive`, `restore_entity`, `preview_purge`, `purge_entity` | H JVM/Browser, native Pfade nur kompiliert | Archiv bewahrt Originale; Purge benötigt explizite Bestätigung/Hash und geschützte Referenzen. Foto-Dateibereinigung nach überprüfter Veröffentlichung; Live weiterhin Build72 ohne neue Purge-Werkzeuge |
| Gerätestandards | `get_settings`, `update_settings` | H Schema, N | Nur erlaubte fachliche Defaults; kein Konto-/Berechtigungsersatz |
| Rechnungseinstellungen | `get_business_settings`, `update_business_settings` | H Python/JVM, N | Nur nicht geheime Aussteller-/Nummernkonfiguration; tatsächliche Prüfung externer Nummernstände bleibt nötig |
| Sicherung | `create_backup`, `list_backups`, `inspect_backup` | H Schema, N | Privates Geschäftsartefakt bis 64 MiB mit lokal verfügbaren Originalen; fehlende Originale führen zum Fehler, kein Teil-Erfolg |
| Wiederherstellung | `restore_backup` | H Schema, N | Ausschließlich fehlende Geschäftsdatensätze ergänzen; abweichende Versionen, offene Queues/Sitzungen oder geschützte Belege stoppen; Geräteeinstellungen bleiben erhalten |
| Pflanzen/Neophyten | `search_catalog`, `get_catalog_entry`, Katalogbilder über Dateiwerkzeuge | H bestehende Katalog-/Assetprüfungen, N | Kanonischer bestehender Katalog; Änderungen weiterhin über gemeinsamen Quell-/Generierungsprozess |

Die Anwendung behält Kamera, Dateiauswahl, Standort und Android-Einstellungen als
Gerätefunktionen. Ein Tunnel kann weder Bildausschnitt/Standortfreigabe noch eine
Android- oder Kontobestätigung ersetzen. API-Schlüssel, Tunnelkonfiguration,
Drive-Berechtigungen und Android-Keystore gehören nicht zu den editierbaren
Geschäftseinstellungen.

## Gemeinsame Regeln und Datenhaltung

- `ManualChangeRules`: unveränderlicher Formularstand vom Öffnen, explizite
  Feldänderungen, atomarer Vergleich gegen den aktuellen Datensatz. Neue manuelle
  Aktionen übernehmen keine Vergleichsbasis einer früheren MCP-Aktion.
- `InvoiceDraftRules`: gleiche Entwurfsberechnung und Verknüpfungsprüfung für UI,
  MCP und Pixel-Journale; Rechnungsabschluss prüft zusätzlich Entwurfs- und
  verknüpfte Arbeitsversionen innerhalb seiner Transaktion.
- `db.atomic()` und `owner_journal`: bestehende Python-Saver teilen eine
  Transaktion mit dem dauerhaften Aktionsbeleg. Abbruch nach Commit, aber vor
  Drive-Export, wird ohne doppelte Mutation wieder aufgenommen.
- `OwnerJournalRules`: versionierte Archiv-/Wiederherstellungs-, Konflikt-,
  Zahlungs- und Konfigurationsaktionen. Eine geprüfte Konfliktentscheidung ist
  keine erfundene Annahme der ursprünglichen Aktion.
- Pixel-Snapshot und das Entfernen tatsächlich bestätigter Queue-Einträge
  werden gemeinsam in der vorhandenen SQLite-Datenbank festgeschrieben.
  Legacy-SharedPreferences bleiben als erhaltene Upgrade-Ausgangsbasis lesbar.
  Große Werte werden in begrenzten SQL-Abschnitten gelesen, damit eine einzelne
  CursorWindow-Zeile den Snapshot nicht unlesbar macht.
- Vorhandene Queue-Aktionen bleiben erhalten. Die MCP-Saver ersetzen nur die
  gerade neu angelegte interne Aktion durch ihre stabile Request-ID.
- Alter Importer darf einen neuen Abschlussauftrag nicht ohne dessen
  Vergleichsschutz ausführen: neuer Typ `owner_invoice_issue`. Alte Typen bleiben
  lesbar; neue, unbekannte Vorgänge werden von alten Importern zurückgehalten.
  Foto-/Entwurfsänderungen verlangen die passenden Backend-Capabilities.
- Dateiübertragung bleibt getrennt von Geschäftssnapshots. Private Uploads werden
  erst nach Vollständigkeits-, Hash-, Bildtyp- und Referenzprüfung zu Fotos.
- HTTP 401/403 bleibt ein Autorisierungsfehler. HTTP 429 erhält eine begrenzte
  Wartezeit mit Backoff und geprüftem `Retry-After`; keine Umgehung von Freigaben.

## Die sieben Korrekturen

1. **Rechnungsrennen:** alle maßgeblichen Arbeits-/Foto-/Rechnungsverknüpfungen
   und ausgestellten Leistungsdaten im tatsächlichen `_upsert`-Schreiblock erneut
   prüfen, auch ohne MCP-Basis. Reproduktion mit realem synthetischem PDF.
2. **Veraltetes Formular:** Öffnungsstand und geänderte Felder übertragen;
   parallele Notizänderung verhindert das Speichern und erhält den Entwurf.
   Dasselbe Modell gilt auch für Kunden-, Auftrags- und Rechnungseditor.
3. **MCP → manuelle Änderung:** frische Vorgängerbasis statt geerbtem `_mcp_base`;
   offline verkettete Aktionen einschließlich Belegen behalten.
4. **Nachtrag:** Übernehmen ruft den geprüften Speicherpfad auf. Bei Fehlern
   bleiben Text und Entwurf erhalten; wiederholtes Übernehmen dupliziert den
   gleichen noch nicht gespeicherten Nachtrag nicht.
5. **Jahresfilter:** Kalenderjahr des Arbeitsdatums; ISO-Wochenjahr nur für Wochen.
6. **Schmale Darstellung:** eigener Platz für b.b. und Aktionsmenü, flexible
   Namens-/Stundenanordnung und umbrechender Seitentitel. 320/360/426 px und
   vergrößerte Schrift tatsächlich im Chromium geprüft.
7. **Leistung:** aktive Listen bedarfsgerecht rendern, Zusammenführungen und
   Kundenzugriff je Datenrevision indizieren, Suche bündeln, Arbeits-, Wochen-
   und Abrechnungslisten begrenzen. Fokus und Entwürfe bleiben erhalten.
   Die Cloud-Messungen sind keine Pixel-Messungen; Einzelwerte stehen im JSON.

## Einführung und verbleibende Barrieren

1. Zuerst den gemeinsamen Web-/Drive-Importer und Nummernservice aus derselben
   Quelle bereitstellen; kein alter Laptop-Tunnel. Vorhandene Daten und Journale
   sichern. Die ausgelieferte Quelle führt keine produktive Migration aus dieser
   Cloud-Sitzung aus.
2. Neue Backend-Capabilities und nicht geheime Rechnungskonfiguration in den
   bestehenden Drive-Gesamtstand übernehmen. Ohne bestätigten Vergleichsschutz
   werden Foto-/Entwurfsänderungen sicher angehalten.
3. Pixel vollständig sichern. Installation erst nach ausdrücklicher Freigabe.
   Installierte Version/Zertifikat, Fresh-Install, In-place-Update und Datenerhalt
   sind noch nicht geprüft. Der neue Original-Kandidat allein ist kein Nachweis.
4. Nach freigegebenem Update App öffnen, vorhandenes b.b. verlustfrei übernehmen,
   private Verbindung neu starten und Connector-Werkzeugliste aktualisieren.
   `get_capabilities` muss Version und erwartete Funktionen bestätigen. Eine
   Quellcodeänderung erweitert die heute installierte Verbindung nicht sofort.
5. Die sieben bestehenden Aktionen und den Kundenkonflikt erst mit vollständigen
   Versionen erneut prüfen. Keine automatische Konfliktentscheidung durch das
   Update; kein Testschreiben auf Betriebsdaten.

Alte Fotojournale mit eingebetteten Originalbytes werden nicht in Konflikt-Snapshots kopiert. Ihre Originalaktionen bleiben im Journal erhalten; automatische Konfliktauflösung bleibt bis zur Prüfung des Originals gesperrt. Neue Fotoaktionen verwenden den getrennten, geprüften Dateitransfer.

Weitere konkrete Grenzen: externe Tunnel-/Organisations- und Connector-Rechte,
persistente Android-Drive-Ordnerfreigabe, Energiesparen/Netzverfügbarkeit,
Tool-Schema-Caches und begrenzte Datei-/Sicherungsgrößen. Die geschäftliche MCP-
Sicherung ist keine vollständige Geräte-/Schlüssel-/WebView-Sicherung und erfüllt
allein nicht die Installationsvoraussetzung. Queues und aktive Sitzungen werden
mitgesichert, aber nicht blind über einen laufenden Bestand zurückgespielt.
Die Cloud hat keine Administrationsfähigkeit, externe Kontorechte zu erfinden;
notwendige Rechte müssen im jeweiligen Eigentümerkonto bestehen. Direkte Cloud-
API-Aufrufe können zusätzlich an Egress-Regeln scheitern, obwohl ein Connector
funktioniert. In dieser Umgebung waren `api.github.com`, `api.openai.com` und
`www.googleapis.com` nicht ausdrücklich freigegeben; Git über `github.com`
funktionierte. Keine Netzwerkregel wurde umgangen.

## Tatsächliche Tests und Artefakt

Das tatsächlich aus der JVM exportierte [Werkzeugschema](verification/mcp-owner-tool-schema.json) dokumentiert die registrierten Operationen und Eingaben. Es erteilt keine externen Kontorechte.

Die maschinenlesbaren Ergebnisse, öffentlichen APK-Metadaten, Signatur- und
Cleanup-Prüfungen stehen in
[verification/mcp-owner-2026-10-18.json](verification/mcp-owner-2026-10-18.json).

Python: `python3 tests/run_quality_suite.py --exclude-qa-signing`.
Der explizite Ausschluss betrifft nur die ältere Suite, die echte temporäre
QA-Schlüssel erzeugt. Keine Ersatz-/Debug-/QA-Signierung in diesem Auftrag.
Java: `tools/run-followup-java.py`, einschließlich Android/Python-Vergleich der
Aktions- und Kundenarchiv-Hashes. Browser: elf bestehende Suiten und die neuen
Reproduktionen in `tests/access-integrity.test.cjs`, ausschließlich synthetische
Daten. Build, Lint und Android-Testquellen-Kompilierung wurden ausgeführt.
`OwnerNativeAcceptance` ist vorbereitet und kompiliert, nicht auf einem Gerät
ausgeführt. Keine produktiven Schreibtests und keine Installation.


### Papierkorb-Protokoll 5 / Cloud-Kandidat 1.49.4/73

Bestehende Funktionen erhalten; Quellkatalog 87 Werkzeuge, Hash `0cd1b37e492eb9370c481a636f45f81c129694d60cd2d6bd25e0517a110eaab0`. Neu: `preview_purge` (lesen) und `purge_entity` (Owner schreiben, `confirmed=true`, aktueller Archivhash, dauerhafte request_id). `delete_entity`/`archive_entity` benötigen bei betroffenen Fotozuordnungen zusätzlich `expected_photo_links_hash` aus `preview_delete`. Aktive und bereits archivierte Fotos werden beim Entfernen des zugehörigen Kunden/Auftrags/Arbeitseintrags kontrolliert entkoppelt. Originale bleiben erhalten. Keine separate manuelle Entkopplungsfunktion.

Purge entfernt den ausgewählten Archivdatensatz, hält einen minimalen Löschbeleg in `owner_archive`, blockiert Wiederbelebung derselben ID und bereinigt nicht mehr verwendete Fotodateien erst nach überprüfter Veröffentlichung und lokaler Snapshot-Übernahme. Historische Sicherungen/Journale bleiben getrennt erhalten; keine forensische Löschzusage. Finanzielle Belege und andere Geschäftsabhängigkeiten bleiben geschützt. Fehlgeschlagener Purge kann ausschließlich mit `keep_current` und erneut geprüften Aktions-/Archivhashes zurückgenommen werden.

Live weiterhin Build 72 / Protokoll 4 / 85 Werkzeuge: die neuen Werkzeuge sind dort noch nicht angeboten oder getestet. Nach gesichertem und ausdrücklich freigegebenem Update auf 73 dieselbe private Pixel-Verbindung neu verbinden, initialize und tools/list aktualisieren, danach get_capabilities auf Version, Hash, Schreibgate und 87 tatsächliche Werkzeuge prüfen. Der Tunnel erzeugt die Liste dynamisch aus derselben Implementierung; ein alter Connector-/ChatGPT-Katalog benötigt ebenfalls Refresh/Neuverbindung. Code erteilt keine externen Freigaben. Abnahmen/Testergebnisse ausschließlich zentral in CODEX-STATUS.md.
