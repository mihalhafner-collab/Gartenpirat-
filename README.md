# Haus & Gartenservice / Gartenpirat

Private Eigentümer-App für Kunden, Aufträge, Arbeitszeit, Fotos, Rechnungen,
Angebote/Kostenvoranschläge und Einsatzplanung. Operative Android-App:
**at.hausgarten.service**, Quellstand **1.52.0 / Build 82**, MCP **9 / 117 Werkzeuge**.
Pflanzenbibliothek und Neophytenbereich bleiben Bestandteil derselben App.

[APK und geprüfte Originalsignatur](downloads/README.md) · [Aktueller Auftrag](CODEX-AUFTRAG-GARTENPIRAT.md) · [Abnahme und offene Grenzen](CODEX-STATUS.md)

## Verbindlicher Arbeitsweg

Entwicklung, Tests, Build und Originalsignierung ausschließlich in der privaten
Codex-Cloud-Umgebung Gartenpirat-. GitHub main ist die dauerhafte Codequelle;
Pixel ↔ Google Drive direkt ist der operative Datenweg. Kein Laptop, zweiter
Tunnel, paralleler Datastore oder zweite operative App als Voraussetzung/Fallback.
Historische Dateinamen bleiben kompatibel, ohne eine alte Runtime vorauszusetzen.

P0-Datentrennung und P1-Vertragsbaseline gelten dauerhaft. Im Workspace nur
Quellcode, öffentliche Katalog-/Markendaten, leerer Bootstrap und synthetische QA.
Host-Betriebsdaten ausschließlich über runtime_paths.py und einen ausdrücklich
konfigurierten externen HG_PRIVATE_DATA_ROOT; ohne ihn fail-closed. Keine privaten
Rohdaten/Backups, Fotos oder Secrets in Quellcode, Tests, Logs oder Artefakten.

## Bedienung und Schutzregeln

Heute, Kunden, Arbeiten und Mehr bilden die bestehende Navigation. Zurück geht
schrittweise; Entwürfe und laufende Zeiterfassung bleiben geschützt. Aufträge
sind bei Arbeitsaufzeichnungen optional. Persönliches b.b. ist vom Rechnungs- und
Abrechnungsstatus getrennt. Kundenakte, Fotobibliothek, Pflanzen, Planung und
Sync-Zentrale bleiben über die vorhandenen Menüs erreichbar.

Rechnung/Angebot/Kostenvoranschlag: Entwurf, Validierung, Finalisierung, Teilen,
tatsächlicher Versand und Zahlung sind getrennte Vorgänge. Keine erfundenen
Preise oder Rechnungsnummern. Bestätigte Unternehmensdaten bleiben zentrale
Quelle; finale Dokumente behalten eingefrorene Angaben und Original-PDF-Hash.
Papierkorb ist kein automatischer Purge; geschützte Dokumente und Beziehungen
bleiben erhalten. Aktuelle Hashes, stabile Request-IDs, Vorschauen, Bestätigungen,
Backups und Konfliktbelege sind verbindlich.

## Reproduzierbare Prüfung in Codex Cloud

- `python3 tests/run_quality_suite.py --exclude-qa-signing --artifact <Original-APK>`
- `python3 tools/run-followup-java.py` mit dem eingerichteten HG_JSON_JAR
- `python3 tests/run_browser_regressions.py` mit dem eingerichteten Chromium/Playwright
- `python3 tests/mcp_stdio_e2e.py`
- `python3 tools/check_refactoring_contract.py`
- `python3 tests/apk_privacy_guard.py --apk <Original-APK>`

Alle Schreibtests ausschließlich in kurzlebigen synthetischen Stores. Die
historischen Python-/Web-/Migrationskomponenten einschließlich db.py, server.py
und mcp_integration/ werden von Regressionen tatsächlich verwendet und bleiben
erhalten; sie sind keine produktive Betriebsanleitung. Produktive Import- oder
Reparaturskripte niemals als Tests starten.

## Freigaben und technische Grenzen

Originalsignierung ausschließlich nach ORIGINAL-SIGNIERUNG-CLOUD.md mit der
bestehenden Identität. Kein Ersatzschlüssel und keine QA-App als Updatebeleg.
Pixel-Updates nur mit Sicherung und ausdrücklicher Freigabe, ohne Deinstallation
oder Datenlöschung. Google-/Android-/Tunnelrechte und physische Kamera,
Hintergrundbetrieb, Hardware-Zurück und externe Share-Apps bleiben gesonderte
Geräte-/Providerprüfungen. Ein Build oder Provider-Readback beweist keine
vollständige Cloud-, Hardware- oder Datenerhaltabnahme.

P2-Archiv-/Backup-/Medienklassifikation und verbleibende Grenzen stehen im
aktuellen CODEX-STATUS.md. Historische Integrationsnachweise bleiben unter
INTEGRATIONSNACHWEIS-2026-10-18.md; verbindliche Regeln unter AGENTS.md.
