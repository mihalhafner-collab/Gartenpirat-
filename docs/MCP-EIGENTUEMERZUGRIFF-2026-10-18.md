> Aktuelle Implementierung 19.10.2026: **1.51.0 / 79**, Paket `at.hausgarten.service`, internes MCP-Protokoll **9**, **117 Tools** (52 lesend / 65 schreibend), Quellkatalog `235f683d99d5170801d1e34c19ad386d9c4acf68b60a6992f72a3e5d2ea14314`. Der tatsächlich verbundene physische Pixel bleibt **1.50.1 / 77, 111 Tools / Protokoll 8**. Quelle und installierter Stand nicht gleichsetzen; abschließende Abnahme und verbleibende Grenzen stehen in [CODEX-AUFTRAG-GARTENPIRAT.md](../CODEX-AUFTRAG-GARTENPIRAT.md) und [CODEX-STATUS.md](../CODEX-STATUS.md).

## Gemeinsamer Dokumentvertrag und aktuelle MCP-Ergänzungen

`android/app/src/main/assets/offer-contract.json` ist die gemeinsame Definition für Dokumentarten, Preisquellen/-grundlagen, Steuer-/Kundentypen, Vertragswege, Pflichtangaben und Statuswerte. Java-/Browser-Adapter werden mit `tools/sync_offer_contract.py` generiert und im Build geprüft. Keine zweite Estimate-Entität und kein paralleler Nummernkreis.

| Fachlicher Ablauf | MCP-Pfad | Schutz / realer Nachweis |
| --- | --- | --- |
| Gemeinsame Werte nachlesen | `get_offer_contract` | Readonly, keine Kundeninhalte |
| Angebot/Kostenvoranschlag erstellen und prüfen | `create_and_validate_offer`, bestehende `create_offer_draft`/`update_offer_draft`/`get_offer`/`validate_offer` | Atomare Create+Validate-Operation; unvollständige Entwürfe bleiben separat möglich; `request_id`, Hash; keine erfundenen Preise |
| Geprüften Entwurf speichern | `validate_offer_draft` | Aktueller Hash, Status `validated`; Änderungen machen die Validierung erneut erforderlich |
| Verbindlich/unverbindlich | bestehende Offer-Tools mit `estimate_binding` / `estimate_nonbinding` | Persistierter Bindungsmodus, explizite Bestätigung und zugehöriger Texthash; Änderungen erben keine alte Bestätigung |
| Finale Dokumente und PDF | `finalize_offer`, `get_file_metadata`, `read_file_chunk` | `confirmed=true`, Record-/Kontexthash; eingefrorener Aussteller/Empfänger, Dokument-ID, Dateiname, SHA und privater Dateispeicher |
| Share-Handoff | `prepare_offer_share` | `share_prepared` ist kein Versand; normale OS-Teilen-Funktion benötigt Geräteinteraktion; Tests nur lokale Vorschau |
| Bestätigten Mailversand vorbereiten | `prepare_offer_delivery` | Expliziter Empfänger und Bestätigung, Original-PDF-Ressource; selbst kein Versand. Separater verbundener Gmail-Kanal nur bei ausdrücklicher Nutzeranweisung |
| Versandbeleg übernehmen | `confirm_offer_delivery` | Pixel liest echte Gmail-SENT-Nachricht, Aussteller, Empfänger, identischen PDF-Anhang; erst dann `sent`. Keine frei übermittelte Quittung akzeptiert |
| Gefahrloser Self-Test | `run_self_test` | Readonly PASS/FAIL/NOT TESTABLE; keine Mail-/Geschäftsschreibtests oder Kunden-/Rechnungsinhalte |
| Vollständiger UI-Katalog | `get_ui_action_catalog` | `offset`, maximal 100 je Seite, `next_cursor`, `expected_catalog_hash`; 100+56; 145 Fachaktionen und elf explizite Plattform-/Darstellungsgruppen |

**Preisvertrag:** Manuelle bestätigte Preise ausschließlich `price_source=user_confirmed`; freigegebener Katalogpreis über ID/aktuellen Hash. `price_basis=confirmed|catalog|calculated|estimated`; berechnete und geschätzte Werte benötigen Erläuterung, berechnete zusätzlich nachvollziehbare Faktoren. Kein unbekannter Preis wird automatisch ergänzt.

