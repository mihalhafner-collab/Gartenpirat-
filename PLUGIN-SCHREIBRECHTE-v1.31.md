# Plugin-Schreibrechte – Version 1.31.0

Stand: 12.10.2026. Originalpaket `at.hausgarten.service`, Versionscode 48.

## Eingebaut

Zusätzliche Pixel-MCP-Werkzeuge: `create_customer`, `update_customer`,
`create_order`, `update_order`, `create_invoice_draft`, `update_invoice_draft`.
Die bestehenden drei Arbeitsaufzeichnungs-Schreibwerkzeuge behalten Namen,
Schemas und das Antwortfeld `work_record_id`.

Zusätzliche Lesezugriffe: `list_invoices`, `get_invoice`, `list_photos`.
Kunden, Aufträge und Rechnungen liefern bei Einzelabruf einen Vergleichshash.
Die Pixel-Freigabe bleibt abschaltbar; die Beschriftung nennt den erweiterten Umfang.

## Schutz

- Eindeutige request_id, gespeicherter Anfragefingerabdruck und SQLite-Transaktion.
- Wiederholungen verwenden dieselbe Datensatz-ID; geänderte Wiederholungen werden abgelehnt.
- Prüfung tatsächlich vorhandener Kunden und unveränderlicher Auftragszuordnung.
- Kunden-Dublettenprüfung einschließlich inaktiver Kunden und Auftrags-Dublettenprüfung.
- Veraltete oder bereits lokal vorgemerkte Bearbeitungen werden abgelehnt.
- Kunden und Aufträge verwenden beim Drive-Import die vorhandene atomare Vergleichsspeicherung.
- Rechnungsentwürfe prüfen zusätzlich Positionen und Zahlungsziel innerhalb der Rechnungstransaktion.
- Kleinunternehmerpositionen haben 0 Prozent Steuer. Beträge werden dezimal je Position gerundet.
- Neue Entwürfe erhalten eine Entwurfskennung; es wird keine endgültige Rechnungsnummer vergeben.
- Bestehende IDs, Datenbankschema, Tunnel-Identität, Schlüssel und Plugin-Registrierung bleiben erhalten.

Die vorhandene vollständige Pixel-Datenhoheit ist noch nicht umgestellt: Der bestehende
Drive-/Laptop-Abgleich bestätigt Aktionen weiterhin. Lokal gespeichert, übertragen und
vom Importer bestätigt bleiben verschiedene Status.

## Grenzen der Freigabe

Diese Version erweitert die oben genannten Fachfunktionen. Sie exponiert noch nicht
sämtliche Android-Bedienfunktionen. Fotoaufnahme/-upload, Timersitzungen, endgültiges
Löschen, Rechnungsabschluss/-versand und Zahlungsänderungen erhalten hier keine neuen
MCP-Werkzeuge. Bestehende App-Funktionen stehen weiterhin in der App zur Verfügung.

## Einrichtung

1. Die aktualisierte Laptop-App mit dem neuen Quellcode starten und Drive abgleichen.
2. Version 1.31.0 als Update der Original-App auf dem Pixel installieren.
3. Auf dem Pixel Drive abgleichen. Der exportierte Datenstand muss
   `entity_compare_and_save: true` bestätigen; andernfalls bleiben Stammdatenkorrekturen gesperrt.
4. Unter Drive → ChatGPT-Verbindung den erweiterten Schreibschalter prüfen und Verbindung starten.
5. Im bestehenden ChatGPT-Plugin die Werkzeugliste aktualisieren, sofern neue Werkzeuge nicht angezeigt werden.
6. Erst nach erfolgreichem Live-Abruf und einem kontrollierten Test ist die Live-Freigabe bestätigt.

Beim letzten USB-Check war nur der Emulator erreichbar. Das Pixel wurde nicht aktualisiert.
Der Live-Gesundheitstest des in diesem Chat verfügbaren Plugins meldete einen nicht
erreichbaren Tunnel. Eine erfolgreiche lokale Prüfung bestätigt keine Live-Verbindung.

## Ausgeführte Prüfungen

