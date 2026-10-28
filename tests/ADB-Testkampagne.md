# ADB-Testkampagne Haus & Garten – Vorbereitung vom 28.10.2026

## Fortschreibung: tatsächliche passive ADB-Prüfung

Direkte Nutzeranweisung „Test per adb auszuführen“ erlaubt jetzt die enge
Laptoprolle als ADB-Brücke. Der Nutzer bestätigt Produktivdaten auf dem Pixel.
Darum sind nur die folgenden rein lesenden Protokolle ausgeführt worden.
Der nachstehende Vorbereitungsstand bleibt historische Planung; seine Angabe
„keine ADB-Befehle“ beschreibt den früheren Schritt.

[Gerät und tatsächliche Messaggregate](../docs/verification/adb-device-readonly-2026-10-28.json)
und [Mess-CSV](../docs/verification/adb-device-readonly-2026-10-28-metrics.csv).
Pixel10Pro/API37, Original108 durch vollständigen on-device APK-Hash bestätigt.
3 Metadatenprüfungen PASS, Isolation BLOCKED. Alle64 synthetischen Fachfälle
weiterhin offen; keine Appstarts, Schreibtests oder Restores.

Ausführbare PowerShell-Protokolle, beide mit ausdrücklich ausgewählter Seriennummer;
Seriennummer über flüchtige Umgebungsvariable setzen, nicht in Git speichern:

```powershell
pwsh -NoProfile -File tests/adb_readonly_preflight.ps1 -AdbPath "$env:LOCALAPPDATA\HausGarten-Adb-Testtools\platform-tools\adb.exe" -Serial "$env:HG_ADB_SERIAL"
pwsh -NoProfile -File tests/adb_readonly_performance.ps1 -AdbPath "$env:LOCALAPPDATA\HausGarten-Adb-Testtools\platform-tools\adb.exe" -Serial "$env:HG_ADB_SERIAL"
```

Zuerst Preflight ausführen; er legt nur das technische Ergebnisverzeichnis unter
LOCALAPPDATA an. Danach Performanceprotokoll für vorhandene Statistiken.
Mehrere Geräte sind nur über die ausdrücklich übergebene Seriennummer zulässig.
Keine Diagnose-APK oder Instrumentierung installieren. Kein Logcat-/Screenshot-
oder Geschäftsdatenexport. Nicht als Funktionstest oder neue Lastmessung ausgeben.
gfxinfo ist kumulativ; PSS hat nur3 passive Stichproben; historische Exitgründe
sind keinem aktuellen Build zugeordnet. Keine Rohdumps werden als Dateien erzeugt.

## Historischer Vorbereitungsstand

**Stand: PREPARATION_ONLY, keine Geräteabnahme.** Paket `at.hausgarten.service`.
Es wurden keine ADB-Befehle, Builds, Installationen, Appvorgänge oder Nutzertests
ausgeführt. Diese Datei dokumentiert den Einstieg für eine später zulässige
isolierte Umgebung; sie erteilt keine neue Freigabe.

## Geltende Quellen und konkrete Sperre

Zuerst wurden die vier zentralen Dateien vom frischen main gelesen.
main `05eac2d5769bed55a42317ed542be9baccda0e77` und der neueste Appbranch
`codex/heute-uebersicht-20261027` auf
`771183ddb9fbae81e68dd7474ea5277f0c4ac48c` enthalten in AGENTS.md:
„Der Laptop ist weder Arbeitsumgebung noch Fallback. Keine zweite lokale Entwicklung.“

Der separate Revierbranch `371384fe5aa49d055cb558c645f82226761fecc8`
enthält eine engere ADB-/Protokollausnahme, aber weder main noch Appbranch haben
diese integriert. Das ist ein konkreter Regelkonflikt. Die Ausnahme wird hier
nicht durch Wahl eines weniger restriktiven Branches übernommen. Der Nutzerauftrag
verlangt in diesem Fall ausschließlich zulässige Vorbereitung. Keine lokale
Quellkopie erstellt. Die anfängliche Statusprüfung fand im aktuellen
Arbeitsverzeichnis keinen Git-Checkout; das ist keine vollständige Suche nach
anderen lokalen Checkouts oder fremden lokalen Änderungen.

