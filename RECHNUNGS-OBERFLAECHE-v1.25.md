# Rechnungsoberfläche 1.25 - 8. Oktober 2026

## Gezielte Überarbeitung

- Gemeinsamer Rechnungseditor für Web-App und Android: Kunde & Details, Leistungen, Vorschau.
- Auf breiten Displays Vorschau neben der Eingabe; auf kleinen Displays eigener Vorschau-Reiter. Große Felder, sichtbare Speichern-Aktion, kompakter Kopfbereich.
- Optische Vorschau auch für unvollständige und ungespeicherte Entwürfe. Logo, Empfänger, Rechnungsdaten, Leistungen, Beträge, Zahlungsziel und Fußzeile. Fehlende Angaben werden genannt. Interne Notizen werden nicht angezeigt.
- Die HTML-Layoutvorschau ist ausdrücklich kein finales PDF und nicht pixelgenau identisch mit dem PDF. Nummer wird erst beim Abschluss vergeben. Bestehende PDF-Erstellung und Abschlussprüfung unverändert; PDF-Prüfung des gespeicherten Entwurfs separat verfügbar am Laptop.
- Kompakte, nach Datum sortierte Rechnungsliste mit Kundenname, Betrag, Status, Bearbeiten und Vorschau. Suche und Statusfilter einschließlich verständlicher Leermeldung.
- Übernahme offener Arbeitsaufzeichnungen eines Kunden, Zuordnungsprüfung, positionsweise Betragsermittlung, optionale Beschreibung/Rabatt und Rückgängig für die letzte entfernte Position.
- Neue Kunden direkt aus dem Rechnungseditor. Beim Kundenwechsel keine Mitnahme fremder Arbeitsaufzeichnungen. Abbrechen der Kundenanlage erhält den Rechnungsentwurf.
- Zurück aus Vorschau führt zu Leistungen, dann zu Details, dann zum Schließen. Ungespeicherte Änderungen werden vor dem Verwerfen abgefragt. Endgültiger Abschluss/Versand bleiben gesonderte, bewusste Aktionen.
- Drive-Snapshot um kompatibles Feld invoice_issuer ergänzt, damit Android die bereits bestätigten Ausstellerdaten offline darstellen kann. Keine Schlüssel, keine Privat-/Servereinstellungen exportiert.

## Bewusst nicht verändert

Keine Datenmigration, keine Änderung vorhandener IDs oder Datensatzbeziehungen. Bestehende Rechnungen, Kunden, Aufträge, Arbeitsaufzeichnungen und Fotos unverändert. Datenbanksicherung: data/backups/pre-v1.25-layout.db. Kein automatisches Ausstellen, Versenden oder Abrechnen während der Entwicklung. Alte Löschaufträge weiter zurückgehalten.

Die App ist weiterhin kein vollständiger sevDesk-Ersatz für Buchhaltung, Bankabgleich, Storno, Teilzahlungen oder steuerliche Sonderfälle. Österreichische Kleinunternehmer-Regelung bleibt erhalten; deutsche Steuersätze aus der sevDesk-Anleitung werden nicht übernommen. Keine Behauptung gesetzlicher Zertifizierung.

## Prüfungen und Grenzen

13 isolierte Python-Funktionstests; gemeinsame Arbeitspositions-Prüfungen; 22 Vorschau-/Rundungs-/Eingabe-/Datenschutz-Prüfungen; Syntaxprüfung der sechs Android-Inline-Skripte. Gemeinsame Editor-Dateien auf Android und Web identisch.

Desktop-Rechnungsliste, vorhandener Entwurf, Leistungsbearbeitung und Live-Vorschau im Browser geprüft. Testpreisänderung nicht gespeichert. Produktionsdaten vor/nach der Änderung verglichen, unverändert.

Android-Oberfläche in einem getrennten Browser-Test mit den echten APK-Asset-Dateien und ausschließlich speicherinternen Demodaten geprüft: Live-Preisänderung von 150 auf 165 Euro, Position entfernen und rückgängig machen, Vorschau, schrittweises Zurück zur Leistungsansicht, Pflichtfeldprüfung, erfolgreiche Entwurfsspeicherung und Listenaktualisierung, Suche ohne Treffer und Filter zurücksetzen. Bei 412 und 360 Pixel Breite kein horizontales Überlaufen beobachtet. Die Änderung in der Testumgebung erzeugte keine produktiven Daten.

Diese Prüfung ersetzt keinen Test der installierten APK auf einem physischen Pixel. Echte Drive-Anbieter, Hardware-Zurück, Bildschirmtastatur und E-Mail-/WhatsApp-Übergabe müssen am Gerät geprüft werden. Der verbindliche Abschluss benötigt weiterhin den Laptop.

Android 1.25.0 (Versionscode 36) erfolgreich gebaut und Signatur geprüft. In Google Drive hochgeladen: https://drive.google.com/file/d/1Iuzv4M5_rQyX5rO2ytSrTPXFVQrY8rRO/view . Vorgängerversion 1.24.0 wiederherstellbar in APK-Backups verschoben, nicht gelöscht. SHA-256 der neuen APK: C4B139A3E0CAD1886D067B3E071FBB78E3AF8E47A93AB8AF85D929EDFCAAFEDA.

## Quellen für den Ablauf

- https://hilfe.sevdesk.de/de/articles/10117558-rechnung-erstellen
- https://hilfe.sevdesk.de/de/articles/11433804-rechnungen-in-der-sevdesk-mobile-app
