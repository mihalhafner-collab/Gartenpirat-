# Fachregeln

Ergänzt AGENTS.md um die fachlichen Einzelheiten. Bei Widerspruch gilt AGENTS.md.

## Grundsätze

- Die App führt die Geschäftsdaten lokal auf dem Pixel. Oberfläche und MCP
  verwenden dieselben transaktionalen Fachpfade.
- Kunde (Einsatzkunde), Objekt und Rechnungsempfänger sind getrennte Beziehungen.
  Arbeiten ohne Auftrag bleiben möglich.
- Fehlende Daten, Preise, Zeiten, Beziehungen und Belegnummern nie erfinden.
  Fehlende Beträge sind unbekannt, nicht 0 €.
- Unklare Antwort: dieselbe Vorgangskennung mit denselben Argumenten erneut
  senden; sie liest das Ergebnis und bucht nicht doppelt.

## Abrechnungsübersicht

- Rechnung und Labor clandestino haben getrennte Summen und eine gemeinsame
  Gesamtsumme (900 € + 200 € = 1.100 €). Die Gesamtsumme ist keine Zahlungssumme.
- Ein Auftrag erscheint höchstens einmal; Arbeiten ohne Auftrag sind eigene
  Einheiten. Jeder Positionsbetrag zählt genau einmal bei seiner Quelle; Auftrag
  und seine Arbeiten nie doppelt addieren. Sammelrechnungen nur anteilig zurechnen.
- Widersprüchliche Zuordnungen werden zur Prüfung gezeigt und keiner Art zugerechnet.
- `accounting_amount` ist ein dezimaler Eurobetrag. Zeitbezug ist das letzte
  berücksichtigte Arbeitsdatum, ohne Arbeit das geplante Auftragsdatum.
- Alte Markierungen (`billed`, `personal_paid`) bleiben lesbar; Abschluss,
  Zahlung und Storno ändern sie nicht automatisch.

## Rechnung und Labor clandestino

- Zwei Arten auf denselben tatsächlichen Leistungen, Mengen und Preisen:
  **Rechnung** und **Labor clandestino** (nur diese Schreibweise). Der Abschluss
  ist die einzige Quelle für den Abrechnungsstand.
- Ein Entwurf wird aus den offenen Arbeiten des Empfängers vorbefüllt; Preise aus
  Stundensatz bzw. Leistungskatalog. In der Vorschau sichtbar und änderbar;
  „Ausstellen“ ist ein Knopf. Die Zustimmung zur elektronischen Rechnung wird
  einmal pro Kunde gespeichert.
- „Bezahlt“ mit einem Tippen (offener Betrag, heutiges Datum). Anzeige
  fertig → abgerechnet → bezahlt; Zuordnung, Beleg und Zahlung bleiben getrennt.
- Österreichische Inlandsrechnung in Euro mit Kleinunternehmerbefreiung. Keine
  Umsatzsteuer berechnen oder ausweisen. Hinweis genau:
  „Umsatzsteuerbefreit gemäß § 6 Abs. 1 Z 27 UStG“.
- Abschluss braucht Aussteller, Empfänger, Leistungsdatum oder -zeitraum,
  verständliche Positionen, positive Mengen, bestätigte Preise und Zahlungsangaben.
  UID und Steuernummer nicht pauschal verlangen.
- Geld mit `BigDecimal`: jede Position auf Cent mit `HALF_UP` runden, dann exakt
  addieren. Deutsches Dezimalkomma zulässig. Kein `Double` für Geld.
- Alle Quellen einer Sammelrechnung haben denselben Empfänger. Material, Fahrt
  und Entsorgung werden bewusst übernommen. Ein Angebot ist keine Leistung.
- Zwei Schritte mit gleicher `settlement_id`: `prepare_settlement` friert Inhalt
  und Stammdaten ein, bindet die Quellen und reserviert bei Rechnung die Nummer;
  `complete_settlement` erzeugt und prüft das Original-PDF. Labor ohne Nummer
  und ohne PDF. Ein PDF-Fehler lässt die Reservierung zum Fortsetzen bestehen.
