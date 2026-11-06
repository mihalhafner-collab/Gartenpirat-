# Gartenpirat: Arbeitsregeln

## Grundsatz: KISS und YAGNI

Jede Entscheidung folgt zuerst diesen beiden Regeln:

- **KISS:** die einfachste Lösung, die die konkrete Aufgabe vollständig löst.
  Flacher, direkter Code; wenige Dateien; kurze Texte; wenige Tipps.
- **YAGNI:** nur bauen, was jetzt gebraucht wird. Keine vorsorglichen
  Abstraktionen, Optionen, Abhängigkeiten, Dateien, Berichte oder Prüfrunden.

Bestehendes erweitern statt Neues anlegen. Bestehende Dokumente aktualisieren.
Steht eine Regel dieser Datei einer deutlich einfacheren Bedienung im Weg,
den Nutzen kurz als Vorschlag nennen; umgesetzt wird erst nach Freigabe.

## Ausgangspunkt

- Vor jeder Aufgabe `main`, diese Datei und `struktur-neu/AKTUELLER-STAND.json`
  lesen. Das Repository ist die einzige verbindliche Quelle; frühere Chats sind
  nur Material.
- Einzige Codebasis für TEST und PRODUKTIV: Kotlin/Compose unter
  `struktur-neu/android/app/src/main/`. Vorher
  `python3 -B struktur-neu/tools/check_release_baseline.py` ausführen; schlägt
  sie fehl, stoppen. Alte Java/WebView-Quellen nie wiederherstellen.
- Vor Geräteaktionen über `Haus & Gartenservice Pixel.get_capabilities` Paket,
  Version, Git-Commit und `source_sha256` der installierten App prüfen. Die
  installierte Version ist maßgeblich. Bei Lücken keine Geräteänderung.

## Feste Grenzen

- Paketkennung `at.hausgarten.service`, Originalsignatur und Backup-Format
  erhalten. SQLite-Schema nur mit ausdrücklichem Auftrag ändern.
- TEST: eigene Paketkennung, eigene Testdaten, bisherige Signatur.
- Geschäftsdaten, Fotos und ZIP-Sicherungen bleiben lokal auf dem Pixel. Keine
  Cloud-Pflicht. Google Drive nur für die beauftragte Verteilung originalsignierter APKs.
- Nur mit ausdrücklichem Auftrag: Installation, Migration, Wiederherstellung,
  Rechnungsausstellung mit echten Daten, APK-Verteilung, neue Branches oder Tags.
- Subagenten nur auf ausdrücklichen Auftrag.

## Abrechnung

- Zwei Arten auf denselben tatsächlichen Leistungen, Mengen und Preisen:
  **Rechnung** und **Labor clandestino** (nur diese Schreibweise).
- Rechnung: gewöhnliche österreichische Inlandsrechnung in Euro mit
  Kleinunternehmerbefreiung. Nach der Vorschau eigene lokale Nummer und
  unveränderliches Original-PDF. Labor: intern bestätigt, ohne Nummer und PDF.
- Der Abschluss ist die einzige Quelle für den Abrechnungsstand. Alte manuelle
  Markierungen bleiben als Altdaten lesbar.
- Ein Rechnungsentwurf wird aus den offenen Arbeiten des Empfängers vorbefüllt,
  Preise aus Stundensatz bzw. Leistungskatalog. In der Vorschau sichtbar und
  änderbar; „Ausstellen“ ist ein Knopf. Die Zustimmung zur elektronischen
  Rechnung wird einmal pro Kunde gespeichert.
- „Bezahlt“ mit einem Tippen (offener Betrag, heutiges Datum). Anzeige
  fertig → abgerechnet → bezahlt; Zuordnung, Beleg und Zahlung bleiben getrennt gespeichert.
- Steuerstatus nie aus Umsätzen ableiten oder selbst ändern. Keine sevDesk-
  oder Bankanbindung, keine automatischen Mahnungen, keine Anzahlungsrechnungen.
  Historische sevDesk-Daten nicht neu ausstellen.
