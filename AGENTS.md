# Gartenpirat: verbindliche Arbeitsregeln

## Verbindlicher Ausgangspunkt

Bei jedem neuen Chat und vor jeder Entwicklungsaufgabe den aktuellen GitHub-Stand
von `main`, diese Datei und `struktur-neu/AKTUELLER-STAND.json` frisch lesen.
Die Kotlin-App unter `struktur-neu/android/app/src/main/` ist die einzige funktionale Codebasis für TEST und PRODUKTIV.
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

## Gemeinsame Build- und Ablageregel

TEST und PRODUKTIV entstehen ausschließlich aus dem identischen Kotlin-/Compose-
Quellcode unter `struktur-neu/android/app/src/main/`. Der Testkanal verwendet
eine getrennte Paketkennung, eigene Testdaten und seine bisherige Signatur.
Produktive Änderungen werden aus der verifizierten installierten Quelle abgeleitet,
niemals aus historischen APKs oder anderen Implementierungen.

Geschäftsdaten und vollständige ZIP-Sicherungen werden nur lokal auf dem Pixel
geschrieben. Es gibt keine verpflichtende Cloud- oder externe Sicherung für die
Umstellung auf lokalen Betrieb. Die dafür notwendige Prüfung weiterer Schreiber,
des aktuellen Geschäftsinventars und der Rechnungsoriginale/Nummern bleibt erhalten.
Manuelles Kopieren von Sicherungen liegt außerhalb der App. Google Drive wird nur
für die ausdrücklich freigegebene Verteilung originalsignierter Installations-APKs
verwendet; keine automatische Übertragung von Geschäfts- oder Sicherungsdaten.
Die Verteilung benötigt jeweils einen eigenen ausdrücklichen Auftrag.

## Vereinfachter Rechnungsumfang (Eigentümerentscheidung 02.11.2026)

sevDesk erstellt und versendet sämtliche Rechnungen. Die Gartenpirat-Rechnungsfunktionen
werden derzeit nicht erweitert oder produktiv freigegeben. Historische aus
sevDesk übernommene Rechnungen sind unverbindliche Versuchsdaten; ihre Übernahme,
Wiederherstellung, Nummernabstimmung oder Fremdschreiber-Nachweise sind keine
Voraussetzung für die übrige App. Alte Cloud-Geschäftsverbindungen und
Migrationsmasken entfernen. Bestehende Rechnungsansichten dürfen rein informativ
bleiben. Kein automatisches Löschen auf dem installierten Pixel durch
Quellcode-Bereinigung, keine Änderungen an bereits ausgestellten sevDesk-Belegen.
Neue Rechnungserstellung in der App erst bei gesondertem Auftrag und eigener Prüfung.

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
