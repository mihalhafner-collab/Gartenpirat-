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

## TEST-Kandidat: ausschließlich aus dem aktuellen produktiven Kotlin-Quellbestand

Der bisherige TEST 2.0.0 / Test-Build 2 mit Commit
`c9d955bd5775ce300e91c4cea2122838c3fa8f10`,
Branch `test/2.0.0-build2` und Quellverzeichnis `app/` ist ein
historischer Java-/WebView-Kandidat und **keine gültige Entwicklungs- oder
Freigabegrundlage** für die aktuelle Kotlin-/Compose-App. Eine alte APK oder
ein historischer Git-Branch darf nicht stillschweigend erneut zum aktiven
TEST-Kandidaten erklärt werden.

Verbindliche gemeinsame funktionale Quelle für beide künftigen Varianten:
`main/struktur-neu/android/app/src/main/` einschließlich der dort
referenzierten Ressourcen, der Fachregeln und der aktuellen Android-Baukonfiguration.
Der produktiv installierte Pixel-Quellstand ist vor Änderungen durch
`get_capabilities`, Build-Fingerprint und Commit zu verifizieren.
Keine fachliche Weiterentwicklung auf dem abweichenden Java-Testbranch.

Die neue TEST-Variante ist erst dann ein gültiger Kandidat, wenn sie **aus
demselben Kotlin-/Compose-Quellstand** gebaut wurde, eine eigene Paketkennung
(`at.hausgarten.service.fulltest`), eigene Android-App-Daten und die
bestehende TEST-Signatur besitzt. Nur Buildvarianten-spezifische Identität
und isolierende Konfiguration dürfen vom Produktivpaket abweichen.
TEST darf Originalsignatur, Produktiv-Versionsreihe, produktive Daten,
Kalender-/MCP-Anbindungen oder Updatezustand nicht verändern.
Bis zur erfolgreichen Build- und Geräteprüfung steht
`current_test_candidate` auf null (kein aktiver gültiger TEST-Build).

Eine spätere Produktivfreigabe verwendet den identischen geprüften
funktionalen Git-Quellstand, aber die ursprüngliche Produktiv-Paketkennung,
Originalsignatur und eine höhere produktive Versionsnummer. Das fertige
Produktivpaket benötigt eigene Integritäts-, Installations-, Update- und
Datenerhaltstests; aus TEST-Abnahmen folgt keine Installationsfreigabe.
Keine historisch aufgezeichneten TEST-Erfolge als heutige Kotlin-/Pixel-
Ergebnisse übernehmen. Historische Testquellen nur als Nachweis aufbewahren.
Neue Branches, Umstellungen und Löschungen erst nach verifiziertem
Test-Build und eindeutiger Referenzbereinigung; kein Force-Push zur
Verschleierung der Historie.

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
