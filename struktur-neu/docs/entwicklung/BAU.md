# Bauen und prüfen

Alle folgenden Befehle beginnen im Ordner `struktur-neu/`. Benötigt werden
Python3, JDK17, Gradle9.3.1 und Android-SDK37 mit Build-Tools37.0.0 gemäß den
vorhandenen Gradle-Dateien. Aktuelle Abhängigkeiten nicht vorsorglich ändern.
`JAVA_HOME`, `ANDROID_HOME` und gegebenenfalls `GRADLE_USER_HOME` auf die vorhandenen
Werkzeuge setzen; keine Maschinenpfade oder Geheimwerte in Git speichern.
In der verwalteten Cloud auch die vorgegebenen Proxy-/CA-Einstellungen für die
Gradle-JVM verwenden; Java übernimmt HTTP-Proxy-Umgebungsvariablen nicht selbst.
Vorhandene Abhängigkeiten können bei vollständigem Cache offline verwendet werden.

## Testumfang festlegen

Der aktuelle Auftrag und seine Freigaben stehen ausschließlich im Arbeitsstand.
Betroffene Quell-, JVM- und Browserprüfungen ausführen; vorhandene Ausgangsmessungen
für Vergleiche verwenden. Installation, Migration, Wiederherstellung, APK-Verteilung
und geschützte Altlastenlöschung nicht aus einer allgemeinen Korrektur ableiten.

## Vorbereitete native Tabellenprüfung über ADB

Der aktuelle Einstieg `PixelMcpInstrumentation` akzeptiert ausschließlich
`-e suite business_tables`. Er erstellt eine neue, zufällig benannte synthetische
SQLite-Datei im Cache, öffnet **keine produktive Datenbank** und entfernt nur sein
eigenes Testverzeichnis. Er prüft die Tabellen-Speichergrenze mit 300 Kunden,
40 Aufträgen, 5 000 Arbeiten, 500 Rechnungen und 3 000 Fotometadaten, einschließlich
eines großen Unicode-Datensatzes. Geprüft werden sollen Kopierabbruch und atomarer
Fortschritt, Schreibsperre, Abbruch/Fortsetzung, Neustart, Hashgleichheit, Konflikte,
Request-Wiederholung, Reihenfolgeänderung, SQLite-Sicherung/Rücklesen und Rückweg
nach nachträglichen Änderungen. Hinzu kommen Filter-/Seitengleichheit zwischen
Tabellen und bestehendem Selektor sowie eine getrennte synthetische Journaldatei:
endgültige statt temporärer Aktionskennungen, gemeinsamer Commit von Journal,
Tabellenzeile und Requestbeleg, erhaltene lokale Autorität, unveränderter
Ursprungssnapshot und Neustart mit derselben Projektionsrevision. Ein absichtlich
fehlschlagender Zeilenschreiber muss Journal, Zeile und Requestbeleg vollständig
zurückrollen.

Nach Abschluss der Implementierung und gesonderter Bereitstellung der zum
Quellstand passenden, mit der Originalsignatur signierten App/Test-APK:

```powershell
& $gpAdb -s $gpSerial shell am instrument -w -e suite business_tables at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation
```

`$gpAdb` und `$gpSerial` werden wie im ADB-Abschnitt unten lokal ausgewählt.
Der Rückgabeblock enthält Prüfanzahl, Gesamtdauer und den synthetischen Umfang.
Ein erfolgreicher Lauf dieses Einstiegspunkts belegt den SQLite-Speichervertrag.
Die Test-Evidenz darin ist ausdrücklich synthetisch; die echte verschlüsselte
Archivprüfung des Wartungspfads, vollständiger App-Restore, Fachschreiber und
Bildschirmabnahme müssen zusätzlich geprüft werden. Quellcode vorhanden bedeutet
nicht gebaut, installiert oder ausgeführt.