**Testdaten:** Markierung und Testkunde/Testempfänger müssen zusammenpassen. Lokale Entwurfs-, Validierungs-, Finalisierungs-, Share- und Archivaktionen werden nicht zu Drive exportiert. Test-PDFs sind markiert; externe Mailübergabe und produktive Auftragsübernahme bleiben gesperrt. Technische Tests verwenden ausschließlich private synthetische Datenräume.

**Versandgrenze:** Der vorhandene ChatGPT-Gmail-Connector erteilt dem Pixel keinen Gmail-Zugriff. Nach Update muss der Eigentümer am Gerät unter Drive-Verbindung „Gmail-Versandbelege freigeben (nur Lesen)“ bestätigen; Google-Konto-/API-/OAuth-Freigabe müssen tatsächlich bestehen. Ohne diesen Readback bleibt der Status unsent. 401 wird nicht umgangen; temporäre Providerfehler liefern begrenztes `retry_after_ms`, keinen automatischen erneuten Mailversand. Reale Gmail-Send-/Readback-Abnahme ist offen, da keine echte Testmail erlaubt ist.

**Connector-Refresh:** Nach autorisiertem Originalupdate App/Pixel-Tunnel mit bestehender Konfiguration starten. Internes Protokoll 9 und neuer `tool_catalog_hash` kommen aus derselben Laufzeit wie `tools/list`. Bestehende ChatGPT-Verbindung aktualisieren/neu verbinden, keinen weiteren Connector und keinen alten Desktop-Tunnel anlegen. `get_capabilities`/`integration_health` und den tatsächlich sichtbaren Toolkatalog vergleichen; 117 Werkzeuge und obiger Hash müssen real bestätigt werden. Ohne Pixel-Update/Refresh ist der weiterhin sichtbare 111er-Katalog erwartbar, kein Beweis der neuen Freigabe.

**Legacy-Sicherung:** Alte Fotoreferenzen ohne SHA werden ausschließlich im freigegebenen Fotoordner exakt aufgelöst und im selben Backup mit zwei identischen Provider-Lesevorgängen, Bildprüfung und beobachtetem Hash gesichert. Keine stillschweigende Änderung von Foto-IDs oder Geschäftsdaten und kein erfundener historischer Hash. Fehlende/doppelte/abweichende Originale blockieren die Sicherung. Letzter tatsächlicher Stand: sechs aktive Altprüfungen, keine Uploads; ursprünglich sieben. Eine außerhalb dieses Auftrags protokollierte Entscheidung änderte die Auswahl. Alte Vorschau ungültig, keine Legacy-Auflösung durch diesen Auftrag. Für 1–100 vollständig historische Aktionen verlangt die Backup-Policy exakten Queueumfang und bei geänderter Anzahl explizit den frischen Previewhash. Vollständige Sicherung und erneute Bestätigung bleiben Pflicht; sechs bleiben offen.

