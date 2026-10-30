# Leistungsnachweise für die native App

Diese Datei beschreibt Messverfahren, Aussagegrenzen und gebundene Testbelege für
die Kotlin-/Compose-App. Auftrag, aktueller Gesamtstatus und offene Punkte stehen
in `../../AKTUELLER-STAND.json`; der detaillierte Gerätebeleg unten belegt diesen
Stand und ist keine weitere Auftragsverwaltung. Ausschließlich öffentliche Repositoryquellen und
vollständig synthetische Daten verwenden. Eine Messung aus einem Browser oder
JVM-Test lässt sich nicht als Android- oder Pixel-Leistungswert übernehmen.

## Quell- und Umgebungsbindung

Jeder Messsatz nennt Quellcommit, abweichenden Appfingerabdruck, Paketversion,
APK-Prüfsumme, ausgeführten Befehl und tatsächlich verwendete Testumgebung.
Gerätemodell oder Emulatorprofil, Android-Version, Displaygröße, Dichte,
Schriftskalierung, Energie- und thermischen Zustand sowie Hintergrundlast festhalten.
Signatur- und Installationsregeln aus `../entwicklung/BAU.md` und
`../entwicklung/SIGNIERUNG.md` bleiben wirksam. Ein allgemeiner Entwicklungsauftrag
erteilt keine Installation oder Leseerlaubnis für einen produktiven Bestand.

Eine Vergleichsmessung verwendet denselben synthetischen Bestand und dieselben
Bedingungen. Ohne vergleichbare Vorhermessung dient der erste gültige Gerätelauf
als Ausgangsmessung. Unkontrollierte Hostlast oder ein anderes Messfenster erlauben
keine Aussage über die Ursache einer Differenz. Persönliche Daten oder Sicherungen
nicht für eine vermeintlich realistischere Vergleichbarkeit zurückspielen.

## Zu messende native Pfade

`NativeDataRepository` liest begrenzte Anzeigespalten aus `native_read_rows` und
lädt Details getrennt aus `native_read_records`. Die Projektion ist ein Cache in
der vorhandenen SQLite-Datei. Ihren ersten Aufbau nach Invalidierung getrennt
von wiederholten Seiten-, Filter- und Suchanfragen bei unveränderter Quellrevision
messen. Ein Wiederaufbau kann den vollständigen Fachstand lesen; Flow beweist
keine inkrementelle Änderung nur einer Zeile.

`diagnostics()` liefert WAL-Modus, Quell- und Cacherevision, Wiederaufbauzähler
und Abfragepläne. `native_data` prüft diese Grenzen mit 40 000 synthetischen
Projektionszeilen, tatsächlichen Coroutines/Flow und gleichzeitigem SQLite-Lesen
und -Schreiben. Die Suite deklariert ausdrücklich keinen Leistungsgewinn.
Abfrageplan, erfolgreiche Wiederverwendung und ein erfülltes Zeitbudget sind
verschiedene Nachweise.

Fotolisten bevorzugen verifizierte kleine Vorschaudateien. Nur beim nötigen
Originalfallback Integritätsprüfung und Dekodierung dieses Originals einbeziehen.
Projektions-/Metadatenzugriffe, Originalbytes, Vorschaubytes, tatsächliche
Dekodiergröße und Cachezustand getrennt erfassen. Die RAM-/Disk-Grenzen des
Coil-ImageLoaders sind Konfiguration und kein gemessener Speicherverbrauch.

`native_compose` prüft die wirkliche MainActivity und ausgewählte Bedienwege,
Activity-Neuerstellung und 200-%-Schrift. Daraus folgen keine gemessenen
Framezeiten, Bildraten oder Pixel-Antwortzeiten. Die genaue Testabdeckung und
Reihenfolge der acht nativen Suiten stehen in `../entwicklung/BAU.md`.

## Messablauf und Auswertung

Start, gefilterte Liste mit Seitenwechsel, Detailansicht, Bearbeiten/Speichern,
Timerabschluss und Fotoansicht mit demselben synthetischen Fixture prüfen.
Pro vergleichbarer Variante mindestens 40 Wiederholungen erfassen. Kalte Starts,
erster Projektionsaufbau, warmer Cache und wiederholte Bildschirmwechsel getrennt
auswerten; Warmups und Wartezeiten offen angeben.

Je Bedienweg Mittelwert, P95, Mittelwert der langsamsten 5 %, Stichprobenanzahl,
Fehler und Messgrenzen ausweisen. Beginn und Ende der Zeitmessung konkret definieren,
etwa bewusste Bedienaktion bis zum sichtbaren bestätigten Zustand. Async-Arbeit,
Entwurfspersistenz und nachfolgende Flow-Aktualisierung berücksichtigen;
ADB-Transportzeit ist keine UI- oder Datenbanklaufzeit.

WAL-Modus, Abfrageplan, abgefragte Zeilen/Spalten, gelesene Bytes und
Projektionswiederaufbauten dem gleichen Messfenster zuordnen. Schreib- und
Rücklesefehler, unklare Bestätigung, Prozessneustart, Abbruch und verworfene
Entwürfe prüfen, ohne Geschäftsdatensätze oder SQL-Argumente zu protokollieren.
Ein korrekter Snapshot einschließlich bestätigter WAL-Änderungen bleibt auch
unter Last Voraussetzung eines erfolgreichen Backups.

PSS, RSS, privaten Speicher, Swap und CPU mit mehreren Messpunkten erfassen.
Bei `gfxinfo framestats` nur vollständige gültige Frames auswerten und
`FrameCompleted - IntendedVsync` verwenden. Variable Bildwiederholraten beachten;
kein pauschales 16-ms-Limit und keine aus Quellcode abgeleitete FPS-Zusage.
Framezeiten erklären allein weder die Ursache einer Verzögerung noch die Anzahl
der Compose-Neuberechnungen. Die gezielten ADB-Befehle stehen in BAU.md;
ungefilterte Logcat-, Heap-, Bildschirm- oder Hierarchieabzüge gehören nicht in
Messartefakte.

## Aussagegrenzen

Quellprüfung, vollständiger Android-Bau, synthetische JVM-/SQLite-Prüfung,
Emulatorlauf und physischer Pixel sind getrennte Belege. Ein aufgelöster Compiler,
vorhandener Test oder definierter CI-Schritt bestätigt keinen ausgeführten Lauf.
Browsermesswerte der eigenständigen Website beschreiben ausschließlich diese
Website. Aus ihnen keine Aussage über Compose, Coil, Android-Speicher oder die
native Startprüfung ableiten.

Ein echtes Drive-Konto, der persönliche Pixel und ein individuell gewähltes
Sicherungsarchiv benötigen jeweils ihren eigenen zulässigen Nachweis. Die
Erhaltung roher alter IndexedDB-/LevelDB-Dateien beweist keine vollständige native
Entwurfs- oder Medienübernahme. Fehlende Daten-, Laufzeit- und Gerätemessungen
bleiben als solche im aktuellen Arbeitsstand erkennbar; daraus keinen Gewinn oder
Abnahmestatus ableiten.

## Synthetische Büro-Messungen für Build120