- Java: 64 Tunnel-/Protokolltests, 44 bisherige Schreibtests, 11 neue Fachfunktionstests erfolgreich.
- Python: fünf neue Drive-/SQLite-Importtests erfolgreich (Anlage, Wiederholung, Korrektur, Konflikt und Rechnungspositionen).
- Erste vollständige Regression: 105 von 106 Tests erfolgreich; HTTP-PDF-Test mit 5-Sekunden-Timeout fehlgeschlagen.
- Isolierter vollständiger Wiederholungslauf: 106 von 106 Tests erfolgreich, einschließlich HTTP-Rechnungsabschluss und PDF-Abruf. Der erste Timeout wurde ohne parallelen Build nicht reproduziert.
- Neue Werkzeuge bisher nicht auf dem produktiven Pixel oder über dessen Live-ChatGPT-Verbindung geprüft.

## Geänderte Komponenten

Android: PixelMcpEntityWriteTools, PixelMcpReadTools, MainActivity.LocalStore,
PixelTunnelActivity, PixelTunnelService und Versionskonfiguration.
Laptop: db.py und server.py (optionale Import-Vergleichsprüfung und Fähigkeitsnachweis).
Tests: Java-Vertrags-/Schreibtests, neuer EntityWrite-Test, Teststarter,
test_mcp_entity_writes.py. Keine produktiven Datensätze wurden testweise verändert.

## Testmatrix

| Funktion | Prüfung | Ergebnis | Live auf Pixel |
|---|---|---|---|
| Tunnelprotokoll, Lesezugriff, Mehrdeutigkeit | 64 echte Java-/HTTP-Vertragstests mit isolierten Daten | bestanden | derzeit nicht erreichbar |
| Bisherige Arbeitsaufzeichnungswerkzeuge | 44 Java-Schema-/Validierungstests und bestehende Python-Regressionen | bestanden | neue APK noch nicht installiert |
| Kundenanlage und -korrektur | echte Java-Aufbereitung, Drive-Importer und SQLite | bestanden | ausstehend |
| Auftragsanlage und -korrektur | echte Java-Aufbereitung, Referenzprüfung, Drive-Importer und SQLite | bestanden | ausstehend |
| Rechnungsentwurf anlegen und bearbeiten | Java-Aufbereitung, Berechnung, Rechnungsschutz, Drive-Importer und SQLite | bestanden | ausstehend |
| Doppelte Anfrage, ID-Kollision, veraltete Korrektur | Java- und SQLite-/Importer-Tests | bestanden | ausstehend |
| Gesamte vorhandene Python-Regression | 106 Tests, ausschließlich Wegwerfdaten | bestanden im Wiederholungslauf | kein Ersatz für Android-Geräteprüfung |
| Neue SQLite-MCP-Schreibpfade in Android | Quellcodeprüfung und Android-Kompilierung | noch kein Lauf auf dem Gerät | ausstehend |

Die Freigabe ist daher vorbereitet und lokal geprüft. Die vollständige Live-Abnahme
erfordert das wieder verbundene Pixel, die Installation und einen erreichbaren Pixel-Tunnel.

## Fertiges Release

- Endgültiger Android-17-/API-37-Release-Build erfolgreich.
- Paket `at.hausgarten.service`, Version 1.31.0, Versionscode 48.
- Signatur identisch mit der bisher installierten Original-App.
- APK: `outputs/Haus-und-Gartenservice-Pixel-v1.31.0.apk`, 2.615.534 Bytes.
- SHA-256: `08ff420071aa1af918223b1c21ce564c900ded49bfa729b73126b40836c48b5a`.
- Original-APK im vorhandenen Emulator installiert und MainActivity gestartet; Prozess läuft.
- Emulator hat API 35. Dies bestätigt keinen Android-17-Gerätetest.
- APK in den vorhandenen privaten Drive-Ordner hochgeladen; Zielordner und Größe per Metadaten geprüft.
- Drive-APKs 1.28.2 und 1.27.1 in den bereits vorhandenen Ordner APK-Backups verschoben und Elternordner geprüft. Beide bleiben wiederherstellbar.
- Produktives Pixel weiter nicht per USB erreichbar; keine Installation und keine Änderung seiner Berechtigung vorgenommen.

[APK in Google Drive](https://drive.google.com/file/d/1xShLR142pVjQaLwTA8JZh42p9tXGEftB/view?usp=drivesdk)
