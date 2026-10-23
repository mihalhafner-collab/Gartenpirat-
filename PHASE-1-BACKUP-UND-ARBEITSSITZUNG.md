# Phase 1 – Sicherung und persistente Arbeitssitzung

## Folgeauftrag Revier Solo,23.10.2026

Der nachstehende Stand vom11.10.2026 ist historischer Nachweis. Das lokale
Pixel-Ziel ersetzt die frühere Laptop-/Drive-Pflicht erst nach kontrollierter
Umstellung; kein alter Wiederanlauf darf eine zweite Schreibautorität aktivieren.

Aktuelle Implementierung/Nachweise stehen oben in `CODEX-STATUS.md` und im
konsolidierten `CODEX-AUFTRAG-GARTENPIRAT.md`. Der neue verschlüsselte Archivkern
ist noch keine vollständige integrierte Gerätesicherung oder Restorefreigabe.
Tink1.24.0 ersetzt kein vorhandenes Speichermodell; die lokale SQLite-Datei wird
nicht zwischen Geräten synchronisiert. Der Recovery-Code wird separat benötigt;
ohne alten Keystore und ohne separat aufbewahrten Code sind verschlüsselte
Sicherungen nicht rekonstruierbar. Niemals Code/Schlüssel in Logs oder Quellcode.

Containerformat1: neutrale Versionskennung und Tink-verschlüsselter Dateischlüssel
im Header; die gesamten ZIP-Dateien einschließlichManifest folgen als Streaming-
AEAD. Authentifizierte Headerbindung,1MiB-Kryptosegmente,64KiB-I/O-Puffer.
Manifestfelder: formatVersion/schemaVersion/appVersion/applicationId/backupId/
installationId/businessId/capturedAt/counts/numberState/lastIssuedDocumentIds,
vollständige Dateiübersicht mit Größe/SHA256, Gesamtbytezahl und Abschlussmarker.
Version/Hashes/Pfade/Dateimengen werden vor jeder möglichen Aktivierung geprüft;
die derzeitige Klasse aktiviert keinen Bestand. Eine gültige alte Sicherung ist
weiterhin **kein** aktueller Rechnungs-/Nummernnachweis. Nach tatsächlichem Restore
muss eine unabhängige persistente Ausgabesperre gelten; Integration noch offen.

Primärnachweise für die Bibliothek (gelesen23.10.2026):
https://repo.maven.apache.org/maven2/com/google/crypto/tink/tink-android/maven-metadata.xml
und https://github.com/tink-crypto/tink-java/tree/v1.24.0 ; insbesondere
StreamingAeadConfig2026, TinkJsonProtoKeysetFormat und AesGcmHkdfStreamingParameters.
Die Annotation-Fassade ist gezielt auf1.8.2 ausgerichtet, weil Tink annotation-jvm
1.8.2 benötigt und die bisherige1.2.0-Fassade sonst doppelte Klassen erzeugt.
Duplicate-Class-Prüfung und lintRelease bestehen nach dieser eng begrenzten
Ausrichtung; kein Wechsel des Java-/WebView-Frameworks.

Die allgemeinen developer.android.com-/developers.google.com-Seiten waren über
die konfigurierte Netzpolicy nicht lesbar; kein anderweitiger Gerätebeleg daraus.

Nutzerpräzisierung23.10.2026: Funktionen und maßgeblicher Speicher ausschließlich
Pixel/App; Drive dient ausschließlich als Sicherungsziel mit regelmäßigem
automatischem Upload aus der App. Die Umsetzung benötigt dauerhafte Jobzustände,
begrenzten Backoff und nachgewiesenes Remote-Rücklesen; lokale Übergabe an einen
Dateianbieter genügt nicht als Cloudnachweis. Offlinebetrieb bleibt unabhängig.
USB ist zusätzlicher Sicherungsweg. Alt-Sync erst kontrolliert stilllegen.

## Historischer Stand11.10.2026

Stand: 11.10.2026. Produktive Pixel-App weiterhin Version 1.28.3; der neue Entwicklungsstand wurde noch nicht produktiv installiert.

## Verifizierte Sicherungen

