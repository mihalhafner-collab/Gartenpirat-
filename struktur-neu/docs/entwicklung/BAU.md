# Bauen und prüfen

Befehle beginnen in `struktur-neu/`. Benötigt: Python 3, JDK 17, Gradle 9.3.1,
Android-SDK 37 mit Build-Tools 37.0.0. `ANDROID_HOME` (und für die JVM-Tests
`ANDROID_SDK_ROOT`) setzen; keine Maschinenpfade oder Geheimwerte in Git.

## Grundlage

- Eine Codebasis: Kotlin und Compose im Modul `:app`
  (`android/app/src/main/java/at/hausgarten/service`). Keine WebView, keine
  zweite Oberfläche, kein Servermodul.
- AGP 9.1.1 mit eingebautem Kotlin 2.2.10; Compose-Compiler-Plugin in derselben
  Version. Abhängigkeiten stehen in `android/app/build.gradle`.
- SQLite-Schema 7 im vorhandenen Store, WAL aktiv. `NativeDataRepository` hält
  eine wiederaufbaubare Leseprojektion (`native_read_*`). Kein Room.
- Blockierende Arbeit auf `Dispatchers.IO`, Aktualisierung über Flow. Bilder über
  einen gemeinsamen Coil-ImageLoader mit Vorschaudateien.
- Oberfläche und MCP nutzen dieselben Fachschreiber; keine Geschäftsregeln im UI-Code.

## Prüfen

```sh
python3 -B tools/check_current.py
```

Prüft Originalidentität, Datenschutz und die Python-Tests; baut nichts.
Verträge, Testdaten, Wartungstexte und die Zuordnung jeder App-Aktion
zu ihrem MCP-Werkzeug prüft jeder Gradle-Build vorab.

```sh
cd android
gradle --no-daemon --no-configuration-cache --build-cache --max-workers=2 \
  :app:assembleRelease :app:assembleReleaseAndroidTest :app:lintRelease
```

Kotlin- und Lint-Warnungen brechen den Bau ab. Die Release-APK bleibt hier
unsigniert. Signieren und Verteilen: `SIGNIERUNG.md`.

JVM-Fachtests (Domain, Abrechnung, MCP, Geräte, ZIP, SQLite) unter
`android/app/src/test`, mit echtem `org.json` und SQLite-JDBC:

```sh
cd android && gradle --no-daemon --no-configuration-cache :app:testReleaseUnitTest
```

Die Tests kompilieren gegen die Android-Fassung von `org.json` (geprüfte
`JSONException`), also gegen dieselbe Schnittstelle wie die App.

## CI

`.github/workflows/qa-apk.yml` läuft bei jedem PR nach `main` und manuell.
Job `build`: Quellprüfung, Release- und Test-APK, Lint,
JVM-Tests (`testReleaseUnitTest`). Job `emulator`: alle nativen Suiten auf einem Wegwerf-Emulator
(API 35); die Liste der Suiten und Abläufe steht nur im Workflow. Ein neuerer
Push auf denselben PR bricht den laufenden Lauf ab. Voller Lauf = PR-Lauf oder
manuell mit `emulator=true` ohne `*_only`; die `*_only`-Schalter dienen nur der
Fehlersuche.

Jeder Compose- und Grafikablauf startet nach `pm clear` in einem neuen
Instrumentierungsprozess (`-e journey <name>`) und legt seine synthetischen
Daten selbst an. Der Emulator nutzt einen temporären Testschlüssel; es wird
keine APK veröffentlicht. Ein Emulatorlauf ist keine Pixel-Abnahme.

Einzelne Suite am Gerät oder Emulator: App- und Test-APK müssen aus demselben
Quellstand stammen und dasselbe Zertifikat tragen, am Pixel die Originalsignatur.
`native_data` und `native_settlement` verwenden eigene synthetische Fixture-Verzeichnisse.
Compose, Grafik und Sicherungsoberfläche benötigen einen frischen synthetischen
App-Zustand. Jeder `journey` läuft in einem eigenen Instrumentierungsprozess;
`*_reopen` folgt seiner Hauptsuite ohne Datenreset. Suiten und Abläufe bleiben im
Workflow aufgeführt.

```sh
adb -s <serial> shell am instrument -w -e suite native_data \
  at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation
adb -s <serial> shell am instrument -w -e suite native_settlement \
  at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation
adb -s <serial> shell am instrument -w -e suite native_compose -e journey timer \
  at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation
```

## Sicherung

Unverschlüsselte ZIP (Manifestformat 1, Datensatz `pixel.snapshot.v2`) über die
Android-Dateiauswahl. Konsistenter SQLite-Snapshot, Einstellungen und private
Dateien werden direkt in die ZIP gestreamt und danach vollständig zurückgelesen.
Hinweise zu fehlenden Originalen stehen in `data/backup-notes.json` und brechen
die Sicherung nicht ab. Wiederherstellung: ausdrückliche Auswahl und Bestätigung,
atomarer Austausch beim Neustart, keine Rückfallkopie. Ältere Formate und
verschlüsselte Container werden abgewiesen. Die WebView ist vollständig
entfernt (Eigentümerentscheidung 07.11.2026); ihre Reste löscht die App beim Start.

## Pixel über ADB

Vom Laptop mit USB-Freigabe: Gerät mit `adb devices -l` wählen, immer `-s`
verwenden, keine Seriennummern in Git. Vor schreibenden Tests Paket, Version und
`source_sha256` lesen (`get_capabilities`). Eigene Testdaten anlegen, Ergebnis
erneut lesen, danach entfernen. Laufende Zeiterfassung nicht nebenbei beenden.
Keine Verbindung heißt „nicht ausgeführt“, nicht bestanden.
