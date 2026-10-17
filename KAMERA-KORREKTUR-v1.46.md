# Kamera-Korrektur auf Basis von 1.46.0 / Build 65

Basis: `1da29e225e2a0876be1d9ae911369b71ecb2e95b`, bestehender Zweig
`codex/v1.46-functional-optimization-20261016`.
Dies ist eine Quellcode-Korrektur, keine Freigabe einer neuen APK.

## Befund und Änderung

`PhotoCaptureProvider.uri()` verglich einen kanonischen Dateipfad mit einem
nicht aufgelösten Dateipfad. Ein Verzeichnisalias zur gleichen privaten
Cache-Datei wurde dadurch fälschlich abgewiesen. Beide Pfade werden jetzt
kanonisch verglichen; Dateiname, erlaubtes Stammverzeichnis und tatsächliche
Datei werden weiter geprüft. Pfade außerhalb des Fotoverzeichnisses und
symbolische Verweise auf solche Ziele bleiben gesperrt.

`MainActivity` trennt die Vorbereitung der Datei vom Start der Kamera.
Speicher-/Dateifehler, Sicherheitsfehler, unerwartete Laufzeitfehler und ein
fehlender Kamera-Handler erhalten eigene Meldungen und Diagnosecodes.
Die Dateireferenz wird vor der URI-Erstellung festgehalten, damit auch bei
einem fehlgeschlagenen Zwischenschritt die temporäre Datei bereinigt wird.
Diagnoseprotokolle enthalten nur Codes und Ausnahme-Klassennamen, keine
Dateipfade, URIs, Kundendaten oder Ausnahmetexte.

Der abgeschlossene Dateiauswahl-Callback wird vor seiner Ausführung gelöst.
Ein währenddessen neu registrierter Callback wird dadurch nicht mehr
versehentlich überschrieben. Bestehende Fotoaufbewahrung, Galerieauswahl,
Datenbank, Kundenzuordnung, Timer, Drive-Abgleich, Berechtigungen, Signierung
und Versionskonfiguration wurden nicht umgestellt.

## Ausgeführte Prüfung

Reproduzierbarer Host-Test mit Python 3 und JDK 17:

```sh
python3 tests/run_photo_capture_tests.py
```

Ergebnis in der lokalen Prüfumgebung: **29 Testfälle erfolgreich**, zusätzlich
statische Prüfung der Kamera-Verknüpfung im Dateiauswahl-Callback erfolgreich.
Der echte Provider und die unverändert aus MainActivity extrahierten
Kameramethoden werden kompiliert und ausgeführt. Android-Grenzklassen sind
Testdoubles; Datei- und Symlinkoperationen laufen auf dem echten Host-Dateisystem.

Geprüft wurden insbesondere kanonische/verknüpfte Pfade, Pfad-Ausbruchsschutz,
Dateivorbereitung, Fehlerkategorien, Bereinigung, Wiederholung nach Fehler,
URI-/ClipData-/Flag-Übergabe, Abbruch, leere/übergroße Aufnahme, erfolgreiche
Rückgabe ohne Ergebnis-Intent, Galerieauswahl und Callback-Wiedereintritt.

Zwei separate lokale Negativkontrollen wurden ausgeführt: Mit dem ursprünglichen
Provider scheitert der Alias-Test; mit der ursprünglichen Callback-Reihenfolge
scheitert der Wiedereintrittstest. Nach Wiederherstellung der Korrektur besteht
die gesamte neue Host-Suite erneut. Die Python-Testdatei wurde auf Syntax geprüft.
Die neuen Host-Tests sind in den bestehenden manuellen QA-APK-Workflow eingebunden;
keine neuen Workflow-Auslöser oder zusätzlichen Berechtigungen wurden ergänzt.

## Grenzen und nächste Abnahme

Kein vollständiger Android-Build, keine vollständige Projekt-Testsuite, kein
Emulator-/Pixel-Test und keine neue APK wurden bei dieser Korrektur ausgeführt
beziehungsweise erstellt. Ein verfügbarer Host-JDK ersetzt das hier nicht
vorhandene Android-SDK nicht. Diese Tests beweisen weder die tatsächliche
Android-Kamera-/Berechtigungsübergabe noch den gesamten WebView-Ablauf.

Vor Freigabe: bestehenden QA-Workflow auf dem korrigierten Commit ausführen,
dann auf dem Pixel Kamera öffnen, aufnehmen, abbrechen, erneut aufnehmen,
Bildvorschau und dauerhafte Speicherung beim vorgesehenen Kunden prüfen.
Die Laufzeitursache auf dem konkreten Pixel ist ohne Gerätetest/Log noch nicht
abschließend bestätigt. Bei erneutem Fehler den Code unter `HausGartenCamera`
auswerten, nicht aus einer allgemeinen Meldung auf eine fehlende Kamera schließen.

Automatische Verknüpfung mit laufendem Arbeitseinsatz und Auftrag sowie
Ende-zu-Ende-Fotosynchronisierung sind durch diesen Kamera-Patch nicht
implementiert oder abgenommen. Bestehende Kundendaten wurden nicht angefasst.
Für eine produktive Veröffentlichung gelten weiterhin die Projektvorgaben
zu Buildnummer, Signierung, Datenerhalt und Installations-/Update-Abnahme.