- Nummernkreis GP, getrennt von sevDesk; Kalendertage in `Europe/Vienna`.
  Reservierte Nummern werden nie neu vergeben, auch nicht nach Aufhebung.
- Aktive Bindungen verhindern doppelte Abrechnung derselben Leistung. Eine
  nicht abgeschlossene Vorbereitung darf begründet aufgehoben werden (`abandoned`).
- Storno: eigene Nummer mit Originalverweis; erst der abgeschlossene Beleg gibt
  die Quellen frei. Labor wird mit begründetem internem Gegenvorgang aufgehoben.
- Zahlungen sind einzelne datierte Beträge; Teil- und Überzahlungen bleiben
  nachvollziehbar. Storno löscht keine Zahlung.
- Ledger in `owner_state` unter `settlement:v1`. Nach Restore beginnt eine neue
  Nummernserie aus Installation und Restore-Epoche; restaurierte Quellen vor
  erneuter Abrechnung gegen den Belegstand prüfen.
- Originale, Abschlüsse, Zahlungen und Historie sind vor Löschen und
  Speicherbereinigung geschützt. Aufbewahrung mindestens sieben Jahre ab
  Jahresende; keine Alterslöschung.
- Export enthält beide Arten, Summen, Positionen, Zahlungen, Korrekturen und die
  verwendeten Filter.
- Nicht unterstützt: Ausland, Reverse Charge, anderer Steuerstatus, strukturierte
  Rechnungen an Bundesdienststellen, automatische Mahnungen, Anzahlungsrechnungen.
  Historische sevDesk-Daten nicht neu ausstellen. Teilen eines PDFs beweist keinen Zugang.