Beim gemeinsamen Prüflauf insbesondere UI-, MCP- und Timerabschluss mit mehrfachen
Änderungen derselben ID, Archivieren/Wiederherstellen, synthetische Löschkompaktierung
mit echtem Testarchiv, Rechnungserstellung und Dokumentjobs ausführen. Jede der
acht Listen-/Suchabfragen mit leerer Seite, Versatz, inaktiven Kunden, natürlichen
Namen, Telefonvarianten, Kunden-/Auftragsbezügen, Rechnungsempfänger und Datumsgrenzen
gegen denselben vollständigen Fachstand vergleichen. Innerhalb einer noch offenen
Schreibtransaktion muss der Leser den uncommitteten Journalstand berücksichtigen;
nach Commit müssen Zeilen und Projektionsrevision übereinstimmen. Fehlende
Journalverfolgung in älteren Zwischenformaten darf nicht als aktuelle Projektion
gelten. Diese Prüfmatrix ersetzt keinen Restore-Nachweis auf dem Pixel.

## Gemeinsame Quellprüfung nach dem Umbau

Bei Fehlern lokaler ZIP-Sicherungen zuerst den Fehlercode der Sicherungsseite und
`get_full_backup_status` vergleichen. `CAPTURING` allein ist kein Beleg für einen
noch laufenden Worker; frühere Appstände hielten einen Capture-Abbruch nicht im
Job fest. `BACKUP_CAPTURE_INVENTORY_IO_FAILED` grenzt den Abbruch auf die
Bestandsprüfung nach der Datenbankaufnahme ein, benennt aber weder eine bestimmte
Datei noch einen konkreten Finanz-/Medienfehler. Die Ziel-ZIP wird erst nach
erfolgreicher interner Aufnahme und Integritätsprüfung ausgegeben.

Für den anschließenden Prüflauf `PixelPlainBackupTests` einschließlich der
Fehlercode-Fälle ausführen: konkrete Medien-/Finanzcodes unverändert durchreichen,
unbekannte Fehler auf den jeweiligen Inventarschritt begrenzen und Freitext/Pfade
nicht ausgeben. Native Fehlerfälle zusätzlich prüfen: Capture-Abbruch führt zu
`FAILED` mit `last_error_code` und Zeitstempel; ein erfolgreicher Wiederholungsversuch
verwendet geprüfte vorhandene Zwischenstände und entfernt den letzten Fehlercode.
Export-/Rücklesefehler dürfen einen vorhandenen Providerbeleg weder löschen noch
zum erneuten Überschreiben eines bereits verifizierten Ziels berechtigen. Eine
bloße Quellkorrektur der Diagnose beweist keine Behebung des ursprünglichen
Bestandsfehlers. Keine Sicherung durch Überspringen der Fachprüfungen freigeben.

```sh
python3 -B tools/check_current.py
```

Der Befehl prüft aktuelle Quellen, generierte Verträge, Pflanzenressourcen,
Prüfdatenadapter und die ausgewählten Python-Prüfungen. Er erzeugt keine APK,
signiert nicht und greift nicht auf ein Gerät zu. Für Oberfläche, native Fachlogik
und Baukonfiguration zusätzlich die jeweils betroffenen Prüfungen ausführen.

Für Statusänderungen außerdem synthetisch prüfen: aktuelle Fehler vor älteren
Erfolgen, Datumsreihenfolge mit Zeitzonen, stabile Übersicht trotz Pagination,
fehlender/ungültiger Providerbeleg, zukünftige Zeiten, abgebrochener und aktiver
Worker, erhaltene ältere Belege nach Rücklesefehlern sowie unveränderter Quellbestand.
`PixelPlainBackupTests` deckt diese Regeln und spezifische Finanzkollisionen ab.
Persistierte Android-Worker-/Activity-Lebenszyklen bleiben ein eigener Gerätetest.

`simple-forms.test.cjs` prüft zusätzlich die Statusanzeige, sichere lokale
Kundenprofil-Thumbnails und explizite Profilrollen, alle Hauptseiten bei 200 %
Schrift sowie genau einen Vollabruf beim Kundenspeichern ohne Foto.
Der Fotopfad behält seinen eigenen notwendigen Rückleseschritt.

