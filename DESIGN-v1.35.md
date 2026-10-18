# Freigegebenes Design – Version 1.35.0

Der grafisch vorgestellte Entwurf wurde nach Freigabe in die bestehende Android-App integriert. Keine zweite App, Datenbank oder Drive-Struktur wurde angelegt.

## Bedienung

- **Heute:** Stundenkennzahlen, Start/Pause/Stop, heutige Aufträge und letzte Arbeiten. Manuelle Nachträge über „Arbeit nachtragen“. Gartenpirat bleibt als kompakter Sprachzugriff erhalten; Logo bleibt vorhanden.
- **Kunden:** ruhige, durchsuchbare Liste. Der Plus-Button steht im Seitenkopf. Kundendetails, Anruf, Route, Profilbilder und zugehörige Arbeiten/Aufträge/Rechnungen bleiben erreichbar.
- **Arbeiten:** kompakte Liste mit bestehenden Such-/Zeitraum-/Kunden-/Abrechnungsfiltern. Tippen öffnet eine eigene Detailseite, nicht sofort ein langes Formular.
- **Mehr:** Projekte/Aufträge, Wochenstunden, Rechnungen und Drive/Verbindung. Keine Funktion wurde wegen der vereinfachten Startseite entfernt.
- **Detailseite:** gegliederte Arbeitsinformationen und vorhandene Fotominiaturen. „Bearbeiten“ bleibt am unteren Rand. „Weitere Aktionen“ öffnet das vorhandene Aktionsmenü auch ohne langes Tippen.
- **Bearbeiten:** zunächst Kunde, Datum, Stunden, Tätigkeit. Zeit/Pause, Material/Kilometer, Fotos/Notizen/Nachträge, Auftrag/Arbeitsort und Abrechnung/Kundenansicht sind aufklappbar. Bestehende Felder samt IDs und Ereignissen wurden umgeordnet, nicht neu erfunden.
- **Neue Arbeit:** gleiche kompakte Gliederung, bestehendes „Kunde hinzufügen“ und „Auftrag hinzufügen“ bleiben erhalten. Ein Auftrag ist weiterhin optional.
- **Langes Tippen:** vorhandene Bearbeiten-/Löschaktionen; bei Arbeitsaufzeichnungen zusätzlich „Duplizieren“. Dabei entsteht zunächst nur eine neue Eingabe mit heutigem Datum. Alte Datensatz-/Sitzungs-IDs, Fotos, Abrechnung und private Notizen werden nicht kopiert. Erst Speichern erzeugt den neuen Datensatz.

## Speichern, Verwerfen und Sicherheit

Während der Bearbeitung bestehender Arbeitsaufzeichnungen wird nicht mehr nach jeder kurzen Tipp-Pause eine Änderung abgeschickt. „Speichern“ oder Zurück sichert gültige Änderungen im vorhandenen lokalen Speicherkanal. Fehler verhindern den regulären Abschluss; die bestehenden Fehlermeldungen bleiben erhalten.

„Abbrechen“ verwirft noch nicht gespeicherte Änderungen und kehrt zur Übersicht zurück. Bereits explizit gespeicherte beziehungsweise beim Verlassen der App gesicherte Änderungen werden dadurch nicht rückwirkend gelöscht. Das bisherige Sichern beim Hintergrundwechsel/Pagehide bleibt erhalten. Neue Arbeitsaufzeichnungen bleiben bis zur bewussten Speicherung Entwürfe; „Abbrechen“ fragt vor dem Verwerfen nach.

Lokale Speicherung und erfolgreicher Drive-Abgleich werden nicht gleichgesetzt. Authentifizierung, Tunnelkonfiguration, MCP-Werkzeugnamen, native Validierungen, Primary Keys und produktive Relationen wurden nicht geändert. Keine Migration oder produktive Schreibprüfung durchgeführt.

## Gefundener zusätzlicher Fehler

Der neue Startseitenaufbau machte einen Doppeltipp-Fehler reproduzierbar: Der zweite Tipp nach der Kundenbestätigung konnte nach sofortigem Dialogschluss ein darunterliegendes Bedienelement aktivieren. Der Standortdialog bleibt jetzt kurz während des Übergangs bestehen; erneute Starts sind gesperrt. Wiederholung des vollständigen Standort-/Timer-Browsertests erfolgreich. Reale GPS-/Akku-/Gerätetests sind dadurch nicht ersetzt.

## Geänderte Komponenten

