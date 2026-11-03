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
