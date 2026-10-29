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

Sicherung bewahrt den vorhandenen Datenbestand unverändert in einem für die App
wieder lesbaren Format. Rechnungsnummernkollisionen und die fachliche Gültigkeit
des Finanzbestands sind keine Voraussetzung für Speichern oder Wiederherstellen.
Die Sicherung vergibt, korrigiert oder entfernt keine Rechnungsnummern. Sie prüft
die technische Lesbarkeit, Vollständigkeit und unveränderte Übertragung der Daten,
einschließlich Datenbank, Originaldateien, Format und Prüfsummen.

Wiederherstellung erfolgt ausdrücklich, nach vollständiger Archivprüfung und mit
dem vorhandenen Rückweg bei Fehlern. Sicherungsfehler blockieren die lokale
Geschäftserfassung nicht. Ein echter Speichern-/Wiederherstellen-Nachweis auf dem
Pixel ist getrennt zu führen; Kompilierung ersetzt ihn nicht.

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
