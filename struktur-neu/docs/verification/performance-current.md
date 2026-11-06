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
Aussagegrenzen in dieser Datei.

## Bedienkonzept und synthetische Abnahme vom 30.10.2026

Bei dieser historischen Prüfung verwendeter Commit
`242ccc9159cefdb22879c57474bcd45586244ab6`. Die App-Quellen entsprechen dort
Build120 / 1.57.1, Quellcommit `f770f38d0bd428ec93004252d1aaacf003a475e2`.
Es gab bei dieser Prüfung keinen vorhandenen lokalen Checkout oder lokale
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

Die 170 semantischen Aktionen bleiben im vorhandenen Manifest erfasst. Die
Quellzuordnung allein beweist keine vollständige Geräteabnahme. Die zugehörige
Compose-Prüfung öffnet 38 Einstiege, prüft 16 fachliche Suchen und sechs
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

### Bestätigte Fehler und Korrekturen

Die Quellprüfung bestätigte die übergebenen, aber nicht zuverlässig angewendeten
Kundenfilter für Fotos und Angebote, fehlende Bereichszuordnung in Unteransichten,
problematische lange Navigationsnamen, die gleichgewichtige Bürofolge, uneinheitliche
Oberflächen und technische beziehungsweise doppelte Detailangaben. Alte allgemeine
Speichermeldungen konnten einen Seitenwechsel überstehen. Die beschriebenen
Kontextfilter, Hauptbereiche, gemeinsamen Bausteine und begrenzten Meldungen
adressieren diese Befunde.

Der Lauf auf `d22c546c5662d2f770ac70226cb01184469de7aa` bestand die zwölf Auswahl-Darstellungen, 16 fachlichen Suchen, Filtereingaben und 38 Einstiege; allein die echte Randgeste blieb fehlgeschlagen. Die systemseitige Gestenerkennung wird mit deutlicherem Weg über die Aktivierungsschwelle und unmittelbarer SystemUI-Diagnose getrennt geprüft; der Dialog verwendet weiterhin Compose-Standardverhalten. Die spätere gemeinsame Fachlisten-Anordnung und SQLite-Lebenszykluskorrektur benötigen ihre eigenen vollständigen Nachweise.

Die alten grünen Systemleisten-Hintergründe hatten zusammen mit dunklen Android-Symbolen zu wenig Kontrast. Die vorhandene Theme-Ressource verwendet jetzt die helle App-Fläche und dunkle Systemleisten-Symbole.

Die strengere Bildschirmprüfung fand zusätzliche konkrete Fehler: lange Filtertexte
verdrängten die Trefferzahl; im kleinen Hochformat wurde bei 200 % ein Treffer
angeschnitten; im Querformat konnte die Trefferzahl unter der Tastatur verschwinden.
Das gemeinsame Auswahlfenster verteilt Suche und Treffer nach dem tatsächlich
verfügbaren Raum. Im engen Querformat ersetzt ein Platzhalter die zusätzliche
schwebende Beschriftung, bei erhaltener zugänglicher Bezeichnung und Schriftgröße.
Suchfeld, Trefferzahl und antippbare Treffer werden gemeinsam innerhalb der sichtbaren
Grenzen geprüft. Foto- und Pflanzensuchen behalten bei vorhandenem Suchtext die
kompakte Ergebnisliste nach „Fertig“.

Aufklappbare Listenfilter bleiben beim Bearbeiten ihrer eigenen Datums-/Statusfelder
sichtbar. Erst der Fokus auf die Hauptsuche schließt sie. Ein stabiler Listenschlüssel
erhält den Eingabeknoten, wenn die Tastatur ergänzende Kontextzeilen ausblendet.
„Zur Liste“ verwendet den vorherigen Listenzustand, einschließlich Suchtext und
Filter. Der Timer zeigt Arbeitsdauer in Stunden/Minuten/Sekunden, Beginn, Pause und Kundenbezug statt interner Millisekunden- und Zustandsfelder; Zeitkorrektur- und Prüfhinweise bleiben sichtbar. Timer und Belegdetails haben ihre häufigen Aktionen am unteren Rand;
ergänzende Prüfungen und Quellen bleiben im scrollbaren Inhalt. Einstellungen
kennzeichnen das Laden; die Abnahme wartet auf die bedienbare Hauptaktion.

Wiederholte Darstellungswechsel zeigten außerdem `SQLITE_BUSY` und eine geleakte
SQLite-Verbindung. In `PixelLocalStore.getState()` fehlte eine Referenzhaltung über
die gesamte Transaktion; Activity-Schließen konnte den Pool vor deren Ende schließen.
Die Abfrage verwendet nun denselben Referenzmechanismus wie die indizierten Lesewege.
Ein deterministischer nativer Test schließt den Helper mitten in der Bestandsabfrage
und prüft Erfolg, Fehlerpfad, vollständige Freigabe und erneutes Lesen/Schreiben.
Keine globale Sperre, geänderte Fachbuchung oder zusätzliche Speicherarchitektur.

Prüfcodefehler wurden getrennt korrigiert: identischer Text im Suchfeld zählte zuerst
als Treffer; einzelne Querformatfälle hatten die Orientierung noch nicht erreicht;
Folgezustände wurden teils während des UI-Übergangs gelesen. Die Prüfung wartet nun
auf die tatsächliche Konfiguration und unveränderten Suchwerte. Frische Testaufgaben
starten nach Sonder-Activities mit `NEW_TASK | CLEAR_TASK`, damit Android nicht nur
eine bestehende Kalenderaufgabe nach vorne holt. Der Gestentest berücksichtigt den
Android-Basismodus ohne aktiviertes Navigations-Overlay und stellt den Ausgangsmodus
wieder her. Ein Accessibility-Knoten allein zählt weiterhin nicht als Sichtnachweis.

### Nachweise und verbleibende Grenzen

