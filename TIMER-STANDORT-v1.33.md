# Zeiterfassung mit Standort – Version 1.33.0

## Bedienung

1. Auf Heute auf Start tippen. Die App fragt bei der ersten Nutzung nach Standortzugriff während der App-Nutzung.
2. Die aktuelle Position wird einmalig geprüft. Bei verfügbarer Android-Adressauflösung erscheint die ermittelte Adresse.
3. Passende vorhandene Kunden werden vorgeschlagen, aber niemals automatisch zugeordnet. Kunden auswählen, Arbeitsort prüfen und Für diesen Kunden starten drücken. Erst diese Bestätigung startet die Zeit.
4. Kein passender Kunde? + Neuen Kunden anlegen öffnet das vorhandene Kundenformular mit einer überprüfbaren Adressvorlage. Namen selbst ergänzen; die Zeit startet nach erfolgreicher Kundenanlage.
5. Bei fehlender Berechtigung, ausgeschaltetem Standort oder fehlender Adresse weiterhin einen Kunden manuell auswählen. Ohne Kunde starten ist eine ausdrücklich gewählte Alternative; vor dem endgültigen Speichern muss ein gültiger Kunde vorhanden sein.
6. Pause, Fortsetzen und Stop benutzen weiterhin den bestehenden offlinefähigen Timer.
7. Angaben ergänzen übernimmt bestätigten Kunden, Arbeitsort, Datum und Netto-Arbeitsstunden. Bereits ergänzte Angaben bleiben bei erneutem Öffnen erhalten. Andere begonnene Angaben werden nicht ohne Rückfrage überschrieben.
8. Zeiterfassung löschen verwirft nur die noch nicht abgeschlossene Sitzung nach Bestätigung. Gespeicherte Arbeitsaufzeichnungen werden nicht gelöscht. Rückgängig innerhalb der bestehenden 8-Sekunden-Anzeige stellt die Sitzung pausiert wieder her; während der verworfenen Zeit wird keine Arbeitszeit nachgezählt.

## Änderungen und Fehlerbehebung

- Neue WorkLocationController-Klasse: einmalige Vordergrund-Ortung über Android LocationManager, begrenzte Wartezeiten, Abbruch bei geschlossener Auswahl/zerstörter Activity, asynchrone Adressauflösung.
- ACCESS_COARSE_LOCATION und ACCESS_FINE_LOCATION im Manifest; keine Hintergrund-Standortberechtigung, kein Standort-Foreground-Service, keine Bewegungsaufzeichnung.
- Standort-Kundensuche vergleicht Straße, Hausnummer, PLZ/Ort und gegebenenfalls Land; genauere Adressübereinstimmungen zuerst. Nur aktive Kunden vorgeschlagen. Ungefährer Standort oder Genauigkeit über 100 m: keine Adressvorschläge, manuelle Auswahl bleibt möglich.
- Kein Entfernungsversprechen: Kundendaten enthalten keine bestätigten GPS-Koordinaten. Die Vorschläge beruhen auf der von Android aufgelösten Adresse/Straße, nicht auf einer erfundenen Meter-Entfernung.
- Kunden-IDs werden aus dem vorhandenen Bestand einschließlich lokaler Kunden übernommen. Native Validierung verhindert erfundene Kunden-IDs und widersprüchliche Kunde-Auftrag-Paare beim Timer-Start.
- WorkSessionStore: CANCELLED-Zustand und DISCARD/RESTORE-Ereignisse statt physischer Löschung; vorhandene IDs und Ereignisse bleiben erhalten. Wiederherstellung neben einer neuen aktiven Sitzung wird verweigert.
- Arbeitsort wird im bestehenden START-Ereignis gespeichert und beim Abschluss in das vorhandene location-Feld übernommen. Kein neues produktives Datenbankschema, keine Migration, keine historische Neuzuordnung.
- Native Zeitquelle verwendet die Android-Neustartkennung statt einer schwankenden Differenz aus Systemzeit und Laufzeit. Bereits vor dem Update laufende Sitzungen nutzen bis zum nächsten Segmentwechsel ihren alten Neustartmarker mit vorhandener Zeit-Fallback-Regel.
- Timer-Abschluss und neue Arbeitsaufzeichnung committen gemeinsam in der bestehenden SQLite-Transaktion. Fehler beim Finalisieren nehmen auch die vorgemerkte Arbeitsaufzeichnung zurück.
- Reproduzierter bestehender Kundenformularfehler behoben: Das benannte ID-Eingabefeld überschattet form.id. Die Ereignisprüfung verwendet nun getAttribute('id'), damit der zuständige Speicherhandler und Anschlussaktionen ausgeführt werden. Der falsche ältere Fallback-Speicherweg wird nicht mehr benutzt.
- Vorhandene MCP-Werkzeugnamen, Tunnel-IDs, Zugangsdaten und Drive-Datenstrukturen unverändert.

## Testnachweise

