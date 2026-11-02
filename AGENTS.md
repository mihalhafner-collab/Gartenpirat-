# Gartenpirat: verbindliche Arbeitsregeln

## Verbindlicher Ausgangspunkt

Bei jedem neuen Chat und vor jeder Entwicklungsaufgabe den aktuellen GitHub-Stand
von `main`, diese Datei und `struktur-neu/AKTUELLER-STAND.json` frisch lesen.
Für PRODUKTIV ist ausschließlich `struktur-neu/` die Arbeitsbasis.
Für den ausdrücklich separat beauftragten TEST-Kandidaten gilt die unten bezeichnete TEST-Quelle.
Vor produktiven Geräteaktionen und vor der Auswahl einer produktiven Entwicklungsbasis über
`Haus & Gartenservice Pixel.get_capabilities` die tatsächlich installierte App
prüfen: Paket, version_code, version_name sowie runtime.build_fingerprint mit
Git-Commit und source_sha256 abgleichen. Die installierte Version ist maßgeblich;
keine feste Buildnummer aus Erinnerungen, alten Chats oder Prüfprotokollen ableiten.
Bei fehlender Verbindung, unvollständiger Identität oder abweichendem Quellstand
keine Geräteänderung und keine Entwicklung auf einer vermuteten Ersatzbasis beginnen.
Die Lücke benennen und die passende aktuelle Quelle ermitteln.
Produktive Entwicklungsänderungen ausschließlich von der verifizierten installierten Quelle
ableiten; geänderten Code niemals als bereits installierten Build ausgeben.
Nach einem Geräteupdate die Identität erneut lesen und den vorhandenen Arbeitsstand
aktualisieren. Keine neuen Branches, Tags oder Verweisdateien ohne Nutzerauftrag.

## Separater TEST-Kandidat: 2.0.0 / Test-Build 2

Verbindlicher vollständiger Quellcommit: `c9d955bd5775ce300e91c4cea2122838c3fa8f10`.
https://github.com/mihalhafner-collab/Gartenpirat-/tree/c9d955bd5775ce300e91c4cea2122838c3fa8f10
Branch: `test/2.0.0-build2`; Quellcode dort unter `app/`, Variante `app/src/fullTest/`.
Die genaue APK-/Quellzuordnung steht unter `current_test_candidate` im bestehenden
`struktur-neu/AKTUELLER-STAND.json`. Die übrigen Felder dieses Dokuments, insbesondere
`source_directory`, `current_task`, `workflow` und `last_distributed_build`, betreffen PRODUKTIV.

Neue Chats müssen bei TEST-Aufträgen diesen Quellcommit verwenden und dessen
`AGENTS.md` sowie `app-versions.properties` lesen. TEST-Paket:
`at.hausgarten.service.fulltest`; sichtbare Version 2.0.0 / Test-Build 2;
Android-Code 118. Der TEST-Zähler darf den Produktivzähler niemals erhöhen.
Die APK wurde vor dieser Git-Sicherung auf Basis `d721780` mit lokalen Änderungen
gebaut; `SOURCE_DIRTY=true` ist für diese bestehende APK korrekt. Der neue Quellcommit
ist der vollständige identische Archiv-Dateistand, kein neuer APK-Build.
Vor TEST-Geräteaktionen die TEST-Paketidentität gesondert prüfen; eine MCP-Antwort
der produktiven App beweist keine Installation der TEST-App. Keine produktiven
Daten zum Testen verwenden. Aus der Quellenveröffentlichung folgt keine Installation
oder produktive Freigabe.

## Umsetzung

KISS und YAGNI: flacher, direkter Code; keine vorsorglichen Abstraktionen,
Abhängigkeiten oder zusätzlichen Dateien. Bestehende Dokumente aktualisieren.
Paketkennung `at.hausgarten.service`, Originalsignatur, SQLite-Schema und bestehendes
Backup-Format erhalten. Nur vollständig synthetische Testdaten verwenden; keine
produktiven Kundendaten, Datenbanken, Fotos oder Sicherungen für Entwicklung lesen.
Autorisierte Aufträge direkt vollständig ausführen; bereits erteilte Freigaben
nicht erneut erfragen. Subagenten nur bei ausdrücklichem Auftrag einsetzen.
Keine Installation, Migration, Wiederherstellung oder Rechnungsfreigabe aus einem
allgemeinen Entwicklungsauftrag ableiten. Produktive Gerätearbeiten ausschließlich über die
aktuelle Pixel-Verbindung; Entwicklungsprüfungen isoliert mit synthetischen Daten.

## Prüfung und Abschluss

Nur passende Prüfungen ausführen. `python3 -B struktur-neu/tools/check_current.py`
prüft den aktuellen Quellstand. Geräteidentität und Quellfingerabdruck sind kein
Beleg für einen Gerätefunktionstest. Fehlende oder fehlgeschlagene Prüfungen nennen.
Nur eigene geprüfte Änderungen nach `main` committen, pushen und zurücklesen.
Ergebnisse und Einschränkungen kurz melden; keine zusätzlichen Prüfberichte anlegen.
