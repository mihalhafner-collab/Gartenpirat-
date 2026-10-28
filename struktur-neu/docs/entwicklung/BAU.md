# Bauen und prüfen

Alle folgenden Befehle beginnen im Ordner `struktur-neu/`. Benötigt werden
Python3, JDK17, Gradle9.3.1 und Android-SDK37 mit Build-Tools37.0.0 gemäß den
vorhandenen Gradle-Dateien. Aktuelle Abhängigkeiten nicht vorsorglich ändern.
`JAVA_HOME`, `ANDROID_HOME` und gegebenenfalls `GRADLE_USER_HOME` auf die vorhandenen
Werkzeuge setzen; keine Maschinenpfade oder Geheimwerte in Git speichern.
In der verwalteten Cloud auch die vorgegebenen Proxy-/CA-Einstellungen für die
Gradle-JVM verwenden; Java übernimmt HTTP-Proxy-Umgebungsvariablen nicht selbst.
Vorhandene Abhängigkeiten können bei vollständigem Cache offline verwendet werden.

## Vor und nach einer Änderung

```sh
python3 -B tools/check_current.py
```

Der Befehl prüft aktuelle Quellen, generierte Verträge, Pflanzenressourcen,
Prüfdatenadapter und die ausgewählten Python-Prüfungen. Er erzeugt keine APK,
signiert nicht und greift nicht auf ein Gerät zu. Für Oberfläche, native Fachlogik
und Baukonfiguration zusätzlich die jeweils betroffenen Prüfungen ausführen.

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