Android-Assets: workday-ui.js, design-concept.js, design-concept.css, order-window.js, work-location.js, mobile.html. android/app/build.gradle erhöht ausschließlich die App-Version auf 1.35.0 / Code 52. Neue Tests: tests/simple-design.test.cjs. Bestehende Browsertests wurden auf die freigegebenen Navigationselemente und aufklappbaren Felder abgestimmt; fachliche Prüfungen bleiben erhalten.

## Tatsächlich ausgeführte Tests

| Bereich | Methode | Ergebnis |
| --- | --- | --- |
| Neues Design, Verwerfen ohne Schreibvorgang, Duplizieren ohne alte ID, neue Kundenanlage, Bildschirmbreiten | Echter Chromium, isolierte Android-Testbrücke | 7 Szenarien erfolgreich |
| Detailseite, Speichern, ungültige Eingaben, Zurück-Hierarchie | Echter Browser, Testbrücke | 8 erfolgreich |
| Kundenprofil, Kontakte, Historie, Formularentwurf und Navigation | Echter Browser, Testbrücke | 11 erfolgreich |
| Filter, Galerie, Entwurfsschutz und Sitzungsreset | Echter Browser, Testbrücke | 10 erfolgreich |
| Standortvorschläge, Doppeltipp, Start/Pause/Stop, Abbrechen/Rückgängig | Regeln plus Browser mit simuliertem GPS | 11 erfolgreich nach Korrektur |
| Laptop-CRUD, Offline/Reload/Synchronisation und mobile Eingabefelder | Isolierter HTTP-Server plus mobile Testbrücke | 20 erfolgreich |
| Rechnungseditor/-fluss | JavaScript-Tests | 22 plus 12 erfolgreich |
| Backend und Synchronisationsregression | Isolierte Python-Suite | 106 erfolgreich, keine übersprungen |
| MCP-Prozess → Aktionsdatei → vorhandener App-Import → Snapshot | Echter lokaler MCP-Testclient, isolierte Dateien | 55 erfolgreich |
| APK-Build und ursprüngliche Signatur | Gradle, apksigner, aapt | Erfolgreich, Originalpaket at.hausgarten.service |
| Produktive Datenbank | Ausschließlich lesende Integritätsprüfung | Separater Beleg outputs/QA-Produktivdaten-readonly.json |
| Physischer Pixel, Tastatur/Hardware, GPS-Berechtigungen und Tunnel-Dauerbetrieb | Gerätetest | Nicht durchgeführt |

Die erste Erreichbarkeits-Testfassung versuchte noch, jetzt eingeklappte Zusatzfelder direkt anzutippen. Der Test wurde auf reales Aufklappen umgestellt; alle ursprünglichen Feld-/Fokusprüfungen sind erhalten. Alte Sofort-Autosave-Erwartungen wurden durch den freigegebenen Ablauf „Zurück speichert / Abbrechen verwirft“ ersetzt und mit neuen Tests abgesichert.

## Nachweise und Installation

Echte Browserbilder mit isolierten Beispieldaten:

- outputs/Design-v1.35-Heute.png
- outputs/Design-v1.35-Kunden.png
- outputs/Design-v1.35-Detail.png
- outputs/Design-v1.35-Bearbeiten.png

Tests: outputs/Design-v1.35-Tests.json, QA-Browser-results.json, QA-Python-results.json und QA-MCP-E2E.json. Ältere Testskripte behalten teilweise ihre historischen Ergebnis-Dateinamen; entscheidend sind der aktuelle Lauf und der tatsächlich getestete Quellcode, nicht der alte Dateiname.

APK: outputs/Haus-und-Gartenservice-Pixel-v1.35.0.apk. Bestehende Signatur, min SDK 26, target/compile SDK 37. Auf dem Pixel als Update installieren, ohne die alte App vorher zu deinstallieren. Neue APK im bestehenden privaten Drive-Ordner gespeichert; Größe und Ordnerzuordnung rückgelesen. Version 1.34 nach dieser Prüfung in vorhandene APK-Backups verschoben und Zielzuordnung bestätigt, nicht gelöscht.

APK-Größe: 2.640.390 Bytes. SHA-256: 50adbe24c2ba96fec9014c4c361e0b78f97abb6af5975e54a58044935598309e. Eingebettete geänderte Assets mit getestetem Quellcode verglichen: identisch. Produktive Datenbankprüfung: Integrität ok, keine gefundenen Fremdschlüsselverletzungen, Datei unverändert.

Offen bleiben reale Pixel-Tests, Tastaturüberlagerungen, dauerhaft erreichbarer Tunnel unter Energiesparen und die schon dokumentierte Laptop-Abhängigkeit der endgültigen Rechnungsausstellung. Diese Einschränkungen wurden nicht durch reine Designänderungen als gelöst behauptet.