Maßgeblich sind Ergebnis, Commit und Quellfingerabdruck im aktuellen Arbeitsstand.
Fehlgeschlagene Zwischenläufe werden nicht als PASS gewertet. Der vollständige
CI-Checkout führt `check_current.py`, Release-/Test-Build, Lint, neun JVM-Suiten
und acht native Suiten aus. Der lokale Teilcheckout enthält die großen unveränderten
Pflanzenassets nicht; sein Gesamtcheck endet an dieser fehlenden Datei.
Der aktuelle Python-Testbestand umfasst 59 Tests.

Die normalen Fachlisten verwenden dieselbe adaptive Flächenaufteilung wie die Auswahlfenster. Im Querformat stehen Suchkopf und Ergebnisse nebeneinander; bei großer Schrift im Hochformat steht die Trefferzahl neben dem Suchfeld. Zusatzfilter bleiben nach Beenden der Eingabe erreichbar. Bei großer Schrift bleibt auch der geschlossene Suchkopf kompakt; seine zugängliche Feldbezeichnung bleibt vollständig erhalten. Auswahlfenster behalten ohne Tastatur ihren vollständigen Kopf. Die Eingabeknoten bleiben beim Umordnen erhalten. Technische Lesefehler erscheinen in den Listen als verständliche deutsche Rückmeldung; Fach- und Schutzprüfungen bleiben unverändert.

Die Compose-Abnahme kombiniert sechs Darstellungen (100/130 % bei normaler Höhe,
200 % bei kleiner Höhe, jeweils Hoch-/Querformat), 16 fachliche Suchwege, 17 zusätzliche Fachlistenfälle bei 200 %/kleiner Höhe gemäß `../entwicklung/BAU.md` und 38 Einstiege mit synthetischen
Bildaufnahmen, tatsächlichen IME-Fenstergrenzen und injizierten Android-Touchereignissen.
Sie prüft zusätzlich leere Suche, null/einen/viele Treffer, Seitenwechsel, A/B-Kontext,
gleichnamige Kunden, Filtereingaben, Zurück-Taste/-Geste, Speichern, Verwerfen,
Entwurfsfortsetzung und Activity-Neuerstellung. Sichtbarkeit und Antippbarkeit werden
für die geprüften Ziele belegt. Bei langen Detailkarten in sehr kleinen Ausschnitten müssen Titelanfang und mindestens 48 × 48 dp Touchfläche sichtbar sein; ergänzende Karteninhalte bleiben scrollbar. Dialogtreffer behalten die strengere vollständige Zeilenprüfung; daraus folgt keine Abnahme jeder denkbaren Kombination
auf jedem Android-Gerät. Lange Formulare bleiben scrollbar, ohne ihre Schrift zu verkleinern.

Physischer Pixel und ein echtes Drive-Konto sind nicht geprüft. Produktive Installation,
Migration, Wiederherstellung, Rechnungsfreigabe und Veröffentlichung sind nicht erfolgt.
Die zuvor gebundenen historischen Pixel-Belege sind keine Abnahme dieser Oberfläche.
Die ChatGPT-Zugangsdatenansicht behält `FLAG_SECURE`; der Schutz wird für Bilder nicht
aufgehoben. Es werden ausschließlich vollständig synthetische Daten verwendet.

Lokale Backup-/Löschvereinfachung (Zusatzauftrag, Umsetzung in Prüfung): Der
Sicherungspfad streamt Originaldateien direkt, der Restore behält seine notwendige
Rückfallkopie. Eine gemeinsame Speicherbereinigung ersetzt die alte testdatenabhängige
Tombstone-/Receipt-Kompaktierung und deren unspezifischen Start-Toast (B05).
Normale Archivierung/Purge benötigen keine vom Nutzer beschafften Hashes.
Einstellungen zeigen Speicherbereiche und bieten eine einzelne Bereinigungsaktion.
Cache und alte unreferenzierte Lösch-Sicherungen sind einbezogen. Belege und
Foto-Idempotenzantworten endgültig gelöschter Einträge werden entfernt; Antworten
zu noch bestehenden Datensätzen bleiben erhalten. Die zusätzliche UI-Prüfung
betätigt die Bereinigung und kontrolliert den unveränderten Geschäftsdatenstand
sowie die sichtbare Aktion bei 100 % und 200 % Schrift.
KISS: vorhandene SQLite-Transaktion und bestehende Dateiverweise statt zusätzlicher
Freigaben; keinerlei neue Abhängigkeit. Noch kein bestandener Lauf der neuen
Speichersuite und keine Messung des produktiven 22,8-GB-Bestands.

Nachprüfung ce44f02: Quellprüfung, Android-Prüfbuild/Lint und neun JVM-Suiten
bestanden; native ZIP-Sicherung51 und neuer Prozess13, Formular-Restore79+23
bestanden. Die neue Speichersuite erreichte den Entwurfs-PDF-Fall; dessen
synthetischer Datensatz hatte keinen Status Entwurf und wurde korrekt geschützt.
Testvorlage korrigiert, vollständiger Lauf bleibt offen. Die UI-Suite erwartete
noch den alten Löschdialog; Folgefehler ohne erzeugtes Zubehör sind kein neuer
Beleg für Geräteverlust. Großschrift in Planung, lokaler Suche und Fotos nach
IME-Ende benötigt mehr Ergebnisraum: ergänzende Aktionen liegen bei Suchtext
nun am Listenende. Der Dialog berücksichtigt auch die tatsächliche IME-Höhe.
Der Gesten-Dump zeigte zuerst einen inaktiven Taskbar-Handler; daraus lässt sich
nicht auf den aktiven Navigationsleisten-Handler schließen. Der Nachtest erfasst
alle Handler und injiziert tatsächliche Touch-Ereignisse von beiden Rändern.