Der anfangs gelesene Appbranch liegt 20 Commits vor main; Revier 7 Commits vor main. Pflanzenbranch
`7d499b225ed28f501333227e0ce92a54d4c5d542` hat 7 eigene Commits und liegt 171
hinter main. Alle fremden Branches bleiben erhalten. Nur eigene Dokumente werden
auf dem bereits bestehenden aktuellen Appbranch ergänzt; keine Integration.
Vor der Sicherung rückgelesene parallele Dokumentcommits bis
`46c68899dbe00b97582b0040f4025fbf43cf84dc` vollständig erhalten. Sie ändern
weder Appcode noch Regeln; sie werden als Commiteltern übernommen.

Appquelle108: `source/v1.55.3-build108`,
`9415c8da6c26314969b731bc01708c5e8f424349`,
Quellfingerprint `84e1d90175fe3a5863aaba14735f8ea25193fe5cf43005c99bc2e6e746315da5`.
Der Vergleich zum betrachteten Appbranch enthält nur Verwaltungs-/Berichtsdateien.
Das beweist keine installierte Version. Historischer Metadatenreadback vom27.10.
meldete106; heute wurde weder dieser Stand noch ein Pixelmodell bestätigt.

## Matrix und maschinenlesbare Ergebnisse

- [Vollständige Szenarien, Quellen, Katalogzuordnungen, Profile und Befunde](../docs/verification/adb-campaign-2026-10-28.json)
- [64 Szenarien als CSV](../docs/verification/adb-campaign-2026-10-28-matrix.csv)
- [50 vorbereitete Messzeilen als CSV](../docs/verification/adb-campaign-2026-10-28-metrics.csv)

64 Szenarien: 39 BLOCKED, 25 NOT TESTED, 0 PASS, 0 FAIL. Die 169 Werkzeugnamen und
212 semantischen UI-Aktionsgruppen sind vollständig inventarisiert und geplanten
Szenarien zugeordnet. Diese Zuordnung ist keine vollständige Automatisierung
aller Argumentvarianten oder ein Test sämtlicher sichtbarer Buttons.
Die UI wurde aus den tatsächlichen Quellen abgeleitet, heute aber nicht angesehen.
Eine teilweise vorhandene Suite beweist nicht die Ausführung des ganzen Szenarios.

Frisch gezählt: main 166 Werkzeuge (72read/94write),209 UI-Gruppen
(194business/15platform); Appbranch 169 (73/96), 212 (197/15).
Die im aktuellen Gespräch exponierten Verbindungsmetadaten enthalten 166 Namen,
identisch mit main. Gegenüber App108 fehlen die drei Wartungswerkzeuge:
`get_equipment_maintenance`, `save_equipment_maintenance_plan`,
`record_equipment_maintenance`. Keine RPC-Abfrage oder Gerätefunktion ausgeführt.

## Fortsetzung in der erlaubten Cloudumgebung

1. Zentrale Regeln erneut in Reihenfolge vom frischen main und passenden
   Arbeitsbranch lesen; geltende Umgebungsfreigabe eindeutig nachweisen.
2. Bestehendes Cloud-AVD bevorzugen. Vor jeder Mutation zusätzlich zu Seriennummer,
   QEMU und AVD-Namen austauschbare Userdata, synthetischen Seedmarker, isolierte
   DB/Files/Preferences, fehlende Produktivkonten und kontrollierte Dienstadapter
   nachweisen. AVD-Name und ein Bestätigungsflag allein genügen nicht.