Ein veralteter ChatGPT-Connector-Katalog wird am bestehenden Connector aktualisiert.
Zuerst `get_capabilities.tools` mit den tatsächlich exponierten Werkzeugen vergleichen;
`run_self_test.ui_mcp_parity` prüft nur den App-internen Katalog. Nach Aktualisierung
Anzahl, Namen, Schemas und Beschreibungen erneut vergleichen. Pluginmanifest und
Appkennung nicht ändern, wenn nur der gespeicherte Werkzeugkatalog veraltet ist.

## App-Kompilierung

```sh
cd android
gradle --no-daemon --no-configuration-cache --no-build-cache :app:compileReleaseJavaWithJavac
```

Ein tatsächlich beauftragtes Installationspaket wird mit `:app:assembleRelease`
gebaut. Die eingebauten Vor-/Artefaktprüfungen bleiben erforderlich. Es gibt keine
automatische Installation oder Freigabe durch erfolgreiche Kompilierung.

## Aktuelle Java-Prüfungen

`tools/run-followup-java.py` führt nur die drei übernommenen aktuellen Suiten für
lokales ZIP-Backup, Gerätewartung und Gerätefotos aus. Benötigt werden der vorhandene
Android-SDK und die durch Gradle aufgelösten Tink-/Gson-Abhängigkeiten.
`HG_JSON_JAR` verweist auf `org.json:json:20240303`, SHA256
`3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed`.

```sh
python3 -B tools/run-followup-java.py
```

Für die Tabellenumstellung zusätzlich `--tables` verwenden. Dafür
`HG_SQLITE_JDBC_JAR` auf SQLite-JDBC3.46.1.0 und `HG_SLF4J_API_JAR` auf
SLF4J-API1.7.36 setzen; der Runner prüft die festgelegten SHA256-Prüfsummen.
Die vierte Suite verwendet echte SQLite-Transaktionen in einer frisch angelegten
synthetischen Datei. Sie prüft Übernahme, Kopierabbruch, Fortsetzung, Schreibsperre,
66 Filter-/Seitengleichheiten, Unicode-Großdaten, CAS/Wiederholung, Journalprojektion,
Fehler-Rollback und Rückweg mit aktuellen Änderungen. Sie prüft weder den
Android-Transaktionslistener noch CursorWindow, die vollständigen App-Schreiber
oder Backup/Restore auf dem Pixel; dafür bleibt der ADB-Einstieg erforderlich.

```sh
python3 -B tools/run-followup-java.py --tables
```

Die zwei Dateien in `tests/java-support/` liefern tatsächlich referenzierte
Speicher-Fixtures. Ihre früheren main-Testprogramme wurden entfernt.

## Aktuelle Oberflächenprüfungen

Die fünf übernommenen Suiten sind `simple-forms`, `startup-ui`,
`equipment-maintenance-ui`, `equipment-profile-photo-ui` und
`work-entry-photo-failure` unter `tests/*.test.cjs`. Playwright1.64.1 und Acorn8.19.0
außerhalb des Quellbestands bereitstellen; bei Bedarf die vorhandenen Modulpfade
über `HG_PLAYWRIGHT_MODULE` und `HG_ACORN_MODULE` auswählen. In Cloud-Chromium
`HG_TEST_LOCAL_ASSETS=1` setzen. Jede betroffene Suite mit `node` aufrufen.

`daily-usability.test.cjs` und `revier-solo-ui.test.cjs` liefern dabei benötigte
synthetische Browser-Bridges; daraus folgt keine Pflicht zu deren vollständigem Lauf.
Der Starttest prüft den aktuellen Startablauf und fehlende Pflichtmodule ohne
historischen Stylesheet-Vergleich oder frühere Diagnoseberichte.

## Grenzen der Bereinigung

Alte Tests und Diagnosekampagnen wurden nicht übernommen. Die komplette frühere
Android-Instrumentierung ist deshalb hier kein lauffähiger Abnahmeweg. Ihre
synthetischen Hilfsklassen bleiben nur erhalten, soweit die aktuellen Werkzeuge
sie brauchen. Vor einer späteren Umstellung der bisherigen Abläufe sind ein
aktueller nativer Testeinstieg und die betroffenen Pixel-Prüfungen gesondert nötig.
Quellprüfungen, Kompilierung und JVM-/Browsertests sind getrennte Nachweise.

