# Leistungsnachweise für die native App

Diese Datei beschreibt Messverfahren und Aussagegrenzen für die Kotlin-/Compose-App.
Auftrag, Quellstand und tatsächliche Prüfergebnisse stehen ausschließlich in
`../../AKTUELLER-STAND.json`. Ausschließlich öffentliche Repositoryquellen und
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
