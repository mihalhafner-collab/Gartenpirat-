# Rechnungsablauf 1.24 - Stand 8. Oktober 2026

## Umsetzung

- Tatsächliche sevDesk-Vorlage RE-94/2026 ausschließlich lesend heruntergeladen und visuell geprüft. Rechnung übernimmt A4-Aufbau, Open Sans, Logo rechts, Empfänger links, Metadaten rechts, grau hinterlegte Positionen-/Summenköpfe und mehrspaltige Fußzeile. Keine pixelgenaue Gleichheit zugesichert: Steuerhinweis und rechtlich bestätigte Ausstellerdaten bleiben maßgeblich. Nicht bestätigte Gerichts-/Steuerangaben aus der alten Fußzeile wurden nicht übernommen.
- Bestätigte N26-Bankdaten übernommen. Kein tatsächlicher Rechnungsversand während der Entwicklung.
- Gemeinsame Vorbereitung von Arbeitspositionen für Android und Laptop. Tätigkeiten, Datum, Einsatzort, Stunden, Material und Kilometer; interne Notizen bleiben intern. Preise werden nicht aus Freitext geraten. Materialmenge 1 pauschal ist eine zu prüfende Vorgabe, keine aus Notizen abgeleitete Menge.
- Direkter Einstieg „Rechnung erstellen“ aus Arbeitsaufzeichnungen. Weitere Arbeiten desselben Kunden im Entwurf auswählbar. Kunde und Leistungszeitraum werden vorgeschlagen, Positionen chronologisch aufgebaut.
- Keine doppelte Übernahme derselben Aufzeichnung. Nach Entfernen erneut übernehmbar. Kundenwechsel entfernt fremde verknüpfte Positionen nach Bestätigung. Tatsächlich vorhandene Positionsverknüpfungen bestimmen die abzurechnenden Arbeitsaufzeichnungen.
- Desktop: große feste Speichern-Aktion, mehrzeilige Leistungsbeschreibung, E-Mail-/WhatsApp-Aktionen. Wenn der Browser PDF-Teilen unterstützt, über den Teilen-Dialog. Andernfalls PDF-Download und ausdrücklicher Hinweis, das PDF im geöffneten E-Mail-/WhatsApp-Programm anzuhängen. Kein SMTP-Dienst und keine Zustellbestätigung.
- Android: getrennte E-Mail-, WhatsApp- und allgemeine PDF-Teilen-Aktionen mit schreibgeschütztem PDF-Anhang. WhatsApp und WhatsApp Business berücksichtigt. E-Mail-Empfänger aus dem eingefrorenen Rechnungsdatensatz; endgültige Kontrolle und Senden erfolgen in der Versand-App. Ohne passende installierte Versand-App klare Fehlermeldung.
- Android: Rechnungsentwürfe auch über Langdruck bearbeitbar. Falsche Zuordnung beim Wisch-Löschen durch Listenindex ersetzt durch Datensatz-ID. Keine Bearbeitung mehr während eines vorgemerkten Abschlusses.
- Zurück schließt zunächst den Rechnungsentwurf; ungespeicherte Eingaben werden nicht still verworfen.
- Drive: Abgleich serialisiert gegen parallele Verarbeitung. Fehler eines Eintrags verhindern nicht alle folgenden Einträge. Abgelehnter Rechnungsabschluss bleibt Entwurf; Ablehnung wird dauerhaft erfasst, damit dieselbe Anfrage nicht später automatisch ausstellt. Neue Prüfung und ausdrückliche neue Bestätigung erforderlich. Android zeigt Ursache an und gibt den Entwurf wieder frei.
- Mit Freigabe vollständiger Datenabgleich aktiviert. Vorher vorhandene fünf Lösch-IDs wurden auf Halte-Liste gesetzt; zwei davon waren bereits früher bestätigt. Drei noch offene Löschaufträge werden nicht automatisch ausgeführt. Keine bestehenden Datensätze gegenüber der Sicherung entfernt; neue mobile Aufzeichnungen/Fotos übernommen.

