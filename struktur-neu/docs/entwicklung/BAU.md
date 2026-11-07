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

Die Release-APK bleibt hier unsigniert. Signieren und Verteilen: `SIGNIERUNG.md`.

JVM-Fachtests (Domain, Abrechnung, MCP, Geräte, ZIP, SQLite):

```sh
HG_JSON_JAR=… HG_SQLITE_JDBC_JAR=… HG_SLF4J_API_JAR=… \
  python3 -B tools/run-followup-java.py --tables
```

Benötigt `org.json:json:20240303`, SQLite-JDBC 3.46.1.0 und SLF4J-API 1.7.36;
der Runner prüft deren SHA256 und lädt nichts nach.

## CI

`.github/workflows/qa-apk.yml` baut, führt die JVM-Tests aus und startet auf
einem Wegwerf-Emulator (API 35) die Suiten `local_backup`, `local_backup_reopen`,
`native_data`, `document_provider`, `document_provider_reopen` und
`native_compose`. Das geschieht bei jedem Push nach `main` und bei manuellem
Start mit `emulator`. Die Suiten für Abrechnung, Rechnungen und Grafik laufen
nur über die Schalter `accounting_only`, `settlement_only`,
`settlement_ui_only` und `graphics_only`.

Der Emulator verwendet nur synthetische Daten und einen temporären
Testschlüssel; es wird keine APK veröffentlicht. Ein Build ist kein Emulatorlauf,
ein Emulatorlauf keine Pixel-Abnahme.

Die 21 Compose-Abläufe laufen einzeln mit `-e journey <name>`, jeweils nach
`pm clear` und in einem neuen Instrumentierungsprozess. Jeder legt seinen
synthetischen Bestand selbst an; Ergebnisse stehen je Ablauf im CI-Artefakt.

Einzelne Suite am Gerät oder Emulator:

```sh
adb -s <serial> shell am instrument -w -e suite native_data \
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
