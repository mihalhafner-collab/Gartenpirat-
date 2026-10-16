# Haus & Gartenservice – Pixel-First Baseline

Stand: 11.10.2026, App 1.28.3 (versionCode 45), Android target/compile SDK 37.

## Nachgewiesener Ist-Zustand

| Bereich | Maßgebliche Implementierung | Offline | MCP | Risiko / Abweichung zum Ziel |
|---|---|---:|---:|---|
| Android-Oberfläche | `android/app/src/main/assets/mobile.html` plus JS/CSS-Module | Ja | indirekt | Große, historisch gewachsene HTML-Datei; UI-Logik teilweise mehrfach ergänzt. |
| Native Android-Brücke | `MainActivity.DataBridge` | Ja | Nein | UI-Schreibvorgänge landen in derselben lokalen Warteschlange wie MCP, das ist eine gute Ausgangsbasis. |
| Lokale Datenbank | `haus-garten-mobile.db`, Tabelle `pending` | Ja | Ja | Enthält derzeit Warteschlange und MCP-Belege, noch nicht den vollständigen führenden Geschäftsbestand. |
| Geschäftsdaten auf Pixel | `drive_snapshot` in SharedPreferences plus `pending`-Overlay | Ja, nach vorherigem Abgleich | Ja | Noch kein echter SQLite-Master; vollständiger Snapshot ist nicht transaktionsnormalisiert. |
| Kunden/Aufträge/Arbeitsaufzeichnungen | Drive-Snapshot + lokale Aktionen | Ja | Lesen; Arbeitsaufzeichnungen schreiben | IDs werden erhalten; Kunden-/Auftragsschreibwerkzeuge fehlen im Pixel-MCP. |
| Fotos | Fotoaktion mit Daten/Referenzen | Ja | Lesen über Snapshot | Vollständigkeit und Wiederherstellbarkeit der Binärdateien muss gesondert geprüft werden. |
| Rechnungen | Snapshot, lokale Entwürfe und Rechnungsworkflow | teilweise | Lesen | Abrechnungsstatus darf bei Master-Umstellung nicht abgeleitet oder massenhaft verändert werden. |
| Android-Synchronisation | `MainActivity.performDriveSync` | Nur lokale Vormerkung | Status vorhanden | Läuft bei geöffneter App; kein WorkManager. Upload, Annahme und Bestätigung werden bereits teilweise getrennt. |
| Laptop-Synchronisation | `server.py` / `db.py` | Ja | Laptop-MCP | Laptop verarbeitet weiterhin Aktionen und ist damit noch Teil des Masterpfads. |
| Pixel-MCP | `PixelTunnelService`, `PixelMcpReadTools`, `PixelMcpWriteTools` | lokaler Snapshot | 11 Lese-/3 Schreiboperationen laut Zielstand | Live verbunden; dauerhafte Android-Erreichbarkeit bleibt von Doze/Netz abhängig. |
| Laptop-MCP | `mcp_integration/` | Nein, benötigt Drive-Dateien | Ja | Paralleler, historischer Zugriffspfad; während Übergang rückwärtskompatibel halten. |
| Zeiterfassung | Formularfelder in WebView | Ja | Arbeitsaufzeichnung direkt | Keine eigenständige persistente Arbeitssitzung für Start/Pause/Fortsetzen/Stopp. |
| Backup/Wiederherstellung | Drive-Dateien und vorhandene Prüfscripte | teilweise | Nein | Noch kein manifestiertes, transaktionskonsistentes Komplettbackup mit Restore-Test. |

## Datenfluss heute

1. Die Pixel-Oberfläche oder Pixel-MCP validiert eine Änderung.
2. Die Änderung wird als Aktion in SQLite `pending` gespeichert.
3. Die Anzeige bildet den Drive-Snapshot und die lokalen Aktionen zu einem effektiven Stand zusammen.
4. Der Drive-Abgleich schreibt mobile Aktionen und liest `HausGarten-Laptop.json` sowie ChatGPT-Aktionen.
5. Der Laptop verarbeitet Aktionen und bestätigt über `accepted_ids` beziehungsweise Fehlerlisten.

Damit ist das Pixel bereits offline schreibfähig, aber noch nicht alleinige führende Dateninstanz.

## Bereits vorhandene Schutzmechanismen

- Stabile fachliche IDs; keine Umnummerierung beim Bearbeiten.
- Transaktionale SQLite-Warteschlange für Arbeitsaufzeichnungen und MCP-Belege.
- MCP-Wiederholungsschutz über `request_id` und Inhaltsfingerabdruck.
- Prüfung von Kunden-/Auftragsreferenzen und Kunde-Auftrag-Konsistenz.
- Trennung zwischen lokal gespeichert, nach Drive übertragen und vom Laptop bestätigt.
- Synchronisationssperre gegen parallele Läufe innerhalb des Android-Prozesses.
- Rückgängig-Fenster für lokale Löschvormerkungen.
- Android-Foreground-Service für den privaten Pixel-Tunnel.

## Kritische Lücken

1. Keine persistente Arbeitssitzung mit Zustandsautomat und Ereignisprotokoll.
2. Geschäftsdaten liegen noch nicht vollständig und transaktionssicher als Pixel-SQLite-Master vor.
3. Keine Revisionsnummern/Tombstones über alle Entitäten und kein feldbezogener Konfliktmechanismus.
4. Hintergrundabgleich basiert noch nicht auf WorkManager.
5. Kein verifiziertes Komplettbackup mit Manifest und Wiederherstellungstest.
6. Standortermittlung und nachvollziehbare Kundenvorschläge fehlen.
7. WebView-UI ist funktional, aber durch nachträgliche Inline-Erweiterungen schwer wartbar.
8. Laptop- und Pixel-MCP bilden während des Übergangs zwei unterschiedliche Schreibpfade.

## Sichere Reihenfolge

1. Unveränderliche Baseline und vollständige Sicherung verifizieren.
2. Persistente Arbeitssitzung isoliert hinzufügen; bestehende Arbeitsaufzeichnung unverändert lassen.
3. Sitzung nach Stopp genau einmal in einen prüfbaren Entwurf überführen.
4. Erst danach Standort und Kundenvorschläge anbinden.
5. Geschäftsdaten schrittweise in normalisierte Pixel-Tabellen spiegeln und vergleichen.
6. Pixel erst nach Restore-, Sync- und Regressionstests per ausdrücklicher Freigabe zum Master erklären.

## Nicht automatisch auszuführen

- Produktive Datenmigration oder Umschaltung der Datenhoheit.
- Historische Zuordnungen, Rechnungsstatus oder IDs korrigieren.
- Bestehende Tunnel-/Plugin-Verbindungen ersetzen.
- Alte Sync-Dateien erneut importieren oder Löschvormerkungen anwenden.