3. Androidpaket-/Aktivitätsdienst und Paketpfad mehrfach über einen festen
   Beobachtungszeitraum prüfen, einschließlich wiederholter Kaltstarts.
   Bootflag1 ist kein Stabilitätsnachweis. Bei Systemfehlern abbrechen und
   Umweltfehler von Appfehlern trennen.
4. Installierte Version/Paket, APK-/Zertifikat-/Quellfingerprint soweit
   technisch zugänglich gegen den genauen Release prüfen. Keine Annahme aus
   Appselbstauskunft. Originalsignierung und source_preservation bleiben nötig,
   falls ein gesondert beauftragter neuer Build erforderlich wird.
5. Vorhandene native Suites ausführen, dann fehlende Profile/Messadapter ergänzen.
   Dieser Vorbereitungsstand installiert oder baut keine APK und liefert keine
   neu implementierte Benchmarkpipeline.

Vorhandener ausführbarer Einstieg, **hier nicht ausgeführt**, Linux/Cloud
(`fcntl`), auf bereits isoliertem AVD mit schon vorbereiteter Instrumentierung:

```sh
python3 tests/run_cloud_emulator_tests.py \
  --serial "$HG_QA_SERIAL" \
  --adb "$ANDROID_HOME/platform-tools/adb" \
  --mode revier_core
```

Weitere vorhandene Modi: `revier_ui`, `revier_authority`, `revier_finance`,
`revier_recovery`, `backup_setup`, `drive_backup_only`,
`process_recovery`, `fresh_isolated`, `accessibility`, `plants`.
Vorher ihre aktuellen Definitionen und Fixtures lesen. Suites nacheinander unter
dem vorhandenen Lock ausführen. Optionales `--apk`/`--instrumentation-apk`
installiert Pakete und darf erst nach vollständigem Isolationsnachweis verwendet
werden. Diese Optionen sind absichtlich nicht Teil des obigen Aufrufs.
`process_recovery` führt tatsächliches `am force-stop` in fünf QA-Paaren aus;
niemals auf das produktive Pixel übertragen.

**Bekannte Guardlücke:** App108/main prüft bislang QEMU, AVD und Bootflag;
`successful_output()` akzeptiert syntaktisch auch RESULT0. Revier hat bereits
strengere Kerndienst-/Paketpfadprüfungen und lehnt Nullsummary ab. Vor einer
belastbaren Abnahme diese vorhandene Änderung geordnet reviewen/integrieren
und die zugehörigen Schutzregressionen ausführen. Keine Schutzabkürzung.

Nicht als aktuellen Einstieg verwenden: `native_device_acceptance.py`
(physischer Pixel, Diagnoseinstallation, Produktivzählungen, Defaultbuild58,
Tunnelwiederherstellung), `android-emulator.test.js` (historisch41) oder
`run_emulator_font_tests.py` (Versionsgate98). Letzterer ist für108 nicht bereit.
Die native Accessibilityprobe verlangt360CSS-Pixel; kein TalkBacknachweis.

## Gerätemanifest und rein technische Erfassung

Erst nach aufgehobenem Umgebungskonflikt in der erlaubten Umgebung lesen.
`adb devices -l` nur flüchtig zur Auswahl; jede weitere Abfrage ausdrücklich
mit `adb -s "$HG_QA_SERIAL"`. Seriennummer nur für die Auswahl behalten,
im Bericht Laufalias verwenden. Kein kompletter getprop-/Netzwerk-/Paketdump
oder Logcat in Git. Beispielhafte eng gefilterte Abfragen:

```sh
adb devices -l
adb -s "$HG_QA_SERIAL" shell getprop ro.product.model
adb -s "$HG_QA_SERIAL" shell getprop ro.build.version.release
adb -s "$HG_QA_SERIAL" shell getprop ro.build.version.sdk
adb -s "$HG_QA_SERIAL" shell getprop ro.product.cpu.abilist
adb -s "$HG_QA_SERIAL" shell wm size
adb -s "$HG_QA_SERIAL" shell wm density
adb -s "$HG_QA_SERIAL" shell df -k /data
```