## ADB-Analyse und Lese-/Schreibtests über den Laptop

Der konkrete aktuelle ADB-Auftrag muss Lesen, Schreiben und Testumfang abdecken.
Eine frühere Freigabe oder diese Anleitung ersetzt den aktuellen Auftrag nicht.
Der Windows-Laptop wird konkret als USB-/ADB-Verbindung zum Pixel benötigt;
Android Studio, lokale Builds und ein Emulator sind dafür nicht erforderlich.
Quellarbeit und Auswertung möglichst in der Cloud belassen. Eine Cloud-Sitzung
besitzt durch eine lokale USB-Verbindung nicht automatisch Zugriff auf ADB.
Den tatsächlichen Ausführungsort und die erreichbare Verbindung feststellen.

### Verbindung und eindeutig gebundener Test

Vorhandene Android Platform Tools und die bewusst bestätigte Gerätefreigabe
verwenden. `adb devices -l` lokal zur Auswahl lesen; keine Seriennummern in GitHub
übernehmen. Bei mehreren Geräten immer die konkrete Auswahl `-s` verwenden.
Keinen offenen ADB-Port ins Internet stellen. Keine Verbindung, `offline` oder
`unauthorized` bedeutet **NICHT AUSGEFÜHRT**, nicht PASS.

PowerShell, lokal auf dem verbundenen Laptop:

```powershell
$gpAdb = 'adb'
& $gpAdb devices -l
$gpSerial = Read-Host 'Seriennummer des ausdrücklich ausgewählten Testgeräts'
& $gpAdb -s $gpSerial get-state
& $gpAdb -s $gpSerial shell getprop ro.build.version.release
& $gpAdb -s $gpSerial shell getprop ro.build.version.sdk
& $gpAdb -s $gpSerial shell dumpsys package at.hausgarten.service |
  Select-String '^\s*versionCode=|^\s*versionName='
& $gpAdb -s $gpSerial shell wm size
& $gpAdb -s $gpSerial shell wm density
& $gpAdb -s $gpSerial shell settings get system font_scale
```

Vor einem schreibenden **App-Test** müssen exakte Quell-/APK-Version, Paketkennung,
Testfall und synthetischer Bestand feststehen. Kein Import von Produktivdaten in
Testprofile. Keine zweite operative App oder Datenbank einführen. Die bestehende
Installation nicht durch eine ungeprüfte Test-APK ersetzen. Die allgemeine
ADB-Testfreigabe setzt weder Installations- noch Rechnungs-/Migrationsgates außer
Kraft. Ein bloßes `test_record`-Flag beweist keine Isolation eines echten Bestands.
Die neue native Testanbindung muss einen eigenen temporären synthetischen
Datenbestand verwenden; der bisher ausgeschlossene historische Instrumentierungslauf
ist dadurch nicht wieder freigegeben.

### Prüfen des ADB-Lese-/Schreibtransports

Die folgenden Schritte schreiben ausschließlich eine neu erzeugte synthetische
Prüfdatei in ein neues Verzeichnis unter `/data/local/tmp`, lesen genau diese Datei
zurück und vergleichen Größe und SHA256. Das ist ein Transporttest, kein Nachweis
für App-Speichern, SQLite, Backup oder Restore. Der Verzeichnisname wird vom Gerät
erzeugt und geprüft; es werden keine vorhandenen Appdateien gelesen oder gelöscht.