## Eigentümeränderung vom 03.11.2026: Speicher, Löschen und Menüs

Die vorher beschriebenen Rückfallkopien und zusätzlichen Löschbestätigungen sind
abgeschafft. Der neue Stand entfernt alte interne Arbeits-/Sicherungsverzeichnisse
rekursiv, gibt abgeschlossene Restore-Arbeitsbäume frei und entsorgt leere
Unterordner. `full-backup-work` gehört ausdrücklich zur Bereinigung. Einstellungen
verwenden kompakte deutsche Ergebnisse ohne rohe Kennungs-/Protokolllisten.
Der Pixel-Screenshot zeigte zuvor 22,53 GB Arbeits-/Wiederherstellungsdaten.
Eine tatsächliche Freigabe dieser Bytes ist erst nach Installation und Ausführung
am Pixel messbar. Prüfstand und Grenzen stehen in `AKTUELLER-STAND.json`.

## Physischer Pixel über USB-ADB am 06.11.2026

Pixel 10 Pro mit Android 17 (API 37), 1280 × 2856 px, 480 dpi,
Gestennavigation und rund 52 GB freiem Speicher. Ausgangswerte waren 130 %
Schrift, Hochformat und ausgeschaltete automatische Drehung. Benachrichtigungen
und beide Standortberechtigungen waren erteilt. Die Fotoaufnahme öffnet die
externe Kamera-App. ADB 37.0.1 lief lokal am Windows-Laptop. Nach der
USB-Freigabe war genau das ausgewählte Pixel verbunden. Der App-Prozess
lief bereits, einschließlich des Tunnel-Vordergrunddiensts. Die separate
Test-App mit anderer Paketkennung gehörte nicht zum Prüflauf.

Installiert war `at.hausgarten.service` 1.61.0 (131). Die lokal über USB-ADB
gelesene APK-Prüfsumme stimmt exakt mit der signierten Release-Referenz überein.
Die unveränderten Git-Blobs des Release-Commits `7e3b045d` und des untersuchten
`main`-Commits `94803853` ergeben jeweils `67afb8663297c6d3b5cfa0825514d06b76a775e52e5e848c11fae50676820a38`.
Die ersten MCP-Aufrufe von `get_capabilities` scheiterten am HTTP-Transport.
Der spätere Abruf meldete dasselbe Paket und `runtime.build_fingerprint` für
1.61.0 (131) mit Git `7e3b045d1975c407885a85bb9da1edb167c395e9`, demselben
`source_sha256`, `source_dirty=false`, Buildtyp `release` und MCP-Protokoll 9.
Die Quellbindung ist separat im [Arbeitsstand](../../AKTUELLER-STAND.json) erfasst.

### Ausgeführte Bedienfälle und Rücklesen

Alle Bedienhandlungen wurden per ADB an der tatsächlichen Oberfläche ausgelöst.
Bei reinen Sichtbefunden nennt die Tabelle ausdrücklich die abgelesene Anzeige.
Zubehörfunktionen beziehen sich auf das eigene synthetische Gerät
`GPQA-20261106-Geraet`. Die Aktionen der Fotoansicht und die Systemübergänge sind
Zusatzfälle zur Katalogzuordnung. Ein bestandener Teilpfad deckt nur die genannten
Bedienhandlungen ab.