Zusätzlich selektiv erfassen: CPU-Modell/Kerne, MemTotal, Akku-Ladezustand/Prozent/
Temperatur, thermischen Zustand, tatsächliche Displayfrequenz und Auflösung,
WebView-Anbieter/-Version, Appversion/Build/Paket, installierte APK-/Signaturherkunft,
freie Bytes und Berechtigungsnamen samt granted-Status. Netzwerk nur als
Transport/verbunden/validiert erfassen, keine SSIDs, IPs, Konten oder Zugangsdaten.
Nicht zugängliche Werte bleiben null mit Begründung. Fingerprint meint
App-/APK-/Quell-/Zertifikatprovenienz; keine private Gerätekennung veröffentlichen.

## Synthetische Profile

Vorhandene Factory: `tests/synthetic_fixtures.py`, JSON
`tests/fixtures/synthetic-business.json`. Generator:
`tools/sync_synthetic_fixtures.py`; Java-/JS-Spiegel nicht unabhängig ändern.
Aktuell nur minimal, edge_cases und scale=3000Arbeiten bei1Kunden/1Auftrag.
Fotos/Termine im zentralen Seed leer. Die angeforderten Profile sind **Spezifikation,
nicht implementiert oder erzeugt**:

| Profil | Kunden | Arbeitseinträge | Objekte | Termine | Fotos |
| --- | ---: | ---: | ---: | ---: | ---: |
| small | 50 | 500 | 75 | 100 | 50 |
| medium | 500 | 5000 | 750 | 1000 | 500 |
| large | 2000 | 20000 | 3000 | 4000 | 2000 |

Seed20261028, feste Testuhr, eindeutige QA-IDs. JSON enthält weitere Verknüpfungen,
Materialbewegungen, Geräte, Kantenfälle, Archiv/Papierkorb und Bildvorgaben.
RAM/Platz/Quota/Reserve einschließlich DB, Originalen, Thumbcache, Backup und
Restore-Staging vor Generierung messen. Angepasste Profile neu benennen und
tatsächliche Größen berichten. Kein produktives Bild/Backup als Vorlage.

## Messvertrag

Release beziehungsweise vergleichbarer Benchmarkbuild aus exakt gebundener Quelle.
Macrobenchmark ist noch nicht eingerichtet; `android/settings.gradle` bindet
nur `:app` ein. Die Definitionen P01–P18 ersetzen keine Implementierung.

Je Profil zunächst5 getrennte Warmups; mindestens 30 gemessene Versuche und für
belastbarere Quantile30 erfolgreiche Samples anstreben; Abbruchbudget 60 Versuche
vorher festlegen. Alle Fehlversuche/Timeouts samt Dauer und Ursache erhalten.
Fehlerquote=Fehlversuche/alle gemessenen Versuche; bei0Versuchen null.
Quantile nur über ausdrücklich bezeichnete erfolgreiche monotone Zeitmessungen:
Median mittlerer Wert beziehungsweise Mittel der beiden mittleren; p90/p95
Nearest-Rank `sorted[ceil(p*n)-1]`; Maximum separat. Kleine n als vorläufig
kennzeichnen. Warmup, Trace-Diagnose und Baseline nicht zusammenrechnen.
Dreißig Samples allein garantieren kein stabiles p95.

Sichtbar und bedienbar getrennt definieren: erster Frame mit richtigen Daten
sowie erfolgreiche Hauptaktion. WebView `performance.now()`-Marker mit nativer
monotoner Zeit/Trace korrelieren; nicht nur RPC-/DOMContentLoaded-Zeit berichten.
Service-Suche und UI-Suche bleiben getrennte Metriken. Erfolgreicher Commit erst
mit korrektem Rücklesen. Für Scrollen tatsächliche Displaydeadlines/FrameTimeline
und FrameTimingMetric nutzen; keine pauschale16,7ms-Grenze bei anderer Frequenz.
Metrikdefinition, langsame Frames, verpasste Deadlines und Jank-Nenner festhalten.