```powershell
$gpTestDir = (& $gpAdb -s $gpSerial shell mktemp -d /data/local/tmp/qa_gartenpirat_XXXXXXXX).Trim()
if ($LASTEXITCODE -ne 0 -or $gpTestDir -notmatch '^/data/local/tmp/qa_gartenpirat_[A-Za-z0-9]+$') {
  throw 'Kein eindeutiges neues ADB-Testverzeichnis; Schreibtest nicht begonnen.'
}
$gpLocal = Join-Path ([IO.Path]::GetTempPath()) ('qa_gartenpirat_' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($gpLocal) | Out-Null
$gpSource = Join-Path $gpLocal 'probe.bin'
$gpReadback = Join-Path $gpLocal 'readback.bin'
$gpBytes = [Text.Encoding]::UTF8.GetBytes(('SYNTHETIC_GARTENPIRAT_ADB_0123456789' * 4096))
[IO.File]::WriteAllBytes($gpSource, $gpBytes)
try {
  & $gpAdb -s $gpSerial push $gpSource "$gpTestDir/probe.bin"
  if ($LASTEXITCODE -ne 0) { throw 'ADB-Schreiben fehlgeschlagen.' }
  & $gpAdb -s $gpSerial pull "$gpTestDir/probe.bin" $gpReadback
  if ($LASTEXITCODE -ne 0) { throw 'ADB-Rücklesen fehlgeschlagen.' }
  $gpExpected = (Get-FileHash -Algorithm SHA256 $gpSource).Hash
  $gpActual = (Get-FileHash -Algorithm SHA256 $gpReadback).Hash
  if ($gpExpected -ne $gpActual -or (Get-Item $gpReadback).Length -ne $gpBytes.Length) {
    throw 'ADB-Rücklesen: Inhalt oder Größe abweichend.'
  }
  [PSCustomObject]@{ scope='SYNTHETIC_ADB_TRANSPORT'; bytes=$gpBytes.Length; sha256=$gpActual; result='PASS' }
} finally {
  & $gpAdb -s $gpSerial shell rm -f "$gpTestDir/probe.bin"
  & $gpAdb -s $gpSerial shell rmdir $gpTestDir
  Remove-Item -LiteralPath $gpSource -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $gpReadback -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $gpLocal -ErrorAction SilentlyContinue
}
```

Bei Verbindungsabbruch den bekannten erzeugten Testpfad zur gezielten späteren
Bereinigung behalten. Kein pauschales `rm -rf`, `pm clear`, Löschen der Appdaten,
Zurücksetzen von Rechnungszählern oder Überschreiben einer produktiven Datenbank.

### Messwerte während der synthetischen App-Testfälle

Dieselben sechs Bedienwege wie in Etappe 1, mit demselben Fixture und mindestens
40 Wiederholungen je vergleichbarer Variante. Kalte Starts und warme Wechsel
getrennt messen. Vor jedem Vergleich Build, Schriftgröße, Anzeige, Energielage,
thermischen Zustand und Hintergrundlast festhalten. Neustart-/Abbruch-/Restoretests
schreiben ausschließlich in den expliziten synthetischen Testbestand.

Gezielte Abfragen, ohne Rohprotokolle im Repository zu speichern:

```powershell
& $gpAdb -s $gpSerial shell dumpsys meminfo at.hausgarten.service |
  Select-String '^\s*(Native Heap|Dalvik Heap|Java Heap|Graphics|Stack|Code|Private Other|System|TOTAL|TOTAL PSS|TOTAL RSS|TOTAL SWAP PSS|Views:|AppContexts:|Local Binders:|SQL|MEMORY_USED:|PAGECACHE_OVERFLOW:|MALLOC_SIZE:)'
& $gpAdb -s $gpSerial shell dumpsys cpuinfo |
  Select-String '^\s*[0-9.]+%\s+\d+/at\.hausgarten\.service(?:\s|:|/)'
& $gpAdb -s $gpSerial shell dumpsys gfxinfo at.hausgarten.service framestats |
  Where-Object { $_ -match '^Flags,|^\d+(,\d+)+,?$|^\s*(Total frames rendered:|Janky frames:|\d+th percentile:)' }
```

