# Gartenpirat

Android-App `at.hausgarten.service`. Der aktuelle Quellcode liegt direkt unter
`app/`, die Gradle-Konfiguration im Repository-Hauptverzeichnis.

Arbeitsregeln: [AGENTS.md](AGENTS.md).

Tests vom Hauptverzeichnis aus starten:

```sh
./gradlew test
```

Dafür werden Java 17 und das zum Projekt passende Android SDK benötigt.
Verbleibende Prüf- und Buildwerkzeuge liegen unter `tools/`, Testdaten und
zusätzliche Regressionstests unter `tests/`.