Pro Lauf Gerätetyp/Build/Profil, WebView, Hz, Temperatur, Akku/Laden, thermischen
Status, Debugger/Profiler, Hintergrundlast und Cachezustand dokumentieren.
Keine Debugger an Vergleichsläufen. Perfetto-Ursachenläufe separat.
`dumpsys gfxinfo`, `meminfo`, `procstats` und Prozess-CPU gezielt verwenden;
ANR/Crash/Main-Thread-Blockaden, Wachstum nach wiederholten Abläufen und Ruhephase
korrelieren. Android-Profiler/simpleperf nur bei tatsächlich unterstützter Umgebung.
Langsame synthetische SQLpfade mit Dauer/Callcount und EXPLAIN QUERY PLAN prüfen;
Datei-I/O, Decoding und Mehrfachzugriffe über Trace zuordnen. Rohdaten außerhalb
des Quellbaums; nur bereinigte technische Aggregate nach docs/verification.
Große Traces nur über bestehenden genehmigten Artefaktweg, andernfalls offenlassen.

Bestehende Ziele bleiben unverändert: native Service-Suche300ms; historisch
dokumentierte20-Stopp-Service-Tour1000ms. Das sind keine sichtbaren UI-Ziele.
Die frühere105-Cloudmessung (API30/x86_64 ohneKVM) ergab Such-p95
772,582006ms bei25Samples nach3Warmups und Tour376,463853ms als Einzelwert.
**Historische Evidenz, heute nicht wiederholt; kein Pixelwert und kein neues FAIL.**
Keine neuen numerischen Zielwerte festgelegt, kein Vorher-/Nachhervergleich.

## Kurzer echter Nutzertest

Aufgabenkarte: „Finde QA Musterkunde → erfasse20Minuten QA Gartenpflege →
ordne das bereitgestellte synthetische Foto zu → plane einen Termin →
prüfe den Rechnungsentwurf ohne Freigabe oder Versand.“

Je Aufgabe protokollieren: anonymer Teilnehmercode, Erfolg ja/nein/teilweise,
Bearbeitungszeit, Fehlversuche, Hilfebedarf, Schwierigkeit1(sehr leicht)bis5(sehr
schwer), kurze freiwillige Beobachtung. Keine realen Kundendaten, keine
Teilnehmernamen oder Aufzeichnungen ohne eigenen Zweck. Teilnehmerzahl heute 0;
keine Nutzerzufriedenheit oder Ergebnisse berichtet. Automatische Accessibility-
und Ablaufprüfungen bleiben ergänzende technische Evidenz.

## Befunde und Abschlussgrenze

Priorisierte Vorschläge und Repro-/Risiko-/Regressionsangaben stehen im JSON:
D01Guard, D02Factory/Messpipeline, D03Suchkostenhypothese, D04Regelkonflikt,
D05Verbindungskatalog, D06Fontabdeckung. Keine Produktänderung umgesetzt.
Größter erwarteter Nutzen: verlässliche isolierte Abnahme/Guards; realistische
Lastprofile samt sichtbaren Messpunkten; danach gezielte Such-/Navigationsoptimierung
auf Grundlage aktueller Traces. Keine aktuelle Geräteursache oder Beschleunigung
behauptet. Produktive Transition-, Rechnungs-, Restore- und Versandgrenzen gelten.

Privacyprüfung dieses Schritts umfasst ausschließlich eigene Dokumentänderungen
und neue technische Evidenz; kein erneuter vollständiger Source-/Worktree-Gate
oder Androidtest. Exakte Remote-Commits und Inhalte nach Sicherung zurücklesen.