| ID | Bereich und Aktions-ID | Voraussetzung | Schritte | Erwartet | Tatsächlich und wirksam zurückgelesen | Status |
| --- | --- | --- | --- | --- | --- | --- |
| QA01 | Heute, `revier.get_revier_overview` | App auf Heute | Übersicht und Timerstatus ablesen | Der angezeigte Startzustand ist erkennbar | „Arbeit starten“ war sichtbar. Eine aktive Timerleiste wurde nicht angezeigt. Die Schaltfläche wurde hier abgelesen. | BESTANDEN |
| QA02 | Geräte, `revier.list_equipment` | App geöffnet | Zu Geräte navigieren und Liste öffnen | Geräteliste wird angezeigt | Der tatsächlich geöffnete Gerätebereich zeigte die Liste. | BESTANDEN |
| QA03 | Geräte, `revier.save_equipment` | Geräteliste geöffnet, eigener Testname | Synthetisches Gerät anlegen, speichern, Liste und Gerätedetail erneut öffnen | Genau das gespeicherte Gerät lässt sich wiederfinden | Das Gerät war nach dem Speichern über die Liste erneut zu öffnen. | BESTANDEN |
| QA04 | Zubehör, `platform.151` und System-Zurück | Zubehörformular mit eingegebener Bezeichnung | Fotoauswahl antippen, aus der verschachtelten Bildansicht zweimal Android-Zurück auslösen | Rückkehr ins Formular, Eingabe und Fototasten wieder verfügbar | Erste Rückaktion führte zur Picker-Wurzel, zweite ins Zubehörformular. Die Bezeichnung blieb erhalten und die Aktionen waren wieder sichtbar. Die Fotoauswahl wurde in QA05 erneut ausgelöst. | BESTANDEN |
| QA05 | Zubehör, `platform.151` und `revier.save_equipment` | Zubehörformular, synthetisches JPEG mit 18.570 Byte in Downloads | Foto auswählen, JPEG antippen, speichern, Geräteübersicht und Detail erneut öffnen | Gespeichertes Zubehör und aktuelle Kette lassen sich erneut lesen | Systemauswahl und Speichern ausgeführt. Das erneut geöffnete Gerätedetail zeigte die Kettenzuordnung. | BESTANDEN |
| QA06 | Zubehör, `platform.151` und `revier.save_equipment` | Eigenes Gerät und synthetisches JPEG mit 6.224.233 Byte | Zweimal die Datei über den Systempicker auswählen und speichern. Formularzustand, Hinweise und Rückkehr beobachten. | Während der Sperre erscheint eine verständliche Beschäftigtanzeige. Erfolgsanzeige folgt auf Abschluss. | In beiden Läufen war Speichern ausgegraut. Eine Beschäftigtanzeige fehlte. Der Hinweis „Lokal gespeichert.“ aus einem früheren Speichern blieb während der laufenden Verarbeitung sichtbar. Beide Läufe kehrten später ins Gerätedetail zurück. Zeiten stehen unten. | FEHLGESCHLAGEN |
| QA07 | Zubehör, `revier.save_equipment` und Wiederholung | Erster Lauf von QA06, ein synthetisches Zubehör | Speichern schnell zweimal antippen und Gerät nach Abschluss erneut öffnen | Ein Zubehör, zugehöriges Foto und aktuelle Kette | Ein Zubehör wurde erneut gelesen. Foto und aktuelle Kette waren im Gerätedetail sichtbar. | BESTANDEN |
| QA08 | Zubehörfoto, Zusatzfall | Gespeichertes Foto im Gerätedetail | Foto antippen und Android-Zurück auslösen | Foto vergrößert sich und Zurück führt zum Detail | Das tatsächliche Antippen öffnete die Vergrößerung. Android-Zurück schloss sie. | BESTANDEN |
| QA09 | Kamera, `platform.151` und System-Zurück | Zubehörformular geöffnet | „Foto aufnehmen“ antippen, in Google Kamera Android-Zurück auslösen | Kamera öffnet sich, Abbruch erhält das Formular | Google Kamera öffnete sich. Android-Zurück führte zum unveränderten Formular mit sichtbaren Aktionen. | BESTANDEN |
| QA10 | Zubehör, Großschrift und Querformat, Zusatzfall | Ausgangsschrift 130 %, Rotation 0 und Automatik 0 | Schrift auf 200 % stellen, Hoch- und Querformat beobachten, im Querformat „Zubehör bearbeiten“ und „Zurück“ tatsächlich antippen, Ausgangswerte wiederherstellen | Der geprüfte Einstieg und der Rückweg bleiben bedienbar. Schrift und Rotation werden zurückgesetzt. | Beide Formate wurden bei 200 % beobachtet. Die beiden Eingaben im Querformat führten in die Bearbeitung und zurück. Ausgangswerte 1,3 / Rotation 0 / Automatik 0 wurden wiederhergestellt. | BESTANDEN |
| QA11 | Kameraaufnahme und Rückgabe, `platform.151` | Kamera erreichbar, neutrale Szene vor der Linse muss bestätigt sein | Auslösen, Aufnahme übernehmen, speichern und erneut öffnen | Die Aufnahme erscheint erneut beim Zubehör | Auslösung und Rücklesen stehen aus, da die neutrale Szene noch unbestätigt war. | BLOCKIERT |

### Beobachtete Dauer beim großen Zubehörfoto

Die Zeiten wurden am Laptop ab der jeweiligen Speichereingabe erfasst. Die
Momentaufnahmen begrenzen den beobachteten Zustand zeitlich. ADB-Eingabe,
Bildabruf und deren Laufzeiten sind Teil der Beobachtung. Die Tabelle gibt
Zeitfenster bis zur sichtbaren Reaktion und zum erneuten Detail an.

| Lauf | ADB-Eingabe dauerte | Speichern zuerst grau abgelesen bei | Formular zuletzt sichtbar | Detail sichtbar bis spätestens |
| --- | ---: | ---: | ---: | ---: |
| 1 | 0,156 s | 1,016 s | 26,469 s | 28,937 s |
| 2 | 1,109 s | ca. 5 s | 54,375 s | 60,125 s |

In Lauf 2 war der Bildschirmwechsel bei 57,218 s bereits im Gang. Die separate
ADB-Dateiübertragung dauerte beim kleinen Bild 0,492 s und beim großen 2,582 s.
Die beiden Speichermessungen bilden eine Stichprobe von zwei Läufen. Der im
Messverfahren vorgesehene Vergleich mit 40 Wiederholungen steht aus.

Die kumulativen `gfxinfo`-Stichproben für den App-Prozess ergaben zunächst
6.147 Frames mit 119 als ruckelnd klassifizierten Frames (p50 5 ms, p90 7 ms),
später 6.541 mit 132 (p95 12 ms). Das sind aggregierte Framewerte mehrerer
Bedienwege. `dumpsys meminfo` zeigte für denselben Prozess 95.594 und später
272.100 KiB Gesamt-PSS. Während der zweiten Messung zeigten zwei CPU-Stichproben
126 % und 119 %. Aus diesen Einzelwerten lässt sich die Dauer einzelner
Verarbeitungsschritte oder ein dauerhafter Speicherzuwachs nicht ableiten.

### Ursache und Umfang der Korrektur

`RevierEditor` setzt `busy` vor dem lokalen Aufbereiten und Zwischenspeichern
des Zubehörfotos, der Entwurfssicherung und dem eigentlichen Speichervorgang.
Die Sperre greift vor diesen Schritten. In Build 131 bleibt die Schaltfläche dabei
weiter mit „Speichern“ beschriftet, und die globale Meldung aus dem vorherigen
Vorgang wird erst bei der neuen Rückmeldung ersetzt. Bei der Fotoauswahl sperrt
`RevierAccessoryFields` über `edit.mediaBusy` bis zur Rückgabe und lokalen
Übernahme. Die Ursache der irreführenden Anzeige liegt damit in der Verknüpfung
von Sperrzustand, Aktionsbeschriftung und Meldung. Die gemessene Gesamtdauer
wurde nicht auf einzelne Speicher- oder Verarbeitungsschritte aufgeteilt.
[`ComposeRevierScreens.kt`](../../android/app/src/main/java/at/hausgarten/service/ComposeRevierScreens.kt)

