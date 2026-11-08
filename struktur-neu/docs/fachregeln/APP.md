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

## Geräte

- Gerätefotos gehören zum Gerät. Zubehör liegt im Gerätedatensatz (`accessories`);
  `current_chain_id` zeigt auf eine Kette derselben Maschine.
- Wartungsintervalle und ihr Verlauf bleiben erhalten.

## Oberfläche

Verbindlich ist der Entwurf „Gartenpirat Optik-Entwürfe“ (Claude, 06.11.2026);
dieser Abschnitt hält ihn fest. Abweichungen nur mit Freigabe des Eigentümers.
Ziel: jede wichtige Aufgabe in höchstens zwei Tipps.

- **Roter Faden:** Kunde → Objekt → Anfrage → Angebot → Auftrag → Einsatz →
  Arbeit → Abrechnung → Zahlung. Arbeiten gehören zu einem Objekt.
- **Navigation:** genau vier Reiter Heute · Planung · Kunden · Abrechnung. Kein
  Reiter „Büro“; Seltenes (Arbeiten, Archiv, Geräte, Material, Einstellungen,
  Sicherung) liegt im ⋮-Menü der Kopfzeile oder unter „Mehr“ in Kunden.
- **Heute:** Titel ist das Datum („Freitag, 6. Nov.“), darunter „3 Einsätze ·
  ca. 6 Std. · 14 km Tour“ und rechts ein Wetter-Chip, wenn Wetter aktiv ist.
  Dann die Einsätze des Tages in Tourreihenfolge: Uhrzeit als linke Spalte, Titel,
  Kunde · Objekt; der nächste hervorgehoben mit „▶ Start“, die anderen mit „Route“.
  Darunter „Zu erledigen“ nur bei Bedarf: offene Stunden (Bernstein, € ›),
  fällige Wartung, neue Anfragen, fehlgeschlagene Sicherung (Gelb, ! ›).
  Keine Kennzahlen-Kacheln, keine „Gespeicherten Arbeiten“, keine
  „Weiteren Tageshinweise“, keine untere Knopfleiste.
- **„+“:** fester Knopf unten rechts auf allen vier Reitern. Öffnet „Neu anlegen“
  mit sechs festen Zielen in dieser Reihenfolge: Arbeit nachtragen, Anfrage,
  Kunde, Einsatz, Foto, Material; darunter „Zuletzt: Kunde · Objekt“. Auf einem
  Kunden oder Objekt wird der Zusammenhang vorausgefüllt.
- **Suche:** ein Suchfeld oben auf Heute und Kunden mit Mikrofon. Ergebnisse
  nach Art gruppiert (Objekte, Kunden, Abschlüsse, Arbeiten) mit Anrufen und
  Route direkt im Ergebnis; ohne Eingabe zuletzt Geöffnetes.
- **Detailseiten:** Titel ist der Inhalt (z. B. „Hecke geschnitten“), nicht der
  Bereichsname. Oben „Gehört zu“-Chips (Kunde, Objekt, Auftrag, Einsatz), jeder
  antippbar; Verknüpfungen in beide Richtungen. Status als Chip. Angaben in
  Gruppen wie auf einem Lieferschein; leere Felder gesammelt als „+ ergänzen“.
  Unten die nächste sinnvolle Aktion (z. B. Bearbeiten · Abrechnen). Seltene
  Aktionen im ⋮-Menü; „Löschen“ ganz unten, eigene Zeile, Ziegelrot mit Symbol.
- **Objekt:** Kopf mit Foto oder Farbfläche, Knöpfe Route · Anrufen · Einsatz
  planen, Reiter Verlauf · Pflege · Fotos · Geld. Verlauf als Zeitleiste von der
  Anfrage bis zur Zahlung (voll = erledigt, leer = geplant).
- **Planung:** ein Objekt „Einsatz“; Ansichten Tag · Woche · Karte. Verschieben
  durch langes Drücken und Ziehen; Wetterwarnung im Raster. Tour mit
  nummerierten Stopps, „Reihenfolge“ und „Navigation starten“ (Übergabe an die
  Karten-App, keine eigene Kartendienst-Anbindung).
- **Abrechnung:** Reiter Offen · Abschlüsse · Summen. Offen: Bernstein-Karte
  „Noch nicht abgerechnet“, je Rechnungsempfänger Kürzel, Arbeiten, Stunden,
  Fortschrittsbalken fertig → abgerechnet → bezahlt und „Abrechnen“. „Abrechnen“
  öffnet den vorbefüllten Entwurf (Umschalter Rechnung | Labor clandestino,
  Abwählen statt Hinzufügen), dann Vorschau mit einem Knopf „Ausstellen“.
- **Listen:** Kürzel-Avatar, rechts der wichtigste Zustand (offene Stunden in
  Bernstein oder nächster Termin). Wischen nach rechts = häufigste positive
  Aktion (bezahlt, erledigt), nach links = löschen; ausgestellte Rechnungen
  lassen sich nicht wegwischen. Danach Snackbar mit „Rückgängig“ (5 s).
- **Laufende Zeit:** große Uhr mit gleich breiten Ziffern, Tätigkeit als Chips,
  Foto · Material · Gerät als drei Knöpfe, unten Pause · Zeit stoppen.
  Benachrichtigung mit Pause, Foto und Stopp; Schnellkachel und App-Verknüpfungen.
- **Farben im Theme:** Waldgrün = Handeln, Bernstein = Geld, Gelb = Prüfen,
  Ziegelrot nur für Löschen und Fehler. Keine Farbwerte im Feature-Code. Dunkle
  Ansicht nach Systemeinstellung (optional).
- **Bausteine:** Material-3-Standardkomponenten. Große Schrift muss funktionieren.
- **Texte:** kurz und deutsch, keine technischen Zustände, keine Textwände;
  Hinweise einmal zeigen, danach als ⓘ.
- **Rückmeldung:** Erfolg als kurze Snackbar, Fehler am Ort. Leere Listen nennen
  in einem Satz den Zweck und bieten die erste Aktion an.
- **Eingabe:** passende Tastatur (Telefon, E-Mail, Zahl), Spracheingabe in
  Textfeldern, zuletzt Verwendetes oben.
- **Prüfungen:** unklare Vorgänge automatisch intern prüfen, ohne doppelte
  Ausführung; nur echte Konflikte zeigen. Wiederherstellung als Hinweisbalken,
  nicht als Sperre.

## Verbindungen

Kalender, Wetter und die ChatGPT-Verbindung sind optional. Eine Statusprüfung löst
keinen Kalenderabgleich und keine Wetterabfrage aus. Keine sevDesk- oder
Bankanbindung, keine Pflichtcloud.
