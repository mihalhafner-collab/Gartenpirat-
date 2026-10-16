# Designüberarbeitung 1.32.0 – 12.10.2026

## Umsetzung im vorhandenen Original

- Einheitliche helle Grünraum-Oberfläche, vier Hauptziele: Heute, Arbeiten, Kunden, Aufträge.
- Tagesübersicht mit bestehender Zeiterfassung, Tages-/Wochenstunden, offenen Aufträgen und heutigen Terminen.
- Kompakte Kundenliste; Antippen öffnet ein eigenes Kundenprofil mit Kontaktdaten, Anruf/Route, Aufträgen, Arbeiten, Rechnungen und vorhandenen Profilfotos.
- Neue Arbeit und neuer Auftrag direkt aus dem Kundenprofil; vorhandene Formulare und IDs werden weiterverwendet.
- Größere Listen im Profil aufklappbar, statt ältere Aufträge und Rechnungen auszublenden.
- Arbeitsaufzeichnungen mit eigener Übersichts-/Bearbeitungsseite und erreichbarer Speichern-Leiste.
- Neue Arbeit direkt beim Kunden ohne künstlichen Auftrag; bestehende Kunden-/Auftragsprüfung bleibt erhalten.
- Begonnene neue Arbeitsaufzeichnung lokal als Formularentwurf wiederherstellbar. Kein automatisches Anlegen eines Betriebsdatensatzes. Fotos müssen nach einem Reload erneut gewählt werden.
- Auftragsstatusfilter und kompakte Arbeitslisten. Vorhandene Langdruck-/Wischfunktionen bleiben erhalten.

## Tatsächliche Prüfungen

| Prüfung | Methode | Ergebnis |
| --- | --- | --- |
| Design, Kundenprofil, längere Historien, Anruf/Route, optionale Aufträge, Formularentwurf, Filter, Zurück | Echter Chromium-Browser, isolierte Android-Bridge-Testdaten | 11 bestanden |
| Arbeitsaufzeichnungen lesen/bearbeiten/speichern, IDs, Autosave, ungültige Angaben, Zurück | Echter Chromium-Browser, isolierte Android-Bridge-Testdaten | 8 bestanden |
| Rechnungsvorschau, Rundung, Eingaben, gemeinsame Android-/Laptop-Dateien | Automatisierte JavaScript-Tests | 22 bestanden |
| Rechnung aus Arbeitsaufzeichnungen | Automatisierte JavaScript-Tests | 12 bestanden |
| Mobile Darstellung | Browserbreiten 360/412, Bearbeitungsprüfung zusätzlich 426 Pixel; Screenshots angesehen | Bestanden |

Ergebnisdateien: outputs/Design-v1.32-Tests.json, outputs/Worklog-Window-Tests.json.

## Schutz und Grenzen

Keine produktiven Datensätze, IDs, Datenbankschemata, Drive-Daten oder MCP-Werkzeugnamen geändert. Keine zweite App-Struktur. Das Paket bleibt at.hausgarten.service, Version 1.32.0, Code 49. Android-Buildkonfiguration unverändert compile/target 37.

Die Browserprüfungen ersetzen keinen physischen Pixel-Test. Beim Prüflauf war kein USB-Gerät angeschlossen. Native Fotoauswahl, Tastatur, Akkuschutz und Live-Tunnel/Drive wurden in diesem Designprüflauf nicht End-to-End getestet.

Die Designstudie ist eine Orientierung, keine pixelgenaue Kopie: Es gibt einen einheitlichen hellen Stil, nicht vier konkurrierende Designs. Ein separater Dark-Modus ist nicht Teil dieser Version. Die vorhandene Zeiterfassung wurde beibehalten; ihre zentrale Geschäftslogik wurde nicht ersetzt.

Die zuvor nach Drive hochgeladene Gemini-Quellcodekopie ist ein zeitlicher Schnappschuss und enthält die anschließenden kleinen Historien-Ergänzungen noch nicht.

## APK und Bereitstellung

- assembleRelease erfolgreich; Android-17-Buildkonfiguration (compile/target 37), Originalpaket at.hausgarten.service, Version 1.32.0 / Code 49 geprüft.
- Bestehende Update-Signatur identisch zur APK 1.31.0. SHA-256 des Zertifikats: a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8.
- mobile.html, design-concept.js und design-concept.css im APK mit den getesteten Quelldateien per SHA-256 verglichen: identisch.
- outputs/Haus-und-Gartenservice-Pixel-v1.32.0.apk: 2.623.867 Byte; SHA-256 f703cb53860c3bf97718d7a600ac9a83088ef8abf771b33568e87e00ed98d340.
- In bestehendem Drive-Ordner Haus & Garten App hochgeladen, Größe und Elternordner zurückgelesen: bestätigt.
- Download: https://drive.google.com/file/d/1UEDjvl5BHJ6MyhXGQCKy-tRKH_BuPbhI/view?usp=drivesdk
- Vorgängerversion 1.31.0 in bestehenden APK-Backups-Ordner verschoben; neuer Elternordner bestätigt. Keine APK endgültig gelöscht.
- Kein physisches Gerät angeschlossen, daher nicht auf dem Pixel installiert oder dort gestartet.
- Bestehende Build-Warnungen: veraltete AndroidX-Option, SDK-XML-Versionshinweis, künftig inkompatible Gradle-10-Funktionen. Build erfolgreich; keine unnötige Buildsystem-Migration für diese Designänderung.

Installation: Neue APK auf dem Pixel herunterladen und Aktualisieren wählen. Die bestehende App nicht deinstallieren und keine App-Daten löschen.
