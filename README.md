# Gartenpirat

Android-App `at.hausgarten.service`, Quellcode unter `app/`.
Arbeitsregeln: [AGENTS.md](AGENTS.md).

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
