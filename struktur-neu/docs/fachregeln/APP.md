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

Wiederherstellung erfolgt ausdrücklich, nach vollständiger Archivprüfung und mit
dem vorhandenen Rückweg bei Fehlern. Sicherungsfehler blockieren die lokale
Geschäftserfassung nicht. Ein echter Speichern-/Wiederherstellen-Nachweis auf dem
Pixel ist getrennt zu führen; Kompilierung ersetzt ihn nicht.

Statusanzeigen ordnen Versuche nach tatsächlichem Zeitpunkt, unabhängig von
zufälligen Kennungen und Listenseiten. Ein neuerer Fehler bleibt sichtbar, auch
wenn eine ältere Sicherung bestätigt ist. Eine gespeicherte Capture-Phase allein
beweist keinen laufenden Worker. Der Selbsttest unterscheidet fehlende Nachweise,
Fehler und protokolliertes Dateirücklesen; letzteres ist keine neue Dateiprüfung
und kein Restore-Test. Nummernkollisionen bleiben Integritätsfehler. Deren genauer
Fehlercode darf weder Finanzdaten ausgeben noch Belegnummern verändern.

## Rechnungen und Datenübernahme

Die bestehende Prüfung von Integrität, Nummern und Originalbelegen bleibt wirksam.
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
