# Gartenpirat: verbindliche Arbeitsregeln

Diese Regeln gelten für die neue Arbeitsbasis in `struktur-neu/` und für alle
beteiligten KI-Werkzeuge, Hauptagenten und Subagenten. Die bestehende Installation
und die bisherigen Quellpfade werden durch diese Übernahme nicht umgestellt.

## Oberste Entwicklungsprinzipien

**KISS und YAGNI:** Wähle die einfachste Lösung, die den konkreten Bedarf
vollständig, korrekt und wartbar erfüllt. Zusätzliche Dateien, Abhängigkeiten,
Abstraktionen und Schutzmaßnahmen brauchen einen konkreten Nutzen. Vorhandenes
bevorzugen, wenn es die Gesamtlösung vereinfacht. Keine vorsorglichen Funktionen.
Bedienablauf zuerst festlegen; erforderliche Technik daran ausrichten.

## Beginn und Freigabe

1. Bei jeder neuen Anfrage diese Datei und `AKTUELLER-STAND.json` erneut lesen.
2. Den tatsächlichen GitHub-Stand, Arbeitszweig und lokale Änderungen prüfen.
   Build113 ist die übernommene Grundlage, keine unveränderliche Zukunftsvorgabe.
3. Nur betroffene Fachregeln, Quelltexte und Prüfungen zusätzlich lesen.
4. Vor Änderungen den einfachsten Weg, eine sinnvolle Alternative und die
   Empfehlung kurz erklären. Ist kein sinnvoller anderer Weg vorhanden, sagen.
5. Dateien in GitHub erst nach ausdrücklicher Nutzerbestätigung anlegen, ändern
   oder löschen. Eine Freigabe umfasst die vorgestellte Änderung und ihre nötigen
   Prüfungen. Bereits erteilte Freigaben nicht erneut anfordern.

Diese Lesepflicht ist eine Arbeitsanweisung. Ein technischer Hook vor jeder
Anfrage wurde nicht eingerichtet und darf nicht als vorhanden behauptet werden.

## Informationen pflegen

- Eine zuständige Datei je Thema. Vor Neuanlage nach vorhandenen Inhalten suchen.
- Technischer Stand, aktueller Auftrag und offene Punkte stehen ausschließlich
  in `AKTUELLER-STAND.json`; Anleitungen und Fachregeln in den verlinkten Dokumenten.
- Bei Änderungen betroffene vorhandene Informationen im selben Auftrag anpassen.
  Erledigte Aufträge und überholte Aussagen nach Übernahme gültiger Inhalte löschen.
- Keine Statuschronik, zusätzlichen Archivordner oder externen Entwicklungsexporte
  anlegen. Alte Aufträge enthalten keine fortgeltende Ausführungsfreigabe.
- Tests und Diagnosen bis einschließlich Build100 nicht übernehmen. Weiterhin
  benötigte Build-, Prüf- und Fixture-Helfer bleiben unabhängig von ihrem Alter.
  Aktuelle Prüfungen müssen den tatsächlich bearbeiteten Quellstand betreffen.
- Ein Hauptagent führt Änderungen zusammen. Subagenten nur bei ausdrücklichem
  Auftrag oder geltender Delegationsanweisung einsetzen; mit begrenzter Aufgabe,
  diesen Regeln und eindeutigem Quellstand. Sie liefern Befunde mit Dateiverweisen.

## Bestehende App erhalten

- Paketkennung `at.hausgarten.service` und Originalsignatur erhalten.
- Nur öffentlichen Quellbestand und synthetische Testdaten verwenden. Betriebliche
  Dateien und Geheimwerte bleiben außerhalb des Repositorys und der Testdaten.
- Keine Installation, Datenmigration, Wiederherstellung oder Rechnungsfreigabe
  aus einem allgemeinen Entwicklungsauftrag ableiten.
- Ein Build oder synthetischer Test beweist keine Prüfung auf dem produktiven Pixel.
- Keine pauschale Bereitstellungs- oder Updatefreigabe aus alten Anweisungen
  übernehmen. Externe Veröffentlichungen benötigen einen eigenen Nutzerauftrag.

## Abschluss

Vor Erfolgsmeldung: Änderungen gegen den freigegebenen Umfang prüfen,
`python3 -B tools/check_current.py` ausführen und die zusätzlich betroffenen
Prüfungen nach `docs/entwicklung/BAU.md` durchführen. Dokumentation, offene Punkte
und unnötige Komplexität gegenprüfen. Nicht ausgeführte oder fehlgeschlagene
Prüfungen offen benennen; keinen vollständigen PASS daraus ableiten.

Nur eigene geprüfte Dateien gezielt committen, nach GitHub pushen und den exakten
Commit zurücklesen. Bei fehlender Sicherung den Auftrag nicht als gesichert melden.
