# Zwischenbericht – Etappen1 und2,28.10.2026

Auf ausdrücklichen Nutzerwunsch vor Fortsetzung der Etappen3–5. Bezugsstand:
Original-APK1.55.4/109, Quellcommitac67b156d8bc17bc07722f387a1c7a392b865176.

## Was umgesetzt und selbst geprüft wurde

Etappe1: begrenzte native Zeit-/Antwortgrößenmessung für getState/ownerCall/
getWorkSession und UI-Zeitmessung für render/Seitenwechsel/Start. Nur technische
Zahlen im Arbeitsspeicher, keine Geschäftsdatenprotokolle. Etappe2: zweiter
expliziter Stateaufbau durch getSyncStatus im load-Paar entfernt; vollständige
Warn-/Hintergrundfelder erhalten; zustandsabhängiger Cache und aktive Listen.
Anlage, Änderung, Löschung, native Änderungsmeldung und Entwurfswiederaufnahme
nachgetestet. Unveränderter Tabwechsel:0State-/Owneraufrufe und0Neusortierungen.

## Messung – Cloud, keine Pixel-/SQLite-Zeitmessung

Je40 Stichproben mit300 synthetischen Kunden,5000 Arbeiten,3000 Fotometadaten und
500 Rechnungen;360×840 CSS-Pixel, Cloud Chromium mit simulierten nativen Bridges.
Vorher ist die instrumentierte Etappe1, nachher Etappe2. Alle Zeiten in ms.
Langsamste5% = Mittelwert der zwei langsamsten Stichproben. Gemeinsamer Cloudhost,
warmer Browsercache und130ms-Wartezeit bei der Suche begrenzen die Aussage.

| Bedienweg | Vorher Mittel | Nachher Mittel | Vorher langsamste5% | Nachher langsamste5% |
| --- | ---: | ---: | ---: | ---: |
| App-Start | 1823.3 | 1458.2 | 3107.6 | 1754.7 |
| Heute → Büro | 279.1 | 179.3 | 529.8 | 244.6 |
| Büro → Heute | 268.2 | 181.1 | 998.2 | 285.0 |
| Kunden öffnen | 368.4 | 292.4 | 602.1 | 355.7 |
| Suche | 157.8 | 150.9 | 209.2 | 164.5 |
| Speichern | 1003.0 | 757.1 | 1882.9 | 1012.0 |

## Tests und Paket

- 71 Python-Schutztests und82 JVM-Suiten PASS; aktuelle Browserprüfungen einschließlich
18 Kontext-/Cachegruppen,13 Startgruppen und finaler Kunden-Navigation PASS.
- Abschließend84 Registrierungs-/Versionssperren-/Quellsicherungstests PASS.
- Releasebau, Lint0Fehler/103Warnungen, Originalsignaturv2/v3,16KiB-Ausrichtung,
705 quellenidentische Assets,205 aktuelle Produktionsklassen und leerer Bootstrap PASS.
- Quell-/Worktree-/APK-Privacy bestanden. Originalsignierte APK109 auf GitHub
unter downloads gesichert; Metadaten/Commit/Blob und SHA256-Bindung zurückgelesen.

## Offen und Abgrenzung

Pixel-Leistung, native SQLite-Laufzeit, physische Bedien-/Update-/Datenerhalts-
abnahme und echter Backup/Restore sind nicht geprüft. Historisches P1-Tools-
Schemabasisgate und alte Build97-Zwei-Tab-/Dock-Fixture bleiben FAIL; aktuelle
169-Tool-/212-Aktions-Parität und Drei-Tab-/200%-Prüfungen sind separat PASS.
Ein Build oder synthetischer Test ist keine Gerätefreigabe.

Nutzer meldet jetzt eine nicht startende oder hängende Backupfunktion. Damit ist
Backup/Restore ausdrücklich nicht als erfolgreich bestätigt. Vor produktiver
Tabellenübernahme und geschützter Altlastenentfernung muss dieser Fehler behoben
und der Gerätepfad bestätigt werden. Keine Freigabeflags, Installation oder
Migration ausgeführt. Etappe3-Quellplan durch Folgeauftrag freigegeben; operative
Gates bleiben erhalten.

Einzelbelege: performance-stage1-2026-10-28.md, performance-stage2-2026-10-28.md,
performance-stage2-tests-2026-10-28.json und release-109-2026-10-28.json im selben Ordner.
