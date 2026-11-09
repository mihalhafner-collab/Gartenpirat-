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
  **Rechnung** und **Labor clandestino** (nur diese Schreibweise). Die Funktion
  Labor bleibt unverändert erhalten. Der Abschluss ist die einzige Quelle für
  den Abrechnungsstand.
- „Nicht abrechnen“ an einer Arbeit (Probe, Kulanz) nimmt sie aus „Offen“, ohne
  sie zu löschen; jederzeit zurücknehmbar.
- „Abrechnen“ öffnet den Entwurf mit allen offenen Arbeiten des Empfängers
  (abwählen statt hinzufügen; Umschalter Rechnung | Labor clandestino). Der Entwurf ist zugleich die Vorschau; „Ausstellen“
  sitzt direkt darunter, das PDF lässt sich vorher ansehen. Danach öffnet sich
  das Teilen-Menü; Labor ohne Nummer und ohne PDF. Abgewählte Arbeiten bleiben offen.
- Preise aus dem Stundensatz in den Einstellungen bzw. dem Leistungskatalog;
  antippbar änderbar. Ohne Preis bleibt das Feld leer und rot, „Ausstellen“ ist
  gesperrt. Abrechnungstakt minutengenau, wählbar 0,1 Std. oder 15 Min.
- Rechnungsdaten als eine Gruppe in den Einstellungen: Logo, Name, Anschrift,
  Kontakt, Steuer-Nr., IBAN/BIC, Zahlungsziel, Nummernkreis. Fehlt etwas, ist
  „Ausstellen“ gesperrt, mit Sprung zum Feld.
- Die Zustimmung zur elektronischen Rechnung wird beim ersten Teilen einmal
  gefragt und am Kunden mit Datum gespeichert; dort widerrufbar.
- PDF im Aufbau der bisherigen sevDesk-Rechnungen mit Logo Hafner Grünraum
  Handwerk, eine Summenzeile „Gesamtbetrag“, jede Arbeit eine Position mit Datum.
- „Bezahlt“ mit einem Tippen (offener Betrag, heutiges Datum), gebucht erst nach
  5 s „Rückgängig“; gebuchte Zahlungen sind unlöschbar. Anzeige
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

Verbindlich ist das Designkonzept „Gartenpirat Optik-Entwürfe“ mit Farbkonzept
(Claude, Stand 08.11.2026); dieser Abschnitt hält es fest. Abweichungen nur mit
Freigabe des Eigentümers. Ziel: jede häufige Aufgabe in höchstens zwei Tipps.

- **Roter Faden:** Kunde → Objekt → Anfrage → Angebot → Auftrag → Einsatz →
  Arbeit → Abrechnung → Zahlung. Arbeiten gehören zu einem Objekt.
- **Grundregeln:** eine Hauptaktion je Bildschirm, unten rechts, gefüllt in
  Indigo; eine Nebenaktion links daneben; Seltenes im ⋮-Menü. Häufiges liegt im
  unteren Drittel. Rückgängig statt Rückfrage; vorbefüllt statt eingetippt.
- **Navigation:** genau vier Reiter Heute · Planung · Kunden · Abrechnung. Alles
  andere liegt im ⋮-Menü oben rechts, auf allen vier Reitern gleich: Vorgänge
  (Anfragen, Angebote, Aufträge, Arbeiten), Betrieb (Geräte,
  Material), App (Sicherung, Einstellungen). Kein „Mehr“, kein Reiter „Büro“.
  Zurück geht immer eine Ebene; jeder Reiter merkt sich seine Stelle.
- **Heute:** Titel ist das Datum, darunter Anzahl Einsätze und Stunden, rechts
  ein Wetter-Chip, wenn Wetter aktiv ist. Dann die Einsätze in Tourreihenfolge:
  Uhrzeit als linke Spalte, Titel, Kunde · Objekt; der nächste hervorgehoben mit
  „Start“, das nach dem Tippen am selben Ort zu „Stopp“ wird. Darunter „Zu
  erledigen“ nur bei Bedarf: offene Stunden (Orange), fällige Wartung, neue
  Anfragen, fällige Zahlungen (Magenta), fehlgeschlagene Sicherung. Ohne
  Einsätze ist „Arbeit starten“ der große Knopf. Keine Kilometer.
