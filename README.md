# Gartenpirat

Aktueller Testkandidat: **TEST 2.0.0 / Test-Build 2**, Paket
`at.hausgarten.service.fulltest`, Android-Versionscode **118**.

- [Exakter, vollständiger Quellcommit c9d955b](https://github.com/mihalhafner-collab/Gartenpirat-/tree/c9d955bd5775ce300e91c4cea2122838c3fa8f10)
- [TEST-Branch test/2.0.0-build2](https://github.com/mihalhafner-collab/Gartenpirat-/tree/test/2.0.0-build2)
- [Installierbare TEST-APK](https://drive.google.com/file/d/1AJVvySLiJ3bnm8Fdz7UY-hZlrITcuNhC/view)
- [Unverändertes, zur APK gehörendes Quellarchiv](https://drive.google.com/file/d/1Cc3zn2pkqqwBB0QMNBmjBJh4umEk188U/view)

Entwicklungspfad für diesen Kandidaten: `app/`, Testvariante: `app/src/fullTest/`.
`SOURCE_SHA256`: `2e830ea9cfc4352471b495efd31523eaba818ee8653c174d5c96b47f298f38f4`.
Der Quellcommit enthält exakt den vollständigen Dateistand des ausgelieferten
Archivs. Die APK selbst wurde davor auf Basis von `d721780` mit lokalen Änderungen
gebaut (`SOURCE_DIRTY=true`); die nachträgliche Git-Sicherung verändert die APK
nicht. Zusätzliche Verlinkungen im Branch sind Dokumentationsänderungen.

Dieser Branch ist ein TEST-Quellnachweis. Die produktive Entwicklung wird im
[Haupteinstieg](https://github.com/mihalhafner-collab/Gartenpirat-/blob/main/README.md)
separat geführt. TEST 2 ist keine Freigabe für ein produktives Update.

Android-App `at.hausgarten.service`, Quellcode unter `app/`.
Arbeitsregeln: [AGENTS.md](AGENTS.md).

Die App-Identitäten und getrennten Nummern stehen verbindlich in
[app-versions.properties](app-versions.properties). TEST verwendet
`at.hausgarten.service.fulltest`, einen eigenen Testschlüssel und ein pinkes
TEST-Kolbensymbol; PRODUKTIV verwendet `at.hausgarten.service` und die Originalsignatur.
Die sichtbare TEST-Reihe beginnt bei 2.0.0 / Build 2. Für das Update der früheren
Testinstallation entspricht das Android-Code 118 (Test-Build + 116).
Die produktive Reihe bleibt davon unabhängig: am Pixel bestätigt 1.57.2 / Build 121,
als nächstes vorbereitet 1.57.3 / Build 122. Ein Test-Build veröffentlicht keine
Produktivversion und ändert deren Zähler nicht.

APK-Namen: `Garten-TEST-<Version>-Build<Nummer>.apk` oder
`Garten-PRODUKTIV-<Version>-Build<Nummer>.apk`. Die App zeigt Kanal, Version und
sichtbare Buildnummer in Kopfzeile/Einstellungen; die native Diagnose und MCP
liefern zusätzlich Paket, Android-Code, Quellfingerabdruck und Buildtyp.
Ein historischer Dateiname mit Build 117 bezeichnet noch die frühere TEST-APK,
keine aktuelle Produktivversion. Bestehende Archive sind historische Quellnachweise.

`tools/` enthält die benötigten Build-, Katalog- und Prüfwerkzeuge.
`tests/` enthält ausführbare Regressionstests mit synthetischen Daten.
`libraries/plants/` enthält botanische Quellen, Bildrechte und Importnachweise.

Java 17, Python 3 und das in `app/build.gradle` festgelegte Android SDK werden
für den Build benötigt:

```sh
./gradlew test
```

Dieser Befehl kompiliert die App und führt die Buildprüfungen aus. Die fachlichen
Hosttests werden separat gestartet:

```sh
python3 -m pip install -r requirements-plants.txt
python3 -B -m unittest discover -s tests
HG_JSON_JAR=/absoluter/pfad/json-20240303.jar python3 -B tools/run-followup-java.py
HG_JSON_JAR=/absoluter/pfad/json-20240303.jar python3 -B tests/run_photo_capture_tests.py
```

Der Java-Teststarter benötigt außerdem `ANDROID_HOME` und die durch Gradle
aufgelösten Bibliotheken. Die JSON-JAR wird auf ihre festgelegte Prüfsumme geprüft.
Browser-Regressionen: `node tests/<name>.test.cjs`; dafür sind Playwright mit Chromium
und Acorn erforderlich. Die Modulpfade können über `HG_PLAYWRIGHT_MODULE` und
`HG_ACORN_MODULE` gesetzt werden.

Der GitHub-Workflow baut zusätzlich das native Test-APK und prüft Backup und
Wiederherstellung in einem isolierten Emulator. Hosttests ersetzen diesen Test nicht.