Quellen (gelesen 06.11.2026): USP/BMF zu
[Kleinunternehmen](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/weitere-informationen-zur-umsatzsteuer/weitere-steuertatbestaende-und-befreiungen/kleinunternehmen.html),
[Formerfordernissen](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/weitere-informationen-zur-umsatzsteuer/vorsteuerabzug-und-rechnung/formerfordernisse.html),
[e-Rechnung](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/weitere-informationen-zur-umsatzsteuer/vorsteuerabzug-und-rechnung/e-rechnung.html),
[Aufbewahrung](https://www.usp.gv.at/themen/steuern-finanzen/steuerliche-gewinnermittlung/weitere-informationen-zur-steuerlichen-gewinnermittlung/betriebliches-rechnungswesen/aufbewahrungspflicht.html),
[Registrierkassen](https://www.usp.gv.at/themen/steuern-finanzen/steuerliche-rechte-und-pflichten/registrierkassen.html);
[UStR 2000 Rz 1533/1534](https://findok.bmf.gv.at/volltext?dokumentId=e24c9c94-4cc7-4241-a016-524f51f880e3).

## Zeit und Arbeit

- „Zeit stoppen“ legt noch keine Arbeit an; die gestoppte Zeit bleibt mit
  Abschlussaktion sichtbar.
- Netto-Dauer und Uhrzeiten sind getrennte Eingabewege; eine Pause wird nur bei
  Uhrzeiten und genau einmal abgezogen.
- Entsorgung ist ein optionaler Eurobetrag (`disposal_cost`), ohne automatischen Preis.
- Geplante Einsätze erzeugen keine Arbeiten. Eine bestätigte Arbeit bleibt auch
  bei einem späteren Foto- oder Sicherungsfehler erhalten.

## Löschen und Speicher

- Löschen: ein Tippen, keine Rückfrage, kein Papierkorb. 5 Sekunden
  „Rückgängig“, danach endgültig samt ungeteilter Dateien. Zuordnungen intern
  bereinigen; Erfolg am wirksamen Datenstand prüfen.
- Keine Rückfallkopien. Funktionslose Arbeitsdateien entfernen.
- Ein unberührtes Formular erzeugt keinen Entwurf. Verwerfen entfernt nur den
  eigenen Entwurf und seine temporären Fotos.
- Beim Löschen von Auftrag oder Termin werden Bezüge an Arbeiten, Fotos, Einsätzen
  und Anfragen gelöst; diese Datensätze bleiben.
- Gesperrt bleiben: ausgestellte Rechnungen, abgeschlossene Angebote, laufende
  Zeiterfassung. Bei markierten Testeinträgen löscht endgültiges Löschen auch
  zugehörige Testarbeiten und Testfotos.
- „Speicher bereinigen“ schreibt den wirksamen Stand fest, entfernt erledigte
  Journalaktionen, verwaiste Medien und Arbeitsordner und führt danach
  WAL-Checkpoint und VACUUM aus. Neue Formularfotos haben einen Tag Schonfrist.

## Datenbank und Bilder

- Listen laden nur Anzeigespalten und eine begrenzte Seite; Details erst beim
  Öffnen. Indizes nur für tatsächliche Abfragen, belegt mit `EXPLAIN QUERY PLAN`.
- Fotos als Dateien; in SQLite nur Referenz und Metadaten. Vorhandene BLOBs bleiben.
- Datenbank-, Datei- und Bildarbeit nie auf dem UI-Thread.
- Antwortzeit und Speicher am Pixel messen; Emulatorwerte sind kein Nachweis.

## Geräte und Pflanzen

- Gerätefotos gehören zum Gerät. Zubehör liegt im Gerätedatensatz (`accessories`);
  `current_chain_id` zeigt auf eine Kette derselben Maschine.
- Wartungsintervalle und ihr Verlauf bleiben erhalten.
- Pflanzenquelle ist allein `plant-catalog.json` mit den Android-Bildern.

## Oberfläche

Ziel: jede wichtige Aufgabe in höchstens zwei Tipps.

- **Roter Faden:** Kunde → Objekt → Anfrage → Angebot → Auftrag → Einsatz →
  Arbeit → Abrechnung → Zahlung. Detailseiten zeigen oben „Gehört zu“ (Kunde,
  Objekt, Auftrag, Einsatz) als antippbare Chips; Verknüpfungen in beide Richtungen.
- **Objekt als Anker:** Arbeiten gehören zu einem Objekt; das Objekt zeigt seinen
  Verlauf von der Anfrage bis zur Zahlung.
- **Navigation:** Heute · Planung · Kunden · Abrechnung · Büro. Ein „+“ für alles
  Neue, eine Suche für alles.
- **Heute:** Einsätze des Tages mit Uhrzeit und „Start“, darunter nur Punkte mit
  Handlungsbedarf.
- **Planung:** ein Objekt „Einsatz“; Termin, Tour und Pflegeplan sind Ansichten.
- **Abrechnung:** Reiter Offen · Rechnungen & Labor · Summen.
- **Laufende Zeit:** Benachrichtigung mit Pause, Foto und Stopp; Schnellkachel und
  App-Verknüpfungen.
- **Farben im Theme:** Waldgrün = Handeln, Bernstein = Geld, Gelb = Prüfen,
  Ziegelrot nur für Löschen und Fehler. Keine Farbwerte im Feature-Code.
- **Bausteine:** Material-3-Standardkomponenten. Große Schrift muss funktionieren.
- **Texte:** kurz und deutsch, keine technischen Zustände, keine Textwände;
  Hinweise einmal zeigen, danach als ⓘ.
- **Rückmeldung:** Erfolg als kurze Snackbar, Fehler am Ort. Leere Listen nennen
  die erste Aktion.
- **Eingabe:** passende Tastatur (Telefon, E-Mail, Zahl), Spracheingabe in
  Textfeldern, zuletzt Verwendetes oben.
- **Prüfungen:** unklare Vorgänge automatisch intern prüfen, ohne doppelte
  Ausführung; nur echte Konflikte zeigen.

## Verbindungen

Kalender, Wetter und die ChatGPT-Verbindung sind optional. Eine Statusprüfung löst
keinen Kalenderabgleich und keine Wetterabfrage aus. Keine sevDesk- oder
Bankanbindung, keine Pflichtcloud.