Der Kandidat 1.61.1 (132) zeigt während der Fotoübernahme „Foto wird geladen …“
und während eines laufenden Speichervorgangs im Geräte- und Zubehörformular
„Bitte warten …“. Er leert dort beim Beginn des Vorgangs die vorige Meldung.
Die Änderung betrifft die Rückmeldung. Der bestehende Speichervorgang, seine
Sperren und die Erfolgsbestätigung nach wirksamem Schreiben werden weiter
verwendet. Der erneute physische Vergleich mit dem Kandidaten setzt eine
gesondert freigegebene Installation voraus und ist derzeit **BLOCKIERT**.

### Weitere Bedienfälle auf Build 131

| ID | Bereich/Aktion | Voraussetzung und Bedienung | Erwartet | Tatsächlich und wirksam zurückgelesen | Status |
| --- | --- | --- | --- | --- | --- |
| QA12 | Geräte und Testdatenbereinigung | Eigenes Gerät nach QA07 wieder öffnen, bis „Löschen“ scrollen, löschen, Namen im Suchfeld der Geräteliste eingeben | Ziel verschwindet aus dem wirksamen Bestand | Suche zeigte nach Löschung 0 Treffer. Beide synthetischen JPEGs im Download-Ordner wurden nach erneutem Hashabgleich einzeln entfernt und ihr Fehlen geprüft. | BESTANDEN |
| QA13 | App-interne temporäre Dateien | Freigabebuild nach QA12, Lesbarkeit des privaten App-Verzeichnisses per ADB prüfen | Einzelne temporäre Dateien vor und nach dem Test vergleichen können | `run-as` meldete, dass das installierte Paket keine Debug-Freigabe hat. Die Speicheransicht liefert Aggregate. | BLOCKIERT |
| R00 | Hauptnavigation | Geräteliste mit Testsuchtext offen. Auf Heute, Arbeit und Kunden wechseln, Büro erneut antippen. | Büro kehrt zur bisherigen Geräteliste zurück | Geräteliste mit Suche und Anlageaktion war beim erneuten Antippen des Büro-Tabs wieder offen. | BESTANDEN |
| R01 | Heute | Aus einem anderen Bereich den Heute-Tab antippen | Übersicht mit Startaktion sichtbar | Heute zeigte „Arbeit starten“ und „Heute kein weiterer Einsatz geplant“. Der spätere unabhängige MCP-Abruf meldete Timerzustand `IDLE`. | BESTANDEN |
| R02 | Heute-Hinweise | Heute öffnen, „Weitere Tageshinweise“ antippen | Abschnitt lässt sich einblenden | Der Abschnitt zeigte anschließend einen Planungshinweis. | BESTANDEN |
| R03 | Arbeit | Arbeit-Tab antippen | Arbeitsaufzeichnungen erreichbar | Arbeitsaufzeichnungen mit Suche, Filter und „Arbeit nachtragen“ geöffnet. | BESTANDEN |
| R04 | Kunden | Kunden-Tab antippen | Kundenliste erreichbar | Kundenliste mit Suche, Filter und „Kunde anlegen“ geöffnet. | BESTANDEN |
| R05 | Abrechnung | Büro öffnen, Abrechnung antippen | Rechnung, Labor und Gesamt erkennbar | Drei Betragsbereiche, Filter und Abschlüsse sichtbar. Summenberechnung und Filterverhalten stehen separat aus. | BESTANDEN |
| R06 | Angebote | Büro, Angebote antippen | Angebotsliste erreichbar | „Angebot anlegen“, Suche und Filter sichtbar. | BESTANDEN |
| R07 | Planung | Büro, Planung antippen | Tages- und Wochenansicht erreichbar | Reiter „Tag“ und „Woche“ sichtbar. | BESTANDEN |
| R08 | Offene Stunden | Büro, „Offene Stunden“ antippen | Ansicht erreichbar | Ansichtstitel „Offene Stunden“ nach der Navigation gelesen. | BESTANDEN |
| R09 | Aufträge | Büro, „Aufträge und Anfragen“ öffnen, Aufträge antippen | Auftragsliste erreichbar | „Auftrag anlegen“, Suche und Filter sichtbar. | BESTANDEN |
| R10 | Anfragen | Geöffnete Bürogruppe, Anfragen antippen | Anfragenliste erreichbar | „Anfrage anlegen“, Suche und Filter sichtbar. | BESTANDEN |
| R11 | Objekte | Geöffnete Bürogruppe, Objekte antippen | Objektliste erreichbar | „Objekt anlegen“, Suche und Filter sichtbar. | BESTANDEN |
| R12 | Pflegepläne | Geöffnete Bürogruppe, Pflegepläne antippen | Pflegeplanliste erreichbar | „Pflegeplan anlegen“, Suche und Filter sichtbar. | BESTANDEN |
| R13 | Material | Büro, „Material und Pflanzen“ öffnen, Material antippen | Materialliste erreichbar | „Material anlegen“, Suche und Filter sichtbar. | BESTANDEN |
| R14 | Pflanzen | Geöffnete Bürogruppe, Pflanzen antippen | Pflanzenansicht erreichbar | Der Zielbereich war geöffnet und zeigte „Filter“. | BESTANDEN |
| R15 | Fotos | Geöffnete Bürogruppe, Fotos antippen | Fotoliste erreichbar | Zielbereich „Fotos“ mit Filter geöffnet. | BESTANDEN |
| R16 | Leistungskatalog | Geöffnete Bürogruppe, Leistungskatalog antippen | Leistungsliste erreichbar | Zielbereich zeigte „Baustein anlegen“. | BESTANDEN |
| R17 | Lokale Suche | Büro, „Weitere Verwaltung“ öffnen, Lokale Suche antippen | Suche und Filter erreichbar | Ansicht „Lokale Suche“ mit Filter und „Lokal suchen“ geöffnet. | BESTANDEN |
| R18 | Revier-Archiv | Geöffnete Bürogruppe, Revier-Archiv antippen | Wirksamer Archivbestand wird angezeigt | Ansicht meldete „Keine archivierten Einträge dieser Art“. | BESTANDEN |
| R19 | Papierkorb | Geöffnete Bürogruppe, Papierkorb antippen | Wirksamer Papierkorb wird angezeigt | „Der Papierkorb ist leer“ wurde angezeigt. | BESTANDEN |
| R20 | Einstellungen | Büro, bis „Einstellungen und Daten“ scrollen und antippen | Einstellungswege sichtbar | Verbindungen, Sicherung und Wiederherstellung sowie Speicher sichtbar. | BESTANDEN |
| S01 | Speicheranzeige | Einstellungen, Speicher, „Belegung ansehen“ antippen | Übersicht und Kategorien erscheinen | Angezeigter Gesamtspeicher und Detailkategorien für Datenbank und Medien gelesen. | BESTANDEN |
| S02 | Lokale Sicherung, `backup.voll.get_full_backup_status`, `platform.vollbackup_saf` | In der Sicherungsansicht „Jetzt sichern“ antippen und im Systempicker eine neue ZIP im lokalen Download-Ordner benennen und speichern | Datei wird lokal geschrieben und vollständig zurückgelesen | Die App bestätigte das vollständige Rücklesen mit Hinweisen zum Ausgangsbestand. ADB las auf dem Pixel Dateigröße 117.786.589 Byte und ZIP-Kennung. Erneutes Öffnen zeigte „Letzte erfolgreiche Sicherung“. | BESTANDEN |
| S03 | Wiederherstellungsdatei abwählen | Sicherungsansicht, „Sicherung wiederherstellen“ antippen, im Picker Android-Zurück auslösen | Rückkehr zur Sicherung ohne Dateiübernahme | Dateiauswahl öffnete sich und kehrte zur Sicherungsansicht zurück. | BESTANDEN |
| S04 | Wiederherstellungsvorschau, Teil von `platform.vollrestore_bestaetigen` | Neue lokale ZIP im Picker wählen, „Datei prüfen“ antippen, Vorschau lesen, „Abbrechen“ antippen | Vorschau ohne Bestandswechsel, anschließend Sicherungsansicht | „Vorschau bereit. Der aktuelle Bestand ist unverändert“ mit Angaben zum Dateibestand. Abbrechen führte zur Sicherungsansicht zurück. | BESTANDEN |

