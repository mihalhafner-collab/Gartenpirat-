# Android-Testgerät für Haus & Garten

Ein getrenntes Android-Testgerät, keine zweite Betriebsdatenbank und keine neue App.

- Name: `HausGarten_QA_Pixel10Pro_API35`
- Geräteprofil: Pixel 10 Pro; Betriebssystem zunächst Android 15 / API 35. Das bildet nicht die gesamte reale Pixel-Hardware oder deren Android-Version nach.
- Emulator-Adresse: `emulator-5580`; Installationsskript lehnt andere Geräte ausdrücklich ab.
- APK: die veröffentlichte Version 1.27.1, Versionscode 41.
- Daten: ausschließlich künstlicher Testkunde und Testauftrag; keine Drive-Ordnerfreigabe.
- Testgerät und Android-Werkzeuge bleiben im Projektordner.

## Start

Wenn der Windows-Hypervisor noch nicht aktiv läuft: zuerst Windows selbst neu starten. Es wird kein automatischer Neustart ausgelöst.

Danach im App-Ordner `start-android-test.cmd` doppelt anklicken. Das öffnet den Emulator, wartet auf den Android-Start und installiert bzw. aktualisiert die APK. Beim ersten Start wird vor dem Öffnen der App ein künstlicher Testbestand eingesetzt. Vorhandene spätere Emulator-Testdaten bleiben erhalten.

Falls das Testgerät bereits läuft, nicht mehrfach starten. Die App lässt sich separat mit `tests/install-android-emulator-app.ps1` installieren/öffnen. Für einen unbeaufsichtigten Start ist `tests/start-android-emulator.ps1 -InstallApp` ohne sichtbares Emulatorfenster vorgesehen.

Keine Option „Daten löschen“ und keine echte Drive-Anmeldung für diese ersten Tests verwenden.

## Automatische Tests durch Codex

Nach dem Android-Start und der Installation: `test-android-emulator.cmd` oder `node tests/android-emulator.test.js`. Codex kann den Emulator ohne Fenster starten und anschließend dieselben Tests ausführen. Der Testlauf verbindet sich ausschließlich mit dem vorhandenen WebView der **installierten APK**, nicht mit einer Browser-Nachbildung. Die echte Java-Schnittstelle und SQLite-Speicherung bleiben aktiv; keine simulierte Speicher- oder Synchronisationsschnittstelle.

Vor jeder Änderung kontrolliert der Test Gerätename, Adresse, App-Version, künstlichen Ausgangsdatenbestand und fehlende Drive-Freigabe. Bei einem anderen Gerät oder einem eingerichteten Drive-Ordner wird abgebrochen. Testdatensätze bleiben im Testgerät erhalten, damit Fehler anschließend nachvollzogen werden können. Es wird weder die App zurückgesetzt noch ein angeschlossenes Pixel bedient.

Die vorbereiteten Basistests umfassen Navigation, Kundenanlage/-bearbeitung, Auftragsanlage, Offline-Arbeitserfassung, Stundenberechnung, Vorgaben und Zuordnung, automatische Speicherung, echte Android-Zurück-Taste, echte lange Berührung, Lösch-/Rückgängig-Logik, Suche/Filter, ungültige Eingaben, stabile IDs und Erhalt nach App-Neustart. Jeder Prüfschritt benennt seine Methode: UI-Bedienung, echte Android-Taste/Berührung oder direkte Prüfung der realen Java-Schnittstelle. Eine direkte Schnittstellenprüfung wird nicht als vollständiger UI-Test ausgewiesen.

Ergebnisse stehen in `outputs/QA-Android-Emulator-results.json`; jeder Lauf erhält zusätzlich einen eigenen Ordner unter `outputs/android-emulator/` mit Bericht und gegebenenfalls Screenshots/Fehlerprotokollen. Ohne gestarteten Emulator heißt das Ergebnis **BLOCKED**, niemals „bestanden“. Nach bestandenem Basislauf heißt es **BASELINE_PASSED**, nicht „gesamte App freigegeben“.

Die Prüfverbindung ist vorübergehend und lokal. Sie wird nach dem Test getrennt; Flugmodus wird auf seinen vorherigen Zustand zurückgesetzt. Playwright/Node aus der vorhandenen Codex-Laufzeit sind erforderlich. Aktuelle WebViews ermöglichen das Prüfen debuggbarer APKs; für eine nicht debuggable spätere Release-APK ist ein separater Testbuild notwendig – keine dauerhafte Freigabe einer produktiven Debug-Schnittstelle.

Reale Drive-Synchronisation, Google-Fotos-Auswahl, Spracherkennung, Versand und sämtliche weiteren noch nicht automatisierten Funktionen bleiben ausdrücklich offen. Für Drive ist ein gesonderter Testordner und eine kontrollierte Kontofreigabe nötig. Keine Tests mit Betriebsdaten, Rechnungsversand oder Banktransaktionen.

## Vorgesehene Tests – nicht allein durch Einrichtung bestanden

1. App öffnen und Version 1.27.1 sowie ausschließlich Testdaten sehen.
2. Testkunde öffnen, bearbeiten und Zusatzfeld speichern.
3. Auftrag zuordnen und Arbeit erfassen: 08:00–14:30, 60 Minuten Pause = 5,5 Stunden.
4. Tätigkeit, Material, Kilometer und Notiz ergänzen; zurückgehen und erneut öffnen.
5. Mehrfach tippen, lange drücken, Bearbeiten/Löschen/Rückgängig und Android-Zurück prüfen.
6. App beenden/erneut starten; Daten und IDs müssen erhalten sein.
7. Flugmodus/Offline-Betrieb prüfen, danach Verbindung wiederherstellen.
8. Fotos mit ausschließlich künstlichen Testbildern auswählen und zuordnen.
9. Erst danach Drive-Rundlauf in einem separaten Testordner planen. Die Google-APIs-Systemimage enthält nicht automatisch die Google-Drive-App; reale Kontofreigaben sind ein eigener Schritt.

## Grenzen und Voraussetzungen

Der Laptop hat rund 4 GB RAM. Emulator auf 1536 MB und zwei virtuelle CPU-Kerne begrenzt; unnötige Programme vor einem Test schließen. Selbst mit Hardwarebeschleunigung kann die Bedienung langsam sein. Daraus allein darf kein Performancefehler der App auf einem echten Pixel abgeleitet werden.

Die Windows-Hypervisor-Plattform wurde mit ausdrücklicher Freigabe geprüft/aktiviert. Ob sie nutzbar läuft, wird vor jedem Emulatorstart mit dem Android-Werkzeug geprüft. Ein aktiviertes Windows-Feature ist kein Nachweis eines erfolgreich gestarteten Android-Geräts.

Protokolle und Einrichtungsstatus liegen unter `outputs/Emulator-*`. Downloadpakete werden von offiziellen Google-Adressen bezogen und anhand der Google-Paketmetadaten auf Größe und Prüfsumme geprüft. Der spezielle IPv4-Download verändert keine Windows-Netzwerkeinstellungen.
