# Gültiger App-Vertrag

## Betrieb und gemeinsame Fachlogik

Die App auf dem Pixel führt die Geschäftsdaten lokal. Oberfläche und MCP verwenden
dieselben nativen Fachpfade. Kunden, Einsatzobjekte und Rechnungsempfänger sind
unterschiedliche Beziehungen. Auftragslose Arbeiten bleiben möglich. Persönliches
„b.b./bezahlt“ ist vom Rechnungsstatus getrennt. Fehlende Daten, Preise, Zeiten,
Beziehungen und Belegnummern nicht erfinden.

## Speichern und Löschen

Ein unberührtes Formular erzeugt keinen Entwurf. Bearbeitung ist ein Entwurf;
bestätigtes Speichern erzeugt einen Geschäftssatz. Verwerfen entfernt den Entwurf
vollständig. Nach unklarer Antwort dieselbe Vorgangskennung und Argumente erhalten
und zuerst lesen. Ein bestätigter Geschäftssatz bleibt bei einem späteren Foto-,
Oberflächen- oder Sicherungsfehler erhalten.

Löschen, Archivieren und Verwerfen haben unterschiedliche Bedeutung. Referenzen,
ausgestellte Belege und erforderliche Aufbewahrung vor dem Löschen berücksichtigen.
Neustart, verspätete Antworten und Wiederherstellung dürfen verworfene Inhalte
nicht unbeabsichtigt wieder aktivieren.

## Lokale Sicherung

Neue Sicherungen sind unverschlüsselte ZIP-Dateien auf dem Pixel: Speicherknopf,
Android-Dateiauswahl und geprüfte Rücklesung der gespeicherten Datei. Dafür gibt
es keine Kontoeinrichtung, Cloud-Zielwahl oder automatischen Uploadjob.
Vorhandene ältere Archive und Schlüssel bleiben für eine bewusst beauftragte
Wiederherstellung erhalten. Deren Lesbarkeit rechtfertigt keine neue komplizierte
Einrichtung für aktuelle Sicherungen.

Sicherung kopiert den vorhandenen Bestand unverändert. Die direkte Aufnahme umfasst
alle SQLite-Tabellen, die vorhandenen privaten Dateien, typisierte Einstellungen
und den zuletzt lesbaren Formularstand. Die bisherige Sicherungs-Auftragsverwaltung
ist entfernt; es werden nur letzter Versuch und letzter Erfolg gespeichert.
Rechnungsnummernkollisionen, bereits fehlerhafte Datenbezüge, fehlende oder nicht
verifizierte Originale verhindern das Sichern und Wiederherstellen nicht. Konkrete
Datensatzkennungen und Gründe stehen in `data/backup-notes.json`; Oberfläche und
MCP nennen die Anzahl der Hinweise. Ist eine Bestandsübersicht nicht auswertbar,
bleiben Rohdaten erhalten und die fehlende Auswertbarkeit wird ausdrücklich genannt.
Eine Sicherung mit Hinweisen bestätigt keine vollständige Verfügbarkeit aller
referenzierten Originale. Sie repariert oder normalisiert den Ausgangsbestand nicht.

Technisch unlesbare Datenbanken, Schreibfehler und beschädigte oder veränderte
Archive bleiben echte Fehler. Ein Erfolg setzt das vollständige Zurücklesen der
geschriebenen ZIP voraus. Bestätigte Ziele werden bei Wiederholung nur geprüft;
eine neue Sicherung benötigt eine neue Zieldatei. Temporäre Aufnahmen werden nach
dem Versuch entfernt. Es gibt keine Verbindung zur Migrationsprüfung oder zur
Rechnungsfreigabe. Der Leser älterer verschlüsselter Archive bleibt ausschließlich
für bereits vorhandene Altsicherungen erhalten; er erzeugt keine neuen Sicherungen.

Wiederherstellung erfolgt ausdrücklich nach technischer Archivprüfung und mit
einer Rückfallkopie des aktuellen Bestands. Der bestehende atomare Dateiaustausch
mit Wiederanlauf und Rollback bleibt erhalten. Sicherungsfehler blockieren die
Geschäftserfassung nicht. Ein echter Pixel-Nachweis ist getrennt von JVM- und
synthetischen Emulatorprüfungen zu führen.

Statusanzeigen ordnen Versuche nach tatsächlichem Zeitpunkt, unabhängig von
zufälligen Kennungen und Listenseiten. Ein neuerer Fehler bleibt sichtbar, auch
wenn eine ältere Sicherung bestätigt ist. Eine gespeicherte Capture-Phase allein
beweist keinen laufenden Worker. Der Selbsttest unterscheidet fehlende Nachweise,
Fehler und protokolliertes Dateirücklesen; letzteres ist keine neue Dateiprüfung
und kein Restore-Test. Fehlercodes dürfen keine Finanzdaten ausgeben.

## Rechnungen und Datenübernahme

Die Rechnungsfunktion ist bei künftigen Rechnungserstellungen für korrekte und
eindeutige Nummern verantwortlich. Ihre Prüfungen von Integrität, Nummern und
Originalbelegen bleiben wirksam. Eine erfolgreiche Sicherung oder Wiederherstellung
bestätigt keine fachliche Gültigkeit der vorhandenen Rechnungen.
Eine allgemeine lokale Datenautorität ist keine Rechnungsfreigabe. Ausgestellte
Rechnungsangaben und Original-PDFs erhalten; wiederholtes Bestätigen darf keine
zweite Nummer vergeben. Preise und Pflichtangaben müssen vor Abschluss feststehen.
Teilen eines PDFs belegt keinen erfolgreichen Versand. Gesetzliche Aufbewahrung
beachten; bisherige Nummern oder externe Kontowerte nicht als aktuelle Vorgabe kopieren.

Produktive Migration, Rechnungsfreigabe, Installation und Wiederherstellung werden
nicht durch die Repositorybereinigung ausgelöst. Die noch fehlenden Nachweise
stehen im aktuellen Arbeitsstand.

## Pflanzen und Geräte

`android/app/src/main/assets/plant-catalog.json` und die dort referenzierten Bilder
sind die verteilte Pflanzenquelle. Die erzeugten Webspiegel über
`tools/sync_plant_assets.py` pflegen. Fachquellen, Bildrechte und die vom vorhandenen
Importwerkzeug benötigten redaktionellen Eingaben bleiben in `libraries/plants/`.

Gerätefotos gehören zum Gerät. Abgebrochene Auswahl und verworfene Bearbeitung
dürfen keine Geschäftsdaten erzeugen. Bestehende Wartungsintervalle und ihr Verlauf
bleiben erhalten. Der Wortlautkatalog wird über das vorhandene Werkzeug erzeugt.

## Bedienung und Listenfilter

Der Nutzer hat kleine Hände: häufige Aktionen am unteren Bildschirmrand gut
erreichbar anbieten, mit kompakten, gut treffbaren Schaltflächen statt übergroßer
Buttons. Aktionsleisten müssen Platz im Layout erhalten und dürfen weder Inhalte
noch Tastatur oder Systemnavigation verdecken; Systemabstände und verkleinerte
Bildschirmhöhe beachten. Die vorhandene Suche bleibt erhalten.
Kunden nach aktiv/archiviert und Abrechnungsweg, Geräte nach vorhandenem Zustand,
Aufträge und Rechnungen nach vorhandenem Status und Kunde filtern. Aktive Auswahl,
Trefferzahl und einfaches Zurücksetzen sichtbar halten. Keine gespeicherten
Filterprofile oder zusätzlichen Datenmodelle.
