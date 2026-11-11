# Gartenpirat: Arbeitsregeln

## Grundsatz: KISS und YAGNI

Jede Entscheidung folgt zuerst diesen beiden Regeln:

- **KISS:** die einfachste Lösung, die die konkrete Aufgabe vollständig löst.
  Flacher, direkter Code; wenige Dateien; kurze Texte; wenige Tipps.
- **YAGNI:** nur bauen, was jetzt gebraucht wird. Keine vorsorglichen
  Abstraktionen, Optionen, Abhängigkeiten, Dateien, Berichte oder Prüfrunden.

Bestehendes erweitern statt Neues anlegen. Bestehende Dokumente aktualisieren.
Steht eine Regel dieser Datei einer deutlich einfacheren Bedienung im Weg,
den Nutzen kurz als Vorschlag nennen; umgesetzt wird erst nach Freigabe.

## Ausgangspunkt

- Vor jeder Aufgabe `main`, diese Datei und `struktur-neu/AKTUELLER-STAND.json`
  lesen. Das Repository ist die einzige verbindliche Quelle; frühere Chats sind
  nur Material.
- Einzige gültige Design- und Funktionsumfangsvorgabe: **Eisbär 1.0.1**,
  `struktur-neu/docs/fachregeln/eisbaer/EISBAER.json` mit unveränderter PDF/JSON.
  Einstieg: `struktur-neu/docs/fachregeln/APP.md`. Frühere Konzepte sind ersetzt.
  Neue Eigentümerentscheidungen als versionierte Eisbär-Änderung eintragen;
  keine zweite Vorgabe und kein eigenmächtiges Kürzen des Umfangs.
  Bauen und CI: `struktur-neu/docs/entwicklung/BAU.md`. Signieren und
  Verteilen: `struktur-neu/docs/entwicklung/SIGNIERUNG.md`.
- Einzige Codebasis: Kotlin/Compose unter
  `struktur-neu/android/app/src/main/`. Vorher
  `python3 -B struktur-neu/tools/check_release_baseline.py` ausführen; schlägt
  sie fehl, stoppen. Keine WebView; App-Code nur Kotlin (Java nur in Tests).
- Vor Geräteaktionen über `Haus & Gartenservice Pixel.get_capabilities` Paket,
  Version, Git-Commit und `source_sha256` der installierten App prüfen. Die
  installierte Version ist maßgeblich. Bei Lücken keine Geräteänderung.

## Feste Grenzen

- Paketkennung `at.hausgarten.service` und Originalsignatur erhalten. Sicherung
  nur im Format `pixel.snapshot.v2`. SQLite-Schema nur mit ausdrücklichem Auftrag ändern.
- Geschäftsdaten, Fotos und ZIP-Sicherungen bleiben lokal auf dem Pixel. Keine
  Cloud-Pflicht. Google Drive nur für die beauftragte Verteilung originalsignierter APKs.
- Nur mit ausdrücklichem Auftrag: Installation, Migration, Wiederherstellung,
  Rechnungsausstellung mit echten Daten, APK-Verteilung, neue Branches oder Tags.
- Steuerstatus nie aus Umsätzen ableiten oder selbst ändern. Keine sevDesk- oder
  Bankanbindung. Originale, Abschlüsse und Zahlungen vor Löschen schützen.
- Laufende Zeiterfassung und Geschäftsdaten nie nebenbei ändern oder löschen; Tests ausgenommen.
- Subagenten sinnvoll und effizient einsetzen

## ChatGPT-Anbindung (Pixel-MCP)

- Jede fachliche App-Funktion ist auch als MCP-Werkzeug verfügbar; neue
  Funktionen bekommen ihr Werkzeug im selben Schritt (`ui-mcp-actions.json`).
- Schreibzugriff schaltet der Eigentümer in der App („ChatGPT darf Daten
  ändern“). MCP-Schreibwege nutzen dieselben Prüfungen wie die Oberfläche.

## Tests

- Tests prüfen Funktion: Knopf betätigen, Ergebnis in den Daten nachlesen.
  Keine Text- oder Pixelvergleiche außer für Bedienbarkeit (große Schrift, Tastatur).
- Jeder Test erzeugt eigene synthetische Daten, startet sauber und hinterlässt
  keinen Zustand. Fehler, Wiederholung und erneutes Lesen mitprüfen.
- Neue Tests nur für neue Funktionen oder gefundene Fehler. Wer einen Test
  anfasst, entfernt überholte Prüfungen.
- Eigentümerentscheidung 07.11.2026: Die Daten auf dem Pixel gelten als
  Praxistest. Funktion und Stabilität haben Vorrang; volle Test-Suiten dürfen
  per ADB direkt gegen `at.hausgarten.service` laufen und Daten ändern oder
  löschen. Keine Sicherung als Voraussetzung. Betriebsdaten nie ins Repository,
  die Cloud oder CI.

## Abschluss

- Der Eisbär-Umbau ist erst abgeschlossen, wenn **Eisbär vollständig umgesetzt**
  und sämtliche Abnahmen mit realen Nachweisen bestanden sind. Status steht nur
  in `struktur-neu/docs/fachregeln/eisbaer/UMSETZUNG.json`; Checkboxen, Mockups,
  Teil-PRs oder ein grüner Alt-Testlauf reichen nicht. Keine offenen Punkte als
  erledigt melden. `python3 -B struktur-neu/tools/check_eisbaer.py --completion`
  muss bestehen; bei `COMPLETE` wird diese Prüfung automatisch strikt.

- `python3 -B struktur-neu/tools/check_current.py` und die für die Änderung
  nötigen Laufzeitprüfungen ausführen. Geräteidentität ist kein Funktionstest.
- Rot heißt Stopp: Vor neuer Funktion jeden roten Test beheben oder mit
  Begründung löschen. Nach `main` nur über PR mit grünem vollem CI-Lauf
  (`qa-apk.yml` läuft je PR voll; manuell nur mit `emulator=true`, ohne `*_only`).
- Geänderter App-Code bekommt eine neue `versionCode`, damit jede verteilte
  Version genau einem Quellstand entspricht.
- `open_checks` nur für Pixel-Prüfungen, die im Emulator nicht möglich sind;
  nie für rote CI-Tests.
- AGENTS.md und AKTUELLER-STAND.json zusammen unter 19 KB halten
  (Kontext-Hook-Grenze 20 KB); Erledigtes steht in der Git-Historie.
- Fehlende oder fehlgeschlagene Prüfungen nennen. Geänderten Code nie als
  installiert ausgeben.
- Nur eigene geprüfte Änderungen committen, pushen und zurücklesen. Ergebnis
  kurz melden; keine zusätzlichen Prüfberichte.