- **„+“:** fester Knopf unten rechts auf allen vier Reitern. Öffnet „Neu anlegen“:
  oben „Arbeit starten“, darunter sechs feste Ziele in dieser Reihenfolge:
  Arbeit nachtragen, Anfrage, Kunde, Einsatz, Foto, Material; darunter „Zuletzt:
  Kunde · Objekt“. Auf einem Kunden oder Objekt wird der Zusammenhang vorausgefüllt.
- **Suche:** ein Suchfeld oben auf Heute und Kunden mit Mikrofon. Ergebnisse
  nach Art gruppiert mit Anrufen und Route direkt im Ergebnis; ohne Eingabe
  zuletzt Geöffnetes.
- **Detailseiten:** Titel ist der Inhalt, nicht der Bereichsname. Oben „Gehört
  zu“-Chips (Kunde, Objekt, Auftrag, Einsatz), jeder antippbar. Status als Chip.
  Angaben in Gruppen wie auf einem Lieferschein. Tippen zum Ändern: jede Zeile
  öffnet ein kleines Blatt für genau diesen Wert, gespeichert beim Schließen mit
  „Rückgängig“; kein Knopf „Bearbeiten“. Unten die nächste sinnvolle Aktion.
  „Löschen“ ganz unten, eigene Zeile, Ziegelrot mit Symbol.
- **Kundenakte:** Anrufen · Route · Abrechnen; Reiter Objekte · Vorgänge · Geld.
  Vorgänge in der Reihenfolge des roten Fadens; E-Rechnung-Zustimmung sichtbar.
- **Objekt:** Kopf mit Foto oder Farbfläche, Knöpfe Route · Anrufen · Einsatz
  planen, Reiter Verlauf · Pflege · Fotos · Geld. Verlauf als Zeitleiste von der
  Anfrage bis zur Zahlung (voll = erledigt, leer = geplant). Fläche, Gartenteile
  und Foto freiwillig; fehlt etwas, entfällt die Zeile.
- **Planung:** für den Anwender nur „Einsatz“; Kalendertermin, Tour und Pflegeplan
  erzeugen bzw. zeigen Einsätze. Ansichten Tag · Woche. Verschieben durch langes
  Drücken und Ziehen; ein verknüpfter Kalendertermin zieht mit; war er zugesagt,
  folgt „Kunde informieren“ in Magenta. Wetterwarnung im Raster. Tag als Tour:
  nummerierte Stopps, „Reihenfolge“, „Navigation starten“ (Adressen an die
  Karten-App). Keine eigene Karte, keine Koordinaten.
- **Abrechnung:** eine Seite. Oben drei Zahlen in einer Zeile (ausgestellt ·
  bezahlt · offen) mit Umschalter Monat | Jahr, darunter ein dünner Balken;
  Labor clandestino als eigene Summe darunter. Dann „Offen abzurechnen“ je
  Rechnungsempfänger mit Kürzel, Arbeiten, Stunden und „Abrechnen“; „Zahlung
  offen“ mit „fällig seit …“; „Alle Abschlüsse ›“.
- **Listen:** eine Fläche je Gruppe, Zeilen 42 dp mit Trennlinie: links Zeit,
  Kürzel-Avatar (feste Farbe aus dem Namen) oder Punkt, Mitte zwei Zeilen Text,
  rechts genau ein Zustand. Wischen nach rechts = häufigste positive Aktion
  (bezahlt, erledigt), nach links = löschen; ausgestellte Rechnungen lassen sich
  nicht wegwischen. Danach Snackbar mit „Rückgängig“ (5 s); eine neue Aktion
  schließt die vorige ab.
- **Laufende Zeit:** schmale Laufleiste über den Reitern auf allen Bildschirmen
  (Tätigkeit, Zeit, Pause, Stopp). Zeiterfassung mit großer Uhr in gleich
  breiten Ziffern, Tätigkeit als Chips, Foto · Material · Gerät als drei Knöpfe.
  „Stopp“ öffnet das Blatt „Arbeit speichern“, alles vorbefüllt, zuletzt
  genutzte Tätigkeit vorgewählt; Normalfall ein Tipp „Speichern“. Benachrichtigung
  mit Pause, Foto und Stopp; Schnellkachel und App-Verknüpfungen.
