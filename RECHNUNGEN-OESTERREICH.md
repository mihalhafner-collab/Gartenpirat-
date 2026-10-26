# Österreichische Kleinunternehmerrechnungen

## Fortschreibung vom 23. Oktober 2026

Der historische Abschnitt vom 8. Oktober bleibt als datierter Nachweis erhalten.
Seine Laptop-Pflicht ist ersetzt: Der aktuelle Pixelstand verlangt einen direkten
Pixel-/Drive-Onlinevergleich (`pixel_drive_online_compare_required`). Der beauftragte
Zielbetrieb vergibt Nummern und erstellt PDFs ausschließlich lokal in der App;
Drive dient dann nur verschlüsselten Sicherungen. Die bestehende Ausgabesperre bleibt
bis zur kontrollierten Umstellung mit aktuellem Beleg-/Reservierungs-/Nummernnachweis
und Ausschluss anderer Schreiber bestehen. Ein Backupzähler oder der historische
sevDesk-Abruf unten reichen dafür nicht aus. Noch keine produktive Offlinefreigabe.

Am 23.10.2026 erneut tatsächlich gelesen:

- [RIS: §11 UStG, geltende Normfassung](https://www.ris.bka.gv.at/eli/bgbl/1994/663/P11/NOR40273699):
  elektronische Rechnung mit Empfängerzustimmung; Herkunft, Inhaltsintegrität und
  Lesbarkeit während der Aufbewahrung. Grundsätzlich sieben Jahre; Nachweise und
  längere Sonderfristen beachten.
- [USP/BMF: vereinfachte Rechnungen](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/weitere-informationen-zur-umsatzsteuer/vorsteuerabzug-und-rechnung/kleinbetragsrechnungen.html),
  Stand 01.01.2026: Vereinfachungen bei Gesamtbetrag bis400EUR oder tatsächlich
  anwendbarer Kleinunternehmerbefreiung; EU-/Versandhandelsausnahmen. Ausführlichere
  Angaben bleiben möglich. Die Anwendung der Befreiung wird nicht aus Testdaten
  abgeleitet und kein bestätigtes Aussteller-/Steuerprofil automatisch geändert.
- [USP: Rechnung](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/rechnung.html):
  Pflichtangaben und konkrete Dokumentart bleiben Teil der Ausgabekontrolle;
  strukturierte Bundesrechnungen sind gesondert zu behandeln.

Der erste gezielte RIS-Tagesabruf in diesem Task scheiterte; die obige kanonische
Normfassung wurde danach erfolgreich gelesen. Das ist Quellenprüfung, keine
vollständige steuerliche Abnahme. Die148EUR-Probe mit fiktiv0Prozent ist ausschließlich
synthetische Rechenprüfung, keine Kleinunternehmer- oder Steuerentscheidung.

Die Android-App besitzt inzwischen einen lokalen `PdfDocument`-Pfad. Die historische
ReportLab-Angabe unten beschreibt den älteren Hostpfad und keine Betriebsabhängigkeit.
PDF-Erstellung allein beweist weder sichere lokale Nummernvergabe noch erfolgreiche
Übergabe/Zustellung. Unveränderliche Snapshots, Quellmengen, Wiederanlauf und die
Restore-Ausgabesperre sind vor Freigabe gesondert zu prüfen. Vorhandene Korrektur-/
Stornobeziehungen erhalten; die ältere pauschale Aussage fehlender Korrekturen unten
ist keine aktuelle Bestandsbeschreibung.

## Historischer Stand vom 8. Oktober 2026


Recherche und Umsetzung: 8. Oktober 2026. Geltungsbereich: gewöhnliche österreichische Inlandsleistungen eines umsatzsteuerbefreiten Kleinunternehmers. Kein rechtliches Zertifikat und keine vollständige Steuerberatung.

## Grundlage

- Keine Umsatzsteuer ausweisen. Die Befreiung muss tatsächlich anwendbar sein; die App kann Umsätze außerhalb ihrer Datenbasis oder einen früher erklärten Verzicht auf die Befreiung nicht prüfen. Die Grenze beträgt grundsätzlich 55.000 Euro im laufenden und vorangegangenen Kalenderjahr; die 10-%-Toleranz hat eigene Folgen für laufendes und folgendes Jahr.
- Hinweis: „Umsatzsteuerfrei aufgrund der Kleinunternehmerregelung gemäß § 6 Abs. 1 Z 27 UStG.“ Das ist eine Befreiung, kein steuerpflichtiger Umsatz zum Steuersatz 0 %.
- Seit 2025 sind für befreite Kleinunternehmer vereinfachte Rechnungen unabhängig vom Betrag zulässig. Die App verwendet bewusst ausführlichere Angaben: Aussteller und Empfänger samt Anschriften, Leistungsart und Umfang/Menge, Leistungsdatum/-zeitraum, Entgelt, Ausstellungsdatum, eindeutige laufende Nummer und Befreiungshinweis. Eine UID wird für diesen Standardfall nicht verlangt.
- Eine IBAN und ein Zahlungsziel sind praktische, nicht allgemeine umsatzsteuerliche Pflichtangaben. Für eingetragene Unternehmen/Rechtsformen können weitere Angaben erforderlich sein; diese müssen unter den rechtlichen Ausstellerdaten ergänzt werden.
- Elektronische Rechnungen erfordern Zustimmung des Empfängers (auch schlüssig möglich), nachvollziehbare Herkunft, unveränderten Inhalt und Lesbarkeit. Die tatsächliche Zustellung wird von der E-Mail-App erledigt; „Teilen“ beweist noch keine Zustellung.
- Rechnungen und Nachweise grundsätzlich sieben Jahre aufbewahren, gerechnet ab Ende des betreffenden Kalenderjahres; in Sonderfällen gelten längere Fristen. Nicht manuell aus Datenbank, Drive oder Sicherungen löschen.

## Ablauf

1. Kunde und Leistungszeitraum wählen. Fehlende Rechnungsadresse im Kundenprofil ergänzen.
2. Offene Arbeiten in Positionen übernehmen. Preise, Materialmengen und Fahrtkosten bewusst prüfen; keine Tarife werden geraten.
3. Entwurf speichern. PDF-Vorschau und Pflichtangaben prüfen. Bestätigen, dass die Kleinunternehmerbefreiung noch gilt und kein Sonderfall vorliegt.
4. Separat abschließen. SQLite-Transaktion vergibt die Nummer, archiviert PDF und eingefrorene Rechnungsangaben und markiert verknüpfte Arbeiten als abgerechnet. Wiederholter Abschluss derselben Rechnung vergibt keine neue Nummer.
5. PDF öffnen, herunterladen oder über die Android-Teilen-Auswahl versenden. Der Versand wird nicht automatisch als erfolgreich verbucht.

Nummernformat: RE-95/2026 usw.; laufende Zahl wird jahresübergreifend fortgesetzt. Aktueller sevDesk-Stand wurde am 8. Oktober 2026 ausschließlich lesend geprüft: 95 Datensätze, höchste Nummer 94. Ab der Übernahme keine neuen Rechnungen parallel in sevDesk nummerieren. Ohne eine gemeinsame zentrale Nummernvergabe lässt sich ein gleichzeitiger externer Nummernkonflikt technisch nicht ausschließen.

Android-Abschluss benötigt einen laufenden Laptop und Drive-Abgleich. Bei fehlenden Angaben bleibt es beim Entwurf; keine Rechnung wird ausgestellt. PDF und Daten sind innerhalb der App gegen Überschreiben/Löschen geschützt; SHA-256 erkennt abweichende PDF-Dateien. Dies ersetzt keine extern revisionssichere Archivierung oder steuerliche Prüfung. Korrekturen/Storno, Ausland, Reverse Charge, Anzahlungs-/Schlussrechnungen und strukturierte Rechnungen an den Bund werden nicht automatisiert unterstützt.

## Quellen

- [USP/BMF: Kleinunternehmen seit 2025](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/weitere-informationen-zur-umsatzsteuer/weitere-steuertatbestaende-und-befreiungen/kleinunternehmen.html)
- [USP/BMF: Kleinbetragsrechnungen und vereinfachte Rechnungslegung](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/weitere-informationen-zur-umsatzsteuer/vorsteuerabzug-und-rechnung/kleinbetragsrechnungen.html)
- [WKO: Kleinunternehmerregelung](https://www.wko.at/steuern/kleinunternehmerregelung-umsatzsteuer)
- [WKO: Rechnung richtig ausstellen](https://www.wko.at/steuern/rechnung-richtig-ausstellen)
- [USP/BMF: Aufbewahrungspflicht](https://www.usp.gv.at/themen/steuern-finanzen/steuerliche-gewinnermittlung/weitere-informationen-zur-steuerlichen-gewinnermittlung/betriebliches-rechnungswesen/aufbewahrungspflicht.html)
- [USP/BMF: elektronische Rechnungen](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/weitere-informationen-zur-umsatzsteuer/vorsteuerabzug-und-rechnung/e-rechnung.html)

PDF-Bibliothek: kostenlose lokale ReportLab-Abhängigkeiten im App-Ordner `.dependencies`; keine kostenpflichtige API. Keine echten Rechnungen wurden während der Entwicklung ausgestellt oder versendet.
