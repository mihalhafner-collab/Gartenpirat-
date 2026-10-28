# Gartenpirat – minimale Arbeitsbasis

Die Arbeitsbasis wurde aus Build113 übernommen und seitdem weiterentwickelt.
Aktuelle Version, Quellstand, zuletzt belegte Installation und offene Prüfungen
stehen im [aktuellen Stand](AKTUELLER-STAND.json).

Diese Struktur ist die vorbereitete Parallelfassung. Die bisherigen Quellpfade,
GitHub-Workflows und die installierte App werden damit nicht automatisch ersetzt.

## Einstieg

1. [Arbeitsregeln](../AGENTS.md)
2. [Aktueller Stand und Auftrag](AKTUELLER-STAND.json)
3. Je nach Aufgabe [Bau und Prüfungen](docs/entwicklung/BAU.md),
   [Originalsignierung](docs/entwicklung/SIGNIERUNG.md) oder
   [gültige Fachregeln](docs/fachregeln/APP.md)

Das Projekt **Haus und Garten App Entwicklung** wird nach dem
[kurzen Projektablauf](docs/entwicklung/PROJEKT.md) als Gesprächs- und Planungsraum
eingebunden. Die Projektanweisungen sind laut Nutzer gespeichert. Eine automatische
GitHub-Synchronisierung oder Übernahme der Bauumgebung wird nicht vorausgesetzt.

| Pfad | Inhalt |
|---|---|
| `android/` | App, eingebettete Oberfläche und Android-Baukonfiguration |
| `static/`, `assets/` | Benötigte gemeinsame Webressourcen und erzeugte Spiegel |
| `libraries/plants/` | Pflanzenquellen, Bildrechte und notwendige redaktionelle Eingaben |
| `tools/` | Tatsächlich benötigte Build-, Pflege- und Prüfwerkzeuge |
| `tests/` | Ausgewählte aktuelle Prüfungen und ihre Hilfsdaten |
| `docs/` | Kurze gültige Anleitungen, Fachregeln und aktueller Releasebeleg |

MCP ist Bestandteil der nativen App. Ein separater alter Python-Geschäftsserver,
alte Diagnosekampagnen, Quellarchive und Installationspakete werden nicht kopiert.
Leere Gerüstordner werden erst bei tatsächlichem Bedarf befüllt.

Ändere bestehende Dokumente statt weitere Berichte anzulegen. Quellen und offene
Fragen bleiben anhand des Quellstands nachvollziehbar; alte Berichte sind keine
heutige Arbeitsanweisung.
