> Aktueller Auftrag: [CODEX-STATUS.md](../CODEX-STATUS.md). Der folgende frühere Rollout mit Web-/Laptop-Prozessor ist historisch und aufgehoben. Aktuelle Fortsetzung: [Sync und Papierkorb 1.49.3/72](../CODEX-STATUS.md), Einträge 9–12; installierter Pixel weiterhin 71. Keine Installation ohne Sicherung und ausdrückliche Freigabe.

# Drive-Abgleich und Kundenakte – 1.48.0 / 68

Terminplanung: [ursprüngliches Konzept](#konzept-aufträge-termine-google-kalender-und-wetter-19102026) und [Umsetzung1.50](#terminplanung-1500--76-umsetzung-und-geräteeinrichtung).

Umsetzung des freigegebenen Konzepts im vorhandenen Cloud-Repository und auf
`codex/original-signing-cloud-setup-20261018`, Ausgang `5e1ca93`. Vor Beginn war
der Arbeitsbaum sauber; der vorgeschriebene Vorfahr `05fdcbe` ist enthalten.
Keine Desktopübernahme, zweite operative Datenbank, Änderung an main,
Installation oder Betriebsdatenänderung.

## Ergebnis und Bedeutung der Zustände

Pixel bleibt die primäre App. Jede Änderung und ihr lokaler Auftrag werden in
der bestehenden SQLite-Datenhaltung gespeichert. Permanente Request-Belege und
inhaltliche Hashes verhindern doppelte fachliche Wirkung. Der UI-Stand zeigt
dieselbe Vorschau geschützter Eigentümeraktionen wie MCP; eine Vorschau ist kein
Empfangsbeleg. Offene Geschäftsaufträge werden getrennt von Übertragungen gezählt.

| Zustand | Tatsächlicher Nachweis |
| --- | --- |
| Lokal gespeichert | Dauerhafter Datensatz/Aktionsauftrag auf dem Pixel |
| Übertragung offen | Aktion bleibt erhalten; Ursache und nächste Handlung sichtbar |
| Provider zurückgelesen | Android-Dokumentanbieter hat Inhalt geliefert; keine unabhängige Cloud-Bestätigung |
| In Drive bestätigt | Direkter HTTPS-Serverinhalt und inhaltlich gebundener Veröffentlichungsbeleg geprüft |
| Vollständig abgeglichen | Fachliche Übernahme und notwendige Originale bestätigt; keine offene Prüfung |
| Prüfung erforderlich | Version, Berechtigung, Original, Herausgeber oder geschützter Abschluss blockiert |

Offlinebetrieb und echte Konflikte können keine ehrliche stets leere Warteschlange
garantieren. Die App löscht oder bestätigt solche Aktionen nicht zur kosmetischen
Bereinigung. Der zuletzt früher gelesene operative Bestand mit sieben offenen
Aktionen/einem Kundenkonflikt wurde in diesem Entwicklungsauftrag nicht verändert
und nicht erneut live geprüft.

## Neuer Drive-Transport

- `DriveRestClient`: HTTPS zu Google, explizite Authentifizierung pro Aufruf,
  keine Token in Logs, Repo oder dauerhaftem App-Speicher. Ordnerzugriff bleibt
  auf die konfigurierte Ordner-ID begrenzt. Abgelaufene Anmeldung wird nicht
  umgangen; 429/Serverfehler verwenden begrenzte Wiederholungen und Retry-After.
- Upload in 256-KiB-Abschnitten mit privatem, dauerhaftem Sitzungs-Checkpoint.
  Nach Abbruch wird die tatsächliche Serverposition geprüft. Ein wiederholter
  Upload prüft vorhandene Bytes; abweichende Dateien werden nicht überschrieben.
- Alte Austauschdateien bleiben erhalten. Neue mobile Pakete sind unveränderliche,
  inhaltsadressierte Journaldateien mit denselben Aktionskennungen. Eine Wiederholung
  desselben Pakets erzeugt keine weitere Variante.
- `DriveDirectFiles` veröffentlicht erst den geprüften Datenstand, anschließend
  einen unveränderlichen Commit mit Inhalts- und Aktionsbelegen. Ein abgebrochener
  Abschluss wird über die bestätigte Veröffentlichungsfolge wieder aufgenommen.
  Zwei primäre Herausgeber, Lücken oder gleiche Folgenummern mit verschiedenen
  Varianten halten den Abschluss zur Prüfung an. Drive erhält keine behauptete
  Transaktion über mehrere Dateien.
- Foto- und PDF-Originale liegen getrennt in `HausGarten-Fotos` beziehungsweise
  `HausGarten-Dokumente`. Der neue veröffentlichte Rechnungsstand enthält Dateiverweise
  statt eingebetteter Original-PDFs. Fehlende Originale schließen Vollständigkeit aus.
  Unveränderte, bereits geprüfte Fotos werden anhand erneut gelesener Google-
  Dateiversion/Prüfsumme bestätigt, ohne jedes Original ständig neu herunterzuladen.
- `DriveSyncJob`: Android-Systemplanung mit Netzbedingung und dauerhaften Jobs,
  zusätzlich Wiederaufnahme bei App-Nutzung. Nach acht fehlgeschlagenen Versuchen
  wird eine konkrete Benutzerhandlung angezeigt. Entzogene Rechte benötigen eine
  bewusste Neuverbindung. Android kann Hintergrundausführung verzögern.
- Die Fortschrittsanzeige unterscheidet hochgeladene Abschnitte, Serverprüfung
  und bestätigten Inhalt. Ein abgebrochener Job bestätigt keine Warteschlange.

Der bisherige Dokumentbaum bleibt gespeichert und funktionsfähig. Eine direkte
Verbindung wird nur nach bewusster Gerätehandlung und lesender Prüfung des
bestehenden Ordners aktiviert. Kein automatisches Umschalten in einen anderen Ordner.

`sync_protocol` ordnet Vorgänger und Beziehungen unabhängig von Geräteuhren.
Explizite manuelle Änderungen an unterschiedlichen Feldern werden innerhalb der
geschützten Verarbeitung zusammengeführt; widersprüchliche Werte desselben Feldes
bleiben Konflikte. Explizite MCP-Vergleichsbasen werden weiterhin streng geprüft.
Ein einzelner Kunden-/Foto-Konflikt hält unabhängige gültige Aktionen nicht an.

Neue UI-Entfernungen nutzen den bestehenden versionierten Archivablauf mit
Undo-Zeitfenster. Datensatz und Originaldateien bleiben wiederherstellbar.
Ältere Löschaufträge erhalten einen sichtbaren Prüfpfad: aktuellen Stand behalten
oder den tatsächlich geprüften Eintrag archivieren. Die Originalaktion bleibt in
der Entscheidungshistorie. Ausgestellte Rechnungen und Beziehungen bleiben geschützt.

Der ergänzende Web-/Drive-Importer liest die neue veröffentlichte Pixel-Sicht,
verarbeitet dieselben Vorgänger und Regeln und darf keinen unvollständigen
Gesamtstand zurückexportieren. Lokales Lesen eines gemounteten Drive-Ordners
behauptet ausdrücklich keine unabhängige Google-Cloud-Bestätigung.

## Kundenakte und Pflanzenbilder

Die Kundenakte besitzt vier Bereiche: Übersicht, Garten, Fotos/Dateien und Verlauf.
Kontakt und Fotoaufnahme sind direkt erreichbar. Aufträge, letzte Arbeiten,
Rechnungen und eine kleine Fotovorschau halten die Übersicht kompakt. Größere
Verläufe und Bildlisten werden portionsweise geladen.

Gartengröße nutzt das vorhandene Feld `garden_area`. Rasenfläche, Heckenlänge,
Bewässerung, Pflege- und Zugangshinweise ergänzen bestehende Kunden-Zusatzfelder.
Fremde Zusatzfelder bleiben erhalten. Unbekannte Flächen bleiben null; null und
explizit 0 sind unterschiedliche Vergleichsstände. Native/Python-Regeln prüfen
Zahlen und Beziehungen der Flächen. Formulare behalten ihre Ausgangsversion und
lokale Entwürfe auch nach fehlgeschlagenem Speichern und Neustart.

Kundenfotos behalten ihre Originalbytes, feste Kundenzuordnung und optionale
Arbeits-/Vorher-Nachher-Zuordnung. Kleine Vorschaubilder ersetzen kein Original.
Noch nicht gespeicherte Dateien werden im bestehenden privaten Medien-Entwurfsspeicher
gesichert. Nach Abbruch/Neustart ist Wiederaufnahme möglich; dauerhafte native
Foto-Request-Belege verhindern doppelte Datensätze nach einem verlorenen Rückruf.
Der Dateibereich bündelt Originalfotos und vorhandene Rechnungsbelege.

Pflanzenbilder wechseln durch horizontales Wischen bei 100 %. Vergrößerte Bilder
werden verschoben, zwei Finger zoomen. Kurze, vertikale oder abgebrochene Gesten
wechseln kein Bild. Pfeile, Bildzähler, Quellen und Zurück bleiben erhalten. Die
181 Profile/362 Offlinebilder sind unverändert; Webspiegel wurden aus der
verbindlichen gemeinsamen Quelle erzeugt.

## Tatsächliche Abnahme

| Prüfung | Ergebnis / Grenze |
| --- | --- |
| Python | 253 Tests bestanden; 37 ältere QA-Signierungstests mit Schlüsselerzeugung ausdrücklich ausgeschlossen |
| JVM | 13 Suiten, 460 Prüffälle bestanden; synthetische JSON-/HTTP-Gegenstellen |
| Browser | 17 ausgewählte Suiten bestanden; darunter 14 neue Prüfungen der Kundenakte, Fotoentwürfe, Konfliktentscheidung, Fortschritt und Bildgesten |
| Touch | Tatsächliche Chromium-Touch-Ereignisse zusätzlich zu gezielten Pointer-/Abbruchfällen geprüft; keine Pixel-Messung |
| Darstellung | 320/360/426 px und 150 % Schrift; Namen-/Titelhöhe zusätzlich zum Überlauf geprüft, Screenshot visuell kontrolliert |
| Rendervergleich | 3.000 synthetische Arbeiten, 50 sichtbare Zeilen, keine versteckten Abrechnungszeilen; 38 ms Median/4.929 DOM-Knoten im Cloud-Chromium |
| Android | Release-Build, Release-Lint und Android-Testquellen-Kompilierung bestanden; null Lint-Fehler, elf Warnungen |
| Native Abnahme | Neue isolierte Foto-Replay-/Archiv-Abnahmen vorbereitet und kompiliert, nicht auf Android ausgeführt |
| Originalsignierung | Paket/Zertifikat, v2/v3, Ausrichtung, Quellassets und Cleanup tatsächlich geprüft; maschinenlesbarer Nachweis unten |
| Google/Pixel | Neue Anmeldung, realer Google-Abgleich, OS-Hintergrundverhalten, Installation und Update/Datenerhalt nicht ausgeführt |

Maschinenlesbarer Nachweis: [sync-customer-gallery-1.48.json](verification/sync-customer-gallery-1.48.json).
Der APK-Kandidat ist nicht als Geräte-Release freigegeben.

## Externe Voraussetzungen und Einführungsreihenfolge

1. Die gemeinsame Web-/Drive-Verarbeitung aus dieser Branch bereitstellen und
   vorhandene fachliche Capabilities beibehalten. Rechnungsabschluss benötigt
   weiterhin den vorhandenen zentralen Nummernservice; eine Offline-Vormerkung
   ist keine ausgestellte Rechnung.
2. Bestehende Daten einschließlich Originalen/Warteschlangen vollständig sichern.
   Installierte Paketversion und Originalzertifikat prüfen. Code 68 liegt über
   dem vorherigen Cloud-Kandidaten 67; die installierte Version bleibt unbekannt.
   Installation benötigt weiterhin ausdrückliche Freigabe. Keine Deinstallation.
3. Der Google-Projektinhaber muss Drive API, Android-OAuth-Registrierung mit Paket
   `at.hausgarten.service` und Original-SHA1-Zertifikat sowie Konto-/Consent-Freigabe
   tatsächlich bereitstellen. Der geprüfte öffentliche SHA1 steht im JSON-Nachweis.
   Der Zugriff auf bereits bestehende Dateien benötigt den ausdrücklich am Gerät
   bestätigten Scope `https://www.googleapis.com/auth/drive`; eine reine
   `drive.file`-Freigabe deckt fremd erstellte Bestandsdateien nicht automatisch ab.
   Googles Prüfung eingeschränkter Scopes und Organisationsregeln können die
   Verbindung verhindern. Keine dieser externen Freigaben wurde hier erstellt.
4. Nach zulässiger Installation in Drive-Abgleich → Google Drive verbinden das
   bestehende Konto und denselben Ordner auswählen. Anmeldung/Ordnerinhalt werden
   zuerst gelesen und geprüft; erst anschließend wird die neue Verbindung aktiviert.
5. Erste echte Synchronisation und Medien prüfen; bestehende Konflikte einzeln
   anhand beider Versionen entscheiden. Eine Queue wird nicht pauschal geleert.
   Neue MCP-Gesundheit/Capabilities prüfen; bei Connector-Cache neu verbinden.
   Die 66 Werkzeugnamen bleiben erhalten. Private Tunnel-/Organisationsrechte
   bleiben eine eigenständige, extern zu bestätigende Autorisierungsgrenze.

Diese Cloud erlaubt derzeit keinen allgemeinen direkten Google-API-Egress.
Es wurden keine Netzwerkregeln umgangen und keine neue Google-Verbindung behauptet.
Die neue Android-Anbindung benutzt nach Gerätefreigabe dessen eigenen Netzwerkzugang.
Akku-/Hintergrundbeschränkungen und Netzverfügbarkeit begrenzen Abschlusszeiten.

Grenzen: 20 MiB je Foto/PDF, 40 MiB/zehn Fotos je Auswahl, 64 MiB je JSON-Datei,
20.000 Dateien je gelistetem Ordner, 256-KiB-Drive-Uploadabschnitte; MCP-Dateiblöcke
bleiben auf 48 KiB begrenzt. Überschreitungen, fehlende Originale und beschädigte
Historien werden sichtbar angehalten. Historienbereinigung und Anbieterrechte
werden nicht durch ungeprüfte Löschung ersetzt. Keine ununterbrochene
Verfügbarkeit oder erfolgreiche Geräteaktualisierung zugesichert.


## Fortsetzung 1.49.0 / 69: Angebote, Fotos und MCP

Ausgang: sauberer Cloud-Checkout und GitHub-Connector `1382813`, Paket
`at.hausgarten.service`, 1.48.0/68. Git-Transport lieferte einen älteren Ref
`36e5567`; kein Reset. Live nur lesend: ready/pixel_business_write/write_enabled,
sieben Prüfungen (sechs DELETE_REVIEW_REQUIRED, ein WRITE_CONFLICT),
provider_readback, cloud_confirmed=false. Sichtbarer Connector enthält die alten
Entwurfssperren und keine Owner-Werkzeuge. Installierter Stand laut Nutzeranzeige: **1.48.0 / Build 68**. Der Connector
attestiert weder native Versionsnummer noch installierte Signatur. Keine neue APK als Update freigeben,
bevor installierte Version, Sicherung und Originalsignatur abgeglichen sind.

Abnahmekriterien: gemeinsame Fotoverarbeitung, keine Änderung vorhandener
Originale; 30 Dateien pro Auswahl, sequenziell und fehlertolerant, kein Kundenlimit;
30er Fotoansicht mit mehr als 100 synthetischen Metadaten und späterem Originalabruf.
Alt-Konflikte nur mit Backup, unveränderter Hash-Vorschau und Bestätigung als
keep_current; keine Geschäftsänderung, Originalaktionen und Historie erhalten.
Angebote im bestehenden SQLite-/Snapshot-/Journalweg, Dezimalbeträge, bewusste
Dokumentart, bestätigte Preise, B2B/B2C-Prüfung, PDF und explizite Auftragsübernahme
ohne Rechnung. Rechnungsentwürfe unter Owner-Schreibrecht; Abschluss separat
bestätigt und weiterhin ausschließlich zentral nummeriert. Alle Tests isoliert.

Rechtsquellen am 18.10.2026 tatsächlich abgerufen: [ABGB §1170a](https://www.ris.bka.gv.at/NormDokument.wxe?Abfrage=Bundesnormen&Gesetzesnummer=10001622&Paragraf=1170a),
[KSchG §5](https://www.ris.bka.gv.at/NormDokument.wxe?Abfrage=Bundesnormen&Gesetzesnummer=10002462&Paragraf=5),
[USP/BMF Rechnungsmerkmale](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/weitere-informationen-zur-umsatzsteuer/vorsteuerabzug-und-rechnung/formerfordernisse.html).
Verbindliche Voranschläge gewährleisten ihre Richtigkeit; bei unverbindlichen
Voranschlägen bleiben Anzeige- und gesetzliche Rücktrittsrechte bestehen. Im
Verbraucherfall ist die fehlende Gewähr ausdrücklich zu erklären; ein Entgelt
für die Erstellung eines Voranschlags setzt vorherigen Hinweis voraus. Keine
pauschale Zusatzkosten- oder Haftungsfreizeichnung wird erzeugt. Der Validator
prüft technische Vollständigkeit und fordert bei Fern-/Auswärtsgeschäften
geprüfte Verbraucherinformationen; er kann weder alle FAGG-Ausnahmen noch die
rechtliche Wirksamkeit freier Texte automatisch bestätigen. UID und Steuermodus
sind zentrale Einstellungen, keine Annahmen über den Betrieb.


### Implementierter Stand und Berechtigungsmatrix

Die Erweiterung bleibt im operativen Paket `at.hausgarten.service`. Native lokale
SQLite-Version weiterhin 4; Angebote und Katalog liegen im bestehenden JSON-Gesamtstand
und Journal. Der gemeinsame Python-Importer erhält additive Tabellen in seiner
vorhandenen Datenbank. Snapshot-Schema 2 ergänzt `offers: []`; alte Snapshots ohne
Angebote bleiben lesbar. Interne MCP-Version 3; die MCP-Protokollverhandlung bleibt
für vorhandene Clients kompatibel. Der neue native Schreibkatalog umfasst **83**
Werkzeuge. `integration_health` liefert auch für einen älteren Client native
Version, effektive Schreibfreigabe, Kataloganzahl/-hash und Snapshot-Version.
Widerrufene Schreibfreigabe wirkt bei der Ausführung und im neu gelesenen Katalog.

| Bereich | MCP-Werkzeuge / erlaubte Operationen | Rechte und Schutz | Tatsächlicher Nachweis / Barriere |
| --- | --- | --- | --- |
| Kunden / Garteninformationen | search/get/create/update_customer; archive/restore_entity | Owner-Schreibschalter, aktuelle Hashes; Beziehungen erhalten | Lesen live; Änderungen mit synthetischen Regeln/UI geprüft; native Speicherung nur vorbereitet/kompiliert |
| Aufträge | search/get/create/update_order, archive/restore_entity | Kunde/Empfänger, Hash, Beziehungen | Isolierte Regression; kein produktiver Schreibtest |
| Arbeit / b.b. | list/get/create/update_work_record, set_work_record_billed, reassign_work_record, set_personal_paid | Atomare Rechnungs-/Beziehungsprüfung; b.b. bleibt persönlich | Python/JVM/Browser; native Ausführung offen |
| Zeit / Pause | get_active_work_session, start_work, start_break, end_break, stop_work, complete_work_session | Stabile Sitzung und Request-ID, atomarer Abschluss | Bestehende isolierte Regression; keine Geräte-/Uhrabnahme |
| Fotos / Dateien | list/get_photo, begin_file_upload, write_file_chunk, create/update_photo, read_file_chunk, get_file_metadata, archive/restore_entity | Gleicher privater Fotospeicher; Hash, 48-KiB-Blöcke, 20-MiB-Speichergrenze | 105 Metadaten / 21 sequenzielle Browserimporte mit synthetischer Brücke; Bitmap-/SAF-Gerätetest offen |
| Rechnungen | list/get_invoice, create/update_invoice_draft, request_invoice_issue, prepare_invoice_share, correction/payment | Entwurf unter Owner-Recht; finale Nummer nur zentral nach Bestätigung/Hash; Versand und Zahlung getrennt | Entwurfs-/Steuer-/Nummernregeln isoliert geprüft. Live-Katalog enthält zwei alte gesperrte Entwurfsbeschreibungen; request_invoice_issue fehlt dort |
| Angebote | list/get/create/update/validate/finalize/duplicate_offer, prepare_offer_share, convert_offer_to_order, get_offer_context | Request-Belege, Record-/Kontexthash, bewusste Dokumentart/Preise/Bestätigung; keine Rechnung | Python/JVM/Browser bestanden; native PDF/SQLite-Abnahme kompiliert; **kein** Angebotswerkzeug im verbundenen Katalog sichtbar |
| Leistungs- / Textkatalog | search_offer_templates/get_offer_template, search_catalog(service), get_catalog_entry, create/update_catalog_entry | Editierbar, Hash, Preise zunächst unbekannt und unbestätigt | Isoliert geprüft; Veröffentlichung/Connector-Einführung offen |
| Pflanzen / Neophyten / Suche | search_catalog/get_catalog_entry, Dateiblöcke, search_business_data | Bestehende öffentliche Katalogdaten; private Betriebsdaten bleiben untrusted | 181 Profile / 362 Bilder erhalten, Browserregression einschließlich Wischen |
| Sync / Konflikte | get_sync_overview, request_sync, get_sync_conflicts, resolve_sync_conflict, preview/prepare_backup/close_legacy_conflicts | Exakte Aktions-/Datensatz-/Vorschauhashes, Backup vor Sammelentscheidung; keine Queue-Löschung | Sieben synthetische Fälle geprüft; live sieben unverändert aktiv, keine Auflösung ausgeführt |
| Sicherung / Wiederherstellung | create/list/inspect/restore_backup | Kleine private Geschäftssicherung bis 64 MiB; nur fehlende Datensätze, kein Überschreiben; finale Dokumente gesondert | SQLite-/Archivregeln geprüft, native Wiederherstellung nicht ausgeführt; kein Ersatz für Gerätesicherung |
| Einstellungen | get/update_settings, get/update_business_settings | Nicht geheime Felder, Hash; Konten-/Tokenrechte separat | UI für Fotoqualität und Aussteller/Steuer/Zahlungsziel; stale-Hash-/Retry-Prüfung im Browser |

Die neuen Werkzeuge sind im **Quell-/APK-Katalog**, nicht im laufenden ChatGPT-
Connector nachgewiesen. Letzte tatsächliche Lesung: `ready`,
`pixel_business_write`, `write_enabled=true`, **32 sichtbare Pixel-Werkzeuge**,
**0 Angebotswerkzeuge**, kein `get_capabilities` und kein `request_invoice_issue`.
Die ChatGPT-App-Aktionsfreigabe meldet bereits „Allow all actions“. Eine fehlende
pauschale Aktionsfreigabe ist damit nicht als Ursache belegt. Versionsanzeige 68
allein identifiziert den installierten Binärstand nicht. Ein veralteter Runtime-
oder registrierter Werkzeugstand bleibt bis zur Einführung und Neuabfrage zu unterscheiden.

Refresh-Weg nach zulässigem Update: bestehende Pixel-Tunnelverbindung neu starten,
MCP `initialize` und `tools/list` erneut abrufen, in derselben ChatGPT-Verbindung
Werkzeuge aktualisieren beziehungsweise dieselbe App neu verbinden. Kataloghash
mit `get_capabilities` und dem rückwärts erreichbaren `integration_health`
vergleichen. Wenn weiterhin nur 32 Werkzeuge erscheinen, muss die verwaltete
Connector-Registrierung durch deren Kontoinhaber aktualisiert werden. In dieser
Umgebung ist kein editierbares Manifest oder autorisierter Veröffentlichungs-
Endpunkt dieses verwalteten Connectors vorhanden. Keine Registrierung wurde als
aktualisiert ausgegeben.

### Angebote und Rechnungen

Vier bewusste Dokumentarten: Fixpreis, Regie, verbindlicher und unverbindlicher
Kostenvoranschlag. Gemeinsamer editierbarer Katalog: 19 Leistungskategorien und
13 Textbausteine, ohne erfundene Standardpreise. Eindeutiger Auftrag und Einsatzort
werden übernommen; Empfänger bleibt getrennt. Mengen, Preise, Steuer und Summen
werden mit Dezimalrundung geprüft. Unvollständige Preise/Steuersätze bleiben im
Entwurf unvollständig. Finalisierung bindet Kunde, Empfänger, Auftrag, Aussteller
und Katalog an den geprüften Kontext. Ein Angebot erhält seine eigene eindeutige
Angebotsnummer; es verwendet keinen Rechnungsnummernkreis. PDF separat und
unveränderlich; Annahme/Übernahme in neuen oder versionierten bestehenden Auftrag
ist ausdrücklich und erzeugt keine Rechnung. „Mit ChatGPT“ stellt einen kopierbaren
Auftragstext bereit und sendet keine Nachricht automatisch.

Steuermodus, UID, Befreiungstext, Zahlungsziel und Aussteller sind zentrale
Einstellungen und in der App editierbar. Für gewöhnliche Inlandsrechnungen über
10.000 EUR im Standard-Steuermodus wird der bestätigte Empfängertyp verlangt,
bei Unternehmen zusätzlich dessen UID. Rechnungsabschluss bleibt im zentralen
Nummernservice; keine Offline-Nummer, kein automatischer Versand oder Bezahltstatus.
Vorhandene festgeschriebene/sevDesk-Belege werden durch Entwurfwerkzeuge nicht geändert.

Zusätzlich geprüft: [FAGG, geltende Gesamtfassung](https://ris.bka.gv.at/GeltendeFassung.wxe?Abfrage=Bundesnormen&Gesetzesnummer=20008847),
insbesondere §§4 und 11. Bei Verbraucher-Fern-/Auswärtsgeschäften verlangt der
Validator geprüfte Informationen, Aussteller-Kontaktangaben und eine bewusste
Bestätigung. Ausnahmen, frühzeitiger Leistungsbeginn und freie Vertragstexte
bleiben fachlich zu prüfen; keine automatische rechtliche Freigabe.

### Foto- und Medienstrategie

Neue Fotos durchlaufen denselben nativen Speicherpfad aus UI und MCP. Standard:
lange Kante bis 3072 px, JPEG zunächst Qualität 84, bei Bedarf 80/76; Ziel meist
höchstens 3 MiB, kein garantiertes Dateigrößenversprechen. „Hoch“ bis 4096 px;
Profilbilder bis 1024 px. Kleine Bilder behalten ihre codierten Pixel; unterstützte
EXIF-/Textmetadaten werden entfernt, Orientierung wird berücksichtigt. Explizit
„Original behalten“ bewahrt Originalbytes und Originalmetadaten. Vorhandene
Originaldateien werden nicht nachträglich verändert. Hash immer über gespeicherte
Bytes; Ursprungsname, Größenangaben und Aufnahmezeit bleiben separate Metadaten.

Kein Foto-Gesamtlimit pro Kunde. 30 Dateien **pro Auswahl**, danach weitere Auswahl;
sequenziell, einzelne Fehler als wiederholbare Entwürfe. Nur der Nutzer kann einen
ungespeicherten Entwurf gezielt entfernen. Browser-Vorverkleinerung für Dateien
über 20 MiB bis 50 MiB; abschließende native Prüfung weiter bis 20 MiB je Datei.
MCP erhöht sein Dateilimit nicht. Bibliothek lädt 30 kleine Vorschaureferenzen je
Seite, Originale erst beim Öffnen; Rollen-, Zeitraum-, Auftrag-/Einsatzfilter.
Fotozahl und anhand eindeutiger Dateien gezählter Speicher im Kundenkonto,
lokale Original-/Vorschaubelegung und verfügbarer Speicher in Einstellungen.
Keine automatische Löschung. Timerfotos tragen eigene customer/order/session_id;
der atomare Sitzungsabschluss verbindet die endgültige worklog_id.

Originale, Vorschaubilder und PDF-Dateien getrennt von JSON-Journalen. Ein Retry
prüft SHA/Größe und vorhandene Dateien. Nach versuchter Veröffentlichung wird
ein bereits geprüfter SAF-Upload bei verlorenem Rücklesen **nicht gelöscht**.
Mehrdeutige Originaldateien werden sichtbar zurückgehalten. Medienfehler lassen
andere gültige Geschäftsaktionen weiterlaufen. Fehlende PDFs/Originale erhalten
keine vollständige Sync-Bestätigung. Neue Angebot-/Katalogaktionen bleiben mit
`SYNC_COMPONENT_UPDATE_REQUIRED` lokal erhalten, bis der gemeinsame Importer
`offers_v1`/`service_catalog_v1` tatsächlich meldet. Bestehenden Importer zuerst
bereitstellen; diese Aufgabe hat keinen produktiven Dienst ausgerollt.

### Historische Prüfungen und reale Grenzen

Letzte Live-Lesung: **aktiv 7**, davon sechs `DELETE_REVIEW_REQUIRED`, ein
`WRITE_CONFLICT`; **Upload ausstehend 0**, `cloud_confirmed=false`,
`confirmation_scope=provider_readback`. Keine der sieben Aktionen angewendet,
gelöscht oder als erledigt ausgegeben. Ihre Ursache wurde nicht aus Timerfehlern
abgeleitet. Vorschau/Schreibschutz sind implementiert: private konsistente
SQLite-Geschäftssicherung, lokale Medien/Dokumente, aktueller Drive-Snapshot,
gebundene Hashliste, einmalige Bestätigung, bestehendes `keep_current` und
Konflikthistorie. Originalaktionen bleiben bis zum bestätigten Abgleich erhalten.
Die spezielle Geschäftssicherung umfasst keine WebView-Browserentwürfe und keine
vollständige Android-Gerätesicherung; diese sind separat zu sichern. Ohne lesbare
Originale/Drive-Snapshot, bei laufendem Timer oder veränderten Hashes wird blockiert.
Ein später geänderter Aktionshash macht die alte Entscheidung unbrauchbar und wird
sichtbar erneut blockiert. Keine produktive Sicherung oder Sammelentscheidung ausgeführt.

Tunnel-/Organisationsverwaltung ist nicht durch einen erfolgreichen Lesezugriff
attestiert. Android-/Google-Kontofreigabe, persistente Dokumentbaumrechte,
OAuth-Projektregistrierung, Netz und Akku-/Hintergrundbetrieb bleiben separat
zu prüfen. Direkter Cloud-API-Egress ist eingeschränkt; ein funktionierender
GitHub-/Drive-Connector ersetzt ihn nicht. Kein neuer Parallel-Tunnel, kein
401-Umgehen und keine unbegrenzten Wiederholungen. Google-/Pixel-End-to-End,
Installation, Fresh-Install und In-place-Update/Datenerhalt bleiben offen.

### Tatsächlich ausgeführte Prüfungen 1.49

265 Python-Tests bestanden, keine Skips; die 37 alten schlüsselerzeugenden
QA-Signierungstests ausdrücklich ausgeschlossen. 14 JVM-Suiten / 554 Fälle
bestanden, darunter 75 neue Angebots-/Rechte-/Konflikt-/Foto-Regelprüfungen und
Android/Python-Vergleiche von Angebotsnormalisierung, Kontexthash und Auftragstext.
21 vorhandene Browser-Suiten bestanden, zusätzlich acht Integritätsprüfungen,
14 Sync-/Kundenakten-/Wischprüfungen und zwölf neue Angebots-/Foto-/Einstellungs-
prüfungen. Alle verwenden isolierte synthetische Daten beziehungsweise native
Brückenattrappen. 105 Foto-Metadaten sowie 21 gültige und eine fehlerhafte Datei
in einer Auswahl geprüft; das ist kein nativer Test mit 100 Originalfotos.

Neuer Vergleich im Cloud-Chromium, 3.000 Arbeiten: 50 ms Median für fünf Render-
Wiederholungen, 50 sichtbare Arbeitszeilen, null versteckte Abrechnungszeilen,
4.952 DOM-Knoten. Ausgangsmessung 454 ms / 47.729 Knoten; keine Pixel-Messung
und kein streng lastisolierter Gerätebenchmark. 320/360/426 px und 150 % Schrift
geprüft; zusätzliche Screenshots visuell kontrolliert und schmale Filter/Tabs/
Aktionen nachgebessert.

Release-Build, Release-Lint und Android-Testquellen-Kompilierung bestanden;
null Lint-Fehler, zwölf Warnungen. Native Bitmap-/Rotations-/Thumbnail-, 21-Foto-
Wiederanlauf-, Sitzungszuordnungs-, PDF- und SAF-Fehlerabnahmen sind vorbereitet
und kompiliert, **nicht auf Android ausgeführt**. Kein produktiver Schreibtest.
Bestehende Tests und ursprüngliche App-Funktionen bleiben erhalten; kein
Parallelcode wurde ohne Abhängigkeitsnachweis entfernt.


### Tatsächliche Originalsignierung und Bereitstellung 1.49

Vor dem echten Signieraufruf ausschließlich das Vorhandensein aller fünf
`HG_ORIGINAL_*`-Variablen geprüft: vollständig. Keine Geheimwerte ausgegeben,
geloggte Konfigurationskopie oder dauerhafte Keystore-Datei. Ausschließlich
`tools/sign_original_apk.py`, vorhandener Schlüssel, keine Ersatz-/QA-/Debug-
Signierung. Ein tatsächlicher Signierlauf; Ausgabe erst nach erfolgreichem Cleanup.

Geprüft: Paket `at.hausgarten.service`, Version **1.49.0 / 69**, minSdk 26,
targetSdk/compileSdk 37, nicht debuggable; 69 > angezeigter installierter 68.
Keystore-Zertifikat und ausgegebener einzelner APK-Signer stimmen mit dem
konfigurierten Original überein. Öffentlicher Zertifikats-SHA256:
`a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`.
Signatur erneut an der endgültigen Ausgabedatei geprüft: v2/v3 true;
v1/v3.1/v3.2/v4 false. 16-KiB-Ausrichtung bestanden. Paket/Zertifikat und höherer
Code zusätzlich gegen den erhaltenen Original-Kandidaten 1.48.0/68 geprüft.
Dieser Vergleich ist kein Zugriff auf die installierte Pixel-Signatur.

402 Quellassets bytegleich, benötigte operative Klassen vorhanden, null operative
Seedzeilen einschließlich Angeboten. Source-/APK-Privacy-Gate bestanden;
`private_markers_checked=0`, da keine Betriebsdaten als Prüfreferenz vorliegen.
Cleanup unabhängig nachgeprüft: null verbliebene private Signierungsordner im
OS-Temp und null Schlüsseldateien nach geprüften Dateiendungen im Repository.

Finale Datei `downloads/Haus-Garten-1.49.0-69-original-update-kandidat.apk`:
34.389.507 Bytes; SHA256
`c14289a0bacae815fca0d23f798c5f24a991dffe62fdeace84c97e9e464edc67`.
Zusätzlicher [Drive-Download](https://drive.google.com/file/d/1PulK2-5KxzVa-o94e_bQ9yKZG_vjXTMy/view)
im verbundenen privaten Google-Konto: als neue Datei hochgeladen und über den
Connector vollständig als Dateireferenz zurückgeladen (34.389.507 Bytes).
Keine öffentliche Freigabe hinzugefügt. Eine erfolgreiche Übertragung oder
Originalsignierung beweist weder Installation noch Updatefähigkeit/Datenerhalt.

## Cloud-only 1.49.2 / 71 (18.10.2026)

Basis a38765d (main enthält sämtliche bisherigen Cloud-Funktionen und 1.49.1-Reparaturen); bestehende Cloud-Branch per Fast-forward vorgezogen. Keine Desktopübernahme. Pixel verarbeitet die bestehenden Sammlungen, Angebote, Katalog, Rechnungseinstellungen, Archiv, Korrekturentwürfe und Eigentümerjournale selbst. Python/Web bleibt als historische Kompatibilitäts-/Testreferenz erhalten, ist kein produktiver Dienst. IDs, Datenbank 4, ursprüngliche Drive-Datei, Journalaktionen, Medien und Regressionstests bleiben erhalten.

Unveränderliche HausGarten-State-/Commit-Dateien im bestehenden Drive-Ordner bilden eine geprüfte Kette. Beim Neustart wird deren letzter Stand eingelesen; Provider und REST nutzen denselben nativen Geschäftsprozessor. Fremder Primär-Pixel, Fork, fehlender Beleg oder eine parallel veränderte Altquelle stoppen. Auf dem Gerät gespeicherter Stand, Provider-Rücklesen und Google-Servermanifest bleiben getrennt. Originalfotos und PDFs liegen außerhalb des JSON, mit SHA256 und separater Vorschau. Server-Dateiversionen vermeiden wiederholtes Herunterladen unveränderter PDF-Originale.

Fehlende business_settings werden ausschließlich aus nachweisbaren invoice_issuer-Feldern ergänzt; Unbekanntes bleibt unbekannt. Keine erfundenen Aussteller-, Steuer- oder Nummernwerte. Explizite Updates prüfen den aktuellen Settings-Hash und erhalten zusätzliche Felder. Der native Vergleichsschutz ist an implementierte Handler gebunden; entfernte Capability-Flags werden nicht mehr als externer Freigabedienst verwendet.

Rechnungsabschluss: neuer Journaltyp pixel_invoice_issue_v1, interne Protokollversion 4; ältere Prozessoren können diesen Auftrag nicht ungeschützt finalisieren. Werkzeuge bleiben namentlich kompatibel (83), Standard-MCP-Protokollverhandlung bleibt erhalten. Vor Nummernvergabe: ausdrückliche Anweisung, unveränderter Entwurf, Arbeitsstände, Aussteller/Empfänger, bestätigte alleinige Pixel-Nummernhoheit und geprüfter Ausgangszähler. Online-CAS reserviert pro Aktionshash dauerhaft genau eine Nummer; Antwortverlust verwendet dieselbe Reservierung. Eine vorgeschaltete negative Vergleichsprobe schreibt nur synthetische Technikdaten in eine eigene Probe-Datei. Fehlt ETag/HTTP-412-Nachweis, bleibt Abschluss blockiert. PDF wird auf dem Pixel separat erzeugt, gespeichert und übertragen, bevor Rechnung und Arbeitsmarkierungen gemeinsam übernommen werden. Versand und Zahlung bleiben eigene Schritte; b.b. bleibt privat.

Offizielle Grundlagen erneut am 18.10.2026 abgerufen: [USP/BMF Rechnungsmerkmale](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/weitere-informationen-zur-umsatzsteuer/vorsteuerabzug-und-rechnung/formerfordernisse.html), [ABGB §1170a](https://www.ris.bka.gv.at/eli/jgs/1811/946/P1170a/NOR12018912), [KSchG §5](https://www.ris.bka.gv.at/NormDokument.wxe?Abfrage=Bundesnormen&Gesetzesnummer=10002462&Paragraf=5). Die bisherigen Vollständigkeitsregeln sind nativ übernommen; technische Validierung ist keine vollständige Rechtsprüfung. Für Drive: [files.update](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/update), [PATCH/ETag-Hinweise](https://developers.google.com/workspace/drive/api/guides/performance). Diese Dokumentation ersetzt keinen tatsächlichen Google-CAS-Nachweis; deshalb prüft der neue Code ihn am technischen Probeobjekt und verweigert andernfalls den Abschluss.

| Bereich | Quelle / autorisierte Operationen | Isolierte Prüfung | Reale Grenze |
| --- | --- | --- | --- |
| Kunden/Aufträge/Arbeiten | Bestehende create/update/get/list, Archiv/Restore; native Vorgängerketten | JVM/Python/Browser PASS | Geräte-SQLite-/Parallelabnahme offen |
| Zeit und b.b. | Bestehende Sitzungsfunktionen und persönliche Markierung | Bestehende synthetische Regression PASS | Kein Live-Timer verändert |
| Fotos/Dateien | Bestehende Uploadblöcke/create/update/photo/file; private Originale, Optimierung, Pagination | 105 Metadaten/21 Importe im Browser PASS; native Tests kompiliert | Kamera/Bitmap/echter Drive-Upload offen |
| Angebote/Katalog | Alle vorhandenen Angebots- und Bausteinwerkzeuge, native Journale | JVM/Python/Browser PASS, 4 Dokumentarten | Live 69 blockiert noch den Angebotsabgleich |
| Rechnungen/Settings | Entwurf/CAS, Konfiguration, Korrektur, Zahlung; geschützter nativer Onlineabschluss | CAS/Crash/Replays/Rechnungsrennen synthetisch PASS; native PDF-Abnahme kompiliert | Nummernhoheit und reale Google-CAS-Probe nicht bestätigt |
| Sync/Konflikte/Backup | request_sync, get_sync_overview mit Sammlungshashes; keep_current mit Backup und Originalhash | Synthetische sieben Fälle und technische Supersession PASS | Live 9 unverändert; Backup/Update-/Drive-Abnahme fehlen |
| Pflanzen/Suche/Einstellungen | Bestehende Navigation, Such-/Katalog-/Dateifunktionen erhalten | Kanonischer Katalog und Browserregression PASS | Native Kamera-/Offline-Geräteabnahme offen |

Live bestätigt: 1.49.0/69, Protokoll 3, ready, write_enabled, 83 registrierte Werkzeuge. Die Angebots-/Rechnungswerkzeuge sind inzwischen sichtbar, die installierten Handler weiterhin alt. Angebote/Katalog melden false, Settings fehlen, Android-Provider statt direktem REST. Kein Connector-Manifest im Repository und kein verwalteter Veröffentlichungsendpunkt verfügbar. Nach freigegebenem Update private Verbindung neu starten, initialize/tools/list aktualisieren und Quell-Kataloghash/Protokoll 4 vergleichen; notwendige Konto-/Connector-Freigaben kann Quellcode nicht erteilen.

Die sieben historischen Aktionen und zwei technischen Fehler wurden nur gelesen. Vorschau genau sieben, keine Löschung und keine Konfliktentscheidung ausgeführt. Neue Bulk-Sicherung erfordert zuerst direkten Drive-Serverbeleg und ausschließlich sieben historische Restfälle; sie umfasst aktuelle Pixel-Veröffentlichung und Original-Altdatei. Keine Geschäfts-MCP-Sicherung ersetzt die vollständige Gerätesicherung. Gesamtauftrag wegen fehlender nativer End-to-End-Abnahme ausdrücklich nicht abgeschlossen. Fortlaufende Testergebnisse, Queue-Hashes, Kandidatenprüfung und verbleibende P0/P1 stehen ausschließlich im zentralen CODEX-STATUS.

## Konzept: Aufträge, Termine, Google Kalender und Wetter (19.10.2026)

**Planungsstand, keine Implementierung oder Konto-/Kalenderänderung.** Geprüfte Quelle: Cloud-Branch `codex/original-signing-cloud-setup-20261018`, Commit `8b23ab8151131303e35fa6203a9f771dc94dcc8d`, App 1.49.6/75. Der zuletzt belegte Pixelstand 1.49.5/74 ist in dieser Konzeptphase nicht erneut abgefragt. Frühere Versions-/Abnahmeangaben oben sind historische Nachweise, keine Freigabe dieses Konzepts.

### Bestand und Zielablauf

`OfferTools`/`OfferJournalRules` bieten bereits `convert_offer_to_order`: finalisiertes Angebot ausdrücklich annehmen, Kunde/Einsatzort/Leistungen übernehmen, `offer_id` und `converted_order_id` erhalten, keine Rechnung erzeugen. Ein vorhandener Auftrag kann derzeit nur ohne bisherige Angebotsreferenz verknüpft werden. `scheduled_date` ist ein optionales Auftragsdatum ohne Uhrzeit oder mehrere Einsätze. `workflow-ui.js` bündelt Arbeit/Planung im Mehr-Menü; `DriveSyncJob` nutzt bereits den Android-JobScheduler. Die untersuchten operativen Pfade enthalten noch keine strukturierte Termin-, Google-Calendar- oder Wetterfunktion.

Zielkette: **Kunde → Angebot → Auftrag → ein oder mehrere Termine → tatsächlich erfasste Arbeiten → getrennte Abrechnung.** Besichtigungen und Aufträge ohne Angebot bleiben möglich. Ein Termin ist eine Planung, kein Arbeitsnachweis und keine Annahmeerklärung für ein Angebot.

- Im angenommenen Angebot eine Hauptaktion „Auftrag öffnen / Einsatz planen“. Bestehende Auftragsreferenz zuerst verwenden; neue Request-ID darf bei erneutem Antippen keinen zweiten Auftrag für dieselbe Übernahme erzeugen.
- Im Auftrag kompakt: vereinbarte Leistungen/Angebotsreferenz, nächste Termine, geplante und tatsächlich erfasste Stunden, Fotos und offene Aufgaben. „Angebot erstellen“ übernimmt eindeutigen Kunden, Rechnungsempfänger und Einsatzort; keine Preise erfinden.
- Ein Auftrag kann mehrere Einsätze besitzen. Angebotspositionen können auf Einsätze verteilt werden; Fixpreis und Regie bleiben unverändert. Ein Angebot über 14 Stunden erzeugt weder automatisch 14 Ist-Stunden noch eine Rechnung.
- Hauptangebot weiter über `offer_id`; ausdrücklich angenommene Nachträge bei Bedarf als additive Referenzen mit Angebots-ID, Rolle und angenommenem Versionshash. Finalisierte Dokumente nicht nachträglich umschreiben. Änderungen des Leistungsumfangs werden sichtbar geprüft, nicht rückwirkend in Zusagen übernommen.
- Angebots-Zeitraum als Planungsgrenze übernehmen, nicht als erfundene exakte Uhrzeit. „Vorgemerkt“ und „mit Kunde bestätigt“ getrennt halten; interne Bestätigung dokumentiert eine bekannte Zusage und sendet keine Nachricht.

### Einfache Oberfläche

Die untere Navigation Heute/Kunden/Arbeiten/Mehr bleibt bestehen. **Heute** zeigt nach dem aktiven Timer den nächsten Termin und höchstens einen zusammengefassten Wetterhinweis. „Planung“ im vorhandenen Mehr-Bereich Arbeit & Planung öffnet eine gemeinsame Ansicht mit **Tag / Woche / Stunden**; die bestehende Wochenstundenfunktion bleibt als Ist-Auswertung erhalten. Kein zusätzliches paralleles Kalender-Modul im Hauptmenü.

- Standard eine gut lesbare Wochenagenda; optional Zeitraster. Karte: Uhrzeit, Kunde, Tätigkeit, Ort, Terminstatus und verständlicher Wetterhinweis. Ungeplante Aufträge stehen in einem einklappbaren Bereich, getrennt von Sync-Aufträgen.
- Kundenkonto, Auftrag und angenommenes Angebot öffnen denselben Termin-Editor, bereits mit festen IDs und Kontext. Schnellerfassung benötigt im Normalfall nur Datum, Uhrzeit und Dauer; Ort/Tätigkeit werden nur bei Widerspruch erneut gefragt. Ziel: bekannter Auftrag in höchstens drei Aktionen ab Kontextansicht einplanen.
- Mehrtägige Arbeit in einzelne Tageseinsätze aufteilen, damit Pausen, Wetter und freie Zeiten richtig geprüft werden. Wiederkehrende Pflege mit Vorschau und begrenztem Planungshorizont, Ausnahmen pro Termin; eine Verschiebung ändert nicht ungefragt die ganze Serie. Vor automatischer Fortsetzung bestehende Serienvorgaben und Dubletten prüfen.
- Termin öffnen: „Arbeit starten“, „Verschieben“, „Absagen“, weitere Details im Aktionsmenü. Start nutzt vorhandene Zeiterfassung und deren Kundenzuordnung; laufenden Timer nicht ersetzen. Tatsächlicher Abschluss erfolgt weiter in der App, Fotos laufen über die vorhandene Sitzung-/Arbeitszuordnung.
- Wischen zwischen Tagen ist möglich, aber jede Aktion auch per Schaltfläche erreichbar. Verschieben im Raster zeigt vor dem Speichern Zeit und Konflikte. Tastatur, lokale Entwürfe, Zurück und aktive Zeiterfassung bleiben erhalten.

### Datenmodell und gemeinsame Regeln

Neue Sammlung `appointments: []` im **bestehenden** SQLite-/Snapshot-/Drive-Journal. Alte Snapshots ohne Sammlung werden als leer gelesen. Keine zweite operative Datenbank. Erforderliche native Migrationen additiv und transaktional; Datenbank-/Snapshot-Version erst anhand der tatsächlichen Änderungen festlegen. Alte Verarbeiter dürfen neue Sammlungen weder verlieren noch unbekannte Aktionen bestätigen; Capability `appointments_v1` erst nach implementierten und geprüften Regeln aktivieren.

Termin enthält mindestens: `id`, `customer_id`, optional `order_id`, expliziten Zweck (Arbeit/Besichtigung), Titel/Leistungsreferenzen, Beginn/Ende als UTC-Zeitpunkte plus IANA-Zeitzone (Standard Europe/Vienna), geplante Arbeitsdauer getrennt von Pausen und Anfahrt, Einsatzort/bestätigte Koordinaten, Zeitfenster/Flexibilität, Wetterprofil, Status, optionale Serien-/Ausnahme-ID, Erstell-/Änderungszeit und Version. Status: Entwurf/vorgemerkt/bestätigt/erledigt/abgesagt; Wetterbewertung und Kalender-Transportstatus sind davon unabhängig. Eine ausdrückliche Terminbestätigung ist keine gesendete Kundenbestätigung.

`scheduled_date` bleibt als historisches Feld erhalten. Alte reine Datumsangaben erscheinen zunächst als „Zeit offen“ und werden nicht automatisch zu verbindlichen Kalenderterminen. Neue Terminansichten verwenden die Termin-Sammlung; kein zweites manuell zu pflegendes Termin-Datum am Auftrag. Vorhandene Auftrags-/Kundenbeziehungen und Rechnungsempfänger erhalten. Keine Zuordnung anhand des zuletzt geöffneten Kunden.

Geplante gemeinsame Bausteine: `AppointmentRules` und `AppointmentJournalRules` für UI/MCP/Drive; `CalendarAdapter` und reine `WeatherRiskRules` für externe Abbildung bzw. nachvollziehbare Risikobewertung. Terminspeicherung, Journalaktion, dauerhafter Request-Beleg und Kalender-Übertragungsabsicht gemeinsam in einer lokalen Transaktion. Stabile IDs/Request-IDs, Ausgangshash vom Formularöffnen, gezielte Änderungen und aktuelle Beziehungs-/Belegprüfung. Sich überschneidende eigene Termine werden bei Speicherung erneut geprüft; externer Free/Busy-Abruf kann fremde Kalenderänderungen nicht atomar reservieren und wird mit Prüfzeitpunkt ausgewiesen.

### Google Kalender

Empfehlung: direkter Calendar-API-Adapter auf dem Pixel, eigener Kalender **„Haus & Garten – Einsätze“**, automatisch nach ausdrücklich bestätigter Einrichtung. Die App bleibt maßgeblich für fachliche Daten; Google Kalender ist die verbundene Terminansicht. Keine zweite Kunden-/Auftrags-Cloud und kein Codex-Dauerserver. Der Android-Calendar-Provider wird nicht zusätzlich als konkurrierender Schreibweg eingeführt.

Minimaler Einrichtungsscope `calendar.app.created` für den von der App erstellten Kalender. Optional separat `calendar.freebusy` für eigene weitere Kalender bzw. `calendar.events.freebusy` für zugängliche Kalender; Kalenderliste nur bei benötigter Auswahl mit zusätzlichem `calendar.calendarlist.readonly`. Fremde Kalender dienen zunächst nur als Belegtzeiten. Ein schon vorhandener, nicht von der App erstellter Zielkalender benötigt gesonderte tatsächliche Schreibrechte und passende Scopes; dies wird nicht aus einer Drive-Freigabe abgeleitet. [Offizielle Scopes](https://developers.google.com/workspace/calendar/api/auth).

- Vorgemerkte Termine sind als solche gekennzeichnet; bestätigte Termine blockieren die eigene Arbeitszeit. Externe Kalender berücksichtigen Busy-Zeiten, konfigurierbare Arbeitszeiten, Pausen, Urlaub und Anfahrtpuffer. Ohne Routendienst nur ausdrücklich eingestellter oder als Schätzung gekennzeichneter Puffer, keine erfundene exakte Fahrzeit.
- Verknüpfung über Termin-ID und private Event-Metadaten; clientseitige stabile, Google-konforme Event-ID, gespeicherte Kalender-ID/ETag. Antwortverlust: dieselbe ID nachlesen, keinen neuen Termin anlegen. ID-Kollision erst inhaltlich prüfen. [Events.insert](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert).
- Rücklesen über vollständigen Erstabgleich und danach `syncToken`, einschließlich gelöschter Events. HTTP410 setzt ausschließlich den Calendar-Spiegel/Token zurück, niemals Geschäftsdaten oder ausstehende lokale Absichten. Pagination vollständig beenden, bevor neuer Token gilt. [Inkrementeller Abgleich](https://developers.google.com/workspace/calendar/api/guides/sync).
- Externe Änderungen von Uhrzeit/Ort an verknüpften Events anhand der letzten gemeinsamen Basis prüfen und über dieselben App-Regeln als Journaländerung übernehmen. Gleichzeitige Änderung desselben Feldes: beide Varianten erhalten, sichtbare Entscheidung. Änderungen in Google erzeugen keine neuen Kunden, Preise oder Rechnungszustände. Fremde Termine werden nicht automatisch zu Aufträgen.
- Updates/Löschungen mit ETag/If-Match; HTTP412 führt zum erneuten Lesen und sichtbaren Konflikt, nicht zum blinden Retry. Externe Löschung meldet „Kalendereintrag entfernt – Termin prüfen“, löscht weder Auftrag noch Arbeit und erzeugt den Eintrag nicht still erneut. App-Absage bewahrt die Termin-/Auftragshistorie und entfernt den verbundenen Event nachvollziehbar. [Versionsschutz](https://developers.google.com/workspace/calendar/api/guides/version-resources).
- Keine ungefragten Kundeneinladungen, E-Mails oder Serienänderungen. Standard private Events mit knappen Arbeitsangaben; Umfang von Kundennamen/Adresse bewusst einstellbar, keine Rechnungs-/Gartennotizen oder Fotos im Event. Einladung und Änderung einer Kundenzusage sind eigene ausdrücklich ausgelöste Schritte.

Kalenderabgleich und Drive benutzen eigene begrenzte Transportaufträge innerhalb derselben Datenhaltung. Ein Calendar-/Wetterfehler blockiert keine Arbeits-/Kunden-/Fotoübernahme. Keine verteilte Atomarität zwischen SQLite, Drive und Google behaupten: nur lokal atomar, danach wiederaufnehmbare Übertragung mit Versions-/Request-Belegen. Veraltete Übertragungsabsichten überspringen nicht die Historie, sondern werden nachvollziehbar durch ihren bestätigten Nachfolger ersetzt. Konflikt am Termin stoppt nur dessen weitere Veröffentlichung.

Anzeigen unterscheiden **lokal gespeichert / Drive bestätigt bzw. Provider zurückgelesen / Google-Kalender bestätigt / Prüfung nötig**. Ein Calendar-Erfolg bestätigt keinen Drive-Snapshot. Gerätebindung des einzigen operativen Herausgebers auch nach Restore beachten; keine zweite gleichzeitig publizierende App. OAuth-Tokens bleiben privat auf dem Gerät und außerhalb von Geschäftssnapshots und MCP.

### Wetter für die Arbeitswoche

Primäre Vorhersage: **Open-Meteo mit für den Betrieb zulässiger gewerblicher Lizenz**; 14 Tage abrufen, um sowohl die nächsten fünf eingestellten Arbeitstage als auch die vollständige nächste Montag-bis-Freitag-Woche abzudecken. Eigene Arbeitstage/Ferien berücksichtigen; außerhalb tatsächlicher Datenabdeckung „noch keine Vorhersage“. Die API bietet bis zu16 Tage und stündliche Parameter; die freie API ist nicht für den gewerblichen Produktivbetrieb freigegeben. Kein Abonnement abschließen und keinen API-Schlüssel anfordern, bevor Kosten/Einrichtung ausdrücklich freigegeben sind. Ohne Wetterfreischaltung bleibt die gesamte manuelle Terminplanung nutzbar. [API](https://open-meteo.com/en/docs), [Nutzungsmodell](https://open-meteo.com/en/pricing).

Amtliche österreichische Warnungen ergänzend aus **GeoSphere Austria**, räumlich/zeitlich passend zum Einsatz, unter der ausgewiesenen CC-BY4.0-Lizenz mit Herkunftshinweis. Amtliche Warnung und eigene Arbeitsbewertung getrennt darstellen. Keine Annahme, dass fehlende amtliche Warnung gutes Arbeitswetter bedeutet. [Datensatz und Warn-API](https://data.hub.geosphere.at/en/dataset/warnungen-v1).

Bewertung jeweils für Ort und tatsächliches Terminintervall, nicht nur ein Tages-Symbol:

| Profil | Relevante Hinweise |
| --- | --- |
| Rasenpflege / Bodenarbeit | Niederschlagsmenge/-wahrscheinlichkeit, länger anhaltender Regen und mögliche Bodennässe; Vorregen nur bei vorhandenen Daten, Modellbodenfeuchte ersetzt keine Prüfung vor Ort |
| Hecken-/Gehölzpflege | Böen, Niederschlag, Gewitter-/Windwarnung; höhenbezogene Arbeit gesondert bewerten |
| Pflanzung / Bewässerung | Frost, Hitze, Niederschlag, verfügbare Ausweichzeiten; keine automatische Bewässerungssteuerung |
| Besichtigung / wetterarme Tätigkeit | Nur tatsächlich relevante Beeinträchtigungen; keine pauschale Regen-Absage |

Wetterprofil aus Leistungsbausteinen vorschlagen und editierbar halten, gemischte Tätigkeiten getrennt prüfen. Bei unbekannter Tätigkeit/Ort keine beruhigende Freigabe erfinden. Versionsfähige Regeln mit anpassbaren Schwellen für Regen, Böen, Frost und Hitze; Schwellen sind betriebliche Planungshinweise, keine Sicherheitsfreigabe oder Rechts-/Maschinenvorgabe. Modellzeitraum, Datenalter und fehlende Werte immer Teil der Bewertung. Fernere Tage ausdrücklich unsicherer kennzeichnen; keine vorgetäuschten Prozentwerte zur Prognosegüte.

Vier leicht verständliche Ausgaben: **kein erkennbares Wetterhindernis / erschwert / kritisch – prüfen / Wetter unbekannt**; zusätzlich Text und Symbol statt Farbe allein. Beispiel nur zur Darstellung: „Dienstag10–13Uhr: Rasenarbeit voraussichtlich erschwert durch Regen. Mittwochvormittag als Alternative prüfen.“ Vorschläge berücksichtigen freie Dauerblöcke, Anfahrtpuffer, Arbeitszeiten, Auftragsfristen und Kundenverfügbarkeit. Sie begründen ihre Rangfolge; unbekannte Verfügbarkeit bleibt unbekannt. Kein automatisches Verschieben, Absagen, Kundenkontaktieren oder Anpassen des Auftragswertes.

### Aktualisierung und Benachrichtigungen

- Wochenüberblick als Ziel morgens/abends aktualisieren; für Termine innerhalb48Stunden etwa alle3Stunden, innerhalb24Stunden nach Möglichkeit stündlich. Zusätzlich bei Termin-/Ort-/Tätigkeitsänderung, beim Öffnen der Planung, nach wiederkehrendem Netz und „Jetzt prüfen“. Diese Intervalle sind fachliche Ziele, keine zugesicherte Ausführungszeit.
- Vorhersage je räumlichem Raster/Zeitraum cachen und Anfragen bündeln. Nur erforderliche Koordinaten, Zeit und Wetterparameter übertragen, keine Kundennamen, Texte oder Medien. UTC-Zeitwerte abrufen und mit Europe/Vienna je Zeitstempel umrechnen, damit Sommerzeitwechsel nicht durch einen festen Tagesoffset verfälscht werden.
- Bestehenden JobScheduler-Ansatz mit eigenen begrenzten Job-IDs/Zeitschranken wiederverwenden. Wetterarbeit läuft unabhängig von der Drive-Jobwarteschlange; keine weitere dauerhafte Foreground-Verbindung. Keine Exact-Alarm-Berechtigung für Prognoseabrufe. Ohne dauerhaftes Server-Backend sind sofortige Google-Push-Webhooks nicht die Basis; Abgleich bei Nutzung und OS-geplanter Ausführung genügt, Verzögerung sichtbar halten. [Google-Push-Voraussetzungen](https://developers.google.com/workspace/calendar/api/guides/push).
- Persistenter Wetterhinweis pro Termin/Risikoepisode, Push bei neuem relevantem Risiko oder deutlicher Verschärfung. Wiederholte identische Modellabfragen erzeugen keine neue Meldung. Mehrere leichte Hinweise als Wochenzusammenfassung; kurzfristige amtliche Verschärfungen priorisieren. Ruhezeiten und Benachrichtigungskanäle einstellbar; keine ungefragte Umgehung der Ruhezeit.
- Aktionen: „Termin ansehen“, „Alternative suchen“, „Geprüft“. „Geprüft“ bestätigt Kenntnis und ändert keinen Auftrag; neue Verschärfung darf erneut melden. Nach Absage keine weiteren Terminwarnungen, Verlauf bleibt. Sperrbildschirm standardmäßig ohne Kundendetails.
- Schon vorhandene Manifest-Permission `POST_NOTIFICATIONS` ist kein Nachweis einer erteilten Laufzeitfreigabe. Android13+ benötigt diese tatsächlich; bei Ablehnung Hinweise weiter in der App und klare Aktivierungsoption. Offline/Doze/Akkuoptimierung/erzwungener App-Stopp können Prüfungen verzögern; letzte erfolgreiche Aktualisierung und fehlende Prüfbarkeit sichtbar. [Android-Benachrichtigungen](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
- HTTP401/403: echte Neuautorisierung statt Umgehung;429/temporäre Netzfehler mit Retry-After, Backoff, Jitter und begrenzten Versuchen. Fehler erhalten den Termin und seine Übertragungsabsicht. Alte Wetterdaten bleiben als veraltet gekennzeichnet, niemals automatisch als unauffällig bewertet.

### ChatGPT/MCP und manuelle Gleichwertigkeit

Dies sind **geplante, heute nicht implementierte App-Werkzeuge**:

| Werkzeuge | Zweck und Schutz |
| --- | --- |
| `get_planning_context` | Kunde/Auftrag/Angebotsversion, bekannte Dauer/Ort, Arbeitszeiten, freie Blöcke, Wetterstand und fehlende Angaben kompakt; keine Geheimnisse |
| `list_appointments`, `get_appointment` | Filterbare begrenzte Liste, Version und alle getrennten Status |
| `suggest_appointment_slots` | Read-only Vorschläge mit Gründen und Datenalter; nicht heimlich buchen |
| `create_appointment`, `update_appointment`, `cancel_appointment` | Konkreter Benutzerauftrag, Owner/Write, stabile Request-ID, aktuelle Hashes; Status-/Beziehungs-/Zeitregeln wie UI, keine automatische Einladung |
| `get_workweek_weather`, `get_weather_risks` | Strukturierte Vorhersage und betroffene Termine samt Quellen/Prüfzeitpunkt |
| `acknowledge_weather_alert` | Kenntnisnahme mit Hash/Request-ID, keine fachliche Terminänderung |
| `request_calendar_sync` | Begrenzten Abgleich anfordern; Rückgabe „vorgemerkt“ oder belegter Abschluss, keine falsche Sofortbestätigung |

Beispiel: „Plane Hofers angenommenen Auftrag nächste Woche auf zwei trockene Vormittage.“ Kunden-/Auftragsidentität und vereinbarte Stunden prüfen, bekannte Daten übernehmen, nur fehlende Dauer oder Zusage-Verfügbarkeit erfragen, geeignete freie Blöcke vorschlagen. Buchen auf ausreichende konkrete Anweisung oder Auswahl; bei unbestimmter Anfrage nur Vorschläge. „Wegen Regen verschieben“ verlangt einen eindeutig bestimmten Termin und Zielzeit oder Auswahl. Neue operative Arbeitsaufzeichnungen weiterhin ausschließlich über die App; Kalender/MCP erzeugen keine Ist-Zeiten.

Der in ChatGPT bereits verfügbare allgemeine Google-Calendar-Connector ist eine getrennte Freigabe und besitzt in den sichtbaren create/update-Schemata keinen App-Request-/Record-Hash-Vertrag. Deshalb App-Einsätze über die App-MCP-Werkzeuge verwalten und nicht zusätzlich mit dem allgemeinen Calendar-Connector anlegen. Externe Kalenderbearbeitung bleibt über den Rücklese-/Konfliktweg möglich. In get_capabilities Kalender-Verbindung, tatsächlich gewährte Rechte und Wetter-/Termin-Capabilities differenzieren; Schemata, Laufzeit und Registrierung gemeinsam qualifizieren. Internes Protokoll bei inkompatiblen Änderungen erhöhen, additive Clients erhalten. Nach Update tools/list/Connector-Refresh und echte Werkzeugausführung nachweisen, Quellnamen allein reichen nicht.

Einrichtung bleibt konkret zu bestätigen: Calendar API im tatsächlichen Google-Projekt aktivieren, Android-OAuth-Zuordnung für Originalpaket/-zertifikat und Consent-/Organisationsregeln prüfen; danach Kalenderrechte am Gerät gewähren. Vorhandene Drive- oder ChatGPT-Rechte ersetzen dies nicht. Wetterzugangsdaten wie OAuth-Tokens nur im privaten, geeigneten Gerätespeicher, keine Schlüssel in APK/JavaScript/Geschäftsbackup oder protokollierten Anfrage-URLs. Cloud-Egress zu Google/Wetterdienst separat prüfen; funktionierende Connectoren beweisen keinen direkten API-Netzzugang. Ist der Pixel-Tunnel offline, darf ChatGPT Vorschläge erläutern, aber keine App-Speicherung bestätigen oder stattdessen ein unverbundenes Google-Event anlegen.

### Umsetzung und Abnahme nach gesondertem Umsetzungsauftrag

1. **Grundlage:** tatsächlichen Pixel-/Repo-/Connectorstand neu abgleichen; bestehenden Drive-Pfad und Datensicherung qualifizieren. Keine Erweiterung auf ungeprüfte alte Daten aufsetzen. Kalender-/Wetterrechte und Kosten getrennt freigeben, keine Installation aus diesem Konzept ableiten.
2. **Gemeinsame Planung:** Angebotsübernahme gegen Dubletten absichern, Terminmodell/Regeln/Drive-Migration und gemeinsame Tag-/Wochenoberfläche zuerst offline funktionsfähig machen.
3. **Kalender:** Einrichtung, Erstellen/Ändern/Absage, Busy-Zeiten, inkrementelles Rücklesen und geschützte Paralleländerungen. Wiederholung, Netzabbruch und Restore dürfen keine Event-Dubletten erzeugen.
4. **Wetter:** lizenzierter Abruf, lokale regelbasierte Bewertung, Wochenübersicht, Cache und deduplizierte Android-Hinweise. Vorschläge bleiben ohne KI nutzbar; ChatGPT verwendet dieselben geprüften Regeln.
5. **MCP/Gesamtabnahme:** alle neuen read/write-Pfade isoliert, anschließend genehmigtes Originalupdate und tatsächlicher Pixel-/Google-/Connector-Nachweis. Bestehende Rechnungs-, Foto-, Pflanzen-, Timer-, b.b.-, Papierkorb- und Privacy-Regressionen erhalten.

Messbare Abnahmen: ein angenommenes Angebot ergibt auch bei Doppeltippen/Retry genau einen verknüpften Auftrag; ein Auftrag mit drei Einsätzen bleibt ein Auftrag; kein Termin erzeugt eine Rechnung oder Ist-Stunden. Planen aus bestehendem Kontext höchstens drei normale Aktionen ohne Doppeleingabe. Identischer Request nach Commit/Ack-Verlust und Neustart genau einmal wirksam; stale Hash/ETag bewahrt beide Varianten. Kalender410 verliert keine lokale Änderung; externe Löschung wird nicht automatisch rückgängig gemacht. Drive-Ausfall verhindert keinen lokalen Termin, Kalenderausfall keinen Drive-Abgleich.

Zeitfälle: Europe/Vienna-Sommerzeitwechsel, zweideutige/nicht existente Uhrzeit, Mitternacht, ganztägige/exklusive Enddaten, Mehrtageseinsatz, Serienausnahme, Woche über Jahreswechsel und veränderte Arbeitstage. Wetterfälle: Regen nur außerhalb/innerhalb der Arbeitszeit, Böen/Gewitter, Frost/Hitze, fehlende Position/Variable, veralteter Abruf, kurzfristige Verschärfung, unveränderte Wiederholung, Absage/Bestätigung und API-Ausfall. Keine Benachrichtigungsflut; gesperrte Benachrichtigungen bleiben in der App nachvollziehbar.

UI320/360/426px und150%-Schrift einschließlich lesbarer Kunden-/Tätigkeitsnamen, großem Touchziel, aktiver Zeiterfassung, Entwurferhalt und unverändertem Zurückweg. Große Terminlisten portionsweise und nur aktive Ansicht rendern; Wetterabrufe nach Ort bündeln. Tests nur synthetisch, Google-Rundlauf nur in ausdrücklich isoliertem Testkalender; Pixel-Hintergrundverhalten und echter Connector erst nach tatsächlichem Test als bestätigt melden.

**In dieser Phase tatsächlich geprüft:** vorhandene Quellpfade, saubere Branch/HEAD/Remote-Refs und offizielle API-/Lizenzdokumentation. **Nicht ausgeführt:** Implementierung, neue Tests, Konto-/Kalenderzugriff, Ereignisänderung, Wetter-Liveabruf für Kunden, Build, Signierung oder Geräteinstallation. Sämtliche neuen Funktionen und Abnahmen oben sind Planungsumfang.

## Terminplanung 1.50.0 / 76: Umsetzung und Geräteeinrichtung

Fortsetzung des freigegebenen Konzepts ab90f2eff im selben Originalpaket. Die vorangegangene Aussage „nicht implementiert“ dokumentiert ausschließlich die ursprüngliche Planungsphase. Verbindliche aktuelle Testergebnisse und verbleibende Geräteabnahmen: CODEX-STATUS, Einträge30ff.

- **Planung integriert:** Heute zeigt den nächsten Einsatz; Mehr → Arbeit & Planung → Planung bündelt Tag/Woche/Ist-Stunden. Die vorherige separate Wochenstunden-Verknüpfung liegt dort. Kunde, Auftrag und angenommenes Angebot öffnen denselben Editor. Listen laden30 Einträge; Quellen/Ort, bekannte Angebotsstunden und bereits geplante Einsätze werden verwendet. Neue Zeiten bleiben Planung, tatsächliche Arbeit entsteht weiterhin ausschließlich im vorhandenen App-Ablauf.
- **Angebot/Auftrag:** Wiederholung einer angenommenen Angebotsübernahme erzeugt keinen zweiten Auftrag. Ein angenommener Nachtrag erhält eine versionsgebundene offer_refs-Referenz am bestehenden Auftrag; Hauptreferenz offer_id bleibt erhalten. Kein Rechnungsabschluss, keine Übernahme von Preisen als erfundene Standardwerte.
- **Gemeinsame Terminregeln:** appointments im vorhandenen SQLite-/JSON-/Journalweg, DB4 und Snapshot2 mit additiver leerer Sammlung für alte Stände. AppointmentRules schützt IDs, Kunde/Auftrag, Zeit/Zeitzone einschließlich Sommerzeit, Pause/Anfahrt, explizite Angebotspositionsreferenzen und Überschneidungen. Vollständiger Terminhash schützt auch gegen später ergänzte Felder. Atomare SQLite-Schreib-/Request-Belege, begrenzte Wochenserie mit Rollback der gesamten Serie bei Fehler. Keine zusätzliche operative Datenbank.
- **Zeiterfassung:** Start übernimmt die explizite Termin-ID in das vorhandene START-Ereignis; nach tatsächlichem App-Abschluss bleibt appointment_id am Arbeitsnachweis erhalten. Geplant/Ist getrennt lesbar. Eine laufende Sitzung verhindert das Archivieren ihres Termins; erfasste Arbeit schützt die Referenz. Kein automatischer Timerstart, keine fiktiven Stunden und keine automatische Abrechnung.
- **Entwürfe:** unveränderlicher Öffnungsstand, gezielt geänderte Felder und stabile Retry-ID; mehrere begonnene Termin-Entwürfe bleiben beim Wechsel erhalten. Bewusstes Verwerfen löscht nur den ausgewählten ungespeicherten Entwurf. Ort-/Kundenwechsel übernimmt keine fremden Koordinaten. Schmale Ansicht und vergrößerte Schrift sind Abnahmethemen, keine reine Überlaufmessung.

### Kalender und Wetter am Gerät aktivieren

Nach gesicherter und ausdrücklich freigegebener Originalaktualisierung:

1. Planung → **Einrichten**. Google-Konto bewusst auswählen; vorhandenen App-Kalender verwenden oder einmalig „Haus & Garten – Einsätze“ erstellen. Google-Projekt benötigt Calendar API und passende Android-OAuth-/Originalzertifikats-/Consent-Freigabe. Erst danach sind Scope-Erteilung und Erreichbarkeit am echten Gerät nachgewiesen. Vorhandene Drive-Freigabe unverändert lassen.
2. Standard scopes calendar.app.created und calendar.calendarlist.readonly; die Liste ist für Wiederaufnahme einer unbestätigten Kalenderanlage erforderlich. Optional calendar.freebusy für Belegtzeiten des eigenen Hauptkalenders. Kein pauschaler Kalender-Vollzugriff, keine automatische Kundeneinladung. Kalenderwechsel/-trennung ist eine bewusste Gerätehandlung, keine versteckte Kontomigration.
3. Gewünschte Arbeitstage/-zeiten und Anfahrtpuffer einstellen. Wetteroptional: **bereits gewerblich freigegebenen Open-Meteo-Kundenzugang** ausschließlich im nativen Geräteformular hinterlegen. Kein Vertrag wird abgeschlossen und kein Schlüssel im Chat abgefragt. Schlüssel liegt AES-GCM-geschützt in privaten Geräteeinstellungen; kein Schlüssel im APK/JavaScript/Geschäftssnapshot/MCP/Anfrageprotokoll. Ohne Zugang bleiben manuelle Termine vollständig nutzbar.
4. Wetterbenachrichtigungen einschalten und nötige Android-Laufzeitfreigabe bestätigen. Ruhezeit21–6Uhr; bei identischen Hinweisen keine neue Meldung. Einzelhinweis bietet Termine ansehen/Alternative suchen/Geprüft, letzteres nur für den exakt aktuellen Risikohash. Eine erhebliche neue Wetterlage bleibt erneut prüfbar. Verweigerte Rechte und veraltete Vorhersage werden sichtbar, keine Dauerverfügbarkeit zugesichert.

CalendarClient/CalendarRules arbeiten mit deterministischer Event-ID, privater Terminreferenz, ETag/If-Match und vollständigem Seiten-/Synctoken-Abgleich. Antwortverlust führt zum Nachlesen derselben ID.410 setzt nur den Kalender-Abgleich zurück; fachliche Daten/Queue bleiben erhalten. Fremde Termine werden keine Aufträge. Externe Zeit-/Ortsänderung wird über denselben App-Journalweg geprüft; Konflikte behalten App- und Google-Variante. resolve_calendar_conflict bietet hashgebundene Entscheidungen App verwenden/Google übernehmen/ohne Kalender behalten; vor Übernahme wird der externe Stand erneut gelesen. Extern entfernte Events und nicht unterstützte Google-Serien/ganztägige Umformungen bleiben zur Prüfung stehen, keine stille Neuanlage. Wiederherstellung eines App-Termins ist kein automatisch bestätigter Wiederaufbau eines bei Google endgültig entfernten Events.

Kalender-/Wetterjobs besitzen getrennte Wiederholungszähler/-fristen und blockieren weder einander noch Drive. Unveränderte Termine werden nicht wiederholt als Gesamtsnapshot projiziert; eigene bereits bestätigte Google-Echos erzeugen keinen dauerhaften Restabgleich. Neue Aufgaben bleiben dauerhaft aus lokalem Terminstand und privater bestätigter Kalenderbasis ableitbar. Übertragung ist pro Gerät wiederaufnehmbar, keine verteilte Atomarität oder Google-Reservierung fremder Kalender behaupten.

Wetter abrufen:14Tage mit stündlichen UTC-Zeitwerten, metrischen Einheiten und pro Ort gebündeltem Cache. Bewertung für tatsächliche Arbeitsstunden, Niederschlagswahrscheinlichkeit/-menge, Böen, Frost, Hitze und Modellgewitter. Amtliche GeoSphere-Warnungen werden getrennt räumlich/zeitlich bewertet; eine frische Warnung bleibt auch bei unbekannter Modellprognose sichtbar. Fehlende/veraltete/ungültige Werte bedeuten unbekannt. Betriebs-Schwellen editierbar; weder Maschinen-/Arbeitsschutzfreigabe noch automatische Absage. Live-Vertrag/Erreichbarkeit und tatsächliche Antwortformate der Wetterdienste benötigen die gesonderte Geräte-/Providerabnahme; synthetische Adaptertests ersetzen diese nicht.

### Tatsächlich implementierte MCP-Erweiterung

Zusätzlich zu allen91 erhaltenen Quellwerkzeugen: get_planning_context, list_appointments, get_appointment, suggest_appointment_slots, get_workweek_weather, get_weather_risks, create_appointment, create_appointment_series, update_appointment, cancel_appointment, acknowledge_weather_alert, request_calendar_sync, resolve_calendar_conflict. Quellkatalog104, internes Protokoll7; Standard-MCP-Verhandlung bleibt kompatibel. Schreibschalter, private Tunnelgrenze, Schemata, Versionen und Idempotenz gelten weiter. Allgemeiner Google-Calendar-Connector wird nicht als zweiter App-Schreibweg verwendet.

Nach zulässigem Update: vorhandene Pixel-Verbindung neu starten, initialize/tools/list und registrierten ChatGPT-Katalog aktualisieren; get_capabilities muss1.50.0/76, Protokoll7 und104 Tools tatsächlich zurückgeben. Der Stand im Quellcode ist keine bereits erfolgte Registrierung oder erfolgreiche Google-Kontofreigabe. Keine editierbare verwaltete Connector-Veröffentlichung ist hier nachgewiesen. Echte Pixel-/Google-/Wetter-End-to-End-Abnahme bleibt deshalb getrennt von den Cloud-Tests.

## Dauerhafter Kalenderzugriff – Korrektur 1.54.2/91 (21.10.2026)

Die aktuelle Implementierung verwendet Googles AuthorizationClient und dieselben minimalen Kalenderrechte. Nach einmaliger tatsächlicher Zustimmung kann das SDK weitere kurzlebige Zugangstoken ohne neue Nutzerinteraktion beziehen. Token verbleiben ausschließlich im Arbeitsspeicher; kein eigener Refresh-Token oder zusätzlicher Server. Konto-/Kalenderbindung, ETags, stabile Termin-IDs, vollständige Seiten-/Synctoken-Übernahme und Schutz gegen doppelte Kalenderanlagen bleiben erhalten.

- Rückgabedaten des Google-Dialogs werden auch bei nicht-OK-Ergebnis durch das SDK ausgewertet. Fehlende Antwort, Abbruch, Registrierung, Konto, Rechte und Netzwerk bleiben unterscheidbar. Der Abbruchcode allein beweist keine bewusste Nutzeraktion. Einrichtungszeit/Schritt und bereinigter Dialogcode stehen unter Konto und Freigaben; keine Rohantworten oder Zugangstoken.
- Android-Unterbrechungen sperren keine bestehende Kontofreigabe. Der Kalenderabgleich bleibt nach acht temporären Fehlern automatisch wiederholbar: exponentielle Wartezeit bis höchstens eine Stunde, gegebenenfalls längeres Retry-After des Anbieters. Fehlerzähler sättigen; keine enge Dauerschleife. Echte fehlende Freigaben bleiben bewusst gesperrt, Konfliktentscheidungen bleiben manuell. Drive und Wetter haben weiterhin eigene Zustände.
- Einmaljobs werden während ihrer Ausführung nicht durch eine neue Anforderung ersetzt. Fortsetzungen werden nach Abschluss eingeplant; gestoppte periodische und Einmaljobs haben getrennte Futures. Offline-Jobs verbrauchen ohne validiertes Internet kein Kalender-Fehlerbudget. Netzrückkehr bei aktiver App plant eine neue Prüfung.
- Die letzte erfolgreiche Kalenderprüfung ist getrennt vom allgemeinen Planungs-/Wettercheck lesbar. Fehlende/veraltete Zeitpunkte bleiben sichtbar. Ein gespeichertes Konto ist kein Nachweis aktueller Google-Erreichbarkeit oder vollständig übertragener Termine. Android kann Hintergrundarbeit verzögern; ein ausgeschaltetes Gerät überträgt erst nach Wiederaufnahme.

Externe Einrichtung weiterhin tatsächlich prüfen: Calendar API im bestehenden Google-Projekt, Android-OAuth-Client für at.hausgarten.service mit SHA-1 des tatsächlich installierten Originalsignierzertifikats, Zielkonto/Consent/optionale Hauptkalenderrechte und Veröffentlichungs-/Organisationsregeln. Der öffentliche tatsächliche Zertifikatsfingerprint wird am Gerät unter Konto und Freigaben angezeigt. Die Projekt-ID lässt sich aus Paketname oder SHA-1 nicht eindeutig ableiten; keine neue Projektzuordnung erfinden. Google-Projektzugriff und reale Zustimmung werden nicht durch Code oder eine bestehende Drive-/ChatGPT-Freigabe erteilt. Aktuelle Test-/Auslieferungsnachweise ausschließlich in CODEX-STATUS.md.