| Bereich | Tatsächlich ausgeführt | Ergebnis |
| --- | --- | --- |
| Standortregeln und komplette Timer-Oberfläche einschließlich Neuer Kunde, Abbrechen, Rückgängig, Nachträge | Chromium mit isolierten Android-/GPS-Testantworten | 11 bestanden, einschließlich erneutem Öffnen ergänzter Timer-Angaben |
| Bestehendes Design/Kundenprofile/Filter | Chromium mit isolierter Android-Bridge | 11 bestanden |
| Arbeitsaufzeichnungen: Detail, Bearbeitung, Speichern, Zurück | Chromium mit isolierter Android-Bridge | 8 bestanden |
| Rechnungen: Vorschau/Rundung/Eingaben | JavaScript-Tests | 22 bestanden |
| Rechnung aus Arbeitsaufzeichnungen | JavaScript-Tests | 12 bestanden |
| Bestehende MCP/Tunnel-Schnittstellen | Kompilierte Java-Vertrags-/Schema-/HTTP-Tests | 64 + 44 + 11 bestanden |
| Timer, Kunden-/Arbeitsort-Persistenz, Verwerfen/Undo, Rollback, keine Duplikate | Originalpaket im Android-Emulator, echter SQLite-Zugriff in separatem Testnamensraum | 32 bestanden |
| Backend/Import/Datenintegrität/API/Synchronisation | Python-Tests mit temporären Daten | 106 bestanden |
| Echte Pixel-GPS-Messung, Standortberechtigungsdialog, Qualität der realen Adresse | Kein physisches Pixel angeschlossen | Nicht getestet |
| Live-Drive-Übernahme einer neuen GPS-Arbeitsaufzeichnung / Live-ChatGPT-Tunnel | Keine produktiven Schreibtests | Nicht in diesem Prüflauf getestet |

Emulator: Android 15 / API 35. APK compile/target: Android 17 / API 37. Ein API-35-Emulatortest ist kein Android-17-Gerätetest. Das Prüfwerkzeug ist keine zweite Betriebs-App; die Original-App wurde nur im Emulator aktualisiert. Das physische Pixel wurde nicht installiert, deinstalliert oder zurückgesetzt.

Protokolle: outputs/Timer-Standort-v1.33-Tests.json, outputs/Timer-Native-v1.33-Tests.txt, outputs/QA-Python-results.json und bestehende Pixel-MCP-/Tunnel-Testprotokolle.

## Datenschutz, Grenzen und Einrichtung

- Adressauflösung durch Android kann eine Netzwerkverbindung und den Systemanbieter benötigen. Offline bleibt die Zeiterfassung einschließlich manueller Zuordnung verfügbar; eine automatisch erkannte Adresse ist offline nicht garantiert.
- Es wird kein kostenpflichtiger Karten-API-Schlüssel oder neues Abo eingebaut.
- GPS-Position ist ein Vorschlag. Straßen-/Hausnummernfehler, mehrere Kunden an derselben Adresse und abweichende Rechnungs-/Arbeitsadressen müssen vom Benutzer geprüft werden.
- Neue APK als Aktualisierung installieren, bestehende App nicht deinstallieren. Wenn möglich, eine laufende Zeiterfassung vorher stoppen und speichern.
- Anschließend Start drücken, Standortzugriff bei Nutzung erlauben und für gute Vorschläge einen präzisen Standort wählen. Ablehnen bleibt zulässig; dann manuell auswählen.

Offizielle Grundlagen: [Android-Standortberechtigungen](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime), [LocationManager](https://developer.android.com/reference/android/location/LocationManager), [asynchrone Adressauflösung](https://developer.android.com/reference/android/location/Geocoder.GeocodeListener).

## Finale APK und verbleibende Emulator-Einschränkung

- Originalpaket at.hausgarten.service, Version 1.33.0 / Code 50; compile/target 37. Build erfolgreich; bisherige AndroidX-/SDK-XML-/Gradle-Kompatibilitätswarnungen bleiben.
- Signatur identisch zum bisherigen Update-Schlüssel. Alle fünf geänderten Oberflächendateien aus der final signierten APK per SHA-256 mit den getesteten Quellen abgeglichen: identisch.
- APK 2.636.294 Byte, SHA-256 3b856ae253f108883ebe01cc252017063bc4c2a43a941c6bb3bd58cd0cdb5a33.
- In bestehendem Drive-Ordner Haus & Garten App gespeichert; Größe und Elternordner durch Metadaten bestätigt.
- Download: https://drive.google.com/file/d/1fHBDLV7vt5RWEA3sZv0PXV53D_Tp0_wU/view?usp=drivesdk
- APK 1.32.0 in bestehenden Ordner APK-Backups verschoben; Elternordner zurückgelesen. Keine APK endgültig gelöscht.
- Finale APK im Emulator installiert. MainActivity-Prozess und resumed Activity bestätigt; Startwartefrist überschritten. Screenshots zeigen die App 1.33.0, jedoch überlagert von Android-Systemmeldungen: System UI und Pixel Launcher reagieren nicht. Ein Launcher-ANR ist bereits vor diesem App-Start dokumentiert. Die Ursache der Emulatorhänger ist nicht abschließend ermittelt; deshalb keine uneingeschränkte native UI-/Performance-Freigabe.
- Kein AndroidRuntime-Absturz im betrachteten App-Protokoll. WebView meldete Signatur-/Cache-Initialisierungsfehler im Emulator; nicht als fehlerfreier Lauf dargestellt.
- Ergänzender Browser-Smoke-Test mit unverändertem, in der App enthaltenem initial-data.json, rein lesend: Dashboard, Kundenprofil, Arbeitsliste und Laufzeitfehlerfreiheit bestätigt. Protokoll outputs/Current-Data-UI-v1.33.json. Kein produktiver Datenbank-/Drive-Schreibzugriff.

Geänderte Hauptdateien: MainActivity.java, WorkSessionStore.java, neuer WorkLocationController.java, AndroidManifest.xml, app/build.gradle, mobile.html, workday-ui.js, design-concept.js sowie neue work-location.js/.css. Tests: work-location.test.cjs, current-data-ui-smoke.cjs, Erweiterung PixelMcpInstrumentation.java und tests/emulator-release.init.gradle. Bestehende Produktivdaten, IDs, Tunnel-Konfiguration und Drive-Datendateien unverändert.