Die neue Datei `Gartenpirat-Pixel-Pruefung-20261106.zip` bleibt auf dem Pixel
unter Downloads. Sie enthält den lokalen Ausgangsbestand. Die App meldete
Einschränkungen zum Ausgangsbestand. Die Archivdatei und ihre Geschäftsdaten
wurden ausschließlich lokal verarbeitet.

### Leistungsursache weiter eingrenzen

Der 6,22-MB-Ablauf läuft in der App lokal. `stageEquipmentPhoto` liest und hasht
die Datei und übergibt sie in rund 190 Abschnitten zu maximal 32 KiB an den
Dateiupload. Für jeden Abschnitt liest `PhotoApplicationService` den bisherigen
Präfix und schreibt die verlängerte Fassung über die lokale Ablage. Die
wiederholte Arbeit erklärt den plausiblen Größenbezug. Die gemessenen
Speicherzeiten sind Ende-zu-Ende-Werte. Der Anteil der einzelnen IO- und
Speicherschritte und die Verbesserung einer anderen Ablage bleiben offen.
[`NativeAppController.kt`](../../android/app/src/main/java/at/hausgarten/service/NativeAppController.kt),
[`PhotoApplicationService.kt`](../../android/app/src/main/java/at/hausgarten/service/PhotoApplicationService.kt)

### Synthetischer Kunde und Zeiterfassung