## Schutz und Grenzen

95 bestehende Rechnungen unverändert; keine echte Rechnung erstellt oder versendet. SQLite-Sicherung vor Änderungen: data/backups/pre-v1.24-invoices.db. IDs und Beziehungen bleiben erhalten; keine neue Migration nötig.

Die Haus-&-Garten-App kann der zentrale Ort für Kunden, Arbeiten, Aufträge und gewöhnliche österreichische Kleinunternehmer-Ausgangsrechnungen sein. Sie ist noch KEIN vollständiger sevDesk-Ersatz für Buchhaltung: Zahlungseingänge/Teilzahlungen mit Belegjournal, Eingangsbelege und Ausgaben, Storno/Korrektur, Mahnungen, Bankabgleich, steuerlicher Export und Sonderfälle fehlen. Keine parallele neue Nummerierung in sevDesk. Keine rechtliche Zertifizierung oder Garantie revisionssicherer Archivierung. Bei laufender Kleinunternehmerbefreiung keine Umsatzsteuer ausweisen.

PDF-Erstellung und verbindlicher Abschluss benötigen weiterhin die laufende Laptop-App. Android kann unterwegs offline Entwürfe erstellen und sie anschließend über Drive zur Prüfung übergeben. Drive-Abgleich in Android hängt vom gewählten Dokumentanbieter ab; reale E-Mail-/WhatsApp-Übergabe und Wiederaufnahme auf dem Pixel müssen am Gerät geprüft werden.

## Prüfungen

13 isolierte Python-Funktionstests und gemeinsame JavaScript-Prüfungen bestanden, einschließlich mehrseitiger Rechnungen. Android-APK gebaut; Versions-/Signaturprüfung durchgeführt. Gemeinsame JavaScript-Dateien auf Android und Laptop identisch. PDF-Probe gerendert und visuell geprüft. Laptop-Einstieg aus existierender Arbeitsaufzeichnung im Browser geprüft, ohne zu speichern oder auszustellen. Pixel-Gerät nicht angeschlossen; kein realer Versandtest behauptet.

Die drei zunächst vorgemerkten Fotos wurden am 08.10.2026 nach ausdrücklicher Benutzerfreigabe nur ihrem bestehenden Kunden „Hochrindl“ zugeordnet. Die fehlenden Arbeitsaufzeichnungen wurden nicht neu angelegt. Ursprüngliche Zuordnungen und Bildprüfsummen sind in data/Fotozuordnung-bestaetigt-2026-10-08.json dokumentiert; Datenbank, Importbestätigungen und mobiles Originalpaket wurden vorher gesichert. Alle drei IDs bleiben erhalten (identischer Bildinhalt; keine automatische Zusammenführung). Drive-Snapshot enthält die Fotos ohne Arbeits-/Auftragsbezug, alle drei Importbestätigungen und keine Importfehler. Alte Löschaufträge bleiben zurückgehalten.

## Sinnvolle nächste Verbesserungen

1. Zahlungseingang, Teilzahlung und Korrektur/Storno mit unveränderlichem Belegverlauf.
2. Eingangsbelege/Ausgaben mit Kunden-/Projektbezug, Export für Steuerberatung.
3. Laptop-unabhängige, gleichwertige Prüfung und PDF-Vorschau auf Android; sichere gemeinsame Nummernvergabe statt zweier Aussteller.
4. Preisvorlagen nach ausdrücklicher Festlegung von Stundensatz, Material- und Kilometerpreisen.
5. Konfliktprüfung bei gleichzeitiger Bearbeitung desselben Eintrags auf Laptop und Pixel; derzeit werden bestätigte Aktionen nacheinander verarbeitet, aber es gibt keinen vollständigen Versionsabgleich.

Diese Punkte sind nicht als bereits umgesetzt zu verstehen.
