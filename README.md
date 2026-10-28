# Gartenpirat

Private Android-App `at.hausgarten.service`. Pixel/App führt die Geschäftsdaten;
MCP verwendet denselben lokalen Kern. Drive dient der verschlüsselten
App-Sicherung. Der aktuelle Quellkandidat ist **1.55.1 / Build 106**; zuletzt wurde
**1.55.0 / Build 105** vom Pixel gemeldet. Native UI-/Updateabnahme von 106 und
die produktive Integritäts-/Migrationsprüfung bleiben offen.

## Einstieg für Codex

Nur GitHub ist Entwicklungs- und Anweisungsquelle. Zuerst [AGENTS.md](AGENTS.md),
dann [Quell-/Gerätestand](AKTUELLER-STAND.json), [aktuellen Auftrag](CODEX-AUFTRAG-GARTENPIRAT.md)
und [Ergebnisse und offene Punkte](CODEX-STATUS.md) lesen. Historische Aufträge
werden nicht automatisch wieder aufgenommen. Keine externen Pages-/Drive-Regeln.

Für neue Codeaufträge gilt der Vorschlags-/Freigabeablauf aus
[AGENTS.md](AGENTS.md#pflicht-ablauf-vor-jeder-codeänderung). Bestehende Aufträge,
Prüfanleitungen und Starttexte ersetzen diese Freigabe nicht.

Entwicklung, Tests und Originalsignierung laufen in der bestehenden persönlichen
Gartenumgebung. Änderungen ausschließlich in diesem Repository committen, pushen
und zurücklesen. Keine Quellcode-/Statussicherungen in Drive; nach einem beauftragten
APK-Bau ist nur die [Bereitstellung des Installationspakets](downloads/README.md)
mit erforderlicher Installationsprovenienz erlaubt. Keine automatische Installation.

## Ordner und Zuständigkeiten

| Pfad | Zweck |
|---|---|
| `android/` | Operative App, native Fachpfade und Androidtests |
| `static/`, `assets/` | Gemeinsame Web-/Testoberfläche und öffentliche Assets |
| `libraries/plants/` | Pflanzenquellen, redaktionelle Nachweise und Datenwerkzeuge |
| `tests/` | Synthetische Regressionen, unveränderliche Baselines und Fixtures |
| `tools/` | Prüf-, Signier-, Quellnachweis- und Assetwerkzeuge |
| `docs/verification/` | Maschinenlesbare bzw. datierte technische Nachweise |
| `downloads/` | Hinweise zum vorhandenen Installationspaket, keine Quellkopien |
| Pythonmodule im Root und `mcp_integration/` | Von Tests/Kompatibilitätsprüfungen benötigte Hostkomponenten; kein zweiter produktiver Betrieb |

Die Spiegel der Pflanzenassets sind absichtlich getrennte Laufzeitpfade. Nur
`tools/sync_plant_assets.py` erzeugt sie; keine unabhängige Bearbeitung oder
Löschung als vermeintlich alte Kopie. Gleiches gilt für benötigte Legacy-Fixtures.

## Prüfung

- `python3 -B tools/source_preservation.py`: sauberer Commit auf GitHub.
- `python3 -B -c 'import privacy_guard as p; print(p.source_gate()); print(p.workspace_gate())'`
- `python3 -B -m unittest discover -s tests -p test_source_lifecycle.py`
- Für Appänderungen die passenden bestehenden Python-/JVM-/Browser-/Android-
  Regressionen gemäß Auftrag und Status; keine alten PASS als neue Prüfung ausgeben.
- [Originalsignierung](ORIGINAL-SIGNIERUNG-CLOUD.md) und APK-Privacy vor Auslieferung.

Nur leerer Bootstrap und klar synthetische Stores. Produktive Daten, Rohbackups,
Fotos, Schlüssel und Kontowerte gehören nicht in Quellcheckout, Tests oder Git.

## Herkunft und Historie

Originalquellen sind durch Git-Commits und die aufbewahrten Versionsreferenzen in
[source-releases.json](source-releases.json) nachweisbar. Aufbewahrungsgrenze und
Workflow stehen in [source-lifecycle.json](source-lifecycle.json). Ein Entfernen
überholter Arbeitskopien oder Referenzen schreibt die Git-Historie nicht um.

Die vollständige frühere Dokumentation liegt im festen
[Vorbereinigungsstand a41fc67](https://github.com/mihalhafner-collab/Gartenpirat-/tree/a41fc67f9e2d74fb3912c688b0f57cf7476ec598).
Sie ist Quellenmaterial, keine konkurrierende aktuelle Arbeitsanweisung.