- **Farben im Theme** (sechs Rollen aus dem Logo, je eine Bedeutung):
  Nachtindigo `#3138a0` (Fläche `#e7e8fb`, dunkel `#a9aefc`) = Handeln: Knöpfe,
  aktiver Reiter, „+“. Bonsai-Grün `#2c7738` (`#e3f2e5`, `#7cc98a`) = Erledigt:
  gespeichert, bezahlt. Sonnenorange `#b4470b` (`#fff0e3`, `#ffa564`) = Geld:
  offene Stunden, Beträge. Magenta `#9a1b8d` (`#fbe7f8`, `#ea8fde`) = Fällig:
  fällig, neue Anfrage, Wartung. Ziegelrot `#b42318` (`#fdecea`, `#ff8a7a`) nur
  für Löschen und Fehler. Schiefer `#5d6178` (Grund `#f3f4f8`, `#a3a6bd`) =
  Neutral, Nebentext; Labor gestrichelt. Text auf hellen Flächen mindestens
  4,5:1. Keine Farbwerte im Feature-Code. Dunkle Ansicht mit denselben Rollen
  nach Systemeinstellung (optional, zuletzt).
- **Bausteine:** Material-3-Komponenten mit Material-Icons statt Zeichen oder
  Emojis. Knöpfe kompakt, 30 dp hoch (klein 26 dp), rechtsbündig, nie über die
  volle Breite; Tippfläche immer 48 dp. Status-Chips klein und eckig gerundet.
  Kennzahlen als drei Zahlen in einer Zeile statt Hero-Karten. Kurzes Vibrieren
  bei Start, Stopp, Ausstellen und Bezahlt.
- **Große Schrift:** Uhrzeit rutscht über den Titel, Knöpfe werden breit statt
  abgeschnitten; gekürzt wird nur Text mit „…“, nie Beträge oder Uhrzeiten. Die
  Reiterleiste beschriftet dann nur den aktiven Reiter.
- **Texte:** kurz und deutsch, keine technischen Zustände, keine Hinweistexte.
  Beschriftung, Chip und Zahl erklären den Zustand. Ausnahmen: Fehler am Feld
  und der einzeilige Zweck einer leeren Liste.
- **Rückmeldung:** Erfolg als kurze Snackbar mit Sprung zum Ergebnis; Fehler rot
  am Feld mit Ursache und Lösung in einem Satz, Eingaben bleiben erhalten. Leere
  Listen nennen in einem Satz den Zweck und bieten die erste Aktion an. Listen
  zeigen sofort den letzten Stand, ein dünner Balken läuft beim Nachladen.
- **Eingabe:** passende Tastatur (Telefon, E-Mail, Zahl), Spracheingabe in
  Textfeldern, Textbausteine als Chips, zuletzt Verwendetes oben.
- **Prüfungen:** unklare Vorgänge automatisch intern prüfen, ohne doppelte
  Ausführung; nur echte Konflikte zeigen (beide Stände, je „Diesen behalten“).
  Wiederherstellung als Hinweisbalken, nicht als Sperre.
- **Funktionsumfang:** kein Papierkorb und kein Archivieren; Löschen mit
  Rückgängig ersetzt beides. Doppelte Wege werden zusammengelegt (ein „Einsatz“,
  eine Suche, ein Fotobereich, „Annehmen“ legt den Auftrag an). Ziel rund 90
  statt 174 Aktionen. Zusammengelegte Aktionen bekommen ein gemeinsames
  MCP-Werkzeug; alte Werkzeugnamen bleiben eine Version lang als Verweis lesbar.
- **Entschieden (KISS):** Arbeiten tragen ihr Objekt (`property_id`, vorhanden);
  hat der Kunde nur ein Objekt, ist es vorausgewählt. Material nur als Verbrauch
  an der Arbeit, kein Bestand; Geräte nur mit Wartung, keine Nutzung je Einsatz.
  Was im Konzept nicht logisch und sinnvoll umsetzbar ist, wird gelöscht statt
  aufgeschoben.

## Verbindungen

Kalender, Wetter und die ChatGPT-Verbindung sind optional. Eine Statusprüfung löst
keinen Kalenderabgleich und keine Wetterabfrage aus. Keine sevDesk- oder
Bankanbindung, keine Pflichtcloud.