| ID | Bereich/Aktion | Voraussetzung und Bedienung | Erwartet | Tatsächlich und wirksam zurückgelesen | Status |
| --- | --- | --- | --- | --- | --- |
| C01 | Kunden, `kunden.3` Pflichtfeld | Neues Kundenformular, Anzeigename leer, „Kunde speichern“ an der angezeigten Position antippen | Formular bleibt offen, keine Erfolgsmeldung | Das Formular blieb mit „Anzeigename *“ geöffnet, ohne Erfolgsmeldung. | BESTANDEN |
| C02 | Kunden, `kunden.1`, `kunden.2`, `kunden.3` | Im selben Formular synthetischen Namen eingeben, Tastatur schließen, speichern, in der Liste suchen und den Treffer öffnen | Genau ein gespeicherter Kunde und lesbare Akte | „Kunde gespeichert.“, danach genau ein Suchtreffer. In der geöffneten Akte erschien der Testname mit Standard „Rechnungsempfänger: Kunde selbst“. | BESTANDEN |
| T01 | Zeiterfassung, Kundenteil von `zeiterfassung.41` | Timerzustand vorher `IDLE`, Testkunde geöffnet, „Arbeit starten“ antippen | Laufende Sitzung mit diesem Kunden | „Arbeit läuft“ und synthetischer Kunde in der Timeransicht. | BESTANDEN |
| T02 | Zeiterfassung, `zeiterfassung.40`, `zeiterfassung.42` | Eigene laufende Sitzung, „Pause“ antippen | Dieselbe Sitzung pausiert | „Arbeit pausiert“ mit demselben Kunden. Unabhängige Abfrage meldete `PAUSED`. | BESTANDEN |
| T03 | Zeiterfassung, `zeiterfassung.43` | Eigene pausierte Sitzung, „Fortsetzen“ antippen | Sitzung läuft weiter | „Arbeit läuft“ und der Kunde waren wieder sichtbar. Der anschließende Statusabgleich bestätigte dieselbe Sitzungs-ID. | BESTANDEN |
| T04 | Zeiterfassung, `zeiterfassung.44` | Eigene laufende Sitzung, „Zeit stoppen“ antippen | Abschlussoberfläche ohne Arbeitsnachweis | „Arbeit abschließen“ erschien. Unabhängig zurückgelesen war dieselbe Sitzung `STOPPED_PENDING_REVIEW` ohne Arbeitsnachweis-ID. | BESTANDEN |
| T05 | Zeiterfassung, `zeiterfassung.48` | Eigene gestoppte Sitzung, „Weitere Angaben (optional)“ öffnen, „Entwurf verwerfen“ antippen | Keine aktive Sitzung oder Arbeitsaufzeichnung | Startformular mit Rückholhinweis für die verworfene Sitzung. Unabhängige Abfrage meldete `IDLE`. Die App behält den Rückholhinweis gemäß ihrer Bedienlogik. | BESTANDEN |
| C03 | Kunden, Löschung des eigenen Testkunden | Nach T05 Testkunden erneut öffnen, „Weitere Angaben“ und „Löschen“ antippen | Der Datensatz ist dauerhaft aus dem Bestand entfernt | Unabhängige Suche mit inaktiven Kunden lieferte 0 Treffer. Die Kundenliste zeigte nach Android-Zurück ebenfalls 0 Treffer. | BESTANDEN |
| C04 | Kunden, Oberfläche nach Löschung | Derselbe ausgeführte Löschvorgang wie C03 | Automatischer Rücksprung in die Kundenliste mit verständlicher Rückmeldung | Die App blieb bei „Eintrag nicht mehr vorhanden“. Ein tatsächliches Wischen vom linken Bildschirmrand führte zur Kundenliste mit 0 Treffern. | FEHLGESCHLAGEN |
| G01 | Hintergrund und Rückkehr, Zusatzfall | Kundenliste mit 0 Treffern offen, Home auslösen, Android-Übersicht öffnen und Karte „Haus & Garten“ antippen | Vorheriger App-Bereich und Suchtext sind erhalten | Kundenliste, synthetischer Suchtext und 0 Treffer waren wieder sichtbar. | BESTANDEN |

Beim Kundenlöschen beobachtet die Akte den Datensatz über `watchDetail`.
Sobald das Löschen im Speicher sichtbar wird, fällt der Datensatz aus der
Ansicht. Dadurch kann Compose den Löschbutton und seinen lokalen
Coroutine-Scope entfernen, bevor `deleteDirect` zur Rückkehr in die Liste
kommt. Im Kandidaten nutzt der Löschbutton den bestehenden Scope der
Detailansicht, der die Aktualisierung der Akte übersteht. Ein vorhandener
isolierter Instrumentierungstest wartet nach dem Löschen jetzt auf den
automatischen Rücksprung. Er löst keinen eigenen Rücksprung mehr aus.
[`NativePrimaryScreens.kt`](../../android/app/src/main/java/at/hausgarten/service/NativePrimaryScreens.kt),
[`NativeForms.kt`](../../android/app/src/main/java/at/hausgarten/service/NativeForms.kt),
[`NativeComposeAppTests.kt`](../../android/app/src/androidTest/java/at/hausgarten/service/NativeComposeAppTests.kt)

### Abrechnung und Verbindungen

| ID | Bereich/Aktion | Voraussetzung und Bedienung | Erwartet | Tatsächlich und wirksam zurückgelesen | Status |
| --- | --- | --- | --- | --- | --- |
| R21 | Verbindungen | Einstellungen geöffnet, „Verbindungen“ antippen | Verbindungsmenü öffnet | Zielansicht und Wetteroption sichtbar. Keine Zugangsdaten oder Einstellungen bearbeitet. | BESTANDEN |
| A01 | Abrechnungsweg, Teil von `abrechnung.overview` | Abrechnung öffnen, Filter „Rechnung“ auswählen und anwenden, Karte „Labor clandestino“ antippen, Filterzustand erneut öffnen und zurücksetzen | Die Auswahl wirkt und lässt sich auf beide Wege zurückstellen | Trefferzahlen wechselten von 3 über 2 auf 5. Die Filteransicht zeigte nach Antippen der Karte „Labor clandestino“. Die drei Überschriften blieben erreichbar. Die Betragsrechnung wurde hier nicht unabhängig nachgerechnet. | BESTANDEN |
| A02 | Abschlüsse, `abrechnung.closures` | Abrechnung nach Filterrücksetzung, „Abschlüsse“ antippen | Abschlussliste ist lesbar | Die geöffnete Abschlussliste meldete „Keine Abschlüsse für diese Suche“. | BESTANDEN |
| R22 | Planung, Teil von `termine.122` | Planung geöffnet, „Woche“ und danach „Tag“ antippen | Ansicht wechselt und lädt den entsprechenden Zeitraum | „Termine dieser Woche“ wurde in der Wochenansicht angezeigt. Die Tagesansicht meldete keine Termine in ihrem Zeitraum. | BESTANDEN |
| R23 | Lokale Suche, `revier.search_local` | Gelöschten Testkundennamen eingeben, Filter „Kunden“ auswählen, „Lokal suchen“ antippen | Gefilterte Suche liefert einen wirksamen Leerzustand | Die Oberfläche zeigte die Kategorie „Kunden“ und 0 Treffer im gesamten lokalen Bestand. | BESTANDEN |

### Blockierte Bedienfälle