Quelle `a72240dfed6bc871d7fc1c9ef6fe2e8ee52e6709`, Appfingerabdruck `a08ad845065f9d83a8ce9206f98b42c14053f4d779aa9a7961167ac797d7ae3c`,
[Prüflauf 41054301410](https://github.com/mihalhafner-collab/Gartenpirat-/actions/runs/41054301410),
2026-10-30. Tatsächlich verwendete, nur temporär signierte Emulator-App:
SHA256 `aea6dda2ef45ed72b2ea5e91f01d2b7ef14c465783debc4a9a0e90fa2ab31a63`; Testpaket `3c269b978761c464d53f2239ff9e0c717211f6aaa45dccc349b54661cebc7485`.
Die Pakete wurden ausschließlich im isolierten CI-Emulator installiert und danach
verworfen. Keine Nutzer-APK, Originalsignatur oder Pixel-Installation daraus ableiten.

Android35, x86_64, 320×640Pixel,160dpi, Schriftfaktor1,0; warmer Prozess.
Je Pfad zwei Aufwärmläufe und40Messungen. Frei erfundene120Rechnungen,
240Zahlungen und wenige synthetische Arbeitsaufzeichnungen; die UI-Variante enthält
einen Guthabenfall. Keine betrieblichen Daten. Hostlast, Energie und Thermik nicht
kontrolliert; das ist kein Lastprofil des persönlichen Pixel-Bestands.

### Ursache und vergleichbarer Leservergleich

`LocalInvoiceService.paymentEntries` las zuvor erst die Schlüssel und anschließend
jeden Zahlungseintrag einzeln über den gestückelten Leser, einschließlich zusätzlicher
Längen- und Inhaltsabfragen. Kurze Einträge werden jetzt gemeinsam in einer begrenzten
SQL-Projektion gelesen. Werte über32768Zeichen behalten den vorhandenen Stückelungspfad.
Keine Schemaänderung und kein zusätzlicher Cache.

`native_data.office_reads.payment_entry_read_comparison` führt die unveränderte
frühere Leserfunktion aus `e15dcdd` und den neuen Leser abwechselnd auf derselben
SQLite-Verbindung mit demselben Fixture aus. Ergebnis-Hashes stimmen in jedem Paar
überein; ein zusätzlicher großer Unicode-Eintrag prüft den Fallback.

| Zahlungsleser | n | Mittel ms | P95 ms | Mittel langsamste5% ms |
|---|---:|---:|---:|---:|
| Vorher, ursprüngliche Funktion | 40 | 65,907 | 72,354 | 73,056 |
| Nachher, begrenzte gemeinsame Abfrage | 40 | 1,282 | 1,583 | 2,327 |

Die gemessene Verbesserung gilt ausschließlich für diesen Leser unter diesen
Bedingungen. Sie belegt keine entsprechende Beschleunigung der vollständigen
Ansicht oder des Pixel. `balance` durchsucht weiterhin die Zahlungszuordnungen
je Rechnung; `FinancePayments` baut die Reststandskarten in einer scrollbaren
Column auf. Diese weiteren Quellbefunde wurden nicht als isolierte Ursachen gemessen.

### Gesamte Datenabfragen und tatsächliches Öffnen

| Repository-/Ledger-Pfad | n | Mittel ms | P95 ms | Mittel langsamste5% ms |
|---|---:|---:|---:|---:|
| Rechnungsliste, erste50 | 40 | 0,471 | 0,562 | 0,718 |
| Arbeitsliste für Abrechnung, erste50 | 40 | 0,353 | 0,430 | 0,456 |
| Zahlungen/Guthaben einschließlich Reststandsberechnung | 40 | 44,133 | 51,799 | 53,509 |

`native_compose.office_opening` misst von der tatsächlich ausgelösten Öffnungsaktion
bis zum sichtbaren bereiten Inhalt: Rechnungsempfängerauswahl, bestätigte Rechnungsliste
bzw. Reststand/Guthaben. Zahlungen und Guthaben teilen dieselbe Ansicht.
Die Zeit enthält Semantik-Abfrage und Polling; sie ist keine Frame- oder ADB-Transportzeit.

| UI-Öffnung | n | Mittel ms | P95 ms | Mittel langsamste5% ms |
|---|---:|---:|---:|---:|
| Arbeiten abrechnen | 40 | 319,337 | 365,676 | 386,596 |
| Rechnungen | 40 | 120,050 | 175,045 | 203,554 |
| Zahlungen und Guthaben | 40 | 381,596 | 432,774 | 457,677 |

Diese UI-Werte sind eine Ausgangsmessung. Es gibt keinen vergleichbaren vollständigen
UI-Vorher-/Nachherlauf und keine neue physische Pixel-Messung. Unterschiede zwischen
verschiedenen CI-Hosts werden nicht als Gewinn ausgegeben. Die langen Öffnungszeiten
des persönlichen Geräts sind damit noch nicht abschließend erklärt.

Befehle: bestehender `qa-apk.yml`-Prüflauf, insbesondere
`am instrument -w -e suite native_data` und `native_compose` mit
`at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation`.
Rohwerte, Paketprüfsummen und die gezielten synthetischen Bilder liegen im
CI-Artefakt `Gartenpirat-backup-test-results`, SHA256 `d9bda63afbf2baa42e485348463a867d16c78fbe17d07a4f35a848aed01d0868`.
Die begrenzte Artefaktaufbewahrung ersetzt nicht die dauerhaften Zahlen und
Aussagegrenzen in dieser Datei. Der frühere Build119-Gerätelauf folgt getrennt.

## Gebundener Gerätebeleg

Quellstand: `6da869753ee2ed1e8640ec1b2a1c354724fdd0ee` auf `codex/kotlin-refactoring-20261029`; Appfingerabdruck `1dccfad7966f564f3a844c848af4dae50c5200b8697592e07a449e4e06920407`. Nach der ersten Metadatenprüfung erteilte der Nutzer ausdrücklich Lese- und Schreibfreigabe für die **aktuelle Datenbank**. Der folgende Lauf erfolgte deshalb im bestehenden, **nicht isolierten** Appbestand mit neu angelegten vollständig synthetischen Testdatensätzen. Diese Erlaubnis ersetzt keinen Isolationsnachweis oder eine Freigabe für externe Konten, produktive Belegfinalisierung oder destruktive Wiederherstellung.

Auf Nutzeranweisung wurden die Tests beendet. Das Telefon wird nicht weiter durch UI-/Messläufe beansprucht. Der aktuelle Gesamtstatus und Abschluss der gezielten fachlichen Archivierung stehen ausschließlich in `../../AKTUELLER-STAND.json`. Dieser gebundene Beleg behauptet keine vollständige Abnahme.

### Gerät, App und Nachweisgrenzen

- Lokaler Windows-Laptop über USB/ADB; ein bewusst ausgewähltes Pixel10 Pro; alle Gerätebefehle mit expliziter Auswahl. Android17/API37,1280×2856Pixel,480dpi. Ursprünglicher Schriftfaktor1,3; tatsächliche UI-Prüfungen bei1,0/1,3/2,0; Foto-Vollbild auch quer.
- Paket `at.hausgarten.service`,Version1.57.0,Build119. Installierte APK89453503Bytes,SHA256 `f907a1b260ec7719e7b11bf24e6d02671b8eaef7ba797f9ddca02b0a0ab42f86`. Signatur v2/v3 gültig;Zertifikats-SHA256 `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`,gleich dokumentierter Originalreferenz. DEX enthält den dokumentierten Appquellcommit `94b75be56ef46ed1404b19a725a97811cbfcd19a` und Fingerabdruck;kein frischer vollständiger Quellfingerabdruck berechnet.
- Eigene Testdaten: frei erfundene `[TEST] QA20261030`-Kunden und Vorgänge,geometrisch erzeugte PNG. Keine aus Betriebsdaten abgeleiteten Fixtures. 17eigene Fachdatensätze nach Neustarts separat gelesen;ein weiterer Fotoeintrag tatsächlich über die native UI angelegt. Damit18Fachdatensätze plus eine abgeschlossene Timersitzung als eigene IDs.
- Behaltene Screenshots zeigen ausschließlich eigene synthetische Testansichten. Ein gemischter Fotolistenbeleg wurde gelöscht und wird nicht verwendet. Betriebsnamen,Profilkennungen,Seriennummern,Kontowerte und Originaldaten werden hier nicht veröffentlicht. Rohbelege bleiben lokal in `adb-evidence-20261030/`;Bildverweise unten sind Dateinamen unter dessen `screenshots/`.
- Die Emulator-Schutzprüfungen wurden nicht verändert oder auf das Telefon umgangen. Native Emulatorergebnisse und frühere Quelltests bleiben getrennte Nachweise. Die lokale vollständige Quellprüfung konnte auf der unvollständigen Connector-Kopie wegen fehlendem `bootstrap_contract` nicht durchlaufen;kein Quell-PASS daraus.

### Tatsächlich geöffnete Funktionsbereiche

27Menüaufrufe mit Exit0 und passender sichtbarer Überschrift stehen in `route-visits-local.json`;Fotos wurde separat über Kundenakte/Bibliothek tatsächlich geöffnet. Ein erfolgreicher Routenaufruf beweist die Erreichbarkeit,keine vollständige Funktion dieses Bereichs.

| Test-ID / Bereich | Voraussetzung | Schritte | Soll | Ist / Beleg | Ergebnis |
|---|---|---|---|---|---|
| R01 Heute | A; App gestartet | Mehr → Heute öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R02 Arbeiten | A; App gestartet | Mehr → Arbeiten öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R03 Zeiterfassung | A; App gestartet | Mehr → Zeiterfassung öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R04 Kunden | A; App gestartet | Mehr → Kunden öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R05 Objekte | A; App gestartet | Mehr → Objekte öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R06 Anfragen | A; App gestartet | Mehr → Anfragen öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R07 Pflegepläne | A; App gestartet | Mehr → Pflegepläne öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R08 Einsätze | A; App gestartet | Mehr → Einsätze öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R09 Tagestour | A; App gestartet | Mehr → Tagestour öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R10 Material | A; App gestartet | Mehr → Material öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R11 Geräte | A; App gestartet | Mehr → Geräte öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R12 Lokale Suche | A; App gestartet | Mehr → Lokale Suche öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R13 Revier-Archiv | A; App gestartet | Mehr → Revier-Archiv öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R14 Aufträge | A; App gestartet | Mehr → Aufträge öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R15 Angebote | A; App gestartet | Mehr → Angebote öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R16 Rechnungen | A; App gestartet | Mehr → Rechnungen öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R17 Zahlungen | A; App gestartet | Mehr → Zahlungen öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R18 Leistungskatalog | A; App gestartet | Mehr → Leistungskatalog öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R19 Fotos | A; App gestartet | Eigene Kundenakte → Kundenfotos; zusätzlich gefilterte Bibliothek öffnen | Zugehörige native Ansicht erreichbar | Bibliothek/Detail/Vollbild tatsächlich geöffnet;Kundenfilterfehler separat B01;photo-filtered-130.png | **PASS** |
| R20 Pflanzen | A; App gestartet | Mehr → Pflanzen öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R21 Termine | A; App gestartet | Mehr → Termine öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R22 Wetter | A; App gestartet | Mehr → Wetter öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R23 Planung | A; App gestartet | Mehr → Planung öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R24 Papierkorb | A; App gestartet | Mehr → Papierkorb öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R25 Lokale Vorgänge | A; App gestartet | Mehr → Lokale Vorgänge öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R26 Einstellungen | A; App gestartet | Mehr → Einstellungen öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R27 Verbindungen | A; App gestartet | Mehr → Verbindungen öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |
| R28 Sicherung | A; App gestartet | Mehr → Sicherung öffnen | Zugehörige native Ansicht erreichbar | Exit0;Überschrift passt;route-visits-local.json | **PASS** |

### Konkrete Lese-, Schreib- und UI-Prüfungen

`A` bedeutet die spätere ausdrückliche Freigabe für den aktuellen Datenbankbestand und ausschließlich eigene synthetische Schreibziele. Receipt-Indizes sind nullbasiert. **PASS** gilt jeweils für das beschriebene begrenzte Soll. Die Felder `domain_results` im Rohprotokoll wurden gegen Einzelreceipts geprüft:Objekt-Replay war dort vorläufig falsch zusammengefasst;die spätere zulässige Anfrageänderung wurde ebenfalls berücksichtigt. Maßgeblich sind die einzelnen bestätigten Antworten und Rücklesungen.

| Test-ID / Bereich | Voraussetzung | Schritte | Soll | Ist / Beleg | Ergebnis |
|---|---|---|---|---|---|
| T01 Verbindung / Identität | USB-/ADB-Freigabe | Gerät eindeutig auswählen; Paket, Display und APK lesen; Hash/Signatur vergleichen. | Eindeutiges Gerät; dokumentierter Build119. | Pixel10 Pro, Android17/API37;1.57.0/119;89453503Bytes;APK-Hash und Zertifikat entsprechen der ausgelieferten Referenz.<br>Beleg: identity-local.json;apk-binding-local.json;apk-signature-local.txt | **PASS** |
| T02 ADB-Dateitransport | Neues geprüftes /data/local/tmp-Verzeichnis | 147456 neue synthetische Bytes pushen, genau diese Datei pullen, Hash/Größe prüfen; eigene Datei/Ordner entfernen. | Identische Bytes; eigene Geräteprüfdateien bereinigt. | Größe/SHA256 identisch;Datei und leeres Verzeichnis mit Exit0 entfernt.<br>Beleg: transport-local.json | **PASS** |
| T03 Kunden / CAS | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. | Anlegen; gezielt ändern und lesen; mit altem Vergleichshash erneut ändern; danach lesen. | Neue Werte erhalten; veraltete Änderung abgewiesen. | Bestätigte Änderung zurückgelesen;WRITE_CONFLICT/not_committed;abgewiesener Wert nicht übernommen.<br>Beleg: app-rw-receipts-local.json:0–3,42 | **PASS** |
| T04 Aufträge / Replay | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigener Kunde. | Anlegen, lesen, bearbeiten, zurücklesen; ursprüngliche Anlage mit gleicher request_id/Argumenten wiederholen. | Änderung korrekt; Replay identische ID. | Änderungen im GET bestätigt;Replay gleiche ID und replayed=true.<br>Beleg: app-rw-receipts-local.json:4–8 | **PASS** |
| T05 Objekte / Replay | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigener Kunde. | Anlegen, lesen, Fläche/Notiz ändern und lesen; Anlage wiederholen. | Werte/Beziehung erhalten; keine neue Replay-ID. | Geänderte Werte zurückgelesen;ursprüngliche property_id und replayed=true.<br>Beleg: app-rw-receipts-local.json:9–13 | **PASS** |
| T06 Anfragen / Fachgate | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigenes Objekt. | Anlegen/lesen; Besichtigungsnotiz ohne geplante Besichtigung speichern; zulässiges Anliegen ändern und lesen; Anlage wiederholen. | Fachgate erhält Vorgang; zulässige Änderung und Replay korrekt. | INSPECTION_REQUIRED/not_committed;nachfolgende zulässige Änderung korrekt zurückgelesen;Replay gleiche ID.<br>Beleg: app-rw-receipts-local.json:14–20 | **PASS** |
| T07 Material | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. | Neuen eigenen Artikel anlegen, gezielt lesen, Notiz/Preis ändern; nach Neustarts separat lesen. | Bestätigte Artikelwerte erhalten. | Anlage/Änderungsantwort korrekt;separate Abschlusslesung bestätigt eigenen Datensatz. Keine Bestandsbewegung getestet.<br>Beleg: app-rw-receipts-local.json:21,24–25;gesonderte Abschlusslesung | **PASS** |
| T08 Geräte | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. | Eigenes Gerät anlegen, lesen, Zustand/Notiz ändern; nach Neustarts separat lesen. | Gerätestamm und bestätigter Zustand bleiben erhalten. | Anlage/Änderung und Abschlusslesung korrekt. Nutzung/Wartung nicht vollständig ausgeführt.<br>Beleg: app-rw-receipts-local.json:22,26–27;gesonderte Abschlusslesung | **PASS** |
| T09 Pflegeplan | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigenes Objekt. | Eigenen Plan anlegen, lesen, zulässige Planwerte ändern; nach Neustarts separat lesen. | Planwerte und Objektbezug erhalten. | Anlage und Änderung bestätigt;separate Abschlusslesung korrekt. Keine vollständige Serien-/Folgeeinsatzabnahme.<br>Beleg: app-rw-receipts-local.json:23,28–29;gesonderte Abschlusslesung | **PASS** |
| T10 Einsatz | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigenes Objekt. | Eigenen Einsatz planen, lesen, Notiz/Dauer ändern; nach Neustarts separat lesen. | Geänderte Einsatzwerte bleiben erhalten. | Planung/Änderung und eigene Abschlusslesung erfolgreich;Darstellung in Heute separat mangelhaft.<br>Beleg: app-rw-receipts-local.json:30,58,63;gesonderte Abschlusslesung | **PASS** |
| T11 Arbeiten / Replay | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigener Kunde/Auftrag. | Arbeit anlegen, lesen, Tätigkeit/Stunden ändern und lesen; Anlage wiederholen. | Bestätigte Werte und Beziehungen korrekt; gleiche Replay-ID. | Geänderte Werte zurückgelesen;Replay identische Arbeits-ID.<br>Beleg: app-rw-receipts-local.json:32–36 | **PASS** |
| T12 Leistungskatalog / Replay | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. | Eigenen Eintrag anlegen, lesen, Beschreibung/Preis ändern und lesen; Anlage wiederholen. | Preis/Bestätigung und ID erhalten. | Geänderte Felder im GET korrekt;gleiche Replay-ID.<br>Beleg: app-rw-receipts-local.json:37–41 | **PASS** |
| T13 Rechnungsentwurf | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigener Testkunde/Auftrag. | Entwurf mit erfundenen Positionen anlegen, lesen, Notiz ändern und lesen; Anlage wiederholen. | Entwurf speicherbar; Wiederholung ohne zweite ID. | Entwurfswerte zurückgelesen;gleiche Replay-ID. Keine produktive Finalisierung/Nummernfreigabe.<br>Beleg: app-rw-receipts-local.json:43–47 | **PASS** |
| T14 Angebotsentwurf | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Nur eigene Testkundenbeziehungen. | Entwurf anlegen, lesen, Notiz ändern und lesen; Anlage wiederholen. | Bestätigte Entwurfswerte/ID erhalten. | GET bestätigt Änderung;Replay gleiche Angebots-ID. Kein Versand/kein Freigabeabschluss.<br>Beleg: app-rw-receipts-local.json:48–52 | **PASS** |
| T15 Lokaler Termin | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Ausschließlich Status draft. | Draft anlegen, gezielt lesen, Notiz ändern und später listen. | Draft bleibt lokal prüfbar; bestätigte Werte erhalten. | Spätere gezielte Liste bestätigt Änderung und Status draft. Kein bestätigter Kalenderexport geprüft.<br>Beleg: app-rw-receipts-local.json:53,56–57,86 | **PASS** |
| T16 Timer / Abschlussreplay | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Vorher kein aktiver Timer. | Start; Pause; Fortsetzung; Stop; Fortsetzung; Stop; Abschluss; denselben Abschluss wiederholen; aktive Sitzung lesen. | Konsistente Zustände; genau dieselbe Abschluss-Arbeits-ID bei Replay; kein weiterlaufender Timer. | IDLE→RUNNING→PAUSED→RUNNING→STOPPED_PENDING_REVIEW→RUNNING→STOPPED_PENDING_REVIEW→FINALIZED;Replay gleiche Arbeits-ID;später IDLE.<br>Beleg: app-rw-receipts-local.json:54–66,87 | **PASS** |
| T17 Foto über Fachschnittstelle | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Neu generierte geometrische PNG. | Geprüften Upload beginnen/chunken; Foto anlegen und lesen; Caption ändern; gezielt listen; Original vollständig rücklesen. | Eigenes Foto/Zuordnung korrekt; gespeicherte Originalbytes stimmen mit finalem Hash. | Original20342Bytes,eof=true,Block-/Gesamt-SHA256 korrekt;Fotoänderung in eigener Kundenliste zurückgelesen. Uploadoptimierung und endgültigen Hash unterschieden.<br>Beleg: app-rw-receipts-local.json:67–72,85;synthetic-photo.png | **PASS** |
| T18 Appstandard / exakte Rücksetzung | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. | default_location lesen, eigenen Testwert setzen/lesen; Ursprungszustand wiederherstellen. | Nach dem Test derselbe typisierte Ausgangszustand. | Schreib-/Lesetest erfolgreich. Ursprünglich fehlte der Schlüssel;anschließend default_location="". Semantisch leer,aber ABSENT nicht exakt wiederhergestellt;kein API-Remove vorhanden.<br>Beleg: app-rw-receipts-local.json:73–76,78 | **FAIL** |
| T19 Material archivieren / wiederherstellen | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Nur eigener Artikel und aktueller Hash. | Einzelnen Artikel archivieren und lesen; wiederherstellen und erneut lesen. | Archiv-/Aktivzustand korrekt; keine fremden Datensätze betroffen. | Beide Übergänge und separate Rücklesung im Haupttest bestätigt;kein Papierkorb-Leeren.<br>Beleg: eigene Archiv-/Wiederherstellungsbelege im lokalen Haupttest | **PASS** |
| T20 Erhalt nach Neustarts | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigene erzeugte Datensätze. | App-Neustarts durchführen;17 eigene Fachdatensätze getrennt lesen; zusätzlich UI-Foto gezielt listen. | Bestätigte Werte/Beziehungen bleiben erhalten. | 17 eigene Fachdatensätze nach Neustarts separat bestätigt;zusätzliches UI-Foto durch eigene Kundenliste nachgewiesen. Kein Vollbestandvergleich.<br>Beleg: app-rw-receipts-local.json;gesonderte Abschlusslesung | **PASS** |
| T21 Kundenentwurf behalten / speichern | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigener Kunde. | Im UI ändern; verlassen und Entwurf wieder öffnen; nach Konfigurationswechsel öffnen; ausdrücklich speichern. | Entwurf bleibt bis Entscheidung erhalten; bestätigter Name bleibt gespeichert. | Entwurf wieder geöffnet und eigener Kunde als Alpha UI gespeichert;Foto-/Kundenbeziehungen verwenden eigene ID.<br>Beleg: screenshots/customer-draft-reopen-130.png;customer-draft-reopen-after-config130.png;customer-ui-save130.png | **PASS** |
| T22 Kundenentwurf verwerfen | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Bereits bestätigter eigener Kunde. | MUST_DISCARD eingeben; ausdrücklich verwerfen; Editor erneut öffnen. | Verworfener Wert bleibt verworfen; bestätigter Name erhalten. | Nach erneutem Öffnen Alpha UI statt MUST_DISCARD.<br>Beleg: screenshots/customer-draft-discarded.png | **PASS** |
| T23 Sichtbare Listenaktualisierung | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigene Kundenliste offen. | Eigenen Datensatz über Fachschreiber ändern; offene Liste prüfen. | Sichtbare Änderung ohne manuellen Appneustart. | Aktualisierte eigene Kundenkarte sichtbar.<br>Beleg: screenshots/customer-list-flow-update-130.png | **PASS** |
| T24 Native Fotoauswahl / Verwerfen | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Nur eigene generierte PNG im neuen Download-Testordner. | Galerie öffnen; nach gezieltem Scan eigene Datei im Systemdialog wählen; Vorschau öffnen; Verwerfen ausdrücklich bestätigen. | Auswahl/Vorschau möglich; Verwerfen erzeugt keinen bestätigten Fotodatensatz. | Eigene geometrische PNG als Vorschau und Verwerfungsdialog tatsächlich geprüft. Separater Systempicker-Abbruch nicht vollständig belegt.<br>Beleg: screenshots/photo-native-selection-preview.png;photo-discard-confirmation.png;photo-picker-file-local.json | **PASS** |
| T25 Natives Fotospeichern | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigener Kunde Alpha UI und generierte PNG. | Datei erneut wählen; eigenen Kunden und Testcaption setzen; ausdrücklich speichern; Kundenfotos fachlich lesen. | Genau ein zusätzlicher eigener Fotoeintrag mit korrekter Zuordnung. | Caption [TEST] QA20261030 UI-Import gespeichert;gezielte Alpha-Liste danach2eigene Fotos statt1.<br>Beleg: screenshots/photo-native-save-ready.png;lokale fachliche Rücklesung | **PASS** |
| T26 Fotodetail / Vollbild / Ausrichtung | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Ausschließlich eigenes geometrisches Foto. | Gefiltertes Foto öffnen; Originaldetail/Vollbild und Querformat prüfen; zurückkehren. | Eigenes Bild sichtbar; Detail/Vollbild und Querformat zugänglich. | Eigene Vorschau,Originaldetail und Vollbild im Hoch-/Querformat tatsächlich gesehen;keine Foto-Latenz und keine Pinch-Zoom-Bedienung gemessen/geprüft.<br>Beleg: screenshots/photo-filtered-130.png;photo-detail-130.png;photo-fullscreen.png;photo-fullscreen-landscape.png | **PASS** |
| T27 Schrift / Tastatur / erreichbare Aktionen | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Ursprünglicher Font1,3 dokumentiert. | Synthetischen Editor mit Tastatur prüfen; Heute mit Font1,0 und2,0 öffnen; angeschnittene Aktionszeile betrachten. | Ansichten/Aktionen prüfbar; problematische Darstellung separat erfassen. | 1,0/1,3/2,0 und Editor-Tastatur tatsächlich geprüft. Tagestour rechts zunächst angeschnitten;Scrollbarkeit nur durch Quellcode belegt;horizontale Bedienung nicht ausgeführt. Keine vollständige A11y-/Einhandabnahme.<br>Beleg: screenshots/customer-editor-keyboard-130.png;today-font100.png;today-font200.png | **PASS** |
| T28 Kundenfoto-Kontext | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigene Kunden A/B mit eigenen Fotos. | Aus Kunde A Kundenfotos öffnen; anschließend ausschließlich QA-Suche als sicheren Bildbeleg setzen. | Kundenkontext zeigt nur Fotos von A. | Kundenfilter verloren;QA-Suche zeigt auch eigenes Foto von B. Anzeige-/Navigationsfehler,keine falsche Speicherung bewiesen.<br>Beleg: screenshots/photo-customer-filter-leak-synthetic-only.png;B01 | **FAIL** |
| T29 Kundenkarte / Datum | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Neuer eigener Kunde. | Kundenliste mit eigener QA-Suche betrachten. | Karte zeigt fachlich nötige,verständliche Angaben. | Datum und vollständiger roher ISO-Erstellungszeitpunkt redundant sichtbar.<br>Beleg: screenshots/customer-list-130.png;B02 | **FAIL** |
| T30 Heute / Einsatzdarstellung | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Eigener heutiger Einsatz. | Heute bei1,0/2,0öffnen; dargestellten Einsatz vergleichen. | Ein Einsatz als verständlicher fachlicher Eintrag. | Derselbe Einsatz unter Visits und Planned visits;technische Felder sichtbar. Kein Datenbankduplikat nachgewiesen.<br>Beleg: screenshots/today-font100.png;today-font200.png;B03 | **FAIL** |
| T31 Statusleistenkontrast | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Dokumentierter System-Nachtmodus. | Helle tatsächliche Appansicht mit Systemstatusleiste betrachten. | Uhrzeit/Statussymbole ausreichend kontrastreich. | Helle Symbole auf sehr hellem Hintergrund schlecht lesbar.<br>Beleg: screenshots/customer-list-130.png;photo-customer-filter-leak-synthetic-only.png;B04 | **FAIL** |
| T32 Startwarnung / Diagnose | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. | MainActivity neu starten und eigene Fachzugriffe weiter prüfen. | Kein unerklärter Bereinigungshinweis; Fehler bleibt verständlich prüfbar. | Toast Testbereinigung benötigt Prüfung;bei200%gekürzt. Konkrete Exception nicht ermittelt;Fachzugriffe funktionieren weiter.<br>Beleg: Beobachteter Android-Toast;Quellnachweis B05;kein gesondertes Toastbild | **FAIL** |
| T33 Kalte / warme Activity-Anzeige | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Build119,Font1,3;getrennte tatsächliche LaunchState. | am start -W mehrfach ausführen;je40gültige COLD/WARM-Beobachtungen getrennt auswerten. | Gemessene TotalTime-Werte,Stichproben/Ausschlüsse und Grenzen ausgewiesen. | COLD n40 nach171Versuchen;131WARM ausgeschlossen. WARM n40. Werte siehe Messtabelle;keine vollständig geladenen Daten gemessen.<br>Beleg: launch-cold-atomic-local.json;launch-verified-local.json | **PASS** |
| T34 Speicher / Bedingungen | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Gezielte gefilterte dumpsys-Abfragen. | Zwei Reihen mit je10Speicher-/Akku-/Thermikpunkten erfassen;PSS/RSS/PrivateOther/Swap trennen. | Tatsächlich erfasste Werte mit Messfenster;keine Leckvermutung als Befund. | Je10Punkte vorhanden;KB-Bereiche und Bedingungen unten. PrivateOther ist keine private Gesamtsumme.<br>Beleg: launch-measurements-local.json;launch-verified-local.json | **PASS** |
| T35 Individuelle Framezeiten | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Gezieltes gfxinfo einschließlich Profilierungsversuch. | framestats erfassen;gültige vollständige Zeilen für FrameCompleted−IntendedVsync prüfen. | Auswertbare individuelle Frames und Ausschlusszählung. | Nur Header und Android-Zusammenfassungen;keine numerischen Einzelzeilen. Individuelle Framezeiten/FPS NICHT GEMESSEN.<br>Beleg: frames-before-local.txt;frames-profiled-local.txt;frame-analysis-before-local.json | **NICHT AUSGEFÜHRT** |
| T36 Weitere Bedienweg-/SQL-Zeiten | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Je vergleichbarer Variante mindestens40Wiederholungen nötig. | Liste/Seite,Detail,Speichern,Timerabschluss,Foto und SQL/Flow im selben Fenster messen. | Mittelwert,P95,langsamste5%,n und Zähler pro Bedienweg. | Keine40er-Reihen dieser Pfade;SQL-Zeit/-Anzahl/-Zeilen/-Bytes,Cache-/Snapshotzähler und Flow-Zuordnung NICHT GEMESSEN.<br>Beleg: Keine entsprechenden Messbelege;TTID ist kein Ersatz | **NICHT AUSGEFÜHRT** |
| T37 Backup / Restore | Dafür isolierter synthetischer Gesamtbestand nötig. | Neue synthetische Sicherung,Readback,Restore,Neustart/Rollback prüfen. | Vollständiger zulässiger Backup-/Restorenachweis. | Nur vorhandenen Backupstatus gelesen. Kein neuer Backup-/Restoretest;keine persönliche Sicherung geöffnet.<br>Beleg: app-rw-receipts-local.json:88 ist ausschließlich Statuslesung | **NICHT AUSGEFÜHRT** |
| T38 Zahlung / Finalisierung / externe Konten | Gesonderte Fach-/Kontofreigaben und erfüllte Gates. | Zahlung positiv buchen;Beleg finalisieren;Kontoschreiben getrennt prüfen. | Ausgeführte positive Fach-/Kontonachweise ohne Gate-Umgehung. | Zahlungsversuch LOCAL_AUTHORITY_REQUIRED/not_committed. Keine positive Zahlungsbuchung,produktive Finalisierung oder externe Kontoabnahme;Gates unverändert.<br>Beleg: app-rw-receipts-local.json:77–79;kein positiver Beleg | **NICHT AUSGEFÜHRT** |
| T39 Testende / gezielte Bereinigung | A: aktuelle Nutzerfreigabe; ausschließlich eigene neu angelegte synthetische Test-IDs. Nutzer verlangt Testende. | UI-/Messläufe beenden;Gerätewerte zurücksetzen;eigene Download-Testdatei/Ordner entfernen;eigene Fachdatensätze gezielt aus Aktivansichten entfernen. | Telefon frei;Ursprungswerte hergestellt;keine fremden Datensätze oder geschützten Bezüge löschen. | Font1,3,Rotation0,AutoRotation0,GPU-Profil leer;Datei/Ordner jeweilsExit0 entfernt. Alle18eigenen Fachdatensätze aus Aktivansichten entfernt:17archiviert,Alpha wegen geschützter Bezüge auf active=false. Kein Purge;finalisierte Sitzung/Belege erhalten.<br>Beleg: device-cleanup-final-local.json;app-rw-receipts-local.json/cleanup_results | **PASS** |

Die drei abgewiesenen protokollierten Schreibversuche tragen außen lediglich `INVALID_ARGUMENT`. Ihre maßgeblichen inneren Codes sind `WRITE_CONFLICT`, `INSPECTION_REQUIRED` und `LOCAL_AUTHORITY_REQUIRED`,jeweils `commit_state=not_committed`. Schutzablehnung und fehlerhafte Appfunktion werden nicht gleichgesetzt. Replayantworten belegen gleiche Vorgangskennung/Argumente und identische ID;daraus folgt keine pauschale Abnahme aller Schreiber.

### Tatsächlich erhobene Messwerte

Startgröße ist Androids `am start -W TotalTime` bis zur **ersten Activity-Anzeige**. Dies ist weder vollständige Datenbereitschaft noch Listen-,Detail- oder Speicherzeit. Keine Warmups;zwischen Versuchen0,25s. P95 nach Nearest-Rank;langsamste5% bei n40 sind die zwei höchsten Werte. Wegen wechselnder Prozess-/Activity-Zustände werden Vorversuche nicht zusammengelegt.

| Tatsächliche Variante / Rohbeleg | n | Mittel ms | P95 ms | Mittel langsamste5% ms | Min–Max ms | Bedingungen / Ausschlüsse |
|---|---:|---:|---:|---:|---:|---|
| COLD;launch-cold-atomic-local.json | 40 | 109,75 | 119 | 139 | 96–150 | Force-stop und Start in einem ADB-Shellaufruf;171Versuche;131als WARM gemeldete Versuche ausgeschlossen |
| WARM;launch-verified-local.json | 40 | 41,225 | 52 | 72 | 31–77 | Activity-Neuanlage mit -f0x10008000 bei gleichbleibendem lebendem Prozess;kein bloßes Zurückholen |

Die als „cold“ beschriftete Teilreihe in `launch-verified-local.json` enthält tatsächlich9COLD und31WARM;deren ungetrennte Zusammenfassung ist kein Kaltstartnachweis und wird nicht verwendet. Erste Vorversuche und40Rückkehrversuche ohne `TotalTime` bleiben ebenfalls ausgeschlossen. Keine vergleichbare Geräte-Vorhermessung,kein behaupteter Geschwindigkeitsgewinn. Hintergrundlast und Bildwiederholrate nicht gemessen.

Speicher tatsächlich an je10Punkten zweier Startreihen erfasst;Einheit wie `dumpsys`:KB. Die Messpunkte stammen nicht aus einem einheitlichen unveränderten Activity-Zustand und erlauben keinen Lecknachweis.

| Reihe | n | PSS min–max KB | RSS min–max KB | Private Other min–max KB | TOTAL SWAP (KB) min–max |
|---|---:|---:|---:|---:|---:|
| launch-measurements-local.json | 10 | 135720–328027 | 279196–470708 | 1704–5976 | 176–180 |
| launch-verified-local.json | 10 | 141772–341594 | 286644–486164 | 2216–3588 | 180–180 |

`Private Other` ist eine einzelne Kategorie,**nicht der gesamte private Speicher**;aus `TOTAL SWAP (KB)` wird kein Swap-PSS erfunden. Zweite Reihe PSS Anfang/Ende141772→169804KB,RSS286644→314376KB. Akku durchgehend100%,AC powered=true,USB powered=false,Thermal Status0. Batterietemperatur erste Reihe31,5–34,1°C,zweite33,0–35,2°C. Keine CPU-Auslastungsreihe mit zugeordnetem Bedienfenster;keine SQL-Zeiten/Abfragezahlen/Zeilen-/Bytes-/Snapshot-/Flow-Zähler gemessen.

`frames-before-local.txt` und auch der spätere Versuch `frames-profiled-local.txt` enthalten Header/Android-Zusammenfassungen,aber **keine numerischen Einzel-Framezeilen**. Somit individuelle `FrameCompleted−IntendedVsync`-Zeiten,deren Mittel/P95/Ende-5%-Mittel und FPS **NICHT GEMESSEN**. Es ist kein Parserausschluss belegter gültiger Frames. Die spätere Zusammenfassung meldet8269gerenderte Frames,61Janky(0,74%),P50/P90/P95/P99=5/6/6/30ms;die identische Zusammenfassung erscheint zweimal und wird nicht addiert. Die älteren vier Abschnitte sind ebenfalls nicht addiert oder Bedienwegen zugeordnet. Diese Android-Histogramme ersetzen keine individuellen Messwerte und erklären keine Ursache.

ADB-Transport separat:147456Bytes,SHA256 `9234590f51cc5f375a5163c065d8e759ecab20217373445703613121f8928c20`;Push0,708s/Pull0,594s bei n1 laut ADB. Keine UI-/SQLite-Laufzeit daraus. Listen/Seiten,Detail,Speichern,Timerabschluss,Fotoansicht und SQL/Flow haben keine40er-Reihen und bleiben **NICHT GEMESSEN**.

### Reproduzierbare Befunde und kleinste Verbesserungen

Alle folgenden Kotlin-Pfade beginnen mit `android/app/src/main/java/at/hausgarten/service/`. Beobachtete Wirkung und Quellhypothese sind getrennt;keine Verbesserung wurde implementiert.

#### B01 — P2: Kundenfoto-Kontextverlust

- **Beobachtung/Reproduktion:** Eigenen Kunden A mit eigenem Foto öffnen → Kundenfotos. Der Einstieg zeigt nicht nur A. Ein anschließender eindeutiger QA-Suchfilter zeigt die beiden eigenen Fotos von A und B;gespeicherte Zuordnungen sind fachlich korrekt.
- **Screenshot:** `photo-customer-filter-leak-synthetic-only.png` wurde visuell geprüft:genau2Treffer,nur geometrische Testbilder und synthetische Kundenbezeichnungen. Der gelöschte gemischte Beleg wird nicht verwendet.
- **Auswirkung:** Kundenbezogener Einstieg verliert seinen fachlichen Kontext;Verwechslung mit Fotos anderer Kunden möglich. Keine falsche gespeicherte Fotozuordnung oder externe Offenlegung nachgewiesen.
- **Quelle:** `NativePrimaryScreens.kt:226` übergibt customer_id korrekt;`ComposeMediaScreens.kt:83–84` initialisiert Kunden-ID/Label leer;`:98–99` fragt damit ungefiltert ab;`NativeDataRepository.kt:78` setzt den SQL-Filter nur bei nicht leerer ID.
- **KISS-Vorschlag:** Kunden-ID aus Routenargumenten initialisieren,Zustand an diesen Kontext binden,Kundenfilter sichtbar benennen. Zurücksetzen darf den Einstiegskontext nicht still aufheben. SQL-Vertrag beibehalten.
- **Nachtest:** Eigene Kunden A/B mit je einem Foto und leerer C;kundenbezogener/globaler Einstieg,Detail-Zurück,Filterreset,erneuter Einstieg,Neustart,200%.

#### B02 — P3: Kundenkarte mit redundantem Datum und rohem ISO-Zeitpunkt

- **Beobachtung/Reproduktion:** Neuen synthetischen Kunden anlegen → Kundenliste mit QA-Suche;neben den Stammdaten erscheinen ein Datum und der vollständige Erstellungszeitpunkt.
- **Screenshot:** `customer-list-130.png`.
- **Auswirkung:** Zusätzliche technische Zeilen erschweren das Lesen und verlängern Karten;kein falscher gespeicherter Zeitwert belegt.
- **Quelle:** `NativeDataRepository.kt:438–445` verwendet created_at-Fallback;`:451` hängt Datum an subtitle;`:465` speichert denselben vollständigen Wert als work_date. `NativePrimaryScreens.kt:180` zeigt beide.
- **KISS-Vorschlag:** Kundenprojektion ohne Arbeits-/Erstellungsdatum in der Listenkarte erzeugen;bei Update bestehenden abgeleiteten Cache gezielt neu aufbauen. Mappingänderung allein invalidiert gleicheRevision nicht (`NativeDataRepository.kt:398–400`).
- **Nachtest:** Neuer und vorhandener synthetischer Kunde,Cacheaufbau/Neustart;Arbeitsdatum bei Arbeiten weiterhin korrekt.

#### B03 — P2: Heute zeigt Einsatz doppelt und technische Felder

- **Beobachtung/Reproduktion:** Einen eigenen heutigen Einsatz planen → Heute öffnen. Derselbe Einsatz erscheint unter Visits und Planned visits,mit Feldern wie kind,format_version,created_at und remaining_minutes.
- **Screenshots:** `today-font100.png`, `today-font200.png`.
- **Auswirkung:** Unnötige Wiederholung und technische Angaben verdecken Tagesaufgaben. Kein Datenbankduplikat nachgewiesen.
- **Quelle:** `RevierApplicationService.kt:1940` liefert dasselbe planned-Array unter visits und planned_visits;`NativePrimaryScreens.kt:137` rendert alles über NativeBusinessValues. `NativeForms.kt:94,96,100–114` lässt diese technischen Felder durch und bildet unbekannte Labels mechanisch.
- **KISS-Vorschlag:** Heute aus vorhandenen Feldern mit deutscher Zusammenfassung und genau einer Einsatzliste darstellen;API-Kompatibilitätsalias erhalten.
- **Nachtest:** Ein heutiger/ein überfälliger eigener Einsatz,leere Listen,geänderte Einsätze,200%;Anzeige gegen eindeutige IDs prüfen.

#### B04 — P2: Zu geringer Kontrast der Statusleiste

- **Beobachtung/Reproduktion:** Tatsächlich helle Appansicht unter dokumentiertem System-Nachtmodus öffnen;Uhrzeit und Statussymbole sind hell auf sehr hellem Grund schlecht lesbar.
- **Screenshots:** `customer-list-130.png`, `photo-customer-filter-leak-synthetic-only.png`.
- **Auswirkung:** Systeminformationen schwer erkennbar. Quellseitig fester heller Appmodus belegt;dessen fachliche Unterstützung ist separat von diesem Kontrastfehler zu entscheiden.
- **Quelle:** `NativeCompose.kt:16–23` verwendet immer lightColorScheme;`android/app/src/main/res/values/styles.xml:3,7–8` setzt helles Theme und windowLightStatusBar=false;`MainActivity.kt:126–129` bindet das Theme ohne spätere Systemleistenanpassung.
- **Hypothese:** Moderne Edge-to-edge-Darstellung bei targetSdk37 kann den Unterschied zwischen deklarierter Leistenfarbe und tatsächlichem Hintergrund erklären;die genaue Ursache wurde nicht unabhängig gemessen.
- **KISS-Vorschlag:** Statusleistensymbolhelligkeit ausdrücklich an den tatsächlich gezeichneten Hintergrund anpassen;für das vorhandene helle Layout dunkle Symbole setzen. Keinen kompletten Darkmode allein wegen dieses Befunds neu bauen.
- **Nachtest:** Start,Dialoge,Hoch-/Querformat,beide Systemmodi und Schrift1,0/2,0;Status- und Navigationsleisten beurteilen.

#### B05 — P2: Unspezifische Startwarnung zur Testbereinigung

- **Beobachtung/Reproduktion:** MainActivity nach Schriftwechsel/Neustart öffnen:Toast „Testbereinigung benötigt Prüfung. Bestehende Daten bleiben erhalten.“;bei200%gekürzt. Eigene Fachdatensätze bleiben lesbar und Schreibzugriffe funktionieren.
- **Screenshot:** Kein eigenständiges erhaltenes Toastbild;Beobachtung und Quellenstelle dokumentiert. Aus anderen Screenshots wird kein Toastnachweis erfunden.
- **Auswirkung:** Nutzer kann Ursache/Handlungsbedarf nicht nachvollziehen;transienter gekürzter Text reicht nicht zur Prüfung.
- **Quelle:** `MainActivity.kt:152–153` fängt jede Exception aus compactPurgedTestActions mit derselben Meldung ab;`:347` nutzt Android-Toast. `LocalTestCleanup.kt:135–159,186–281` bearbeitet bereits dokumentierte lokale Purge-Aufträge mit Tombstone,Herkunfts-,Referenz- und Sicherungsnachweis.
- **Abgrenzung/Hypothese:** Frische Testkunden werden nicht allein wegen Kennzeichnung automatisch gelöscht;kein besonderer Datum-/ID-Filter. Die konkrete Exception ist ungeklärt;alte Queue-/Receipt-/Medienprobleme sind nur mögliche Ursachen.
- **KISS-Vorschlag:** Datensparsamen Fehlercode und dauerhaft erneut lesbaren Hinweis bereitstellen;keine zusätzliche automatische Löschung und keine Queuebereinigung zur Unterdrückung des Hinweises.
- **Nachtest:** Erfolgreicher Start ohne Warnung und gezielt synthetischer Bereinigungsfehler;vollständig lesbarer Hinweis bei200%;bestätigte Datensätze erhalten.

### Testende, Rücksetzungen und verbleibende Grenzen

Abschlussmetadaten `device-cleanup-final-local.json`:Font1,3,Rotation0,AutoRotation0,GPU-Profil leer;genau die eigene Download-Prüfdatei und ihr leeres Verzeichnis entfernt,beide Exit0. UI-/Messhelfer beendet;nach dem Nutzer-Testende ausschließlich nötige Rücksetzung/Bereinigung,keine weiteren Bedien- oder Messläufe. Die aktualisierte lokale Datei `app-rw-receipts-local.json` enthält164Requests und `cleanup_results`:Alle18eigenen Fachdatensätze sind aus aktiven Ansichten entfernt,17archiviert und der Kunde Alpha auf active=false gesetzt. Seine Archivierung wurde mit REVIER_REFERENCE_PROTECTED gesperrt;Bezüge bleiben erhalten. Beim eigenen Einsatz verhinderten zunächst VISIT_STILL_ACTIVE und danach CORRECTION_REASON_REQUIRED eine unzulässige Archivierung/Absage. Nach begründeter Absage konnte genau dieser Einsatz archiviert werden. Kein Gate umgangen,kein Purge und keine historischen Belege gelöscht. Finalisierte Timersitzung und Aktionsbelege bleiben erhalten;letzte aktive Timerabfrage IDLE.

Der getestete Appstandard default_location blieb nach Rücksetzung als leererString erhalten,obwohl er vorher fehlte. Die Oberfläche zeigt beide Zustände leer;typisiert sind sie verschieden (`NativeForms.kt:114`, `SettingsApplicationService.kt:24–40`). Kein unterstützter Remove-/Null-Vertrag;keine direkte SQL-Umgehung. Dieser Rest ist ausdrücklich kein exakt zurückgesetzter Einstellungszustand.

Nicht vollständig geprüft: positive Zahlungsbuchung,produktive Belegfinalisierung,externe Konten/Versand,neues Backup/Restore/Rollback,alle Foto-/Fehler-/Abbruchvarianten,alle Fachbeziehungen und Seitenfälle sowie schnelle Scrollfolgen mit belastbaren Framezeiten. Das tatsächliche Öffnen aller28Bereiche,erfolgreiche eigene CRU-Pfade und gemessene TTID-Werte begründen keine vollständige Abnahme.


Dokumentationsprüfung: JSON-Syntax, 67 eindeutige Test-IDs, Pflichtspalten, Tabellenzusammenhang und Ausschluss von Seriennummer/Profilkennung geprüft. Der unveränderte Pflichtdateien-Hook ist lokal erfolgreich; vollständiger Quellcheck bleibt mit dem beschriebenen Umgebungsfehler offen. Keine Appquelle geändert.


## Bedienkonzept und synthetische Abnahme vom 30.10.2026

Frisch ermittelte Arbeitsbasis: `codex/kotlin-refactoring-20261029`, Commit
`242ccc9159cefdb22879c57474bcd45586244ab6`. Die App-Quellen entsprechen dort
Build120 / 1.57.1, Quellcommit `f770f38d0bd428ec93004252d1aaacf003a475e2`.
`main` enthält Build117. Es gab keinen vorhandenen lokalen Checkout oder lokale
Änderungen. Alle Änderungen setzen auf dem aktuellen Entwicklungszweig auf.
Quellstand, laufende Prüfungen und offene Punkte stehen im aktuellen Arbeitsstand;
diese Dokumentation beschreibt Bedienwege und Nachweisgrenzen.

### KISS-/YAGNI-Entscheidungen

Vorhandene Route-Argumente, indizierte SQL-Seiten, Fachberichte, Entwürfe und
transaktionale Schreiber bleiben zuständig. Keine neue Architektur, Abhängigkeit,
Datenbank, Migration, Filterprofile oder Freigabeverwaltung. Ein zusätzliches
Navigationsframework ist für vier Bereiche und den vorhandenen Verlauf unnötig.

Die tatsächlichen Kunden-, Geräte-, Planungs-, Foto- und Textauswahlen teilen
sich eine Suchfläche. Sie zeigt Suche, Trefferzahl, Nulltreffer und Ergebnisliste
unmittelbar zusammen. Zusatzfilter sind kompakt erreichbar. Suchtext und Filter
überstehen Detail-/Editorwechsel und Activity-Neuerstellung. IME-Suchen/Fertig
beendet die Eingabe; Auswahl schließt Wähler und Tastatur. Große Schrift wird
nicht verkleinert. Gleichnamige Kunden besitzen Adresse/Ort zur Unterscheidung.

Ein vollständiges Material-3-Farbschema, vorhandene Schriftrollen und Formen,
lesbare Statusflächen und umbrechende Aktionsleisten vereinheitlichen die App.
Der Zurück-Pfeil behält eine beschriftete 48-dp-Tastfläche. Die vier Hauptbereiche
bleiben in Unteransichten erkennbar; „Arbeit“ öffnet Arbeitsaufzeichnungen. Bei
200 % Schrift stehen die Bereichsschalter in zwei Reihen, im Querformat seitlich.
Ausgewählte Bereiche bleiben anklickbar. Der explizite Auswahlzustand ersetzt die
zwischenzeitlich ungeeignete Tab-Rolle, die den Übersichtsklick gesperrt hatte.

Gemeinsame Übersichten verwenden bestehende Beziehungen und begrenzte Listen.
Die Kundenakte zeigt Kontakt, Objekte, nächste Termine/Einsätze, offene Stunden,
letzte Arbeiten, Fotos und Belege. Arbeit bündelt Timer, Nachtragen, Verlauf und
Arbeitsentwürfe. Abrechnung bündelt offene Arbeiten, Belege, Entwürfe, Zahlungen
und Guthaben. Geräte behalten ihre vorhandene gemeinsame Zubehör-/Ketten-/Foto-
und Wartungsansicht. Einstellungen bündeln Verbindungen, Sicherung und Diagnose.

Arbeitskunde und Rechnungsempfänger bleiben getrennt, auch bei mehreren Kunden
mit demselben Empfänger. Der Quellenfilter wird im Formularentwurf erhalten;
die vorhandene Quellenauswahl lässt sich ausdrücklich auf den Empfänger erweitern.
Dieser Anzeigekontext wird nicht als neues Rechnungsfachfeld gespeichert. Objekt-/Anfrageeinstiege übernehmen den vorhandenen Titel und Einsatzort in den Angebotsentwurf; der Herkunftskontext bleibt dort sichtbar und wird mit dem Formularentwurf wiederhergestellt. Es wird keine neue fachliche Objekt-Angebotsbeziehung erfunden. Eine
Tagesübersicht oder Tour erzeugt keine Arbeitsbuchung. Persönliche Bezahlmarkierung
und belegte Rechnungszahlungen bleiben getrennt. Restore- und Belegschutzprüfungen
werden nicht durch Übersichten ersetzt.

### Funktionszuordnung

| Vorhandene Ansicht / Funktion | Gemeinsamer oder direkter Einstieg | Suche / Filter und erhaltene Aktionen |
| --- | --- | --- |
| Heute | Hauptbereich Heute | Geplante Einsätze, heutige Arbeiten, Hinweise; manuelle Erfassung und Timer |
| Arbeitsaufzeichnungen, Timer | Hauptbereich Arbeit / Heute | Suche, Kunde, Zeitraum, Zusammenfassung, Mehrfacharchivierung; laufende Arbeit, Nachtragen, Entwürfe |
| Kundenakte | Hauptbereich Kunden | Suche, aktiv/archiviert, Abrechnungsweg; Kontakt, Bearbeiten, Objekte, nächste Termine/Einsätze, Stunden, Historie, Fotos, Angebote, Aufträge, Abrechnung |
| Objekte, Anfragen, Aufträge, Pflegepläne | Büro → Kunden und Aufträge; Kundenakte kontextbezogen | Suche, vorhandene Status-/Archivfilter; Erstellen, Beziehungen, Historie und alle bisherigen Fachaktionen |
| Planung, Termine, Einsätze | Büro → Planung; Heute / Kundenakte | Suche, Tag/Woche, Datum, Kunde, Status; Termin/Einsatzdetails, Vorschläge, Kollisionsprüfung, Entwürfe |
| Tagestour, Wetter | Planung → Tagestour / Wetterhinweise | Tagesbezug, bestehende Tourreihenfolge und Wetter-/Kalenderprüfungen; keine automatische Ist-Buchung |
| Angebote, Rechnungen, Zahlungen, Guthaben | Büro → Abrechnung; Kundenakte → Abrechnung dieses Kunden | Vorhandene Editoren/Prüfungen; Suche und Kunde bzw. Empfänger/Status; unveränderte Zahlungen, Korrekturen und Original-PDFs |
| Leistungsbausteine | Büro → Material und Wissen → Leistungskatalog; Belegeditor | Suche, Quelle wählen, Positionen/Preise ausdrücklich prüfen |
| Geräte, Zubehör, aktuelle Kette, Wartung | Büro → Maschinenpark und Gerätewartung | Gemeinsames bestehendes Gerätedetail mit Tabs, Fotos, Zubehör und Wartung; Suche/Zustand/Archivfilter |
| Material | Büro → Material und Wissen → Material | Suche, Bestand, Bewegungen/Gegenbuchung und unveränderte Zuordnungsprüfungen |
| Fotos | Büro → Material und Wissen → Fotos; Kundenakte → Kundenfotos | Suche, Kunde/Auftrag/Arbeit/Zeitraum/Rolle, Zuordnen, Kamera/Galerie, Papierkorb und Qualität |
| Pflanzen | Büro → Material und Wissen → Pflanzen | Suche nach Name/Familie, Gruppen-/Standort-/Neophytenfilter, Details und Zuordnung |
| Entwürfe | Arbeit → Arbeitsentwürfe; Abrechnung → Abrechnungsentwürfe; Büro → Weitere Verwaltung → Entwürfe | Gemeinsamer vorhandener Posteingang mit Bereichsauswahl; Fortsetzen, ursprüngliche Aktion prüfen, Verwerfen, übernommene Quellen |
| Lokale Suche, Revier-Archiv, Papierkorb, lokale Vorgänge | Büro → Weitere Verwaltung | Bestehende Suche/Filter/Seiten; Wiederherstellungs- und Löschschutz bleibt wirksam |
| Einstellungen, Verbindungen | Büro → Einstellungen | Standards, Unternehmensdaten; Kalender/Wetter und ChatGPT; Diagnose; weitere Fachprüfungen aufklappbar |
| Sicherung / Wiederherstellung | Einstellungen → Sicherung und Wiederherstellung | Unveränderter Android-Dateidialog, Speichern/Zurücklesen, Vorschau, ausdrückliche Wiederherstellung, Wiederanlauf/Rückfallpfade |

Die 210 semantischen Aktionen bleiben im vorhandenen Manifest erfasst. Die
Quellzuordnung allein beweist keine vollständige Geräteabnahme. Die zugehörige
Compose-Prüfung öffnet 38 Einstiege, prüft 16 fachliche Suchen und zwölf
Darstellungskombinationen; genaue Durchführung in `../entwicklung/BAU.md`.
Sonderseiten werden dabei gelesen, ohne Kontofreigaben oder produktive Aktionen.
`FLAG_SECURE` der ChatGPT-Zugangsdatenansicht wird nicht für Screenshots aufgehoben.

### Vergleich derselben Bedienaufgaben

Vorher meint den oben gebundenen Ausgangsquellstand. Die folgende Tabelle zählt
Navigationsaktionen ab demselben Ausgangspunkt, keine Buchstabeneingaben. Sie ist
ein Quell-/Bedienwegvergleich; genaue Scrollstrecken und Reaktionszeiten werden
daraus nicht als gemessene Verbesserung behauptet.

| Bedienaufgabe | Vorher | Neue Umsetzung / bewusster Nachteil |
|---|---|---|
| Kundenakte → Fotos / Angebote | Direkter Einstieg übergibt Kunde, Liste nutzt ihn nicht zuverlässig; manuelle Filtersetzung nötig | Direkter Einstieg nutzt und zeigt den Kundenfilter; A/B-Ausschluss wird synthetisch geprüft |
| Kundenakte → Objekte / nächste Einsätze / offene Stunden | Getrennte Bereiche und erneute Orientierung | Bestehende Daten stehen in derselben Akte; „Alle …“ führt in die gefilterte vollständige Liste |
| Heute → manuell erfassen / Timer | Je ein direkter Einstieg | Je ein direkter Einstieg bleibt erhalten |
| Büro → Rechnungen | Büro, Rechnungen: zwei Navigationsaktionen | Büro, Abrechnung, Rechnungen: drei Aktionen; die gemeinsame Abrechnungsübersicht kostet hier eine zusätzliche Ebene |
| Büro → Maschinenpark | Zwei Navigationsaktionen plus Scrollen durch die lange Bürofolge | Zwei Aktionen; Maschinenpark ist die dritte Hauptkachel |
| Büro überblicken | 26 Ziele plus „Arbeiten abrechnen“, in drei offenen Gruppen | Vier Hauptkacheln plus drei aufklappbare Gruppen; alle bisherigen Funktionen bleiben über die Tabelle erreichbar |
| Kunde in Formular wählen | Dialog mit bis zu 40 Treffern, danach Suche eingrenzen; kein Seitenwechsel | Begrenzte Seiten mit Vorherige/Weitere, ausdrücklich null Treffer und getrennte „Keine Zuordnung“; nach Auswahl ist die Zuordnung sichtbar |
| Speichern und weiterwechseln | Allgemeine Meldung kann im nächsten Bereich bleiben | Vorgangsbezogene Bestätigung; Navigation/Zurück löschen alte Meldungen |

Die neue Büro-Zeitmessung beginnt in der gemeinsamen Abrechnungsübersicht. Sie
ist wegen dieses veränderten Starts nicht mit den früheren Büro-Zahlen oben
vergleichbar; kein neuer Laufzeitgewinn wird daraus abgeleitet.

### Bestätigte Fehler und Grenzen

Im bisherigen Emulatorlauf sichtbar bestätigt: Bei 200 % drückte eine lange
Filterbeschriftung die Trefferzahl auf einzelne Buchstaben; die kurze Filteraktion
und umbrechende Trefferzeile korrigieren den Aufbau. Der alte Kundenwähler wurde
zunächst nur ohne Tastatur abgebildet. Diese Bilder zählen ausdrücklich nicht
als Nachweis für sichtbare Treffer bei geöffneter Tastatur. Die korrigierte Prüfung verlangt eine sichtbare IME unmittelbar vor und nach jeder Aufnahme. Das Auswahlfenster behält denselben Eingabeknoten beim Wechsel zwischen Stapel- und Querformatanordnung; die Systemsymbole erhalten auf der hellen Fläche dunklen Kontrast.

Prüfcodefehler wurden separat behandelt: Ein identischer Suchfeldwert wurde zuerst
als Treffer gefunden; Querformat war in den damaligen Fällen tatsächlich nicht
erreicht. Die korrigierte Prüfung verlangt weiterhin echte Orientierung,
IME-Fenstergrenzen und injizierte Touchauswahl. Ein vorhandener Accessibility-Knoten
allein zählt nicht als bestandene Sichtbarkeitsprüfung.

Maßgeblich sind die Ergebnisse und der exakte Quellfingerabdruck im aktuellen
Arbeitsstand. Fehlgeschlagene Zwischenläufe werden nicht als PASS gewertet.
Die vollständigen CI-Checkouts führen `check_current.py`, Build/Lint, neun JVM-
und acht native Suiten aus. Im lokalen Teilcheckout fehlen große unveränderte
Pflanzenassets; sein vollständiger `check_current.py` endet an dieser fehlenden
Datei. Die 62 vorhandenen Python-Tests bestehen bei Aufruf ihrer Testprogramme.

Physischer Pixel, echtes Drive-Konto, produktive Installation, Migration,
Wiederherstellung, Rechnungsfreigabe und Veröffentlichung gehören nicht zu diesem
Auftrag. Die zuvor gebundenen historischen Pixel-Belege bleiben historische
Belege und sind keine Abnahme dieser Oberfläche.
