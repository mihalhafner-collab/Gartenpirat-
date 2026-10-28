# Gartenpirat – minimale Arbeitsbasis

Übernommen ist der freigegebene App-Quellstand **1.55.8 / Build113**. Der genaue
Ursprungscommit, die zuletzt belegte Installation und offene Prüfungen stehen im
[aktuellen Stand](AKTUELLER-STAND.json).

Diese Struktur ist die vorbereitete Parallelfassung. Die bisherigen Quellpfade,
GitHub-Workflows und die installierte App werden damit nicht automatisch ersetzt.

## Einstieg

1. [Arbeitsregeln](AGENTS.md)
2. [Aktueller Stand und Auftrag](AKTUELLER-STAND.json)
3. Je nach Aufgabe [Bau und Prüfungen](docs/entwicklung/BAU.md),
   [Originalsignierung](docs/entwicklung/SIGNIERUNG.md) oder
   [gültige Fachregeln](docs/fachregeln/APP.md)

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