- Pixel-App-Datensicherung: `data/backups/pixel-usb/phase1-2026-10-11T14-53-15.tar`
- SHA-256: `855074c0df3082e94aa21b46b771af05ddcec9c98ae6632fef41d1b0efcd0a56`
- Prüfbericht: `data/backups/pixel-usb/phase1-2026-10-11T14-53-15-report.json`
- Temporäre Wiederherstellung der Pixel-SQLite-Datenbank: `PRAGMA integrity_check = ok`
- Laptop-Sicherungen: `data/backups/phase1/2026-10-11T14-53-15/`
- Laptop-Datenbank: Integrität `ok`, keine Fremdschlüsselverletzung, keine doppelten Primärschlüssel.

## Datenvergleich

| Bestand | Pixel-Snapshot | Laptop |
|---|---:|---:|
| Kunden | 44 | 44 |
| Aufträge | 1 | 1 |
| Arbeitsaufzeichnungen | 146 | 164 |
| Fotos | 12 | 12 |
| Rechnungen | 95 | 95 |

Die Differenz von 18 Arbeitsaufzeichnungen wird vor einer späteren Datenhoheits-Umschaltung einzeln über IDs und Synchronisationsbelege abgeglichen. Es wurde nichts automatisch gelöscht, kopiert oder neu zugeordnet. Auf dem Pixel liegen außerdem 62 gültig lesbare lokale Warteschlangen-/Belegzeilen; ungültige JSON-Zeilen: 0.

## Neu im Entwicklungsstand

- Nicht-destruktive Datenbankmigration von Version 1 auf 2.
- Neue getrennte Tabellen `work_sessions` und `work_session_events`.
- Zustände `RUNNING`, `PAUSED`, `STOPPED_PENDING_REVIEW`, `FINALIZED`; ohne Sitzung gilt `IDLE`.
- Start, Pause, Fortsetzen und Stoppen überleben Prozessende und Geräte-Neustart.
- Pausen werden nicht als Arbeitszeit gezählt.
- Ereignisse tragen eindeutige Anfragekennungen; Wiederholungen sind idempotent.
- Eine gestoppte Sitzung wird erst nach Benutzerprüfung in genau eine Arbeitsaufzeichnung überführt.
- Die daraus erzeugte Arbeitsaufzeichnungs-ID ist aus der Sitzungs-ID stabil abgeleitet; ein erneuter Abschluss erzeugt keine Dublette.
- Neue Start/Pause/Fortsetzen/Stopp-Oberfläche auf der Seite „Heute“ mit großen Schaltflächen.
- Der Prüfschritt öffnet die vorhandene Arbeitsmaske und übernimmt Datum und gemessene Dauer.
- Debug-/QA-App besitzt die getrennte Paket-ID `at.hausgarten.service.qa`; sie kann produktive Daten und Tunnel-Schlüssel nicht öffnen.

## Tatsächlich ausgeführte Tests

- Android QA-Build: erfolgreich.
- 24 isolierte Android-SQLite-/MCP-Tests auf dem echten Pixel: erfolgreich.
- Darunter Neustart-Fallback, Pausenrechnung, Idempotenz, genau-eine-Arbeitsaufzeichnung, bestehende MCP-Schreib- und Statuslogik.
- 8 echte UI-Schritte auf dem Pixel in der getrennten QA-App: Start, laufender Zustand, Pause, Fortsetzen, Stopp, Prüfansicht und Datenübernahme erfolgreich.
- 13 bestehende Browser-/CRUD-/Offline-/Sync-Regressionstests: erfolgreich.
- Vollständige bestehende Qualitätssuite: 101 von 101 Tests erfolgreich.
- Produktiver Pixel-Tunnel nach allen Tests: `ready`, Schreibfreigabe unverändert aktiv, Akku-Ausnahme aktiv.

## Noch nicht freigegeben / offen

- Keine Installation dieses Entwicklungsstands über die produktive App.
- Keine Umschaltung des Pixels zur alleinigen Datenquelle.
- Noch kein WorkManager-Hintergrundabgleich.
- Noch kein Standort-/Kundenvorschlag.
- Noch keine normalisierte Spiegelung aller Geschäftsentitäten in Pixel-SQLite.
- Live-Aufruf der in dieser Codex-Sitzung sichtbaren älteren Laptop-App meldete weiterhin einen nicht gesehenen Tunnel. Der neue Pixel-App-Eintrag muss separat live geprüft werden; beide Verbindungen bleiben unverändert getrennt.
- Android-17-Systemabbild für den Emulator ist lokal noch nicht vorhanden; die reale Geräteprüfung erfolgte jedoch direkt auf dem Pixel 10 Pro mit Android 17/API 37.