| ID | Bereich/Aktion | Voraussetzung und Bedienung | Erwartet | Tatsächlich und wirksam zurückgelesen | Status |
| --- | --- | --- | --- | --- | --- |
| B01 | App-Installation und Android-Gerätesicherung, `platform.154` | Gesonderte Freigabe für einen produktiven Update- oder Android-Gerätesicherungslauf | Vorgang am Pixel ausführen und Stand erneut lesen | Gesonderter Auftrag fehlt. Installierter Build 131 erneut per ADB gelesen. | BLOCKIERT |
| B02 | Vollständige Wiederherstellung, `platform.vollrestore_bestaetigen` | Eigene lokale Vorschau aus S04 und Freigabe zum Bestandsersatz | Bestand ersetzen und wiederhergestellte Daten prüfen | Vorschau abgebrochen. Freigabe für Bestandsersatz fehlt. | BLOCKIERT |
| B03 | Echte Rechnungsausstellung, Teil von `abrechnung.complete` | Ausstellungsfreigabe und belegter zulässiger Vorgang | Abschluss mit eigener Nummer und Original | Ausstellungsfreigabe fehlt. A02 prüft nur die Liste. | BLOCKIERT |
| B04 | Externe Übergabe, Teil von `platform.153` | Freigegebenes Ziel für Versand oder Kontakt | Übergabe und Wirkung bestätigen | Externe Versendung und Kontakthandlung sind nicht freigegeben. | BLOCKIERT |
| B05 | Produktive Datenmigration | Gesonderte Migrationsfreigabe | Zielbestand anhand der Ausgangsdaten prüfen | Die Freigabe fehlt. | BLOCKIERT |
| B06 | Pixel-Nachtest QA06 | Freigegebene produktive Installation des korrigierten Builds | Hinweise während Fotoübernahme und Speicherung prüfen, Zubehör erneut öffnen | Der Pixel meldete weiter Build 131. Die Freigabe für den Kandidaten fehlt. | BLOCKIERT |
| B07 | Pixel-Nachtest C04 | Freigegebene produktive Installation des korrigierten Builds | Nach wirksamer Löschung automatisch in die Kundenliste zurückkehren | Der Pixel meldete weiter Build 131. Die Freigabe für den Kandidaten fehlt. | BLOCKIERT |

### Abdeckung und offene Fälle

Die obigen Gerätefälle ergeben 49 BESTANDEN, 2 FEHLGESCHLAGEN und 9 BLOCKIERT.
Die Zählung der registrierten Aktionen ist davon getrennt. Ein Gebietseinstieg
oder ein bestandener Teilpfad zählt nicht als vollständig geprüfte Aktion.

- **BESTANDEN (14)**
  `kunden.1`, `kunden.2`, `kunden.3`, `zeiterfassung.40`,
  `zeiterfassung.42`, `zeiterfassung.43`, `zeiterfassung.44`, `zeiterfassung.48`,
  `revier.list_equipment`, `revier.list_revier_archive`, `revier.search_local`,
  `backup.voll.get_full_backup_status`, `platform.vollbackup_saf`,
  `abrechnung.closures`.
- **FEHLGESCHLAGEN (2)**
  `revier.save_equipment` im Teilpfad Zubehörfoto und `kunden.12` im
  produktiv angebotenen Löschpfad. Beide Schreibvorgänge wirkten im Speicher.
- **BLOCKIERT (5)**
  `platform.151` für Kameraauslösung und Übernahme, `platform.153` für externe
  Übergabe, `platform.154`, `platform.vollrestore_bestaetigen` für die Aktivierung
  und `abrechnung.complete` für echte Rechnungsausstellung.
- **NICHT AUSGEFÜHRT (157)**
  Alle übrigen aktiven Katalog-IDs. Die obigen Teilbelege bleiben für diese
  Aktionen auf die tatsächlich ausgeführten Schritte beschränkt.
- **NICHT ANWENDBAR (6)**
  Die im Katalog als stillgelegt geführten IDs bleiben außerhalb der aktiven
  Abnahme.

Insbesondere stehen die meisten fachlichen Schreib- und Zuordnungsabläufe,
die Abrechnung mit Markierungen und unabhängig geprüften Beträgen, bestehende
Originale, Zahlungen, Korrekturen, Kalender und Wetterlaufzeit, Export, sichere
Versionskonflikte, langer Druck, horizontale Gesten, die übrigen Eingabetypen
und der Prozessneustart nach einem Schreibvorgang aus. Auch die Sperre aller
einzelnen Foto- und Zurück-Tasten während jeder Zwischenphase wurde nicht
separat bedient. [Aktionsinventar](../../android/app/src/main/assets/ui-mcp-actions.json)

### Quell-, Build- und Regressionsnachweise für 132

Der Quellprüfer und der Abgleich aller 184 unveränderten Katalogeinträge
bestanden. Die beiden Prüftools verwenden kanonische Pfade, damit der
Windows-Laptop bei bytegleichen Quelldateien denselben Fingerabdruck wie die
Release-Umgebung bildet. Der LF-Checkout bestätigte den installierten
Fingerabdruck vor der App-Änderung.

Für den Kandidaten mit Quell-SHA-256
`8a8f3dcbbc5e1de78ddeeb17a595c6dfb2fa087269980835bd76fb62b7bbf4d7`
bestanden der lokale unsignierte Release-Build, der Android-Test-APK-Build,
Release-Vital-Lint und die verpflichtende Datenschutzprüfung der Release-APK.
Die Datenschutzprüfung bestätigte null operative Startdatensätze.

Elf JVM-/SQLite-Suiten bestanden, darunter die Zubehör-, Foto-, Arbeits-,
SQLite- und Abrechnungsregressionen. `PixelPlainBackupTests` war durch die
fehlende Windows-Berechtigung für symbolische Links blockiert.
`check_current.py` erreichte die Signaturtests. Der vollständige Lauf endete
an deren POSIX- und Symlink-Voraussetzungen. Die Sicherheitsprüfungen
blieben aktiv.

Der erweiterte Compose-Test ist kompiliert. Sein Laufzeitnachtest im Emulator
steht aus. Die beiden Kandidaten-Nachtests auf dem physischen Pixel sind als
B06 und B07 blockiert. Die oben belegten Geräteergebnisse gelten für Build 131.