`gfxinfo ... reset` ist als schreibender Messzugriff freigegeben, wenn bewusst
ein neues Messfenster begonnen wird; vor dem Reset benötigte Werte erhalten.
Keine ungefilterten `logcat`-/Bugreport-/Heap-/Bildschirm-/UI-Hierarchy-Abzüge.
SQL-Zeit, Abfragezahl, gelesene Zeilen/Bytes und Vollsnapshotzähler aus der
synthetischen Instrumentierung erfassen, ohne SQL-Argumente oder Datensätze zu
protokollieren. Die schon vorhandenen nativen Messpuffer für `getState`, `ownerCall`
und `getWorkSession` sowie die Browserwerte für `render`/Seitenwechsel auswerten,
sobald der aktuelle native Testeinstieg sie liefert. Fehlende Werte als fehlend
ausweisen; ADB-Übertragungszeit ist keine Bridge- oder UI-Laufzeit.

Bei `framestats` nur gültige, vollständige Frames auswerten; nicht abgeschlossene,
markierte oder doppelt gelesene Frames getrennt zählen. Die Framezeit aus
`FrameCompleted - IntendedVsync` berechnen. Variable Bildwiederholrate beachten;
kein pauschales 16-ms-Limit als Gerätebeweis. `gfxinfo` allein deckt bei WebView
nicht jeden Renderer-/Compositorpfad ab. Speicherwerte PSS, RSS, privat und Swap
unterscheiden; mehrere Messpunkte statt eines Einzelwerts verwenden. CPU-Messungen
mit anderem Zeitfenster oder anderem Prozessumfang nicht direkt vergleichen.

### Ergebnisse in Verbesserungen überführen

Alle nutzbaren, datensparsamen Testbefunde zusammenführen: fachliche Ergebnisse,
Fehler-/Abbruchpfade, Hash-/Anzahlvergleiche, Lese-/Schreibumfang, Ausreißer,
Speicherverlauf, CPU und Bildaufbau. Je Befund konkrete Quellstelle, Hypothese,
kleinste passende Änderung und denselben Nachtest nennen. Keine Funktionen auf
Vorrat. Zuerst Regressions-/Integritätsfehler beheben, dann belegte Engpässe.
Jede Verbesserung gegen die ursprünglichen Fach- und Schutzregeln prüfen.

Der abschließende Beleg unter `docs/verification/` enthält pro Bedienweg Mittelwert,
P95 und Mittelwert der langsamsten 5 %, Stichprobenzahl, Bedingungen, Messgrenzen
und Fehler. Cloud-Browser, JVM/SQLite, Emulator, ADB-Transport und physischer Pixel
werden getrennt ausgewiesen. Aus bislang ungemessenen Änderungen keinen Gewinn
ableiten. Fehlt eine vergleichbare Geräte-Vorhermessung, den ersten Gerätelauf als
Ausgangsmessung führen; alte/produktive Daten nicht zur Vergleichbarkeit zurückspielen.

Offizielle Grundlagen (gelesen am 28.10.2026):
[Android Debug Bridge](https://developer.android.com/tools/adb),
[dumpsys: Speicher und Framezeiten](https://developer.android.com/tools/dumpsys),
[AndroidJUnitRunner](https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner).

## Codex-Regeln neu einlesen

Die einzige Regelfassung ist `AGENTS.md` im Repository-Hauptverzeichnis.
`.codex/hooks.json` ruft bei `UserPromptSubmit` den kleinen Lesehelfer auf.
Er stellt die aktuellen Checkout-Dateien samt Prüfsummen bereit; er lädt keine
externen Aufträge und verändert weder Dateien noch die App. Fehlende oder
ungültige Pflichtdateien führen zu einer expliziten Blockierantwort.

Eine unterstützte Codex-CLI muss das Projekt und die konkrete Hookdefinition
zuerst als vertrauenswürdig akzeptieren. Dazu `/hooks` öffnen und die Definition
prüfen/freigeben. Geänderte Definitionen benötigen eine neue Prüfung. Die
Konfiguration im Repository allein beweist keine Aktivierung in anderen Clients.
Lokale Command-Hooks werden nicht in jeder Cloud-Oberfläche unterstützt; die
laufende ChatGPT-Unterhaltung wird durch einen Git-Commit nicht umkonfiguriert.
Der tatsächliche Aktivierungs-/Prüfstand steht im aktuellen Arbeitsstand.