- Originale, Abschlüsse, Zahlungen und nötige Historie vor Löschen schützen;
  keine Alterslöschung. Nach Restore neue Nummernserie; restaurierte Quellen
  vor erneuter Abrechnung gegen den Belegstand prüfen.

## Löschen und Speicher

- Löschen: ein Tippen, keine Rückfrage, kein Papierkorb. 5 Sekunden
  „Rückgängig“, danach endgültig samt ungeteilter Dateien. Zuordnungen intern
  bereinigen; Erfolg am wirksamen Datenstand prüfen.
- Keine Rückfallkopien. Funktionslose Arbeitsdateien (z. B. `full-backup-work`)
  entfernen. Laufende Zeiterfassung und fremde Geschäftsdaten bleiben geschützt.

## Oberfläche

Ziel: jede wichtige Aufgabe in höchstens zwei Tipps.

- **Roter Faden:** Kunde → Objekt → Anfrage → Angebot → Auftrag → Einsatz →
  Arbeit → Abrechnung → Zahlung. Jede Detailseite zeigt oben „Gehört zu“
  (Kunde, Objekt, Auftrag, Einsatz) als antippbare Chips; Verknüpfungen in beide Richtungen.
- **Objekt als Anker:** Arbeiten gehören zu einem Objekt. Das Objekt zeigt
  seinen Verlauf von der Anfrage bis zur Zahlung.
- **Planung:** ein Objekt „Einsatz“. Termin, Tour und Pflegeplan sind Ansichten
  (Tag, Woche, Karte); verschieben durch Ziehen.
- **Navigation:** Heute · Planung · Kunden · Abrechnung. Ein „+“-Knopf für
  alles Neue, eine Suche für alles.
- **Heute:** Einsätze des Tages mit Uhrzeit und „Start“, darunter nur Punkte
  mit Handlungsbedarf.
- **Abrechnung:** ein Bereich mit Offen, Abschlüsse, Summen.
- **Laufende Zeit:** Benachrichtigung mit Pause, Foto und Stopp; Schnellkachel
  und App-Verknüpfungen.
- **Farben im Theme:** Waldgrün = Handeln, Bernstein = Geld, Gelb = Prüfen,
  Ziegelrot nur für Löschen und Fehler. Keine Farbwerte im Feature-Code.
- **Bausteine:** Material-3-Standardkomponenten statt Nachbauten. Große Schrift
  muss funktionieren.
- **Texte:** kurz und deutsch. Keine technischen Zustände, keine Textwände;
  Hinweise einmal zeigen, danach als ⓘ.
- **Rückmeldung:** Erfolg als kurze Snackbar, Fehler am Ort. Leere Listen
  nennen die erste Aktion.
- **Eingabe:** passende Tastatur (Telefon, E-Mail, Zahl), Spracheingabe in
  Textfeldern, zuletzt Verwendetes oben.
- **Prüfungen:** unklare Vorgänge automatisch intern prüfen, ohne doppelte
  Ausführung. Nur echte Konflikte zeigen. Wiederherstellung als Hinweis, nicht als Sperre.

## Tests

- Testprogramme erzeugen eigene synthetische Daten und prüfen Fach-, Speicher-
  und Bedienwege samt Fehler, Wiederholung und erneutem Lesen.
- Notwendige Tests direkt ausführen. Emulator-Suiten bleiben isoliert.
- Auf dem Pixel dürfen gezielte Tests Betriebsdaten lesen und im Testumfang
  ändern; keine zusätzliche Sicherung als Voraussetzung. Eigene Testdaten
  danach entfernen. Betriebsdaten nie ins Repository, die Cloud oder CI.

## Abschluss

- `python3 -B struktur-neu/tools/check_current.py` und die für die Änderung
  nötigen Laufzeitprüfungen ausführen. Geräteidentität ist kein Funktionstest.
- Fehlende oder fehlgeschlagene Prüfungen nennen. Geänderten Code nie als
  installiert ausgeben.
- Nur eigene geprüfte Änderungen committen, pushen und zurücklesen. Ergebnis
  kurz melden; keine zusätzlichen Prüfberichte.
