# Gartenpirat: verbindliche Arbeitsregeln

## Verbindlicher Ausgangspunkt

Bei jedem neuen Chat und vor jeder Entwicklungsaufgabe den aktuellen GitHub-Stand
von `main`, diese Datei und `struktur-neu/AKTUELLER-STAND.json` frisch lesen.
Ausschließlich `struktur-neu/` ist die Arbeitsbasis.
Vor fachlichen Geräteaktionen und vor der Auswahl einer Entwicklungsbasis über
`Haus & Gartenservice Pixel.get_capabilities` die tatsächlich installierte App
prüfen: Paket, version_code, version_name sowie runtime.build_fingerprint mit
Git-Commit und source_sha256 abgleichen. Die installierte Version ist maßgeblich;
keine feste Buildnummer aus Erinnerungen, alten Chats oder Prüfprotokollen ableiten.
Bei fehlender Verbindung, unvollständiger Identität oder abweichendem Quellstand
keine Geräteänderung und keine Entwicklung auf einer vermuteten Ersatzbasis beginnen.
Die Lücke benennen und die passende aktuelle Quelle ermitteln.
Entwicklungsänderungen ausschließlich von der verifizierten installierten Quelle
ableiten; geänderten Code niemals als bereits installierten Build ausgeben.
Nach einem Geräteupdate die Identität erneut lesen und den vorhandenen Arbeitsstand
aktualisieren. Keine neuen Branches, Tags oder Verweisdateien ohne Nutzerauftrag.

## Umsetzung

KISS und YAGNI: flacher, direkter Code; keine vorsorglichen Abstraktionen,
Abhängigkeiten oder zusätzlichen Dateien. Bestehende Dokumente aktualisieren.
Paketkennung `at.hausgarten.service`, Originalsignatur, SQLite-Schema und bestehendes
Backup-Format erhalten. Nur vollständig synthetische Testdaten verwenden; keine
produktiven Kundendaten, Datenbanken, Fotos oder Sicherungen für Entwicklung lesen.
Autorisierte Aufträge direkt vollständig ausführen; bereits erteilte Freigaben
nicht erneut erfragen. Subagenten nur bei ausdrücklichem Auftrag einsetzen.
Keine Installation, Migration, Wiederherstellung oder Rechnungsfreigabe aus einem
allgemeinen Entwicklungsauftrag ableiten. Gerätearbeiten ausschließlich über die
aktuelle Pixel-Verbindung; Entwicklungsprüfungen isoliert mit synthetischen Daten.

## Prüfung und Abschluss

Nur passende Prüfungen ausführen. `python3 -B struktur-neu/tools/check_current.py`
prüft den aktuellen Quellstand. Geräteidentität und Quellfingerabdruck sind kein
Beleg für einen Gerätefunktionstest. Fehlende oder fehlgeschlagene Prüfungen nennen.
Nur eigene geprüfte Änderungen nach `main` committen, pushen und zurücklesen.
Ergebnisse und Einschränkungen kurz melden; keine zusätzlichen Prüfberichte anlegen.