**Rechtliche Grundlage:** Am 19.10.2026 wurden die offiziellen RIS-Fassungen von [ABGB §1170a](https://www.ris.bka.gv.at/NormDokument.wxe?Abfrage=Bundesnormen&Gesetzesnummer=10001622&Paragraf=1170a) und [KSchG §5](https://www.ris.bka.gv.at/NormDokument.wxe?Abfrage=Bundesnormen&Gesetzesnummer=10002462&Paragraf=5) gelesen. Bindungsart bewusst bestätigen; bei Verbrauchern keine stillschweigende Nichtgewährleistung annehmen. Bestehende Texte bleiben zentral editierbar. Die technische Vollständigkeitsprüfung ersetzt keine vollständige Prüfung sämtlicher Vertragsklauseln oder FAGG-Ausnahmen.

---

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


### Additive Mehrfacharchivierung / Cloud-Kandidat 1.49.5/74

Zwei zusätzliche Werkzeuge im Quellkatalog: `preview_archive_work_records`
(lesen) und `archive_work_records` (Owner schreiben). Auswahl 1–200 eindeutige
Arbeits-IDs, gemeinsamer Vorschauhash über sämtliche Record-/Fotovergleichsstände,
`confirmed=true`, dauerhafte request_id. Bei einem Konflikt/geschützten Eintrag
atomarer lokaler Abbruch. Bestehende einzelne archive-Journale und Restore-Pfade,
keine Kaskaden- oder endgültige Massenlöschung, Originaldateien bleiben erhalten.
UI unter Arbeiten → Mehrere auswählen; Transportstatuswechsel allein verwirft
die Auswahl nicht. Versions-/Finanzschutz bleibt im gemeinsamen nativen Pfad.

Protokoll5 bleibt kompatibel, **89 Quellwerkzeuge**, Kataloghash
`2dde37b0694c5379cc55048b9ec7f681a9ffaa0d4162d6d7ae385c324a644a2b`.
Tatsächlich installierter Pixel bei diesem Auftrag: **73 /Protokoll5 /87 Tools**.
Neue zwei Werkzeuge sind dort noch nicht angeboten oder live getestet.
Nach ausdrücklich freigegebenem gesichertem Update private Verbindung und
ChatGPT-Toolkatalog neu initialisieren, danach Version/Hash/Get-Capabilities prüfen.
Reale Prüfmatrix, Emulator-/Browser-/Host-Ergebnisse und verbleibende Grenzen:
ausschließlich `CODEX-STATUS.md`. Keine neue externe Konto-/Drive-Freigabe erteilt.

### Gemeinsame Papierkorbleerung / Cloud-Kandidat 1.49.6/75

Quellkatalog91 Werkzeuge, internes Protokoll6. `preview_empty_trash` prüft alle
Archivseiten und aktive Referenzen gemeinsam. `empty_trash` verlangt den aktuellen
`expected_preview_hash`, `confirmed=true` und eine dauerhafte `request_id`.
Die neue atomare Journalaktion `purge_trash_v1` benötigt `trash_empty_v1`;
ältere Verarbeiter müssen sie zurückhalten. Abhängigkeiten innerhalb der
bestätigten Archivmenge sind gemeinsam löschbar, geschützte Restfälle bleiben
mit Begründung erhalten. Eine fehlgeschlagene Sammelaktion lässt sich nur mit
geprüften Aktions-/Ausgangshashes und `keep_current` zurücknehmen.

Der Legacy-Abschluss verwendet weiterhin vollständige private Sicherung,
aktuelle Vorschau und `keep_current`. Eine vollständig nachgelesene, inhaltlich
verifizierte Provider-Veröffentlichung wird nun für die Sicherung akzeptiert.
Das setzt weiterhin bestehende Ordnerrechte, einen abgeschlossenen Abgleich und
unveränderte State-/Commit-Kette voraus; fehlende Originale stoppen den Vorgang.
Provider-Rücklesen wird ausdrücklich nicht zu `cloud_confirmed` umgedeutet.

Foto- und Entwurfs-PDF-Bereinigung erst nach überprüfter Veröffentlichung und
lokaler Übernahme; aktive/archivierte/offene Referenzen und gemeinsame Dateien
bleiben geschützt. Historische Sicherungen und unveränderliche Belege bleiben
erhalten. Keine pauschale Löschung des Google-Konto-Papierkorbs.

Tatsächlich installierter Pixel bei dieser Umsetzung74/Protokoll5/89 Laufzeit-
Werkzeuge; hier verbundener Katalog83. Nach gesichertem, ausdrücklich freigegebenem
Update dieselbe Verbindung neu initialisieren/aktualisieren und tatsächliche
91 Werkzeuge samt neuen Schemata prüfen. Bestehende Rechnungs-/Angebotswerkzeuge
bleiben erhalten. Aktuelle Ausführung, Testergebnisse und Live-Grenzen nur im
zentralen `CODEX-STATUS.md`; Quellimplementierung ist keine produktive Löschquittung.


## Aktuelle Ergänzung: Eigentümer-Parität und Legacy-Sicherung 1.50.1/77

Ausgang db5fcd3, installierter Pixel bei dieser Prüfung1.50.0/76, Protokoll7,104/104 Werkzeuge mit identischen Namen. Health und Capabilities melden beide9eade6bb724836aee976f9a912c80a0d40d8e0cc1dead509a55f8f7069fc3df2. Diese Live-Aussage nicht mit dem neuen Quellkatalog gleichsetzen.

Die maschinenlesbare, in der APK enthaltene Funktionsmatrix liegt in `android/app/src/main/assets/ui-mcp-actions.json` und ist über `get_ui_action_catalog` mit offset/limit erreichbar. Sie inventarisiert semantische Benutzeraktionen; doppelte Schaltflächen sowie reine Navigation werden gruppiert, nicht als zusätzliche Fachfunktionen gezählt. Die vollständigen31 JavaScript-/HTML-/Native-UI-Quellen sind per SHA versionsgebunden: nach Änderungen ist eine erneute Prüfung erforderlich. `UiMcpParityTests` prüft jeden dokumentierten Werkzeugpfad gegen die tatsächliche Registrierung, Kataloghash, Lesemodus, kritische Parameter und Pagination. Das ersetzt keinen echten Gerätetest jeder Schaltfläche.

| Bereich | Fachliche Aktionsgruppen mit MCP-Pfad | Wesentliche Pfade |
|---|---:|---|
| Kunden / Gartenakte |13|search/get/create/update_customer; archive/restore|
| Aufträge / Versionsprüfung |12|search/get/create/update_order; inspect_order_review/save_reviewed_order|
| Arbeitsaufzeichnungen / b.b. |14|list/get/create/update/reassign; set_personal_paid; Archive-Auswahl|
| Dauerhafte Zeiterfassung |10|start_work; Pause/Fortsetzen/Stoppen; complete_work_session; finish_work|
| Rechnungen |13|Entwurf, geschützter Abschluss, Korrektur, Zahlung, Original-PDF und Share-Vorbereitung|
| Angebote |13|Kontext, Entwurf, Positionen, Validierung, Finalisierung, PDF, Annahme→Auftrag|
| Leistungskatalog |5|search/get/create/update_catalog_entry; active=false/true|
| Fotos / Dateitransfer |12|list/get_photo; begrenzte Chunks; create/update; Archiv/Restore|
| Geschäftssicherungen |6|create/list/inspect/restore_backup; Dateitransfer; vollständige Legacy-Sicherung|
| Papierkorb |6|list_archive; restore; preview/purge; preview_empty_trash/empty_trash|
| Synchronisierung / Konflikte |10|get_sync_overview/status/conflicts; request_sync; resolve; Legacy-Vorschau/Sicherung/Abschluss|
| App-/Unternehmenseinstellungen |7|get/update_settings/business_settings; capabilities; UI-Matrix|
| Termine / Kalender / Wetter |20|Planung lesen/ändern, Serien, Alternativen, Warnungen, Kalendervergleich, get/update_planning_settings|
| Pflanzen / Neophyten |4|search_catalog/get_catalog_entry; authentifizierte Katalogbilder; Kundengartenfelder|
| **Summe fachlich** |**145**|**111 registrierte Werkzeuge im Quellstand, Protokoll8**|

Zusätzlich elf erklärte Plattform-/Darstellungsgruppen: Erstfreigabe Google/Drive-Dokumentbaum, erstmalige Kalenderverbindung, private Wetter-Schlüsseleingabe, Tunnel-Vertrauensanker/Schreibfreigabe, Android-Systemberechtigungen, physische Kamera/Galerie, GPS-Messung, Versand-/Telefon-/Karten-App, vollständige Gerätesicherung/Installation, reine Anzeige/Navigation/Piratenknopfposition und ungespeicherte UI-Eingaben. Inhalte/Dateien sind per MCP lesbar; Kamera/Picker werden durch Datei-Upload ergänzt. Offene UI-Entwürfe dürfen durch MCP niemals heimlich geändert oder verworfen werden. Diese Ausnahmen sind keine künstliche Low-risk-Beschränkung.

### Rechte und Sprachabläufe

Ein authentifizierter Eigentümer mit aktiviertem Pixel-Schreibschalter erreicht alle registrierten Fach- und Adminoperationen. `get_capabilities` weist `owner_admin_write_enabled`, den Aktionsumfang und notwendige Sicherheitsparameter aus. ChatGPTs eigene Allow-all-actions-Einstellung verwaltet der Client; die App attestiert sie nicht und vergibt keine externen Google-/Tunnelrechte. Ohne Schreibschalter werden sämtliche Schreibwerkzeuge aus der Registrierung entfernt und Aufrufe serverseitig abgewiesen. Keine beliebigen SQL-, Dateisystem-, Shell- oder Geheimnislesefunktionen.

Für Sprache IDs zunächst eindeutig suchen; mehrdeutige Namen nicht erraten. `finish_work(session_id,activity,request_id,...)` stoppt eine laufende/pausierte tatsächliche Sitzung und übernimmt die reale Nettozeit atomar. Fehler erhalten den vorherigen Timerzustand, identische Request-ID liefert denselben Beleg; keine doppelte Aufzeichnung. `start_work` akzeptiert eine geprüfte Terminreferenz. Das ist durch den aktuellen ausdrücklichen Benutzerauftrag autorisierte echte Timerarbeit, keine erfundene Tätigkeit. Persönlich bezahlt bleibt getrennt von billed und Rechnungszahlungen. Rechnung aus offenen Arbeiten: Arbeitseinträge lesen, reale bestätigte Preise/Empfänger übernehmen, create_invoice_draft; Abschluss ausschließlich nach ausdrücklicher Anweisung über request_invoice_issue, Versand separat.

`get_planning_settings` liefert Vergleichshash; `update_planning_settings` ändert Arbeitstage, Tageszeiten, Puffer, Wetterschwellen und vorhandene Integrations-/Benachrichtigungsoptionen über denselben privaten SQLite-Speicher wie die Oberfläche. Aktivierung ohne bestehenden autorisierten Kalender beziehungsweise hinterlegten freigegebenen Wetterzugang wird abgewiesen. Kalenderpause löscht keine Termine/Google-Einträge. Android-Benachrichtigungsrechte werden separat ausgewiesen. `request_weather_refresh` stößt den vorhandenen Planungsjob mit seinen Cache-/Retry-Regeln an, keine garantierte neue Providerantwort.

Listen für Kunden, Aufträge, Arbeitsaufzeichnungen und Rechnungen unterstützen offset; Fotos zusätzlich Rolle, Sitzung, Arbeit und Aufnahmedatum. Seiten mit höchstens100 Datensätzen, stabile Fotosortierung; Originale weiterhin nur über begrenzte authentifizierte Dateiblöcke. Kein Base64-Original im Geschäfts-Snapshot.

### Zyklische Legacy-Sperre sicher auflösen

Vorher lehnten Sicherung UND Sammelabschluss jedes drive_sync_failed ab, während readPrimary bei LEGACY_SOURCE_CHANGED selbst die beweissichernde Lesung verhinderte. Eine weitere konkrete Ursache ist die nachträglich eingeführte leere appointments-Sammlung: ein75er Hash kann nach76er Validierung abweichen, obwohl kein Geschäftsdatensatz verändert wurde. Der neue Leser prüft ausschließlich nachweisbar identische Varianten fehlender/leerer additiver Sammlungen; echte Inhalte bleiben Bestandteil der Prüfung.

1. preview_legacy_conflict_resolution neu lesen: exakt sieben bekannte Altaktionen, keep_current, no_business_data_changed.
2. prepare_legacy_conflict_backup mit request_id und **expected_preview_hash**. Das zusätzliche Feld bleibt für alte erfolgreiche Normalabläufe optional; im Fehlerbehebungspfad ist es zwingend. Sync darf nicht laufen, bestehende Ordnerrechte/Veröffentlichungskette müssen tatsächlich lesbar sein. Andere Fehler und neue/technische Queueaktionen bleiben gesperrt.
3. Private vollständige lokale Geschäftssicherung: konsistentes SQLite inklusive Queue, Originale/private Dateien, aktueller Legacy-Snapshot und verifizierter unveränderlicher Pixel-Snapshot samt Commit. Beide Quellen vor/nach dem Lesen prüfen; alle ZIP-CRC, Archiv-SHA und Geschäftshash prüfen. Kein bloßes Storage-Rücklesen als Cloud-Bestätigung ausgeben.
4. close_legacy_conflicts mit backup_id, unverändertem expected_preview_hash, confirmed=true und stabiler request_id. Eine SQLite-Transaktion prüft alle Aktions-/Datensatzhashes und merkt sieben keep_current-Entscheidungen vor. Keine alte Löschung/Variante anwenden. Der lokale Entscheidungsschritt verlangt keine erfolgreiche Netzwerkoperation.
5. request_sync, danach Queue/aktive Konflikte/Historie/Geschäftshash und Bestätigung zurücklesen. Provider_readback bleibt von unabhängigem Drive-Serverbeleg getrennt.

Eine tatsächlich fremdveränderte Legacy-Geschäftsdatei kann damit gesichert und als Evidenz erhalten werden, wird aber nicht automatisch angenommen oder überschrieben. Ein solcher verbleibender LEGACY_SOURCE_CHANGED benötigt die fachliche Prüfung beider erhaltenen Stände. Sicherung oder keep_current für historische Aktionen autorisiert keine fremde neue Geschäftsdatenmigration.

### Veröffentlichung / Refresh

Der neue Runtime-Katalog enthält111 Werkzeuge, Protokoll8. Nach gesichertem ausdrücklich freigegebenem Original-Update den vorhandenen Pixel-Tunnel neu verbinden, `integration_health` und `get_capabilities` lesen, anschließend den verwalteten ChatGPT-Connector aktualisieren/neu verbinden und seine Werkzeuge neu laden. Erwartet: identische Toolnamen, aktuelle Schemas und identischer Tool-Kataloghash zwischen Health/Capabilities/aktueller Registrierung. Bei weiterhin104 Werkzeugen ist der alte Katalog aktiv; neue Funktionen dann nicht als verfügbar ausgeben. Kein alter Desktop-Tunnel, keine Umgehung von401 und kein erneutes Anlegen einer zweiten App. Tatsächlicher Abschlussnachweis und eventuelle Blockaden stehen ausschließlich in CODEX-STATUS.md.


Tatsächlich getrennt nachgeprüft: Die ChatGPT-App-Berechtigungsverwaltung bestätigt für **Haus & Gartenservice Pixel** die eigene Einstellung **Allow all actions**; die globale Low-risk-Voreinstellung wird übersteuert. Keine Einstellung geändert. Der installierte104er Katalog und die hier sichtbaren104 Connector-Deklarationen stimmen zusätzlich in sämtlichen obersten Parameternamen und Pflichtfeldern überein. Ein unabhängiger vollständiger Connector-Schemahash wird von dieser Verwaltung nicht ausgegeben; Hashgleichheit wurde zwischen Pixel-Health und Pixel-Capabilities geprüft. Der neue111er Quellkatalog hat Hash **392fdfacf116c49e13306fcfc8c84ab49ee0ce20b35ca62d250fa069ecd1f2ff**. Vollständige Min-/Max-/Enum-Verträge werden in der App validiert; TypeScript-Connector-Deklarationen stellen diese JSON-Schema-Grenzen nicht vollständig dar.


## P2: konsolidierte Bedien- und Dokumentgrenzen

Die historischen Einzelberichte RECHNUNGS-WORKFLOW-v1.24,
RECHNUNGS-OBERFLAECHE-v1.25 und DESIGN-v1.35 wurden nach Referenzprüfung hier und
in README/AGENTS/Auftrag zusammengeführt. Ihre vollständigen damaligen
Prüfprotokolle bleiben im Git-Commit 348b40a erreichbar; historische Laptoppflicht,
Steuerdefaults und alte Geräteversionen sind keine aktuelle Anleitung.

- Rechnungsempfänger und Einsatzkunde getrennt halten; fremde Arbeitspositionen
  nicht bei einem Kundenwechsel übernehmen. Dieselbe Arbeit nicht doppelt
  übernehmen. Entfernte Positionen bleiben gezielt wieder hinzufügbar.
- Preis, Materialmenge, Fahrtkosten und Steuerstatus bewusst prüfen; keine Preise
  aus Freitext raten. Interne Notizen nicht auf Kundendokumente übernehmen.
- Ungespeicherte HTML-Vorschau ist kein finales PDF. Finale Ausgabe verwendet den
  bestätigten gespeicherten Datensatz; Nummernvergabe und Arbeitsabrechnung nur
  im bestehenden geschützten Abschlussweg. Keine parallelen Nummernkreise.
- E-Mail/WhatsApp/Android-Teilen sind getrennte Handoffs. Eine geöffnete Versand-App
  oder vorbereiteter Anhang beweist keinen Versand. Belegpflichtige Versand-/
  Zahlungszustände nicht aus Darstellung ableiten. Externe App-Verfügbarkeit und
  physische Share-/Tastatur-/Hardware-/GPS-Abläufe separat prüfen.
- Zurück und Abbrechen unterscheiden; Fehlermeldung oder Verbindungsabbruch
  darf Entwürfe nicht still verwerfen. Arbeitsduplikate übernehmen keine alten
  IDs, Fotos, Abrechnungs- oder privaten Notizdaten. Laufende Timer und pausierte
  Arbeit vor Doppeltipp/Überlagerung schützen. Aufklappbare Felder bleiben
  erreichbar; keine Funktion aufgrund einer kompakten Ansicht entfernen.
- Gewöhnliche Rechnungsfunktionen sind keine Zertifizierung, vollständige
  Buchhaltung, Bankabgleich oder automatische Prüfung aller österreichischen
  Steuer-/Gewerbe-/Aufbewahrungssonderfälle. Rechnungen/finale Dokumente und
  unveränderliche Journale bleiben im technischen Cleanup geschützt.
